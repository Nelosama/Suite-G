package com.suiteg.modelo

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Herramienta { LAPIZ, RESALTADOR }

/**
 * Trazo vectorial en coordenadas normalizadas de página.
 *
 * [puntos] guarda tripletas (x, y, presión): x e y en fracción del ancho/alto de la página (0..1),
 * presión en 0..1. [grosor] es fracción del ANCHO de la página, para que escale con el zoom.
 */
class Trazo(
    val id: Long = 0,
    val pagina: Int,
    val herramienta: Herramienta,
    val color: Int,
    val grosor: Float,
    val puntos: FloatArray,
) {
    init {
        require(puntos.size % 3 == 0) { "puntos debe contener tripletas x,y,presión" }
    }

    val numPuntos: Int get() = puntos.size / 3

    /**
     * Distancia mínima (en unidades de ancho de página) de (x, y) al trazo.
     * [aspecto] = alto/ancho de la página, para medir en espacio isótropo.
     */
    fun distanciaA(x: Float, y: Float, aspecto: Float): Float {
        if (numPuntos == 0) return Float.MAX_VALUE
        var mejor = Float.MAX_VALUE
        for (i in 0 until numPuntos) {
            val d = hypot(puntos[i * 3] - x, (puntos[i * 3 + 1] - y) * aspecto)
            mejor = min(mejor, d)
            if (i > 0) {
                mejor = min(mejor, distanciaSegmento(i - 1, i, x, y, aspecto))
            }
        }
        return mejor
    }

    private fun distanciaSegmento(a: Int, b: Int, x: Float, y: Float, aspecto: Float): Float {
        val ax = puntos[a * 3]; val ay = puntos[a * 3 + 1] * aspecto
        val bx = puntos[b * 3]; val by = puntos[b * 3 + 1] * aspecto
        val px = x; val py = y * aspecto
        val dx = bx - ax; val dy = by - ay
        val largo2 = dx * dx + dy * dy
        if (largo2 == 0f) return hypot(px - ax, py - ay)
        val t = max(0f, min(1f, ((px - ax) * dx + (py - ay) * dy) / largo2))
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}

/** Serialización binaria compacta de los puntos de un trazo (big-endian, float32). */
object CodecTrazos {
    private const val MAGIA = 0x5347 // "SG"
    private const val VERSION: Byte = 1

    fun codificar(puntos: FloatArray): ByteArray {
        require(puntos.size % 3 == 0)
        val buf = ByteBuffer.allocate(2 + 1 + 4 + puntos.size * 4).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(MAGIA.toShort())
        buf.put(VERSION)
        buf.putInt(puntos.size / 3)
        for (v in puntos) buf.putFloat(v)
        return buf.array()
    }

    /** Devuelve null si los datos están corruptos (nunca lanza): un trazo dañado no debe tumbar la página. */
    fun decodificar(bytes: ByteArray): FloatArray? {
        if (bytes.size < 7) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (buf.short.toInt() != MAGIA.toShort().toInt()) return null
        if (buf.get() != VERSION) return null
        val n = buf.int
        // Cada punto ocupa 12 bytes; se valida en Long para evitar desbordes con datos corruptos.
        if (n < 0 || buf.remaining().toLong() != n.toLong() * 12L) return null
        val salida = FloatArray(n * 3)
        for (i in salida.indices) salida[i] = buf.float
        return salida
    }
}
