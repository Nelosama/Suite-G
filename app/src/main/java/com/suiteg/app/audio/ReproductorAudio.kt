package com.suiteg.app.audio

import android.media.MediaPlayer

/** Reproductor sencillo de una grabación, con búsqueda de posición. */
class ReproductorAudio {
    private var player: MediaPlayer? = null
    var alTerminar: (() -> Unit)? = null
    var alError: ((String) -> Unit)? = null

    val reproduciendo: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)
    val posicionMs: Int get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    /** Duración en ms según el decodificador (aproximada en flujos ADTS), o 0 si no se conoce. */
    val duracionMs: Int get() = runCatching { player?.duration?.coerceAtLeast(0) ?: 0 }.getOrDefault(0)

    fun cargar(ruta: String): Boolean {
        liberar()
        return try {
            player = MediaPlayer().apply {
                setDataSource(ruta)
                prepare()
                setOnCompletionListener { alTerminar?.invoke() }
                setOnErrorListener { _, _, _ -> alError?.invoke("No se pudo reproducir la grabación"); true }
            }
            true
        } catch (e: Exception) {
            liberar()
            alError?.invoke("No se pudo abrir la grabación (${e.message ?: "error"})")
            false
        }
    }

    fun alternar() {
        val p = player ?: return
        runCatching { if (p.isPlaying) p.pause() else p.start() }
    }

    fun buscar(ms: Int) {
        runCatching { player?.seekTo(ms.coerceAtLeast(0)) }
    }

    fun liberar() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }
}
