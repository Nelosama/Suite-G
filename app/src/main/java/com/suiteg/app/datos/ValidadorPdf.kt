package com.suiteg.app.datos

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface ResultadoValidacion {
    data class Correcto(val paginas: Int) : ResultadoValidacion
    data class Fallo(val motivo: String) : ResultadoValidacion
}

private const val MSG_VALIDAR = 1
private const val MSG_RESULTADO = 2
private const val K_RUTA = "ruta"
private const val K_OK = "ok"
private const val K_PAGINAS = "paginas"
private const val K_ERROR = "error"

/**
 * Servicio en un proceso aparte (`:validador`) que abre el PDF y rasteriza una muestra de sus páginas en
 * miniatura. Si el motor nativo se cae con un PDF malformado, muere ESTE proceso, no la app: el cliente lo
 * detecta con `onServiceDisconnected` y devuelve [ResultadoValidacion.Fallo].
 */
class ServicioValidador : Service() {
    private lateinit var hilo: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        hilo = HandlerThread("validador").also { it.start() }
        messenger = Messenger(object : Handler(hilo.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what != MSG_VALIDAR) return
                val ruta = msg.data.getString(K_RUTA).orEmpty()
                val respuesta = Message.obtain(null, MSG_RESULTADO)
                respuesta.data = validar(ruta)
                runCatching { msg.replyTo?.send(respuesta) }
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        hilo.quitSafely()
        super.onDestroy()
    }

    private fun validar(ruta: String): Bundle {
        val r = Bundle()
        try {
            ParcelFileDescriptor.open(File(ruta), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val n = renderer.pageCount
                    if (n <= 0) throw IllegalStateException("El PDF no tiene páginas")
                    for (i in muestra(n)) {
                        renderer.openPage(i).use { p ->
                            val w = 96
                            val h = (w * p.height.toFloat() / p.width.coerceAtLeast(1)).toInt().coerceIn(16, 4096)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp.recycle()
                        }
                    }
                    r.putBoolean(K_OK, true)
                    r.putInt(K_PAGINAS, n)
                }
            }
        } catch (e: Exception) {
            r.putBoolean(K_OK, false)
            r.putString(K_ERROR, mensajeAmigable(e))
        }
        return r
    }

    /** Hasta [MAX_PAGINAS_MUESTRA] páginas repartidas por el documento, incluidas la primera y la última. */
    private fun muestra(n: Int): List<Int> {
        if (n <= MAX_PAGINAS_MUESTRA) return (0 until n).toList()
        return (0 until MAX_PAGINAS_MUESTRA).map { it * (n - 1) / (MAX_PAGINAS_MUESTRA - 1) }.distinct()
    }

    private fun mensajeAmigable(e: Exception): String = when (e) {
        is SecurityException -> "El PDF está protegido con contraseña o cifrado"
        is java.io.IOException -> "El PDF está dañado o incompleto"
        else -> "No se pudo abrir el PDF (${e.message ?: e.javaClass.simpleName})"
    }

    companion object {
        const val MAX_PAGINAS_MUESTRA = 40
    }
}

class ClienteValidador(private val contexto: Context) {
    suspend fun validar(archivo: File): ResultadoValidacion {
        val r = withTimeoutOrNull(TIEMPO_MAXIMO_MS) {
            suspendCancellableCoroutine<ResultadoValidacion> { cont ->
                var conexion: ServiceConnection? = null
                fun terminar(res: ResultadoValidacion) {
                    conexion?.let { c -> runCatching { contexto.unbindService(c) } }
                    conexion = null
                    if (cont.isActive) cont.resume(res)
                }
                val respuestas = Messenger(Handler(Looper.getMainLooper()) { msg ->
                    if (msg.what == MSG_RESULTADO) {
                        val d = msg.data
                        terminar(
                            if (d.getBoolean(K_OK)) ResultadoValidacion.Correcto(d.getInt(K_PAGINAS))
                            else ResultadoValidacion.Fallo(d.getString(K_ERROR) ?: "PDF no válido"),
                        )
                    }
                    true
                })
                conexion = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        val msg = Message.obtain(null, MSG_VALIDAR).apply {
                            data = Bundle().apply { putString(K_RUTA, archivo.absolutePath) }
                            replyTo = respuestas
                        }
                        try {
                            Messenger(binder).send(msg)
                        } catch (e: Exception) {
                            terminar(ResultadoValidacion.Fallo("No se pudo contactar con el validador"))
                        }
                    }

                    // El proceso del validador murió a mitad de trabajo: el PDF rompió el motor nativo.
                    override fun onServiceDisconnected(name: ComponentName) {
                        terminar(ResultadoValidacion.Fallo("El PDF es defectuoso: el visor se cerró al procesarlo"))
                    }

                    override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
                    override fun onNullBinding(name: ComponentName) {
                        terminar(ResultadoValidacion.Fallo("Validador no disponible"))
                    }
                }
                val ok = contexto.bindService(Intent(contexto, ServicioValidador::class.java), conexion!!, Context.BIND_AUTO_CREATE)
                if (!ok) terminar(ResultadoValidacion.Fallo("Validador no disponible"))
                cont.invokeOnCancellation { conexion?.let { c -> runCatching { contexto.unbindService(c) } } }
            }
        }
        return r ?: ResultadoValidacion.Fallo("El PDF tardó demasiado en procesarse")
    }

    companion object {
        private const val TIEMPO_MAXIMO_MS = 120_000L
    }
}
