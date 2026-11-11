package com.suiteg.app.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.suiteg.modelo.CalculoMiniatura
import java.io.File
import java.io.IOException
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Miniaturas de la primera página de cada partitura, para la biblioteca.
 *
 * Se renderizan con el mismo [MotorPdf] que el lector (sin motor nuevo) y se guardan como PNG en
 * [carpeta], así que solo se rasterizan una vez por partitura. En memoria hay una caché LRU limitada por bytes.
 * Si el PDF no se puede leer, devuelve null y la biblioteca muestra el icono de siempre.
 *
 * Solo se renderiza una miniatura a la vez: [PdfRendererMotor] abre una página por motor y la biblioteca puede
 * pedir muchas a la vez al abrirse. El mismo cerrojo protege el borrado, para que una miniatura que se está
 * generando no reaparezca en disco después de eliminar su partitura.
 *
 * Los cálculos puros (tamaño, nombres de archivo, huérfanos) están en [CalculoMiniatura] y se prueban en la JVM.
 */
class Miniaturas(private val carpeta: File) {
    private val memoria = object : LruCache<String, Bitmap>(PRESUPUESTO_MEMORIA_BYTES) {
        override fun sizeOf(clave: String, valor: Bitmap): Int = valor.allocationByteCount
    }
    private val exclusiva = Mutex()

    /**
     * Partituras cuya miniatura falló en esta ejecución. Sin esto, un PDF que el motor no puede abrir se
     * reintentaría cada vez que su fila vuelve a la pantalla al hacer scroll. Solo vive en memoria: en el
     * siguiente arranque se vuelve a intentar.
     */
    private val fallidas: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * Miniatura ya cargada en memoria, sin trabajo ni espera. La biblioteca la usa como valor inicial para que
     * el icono no parpadee cuando una fila sale de la pantalla y vuelve.
     */
    fun enMemoria(hash: String): Bitmap? = memoria.get(hash)

    /** Miniatura de [hash] (el archivo PDF está en [rutaPdf]), o null si no se puede generar. */
    suspend fun obtener(hash: String, rutaPdf: String): Bitmap? {
        memoria.get(hash)?.let { return it }
        if (hash in fallidas) return null
        return withContext(Dispatchers.IO) {
            exclusiva.withLock {
                memoria.get(hash) ?: leerDeDisco(hash) ?: generar(hash, rutaPdf)
            }
        }
    }

    /** Quita la miniatura (de disco y de memoria) de una partitura eliminada. */
    suspend fun borrar(hash: String) = withContext(Dispatchers.IO) {
        exclusiva.withLock {
            memoria.remove(hash)
            fallidas.remove(hash)
            archivo(hash).delete()
            temporal(hash).delete()
        }
    }

    /**
     * Borra de disco los temporales de escrituras cortadas y las miniaturas de partituras que ya no están en la
     * biblioteca (p. ej. si la app murió entre borrar la partitura y su miniatura). Se llama al abrir la biblioteca.
     */
    suspend fun limpiarHuerfanos(hashesVigentes: Set<String>) = withContext(Dispatchers.IO) {
        exclusiva.withLock {
            val nombres = carpeta.list()?.toList() ?: return@withLock
            for (nombre in CalculoMiniatura.huerfanos(nombres, hashesVigentes)) {
                runCatching { File(carpeta, nombre).delete() }
            }
        }
    }

    private fun archivo(hash: String) = File(carpeta, CalculoMiniatura.nombreArchivo(hash))
    private fun temporal(hash: String) = File(carpeta, CalculoMiniatura.nombreTemporal(hash))

    private fun leerDeDisco(hash: String): Bitmap? {
        val f = archivo(hash)
        if (!f.exists()) return null
        val bmp = try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: OutOfMemoryError) {
            null
        }
        if (bmp == null) {
            // PNG truncado o ilegible: se borra y se regenera desde el PDF.
            f.delete()
            return null
        }
        memoria.put(hash, bmp)
        return bmp
    }

    private suspend fun generar(hash: String, rutaPdf: String): Bitmap? {
        val bmp = try {
            PdfRendererMotor.abrir(File(rutaPdf)).use { motor ->
                val pagina = motor.tamanoPagina(0)
                val tam = CalculoMiniatura.calcular(pagina.ancho, pagina.alto) ?: return@use null
                motor.renderizar(ParametrosRender(pagina = 0, escala = tam.escala, origenX = 0f, origenY = 0f, ancho = tam.ancho, alto = tam.alto))
            }
        } catch (e: CancellationException) {
            // La fila salió de la pantalla: no es un fallo del PDF, se reintentará cuando vuelva.
            throw e
        } catch (t: Throwable) {
            // PDF dañado, motor nativo con error u OutOfMemoryError: no es fatal, la biblioteca usa el icono.
            null
        }
        if (bmp == null) {
            fallidas += hash
            return null
        }
        memoria.put(hash, bmp)
        // Si no se puede guardar (disco lleno…) la miniatura sigue valiendo en memoria; se reintenta otro día.
        try {
            carpeta.mkdirs()
            guardarPng(bmp, hash)
        } catch (e: Exception) {
            temporal(hash).delete()
        }
        return bmp
    }

    private fun guardarPng(bmp: Bitmap, hash: String) {
        val tmp = temporal(hash)
        val destino = archivo(hash)
        tmp.outputStream().use { out ->
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("No se pudo guardar la miniatura")
        }
        if (!tmp.renameTo(destino)) {
            tmp.copyTo(destino, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        /** 8 MB: unas 25 miniaturas A4 (240 x 340 ARGB) en memoria a la vez. */
        private const val PRESUPUESTO_MEMORIA_BYTES = 8 * 1024 * 1024
    }
}
