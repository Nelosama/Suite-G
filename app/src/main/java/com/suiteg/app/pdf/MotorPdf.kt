package com.suiteg.app.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Tamaño de una página en puntos PDF (1/72 de pulgada), ya con la rotación del propio PDF aplicada. */
data class TamanoPagina(val ancho: Float, val alto: Float)

/**
 * Qué se quiere ver: la página [pagina] a [escala] píxeles por punto, con el borde izquierdo/superior de la
 * ventana en ([origenX], [origenY]) píxeles de la página escalada. El resultado es una imagen del tamaño de la
 * ventana ([ancho] x [alto]).
 */
data class ParametrosRender(
    val pagina: Int,
    val escala: Float,
    val origenX: Float,
    val origenY: Float,
    val ancho: Int,
    val alto: Int,
)

/**
 * Motor de lectura de PDF. Detrás de esta interfaz se puede cambiar PdfRenderer por PDFium sin tocar
 * nada más (ver docs/DECISIONES.md).
 */
interface MotorPdf : AutoCloseable {
    val numPaginas: Int

    /** Tamaño ya conocido (sin trabajo), o null si todavía no se ha leído. */
    fun tamanoCacheado(pagina: Int): TamanoPagina?

    suspend fun tamanoPagina(pagina: Int): TamanoPagina

    suspend fun renderizar(p: ParametrosRender): Bitmap
}

/**
 * Implementación sobre [PdfRenderer]. Este solo permite UNA página abierta a la vez y no es seguro entre
 * hilos, así que todas las operaciones pasan por un único hilo dedicado: abrir, rasterizar y cerrar la página
 * es una operación atómica, y las peticiones se atienden en orden.
 */
class PdfRendererMotor private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val hilo: CoroutineDispatcher,
    private val ejecutor: java.util.concurrent.ExecutorService,
) : MotorPdf {

    override val numPaginas: Int = renderer.pageCount
    private val tamanos = arrayOfNulls<TamanoPagina>(numPaginas)
    @Volatile private var cerrado = false

    override fun tamanoCacheado(pagina: Int): TamanoPagina? = tamanos.getOrNull(pagina)

    override suspend fun tamanoPagina(pagina: Int): TamanoPagina {
        tamanoCacheado(pagina)?.let { return it }
        return withContext(hilo) {
            tamanos[pagina] ?: renderer.openPage(pagina).use { p ->
                TamanoPagina(p.width.toFloat(), p.height.toFloat()).also { tamanos[pagina] = it }
            }
        }
    }

    override suspend fun renderizar(p: ParametrosRender): Bitmap = withContext(hilo) {
        check(!cerrado) { "Motor cerrado" }
        val bmp = Bitmap.createBitmap(p.ancho, p.alto, Bitmap.Config.ARGB_8888)
        try {
            // Margen gris claro y papel blanco; los modos de inversión se aplican después, al dibujar.
            bmp.eraseColor(COLOR_MARGEN)
            renderer.openPage(p.pagina).use { pagina ->
                tamanos[p.pagina] = TamanoPagina(pagina.width.toFloat(), pagina.height.toFloat())
                val izq = -p.origenX
                val arr = -p.origenY
                Canvas(bmp).drawRect(izq, arr, izq + pagina.width * p.escala, arr + pagina.height * p.escala, Paint().apply { color = Color.WHITE })
                // Con transform explícito, PdfRenderer lo usa tal cual: puntos de página -> píxeles del bitmap.
                val m = Matrix().apply {
                    setScale(p.escala, p.escala)
                    postTranslate(-p.origenX, -p.origenY)
                }
                pagina.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            bmp
        } catch (t: Throwable) {
            bmp.recycle() // todavía no se ha entregado a nadie: es seguro liberarlo
            throw t
        }
    }

    override fun close() {
        if (cerrado) return
        cerrado = true
        // Se cierra en el mismo hilo que renderiza, para no pisar un render en curso.
        ejecutor.execute {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
            ejecutor.shutdown()
        }
    }

    companion object {
        private val COLOR_MARGEN = Color.rgb(0xC8, 0xC8, 0xC8)

        /** Abre el PDF. Lanza si está dañado, cifrado o no existe: quien llama lo convierte en un aviso. */
        fun abrir(archivo: File): PdfRendererMotor {
            val pfd = ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount <= 0) {
                    renderer.close()
                    throw IllegalStateException("El PDF no tiene páginas")
                }
                val ejecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "pdf-render").apply { priority = Thread.NORM_PRIORITY + 1 } }
                return PdfRendererMotor(pfd, renderer, ejecutor.asCoroutineDispatcher(), ejecutor)
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                throw t
            }
        }
    }
}
