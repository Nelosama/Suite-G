package com.suiteg.navegacion

import com.suiteg.modelo.Posicion
import com.suiteg.modelo.PuntoGiro
import com.suiteg.modelo.TipoGiro

/**
 * Una "vista" es cada parada del pedal: una posición del documento.
 * [compases] es la duración musical de la vista (opcional; la usa el avance por tempo).
 */
data class Vista(
    val posicion: Posicion,
    val compases: Int? = null,
    val origen: TipoGiro? = null,
)

/**
 * Lista ordenada de vistas por las que avanza el pedal.
 *
 * - Sin puntos de giro: una vista por página.
 * - Con puntos de giro: SOLO esos puntos (más el inicio del documento si no hay uno marcado). El pedal
 *   no salta a páginas intermedias que el usuario no haya marcado.
 */
class SecuenciaDeVistas private constructor(
    val numPaginas: Int,
    val vistas: List<Vista>,
) {
    val tamano: Int get() = vistas.size
    val ultimo: Int get() = vistas.size - 1

    operator fun get(indice: Int): Vista = vistas[indice]

    /** Índice de la última vista cuya posición es <= [p] (la que "contiene" a p). Mínimo 0. */
    fun indiceDe(p: Posicion): Int {
        var lo = 0
        var hi = vistas.size - 1
        var res = 0
        while (lo <= hi) {
            val medio = (lo + hi) ushr 1
            if (menorOIgual(vistas[medio].posicion, p)) {
                res = medio
                lo = medio + 1
            } else {
                hi = medio - 1
            }
        }
        return res
    }

    private fun menorOIgual(a: Posicion, b: Posicion): Boolean =
        a.pagina < b.pagina || (a.pagina == b.pagina && a.y <= b.y + EPS)

    companion object {
        /** Dos puntos de la misma página más cerca que esto se consideran el mismo. */
        const val EPS = 0.002f

        fun construir(numPaginas: Int, puntos: List<PuntoGiro>): SecuenciaDeVistas {
            require(numPaginas > 0) { "El documento debe tener al menos una página" }
            // Puntos fuera del documento se descartan (p. ej. si cambió el número de páginas).
            val validos = puntos
                .filter { it.posicion.pagina < numPaginas }
                .map { p ->
                    // Un giro "por página" siempre cae al inicio de la página.
                    val pos = if (p.tipo == TipoGiro.PAGINA) Posicion(p.posicion.pagina, 0f) else p.posicion
                    Vista(pos, p.compases?.takeIf { it > 0 }, p.tipo)
                }
                .sortedBy { it.posicion }

            if (validos.isEmpty()) {
                return SecuenciaDeVistas(numPaginas, List(numPaginas) { Vista(Posicion(it, 0f)) })
            }

            val fusionadas = ArrayList<Vista>(validos.size + 1)
            for (v in validos) {
                val previa = fusionadas.lastOrNull()
                if (previa != null && previa.posicion.pagina == v.posicion.pagina &&
                    v.posicion.y - previa.posicion.y < EPS
                ) {
                    // Duplicado: se conserva el primero, salvo que el segundo aporte los compases.
                    if (previa.compases == null && v.compases != null) {
                        fusionadas[fusionadas.lastIndex] = previa.copy(compases = v.compases)
                    }
                } else {
                    fusionadas += v
                }
            }
            if (fusionadas.first().posicion > Posicion(0, EPS)) {
                fusionadas.add(0, Vista(Posicion(0, 0f)))
            }
            return SecuenciaDeVistas(numPaginas, fusionadas)
        }
    }
}
