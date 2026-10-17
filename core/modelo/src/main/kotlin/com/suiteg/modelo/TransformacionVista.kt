package com.suiteg.modelo

import kotlin.math.max
import kotlin.math.min

/** Cómo se ajusta la página a la ventana. */
enum class AjusteVista { PAGINA_COMPLETA, ANCHO }

/**
 * Transformación entre coordenadas normalizadas de página (0..1 en x e y) y píxeles de la ventana.
 *
 * Las anotaciones se guardan en coordenadas de página, así que dibujarlas o capturarlas pasa siempre
 * por esta clase. Girar el dispositivo o hacer zoom solo cambia la transformación, no los datos.
 *
 * @param escala píxeles de pantalla por punto PDF (1/72 pulgada).
 * @param origenX posición, en píxeles de la página escalada, que queda en el borde izquierdo de la ventana.
 * @param origenY ídem para el borde superior (negativo si la página se centra y sobra espacio).
 */
data class TransformacionVista(
    val anchoPaginaPt: Float,
    val altoPaginaPt: Float,
    val escala: Float,
    val origenX: Float,
    val origenY: Float,
) {
    val anchoPaginaPx: Float get() = anchoPaginaPt * escala
    val altoPaginaPx: Float get() = altoPaginaPt * escala

    fun xAPantalla(nx: Float): Float = nx * anchoPaginaPx - origenX
    fun yAPantalla(ny: Float): Float = ny * altoPaginaPx - origenY
    fun xAPagina(px: Float): Float = (px + origenX) / anchoPaginaPx
    fun yAPagina(py: Float): Float = (py + origenY) / altoPaginaPx

    companion object {
        const val ZOOM_MAXIMO = 4f

        /**
         * Calcula la transformación para mostrar la página dentro de una ventana.
         *
         * @param yInicio fracción de la altura de la página que debe quedar arriba (solo cuenta si la
         *   página escalada es más alta que la ventana, p. ej. con ajuste [AjusteVista.ANCHO]).
         * @param zoom multiplicador del usuario sobre el ajuste base (>= 1).
         * @param desplazX desplazamiento adicional (pan) en píxeles.
         */
        fun ajustar(
            anchoPaginaPt: Float,
            altoPaginaPt: Float,
            anchoVentana: Float,
            altoVentana: Float,
            ajuste: AjusteVista,
            yInicio: Float = 0f,
            zoom: Float = 1f,
            desplazX: Float = 0f,
            desplazY: Float = 0f,
        ): TransformacionVista {
            require(anchoPaginaPt > 0 && altoPaginaPt > 0) { "Tamaño de página inválido" }
            require(anchoVentana > 0 && altoVentana > 0) { "Tamaño de ventana inválido" }
            val z = zoom.coerceIn(1f, ZOOM_MAXIMO)
            val base = when (ajuste) {
                AjusteVista.PAGINA_COMPLETA -> min(anchoVentana / anchoPaginaPt, altoVentana / altoPaginaPt)
                AjusteVista.ANCHO -> anchoVentana / anchoPaginaPt
            }
            val escala = base * z
            val pw = anchoPaginaPt * escala
            val ph = altoPaginaPt * escala
            val origenX = origenEje(pw, anchoVentana, 0.5f * (pw - anchoVentana) + desplazX)
            val deseadoY = if (ajuste == AjusteVista.ANCHO || z > 1f) {
                yInicio.coerceIn(0f, 1f) * ph + desplazY
            } else {
                0.5f * (ph - altoVentana)
            }
            return TransformacionVista(
                anchoPaginaPt, altoPaginaPt, escala, origenX, origenEje(ph, altoVentana, deseadoY),
            )
        }

        /** Centra si la página cabe en la ventana; si no, limita el origen a los bordes de la página. */
        private fun origenEje(tamPagina: Float, tamVentana: Float, deseado: Float): Float =
            if (tamPagina <= tamVentana) 0.5f * (tamPagina - tamVentana)
            else max(0f, min(deseado, tamPagina - tamVentana))
    }
}
