package com.suiteg.app.pdf

import android.graphics.Bitmap
import android.util.LruCache

/** Clave de una imagen ya rasterizada. Los flotantes se cuantizan para que "casi iguales" coincidan. */
data class ClaveRender(
    val pagina: Int,
    val escalaMilesimas: Int,
    val origenX: Int,
    val origenY: Int,
    val ancho: Int,
    val alto: Int,
) {
    companion object {
        fun de(p: ParametrosRender) = ClaveRender(
            p.pagina,
            Math.round(p.escala * 1000f),
            Math.round(p.origenX),
            Math.round(p.origenY),
            p.ancho,
            p.alto,
        )
    }
}

/**
 * Caché LRU de imágenes de página limitada por BYTES (no por número): con partituras escaneadas, una sola
 * imagen puede pesar 16 MB. Nunca se llama a `recycle()` sobre una imagen expulsada, porque podría estar
 * dibujándose en ese momento; el recolector libera su memoria nativa cuando nadie la referencia.
 */
class CacheBitmaps(maxBytes: Int) {
    private val lru = object : LruCache<ClaveRender, Bitmap>(maxBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: ClaveRender, value: Bitmap): Int = value.allocationByteCount
    }

    fun obtener(k: ClaveRender): Bitmap? = lru.get(k)
    fun poner(k: ClaveRender, b: Bitmap) { lru.put(k, b) }
    fun contiene(k: ClaveRender): Boolean = lru.snapshot().containsKey(k)
    fun vaciar() = lru.evictAll()
    val bytesEnUso: Int get() = lru.size()

    companion object {
        /**
         * Presupuesto: 3 imágenes de ventana (actual, siguiente, anterior), pero sin pasar de la cuarta parte
         * de la memoria que Android concede a la app, y nunca menos de 2 (actual + siguiente).
         */
        fun presupuesto(anchoVentana: Int, altoVentana: Int, memoriaClaseMb: Int): Int {
            val una = anchoVentana.toLong() * altoVentana * 4L
            val tope = memoriaClaseMb * 1024L * 1024L / 4L
            return minOf(3 * una, maxOf(tope, 2 * una)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
