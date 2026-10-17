package com.suiteg.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.suiteg.tempo.PatronMetronomo
import com.suiteg.tempo.SecuenciadorMetronomo
import kotlin.math.max

/**
 * Salida de audio del metrónomo. Un hilo escribe sin parar en un [AudioTrack] de baja latencia el flujo PCM
 * que genera [SecuenciadorMetronomo]; así los golpes los marca el reloj de muestras del audio (preciso y sin
 * deriva), no un temporizador de la interfaz.
 */
class MetronomoAudio {
    @Volatile private var patron: PatronMetronomo? = null
    @Volatile private var volumen = 0.8f
    @Volatile private var activo = false
    private var hilo: Thread? = null

    /** Se invoca (desde el hilo de audio) si el dispositivo no puede reproducir. */
    var alError: ((String) -> Unit)? = null

    val encendido: Boolean get() = activo

    @Synchronized
    fun iniciar(p: PatronMetronomo, vol: Float) {
        patron = p
        volumen = vol
        if (activo) return
        activo = true
        hilo = Thread({ ejecutar() }, "metronomo").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** Cambia BPM, compás o volumen sin cortar el sonido. Surte efecto en el siguiente golpe. */
    fun actualizar(p: PatronMetronomo, vol: Float = volumen) {
        patron = p
        volumen = vol
    }

    @Synchronized
    fun detener() {
        activo = false
        hilo?.let { runCatching { it.join(800) } }
        hilo = null
    }

    private fun ejecutar() {
        var pista: AudioTrack? = null
        try {
            val inicial = patron ?: return
            val frecuencia = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: 44_100
            val minimo = AudioTrack.getMinBufferSize(frecuencia, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            pista = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(frecuencia)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minimo, frecuencia / 10 * 2)) // al menos 100 ms
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            val secuenciador = SecuenciadorMetronomo(frecuencia, inicial, volumen)
            val trozo = ShortArray(frecuencia / 100) // 10 ms por escritura
            var aplicado = inicial
            pista.play()
            while (activo) {
                val p = patron
                if (p != null && p != aplicado) {
                    secuenciador.establecerPatron(p)
                    aplicado = p
                }
                secuenciador.establecerVolumen(volumen)
                secuenciador.llenar(trozo)
                if (pista.write(trozo, 0, trozo.size) < 0) break // la escritura bloquea hasta que hay hueco: marca el ritmo
            }
        } catch (e: Exception) {
            alError?.invoke("No se pudo iniciar el metrónomo (${e.message ?: e.javaClass.simpleName})")
        } finally {
            activo = false
            pista?.let { runCatching { it.stop() }; runCatching { it.release() } }
        }
    }
}
