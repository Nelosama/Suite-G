package com.suiteg.modelo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculoMiniaturaTest {

    @Test
    fun `una pagina A4 vertical da 240 x 340`() {
        // A4 = 595 x 842 puntos.
        val t = CalculoMiniatura.calcular(595f, 842f)!!
        assertEquals(240, t.ancho)
        assertEquals(340, t.alto)
        assertEquals(240f / 595f, t.escala, 1e-6f)
    }

    @Test
    fun `una pagina carta apaisada es mas ancha que alta`() {
        val t = CalculoMiniatura.calcular(792f, 612f)!!
        assertEquals(240, t.ancho)
        assertEquals(185, t.alto)
    }

    @Test
    fun `el ancho siempre es el pedido aunque la pagina sea enorme o diminuta`() {
        // Escaneo a 300 dpi guardado con tamaño de página en píxeles (2480 x 3508).
        assertEquals(339, CalculoMiniatura.calcular(2480f, 3508f)!!.alto)
        // Página de 10 x 14 puntos: escala > 1, pero el resultado sigue siendo pequeño.
        val diminuta = CalculoMiniatura.calcular(10f, 14f)!!
        assertEquals(240, diminuta.ancho)
        assertEquals(336, diminuta.alto)
    }

    @Test
    fun `una pagina muy alta se recorta a la proporcion maxima`() {
        val t = CalculoMiniatura.calcular(100f, 100_000f)!!
        assertEquals(Math.round(240 * CalculoMiniatura.PROPORCION_MAXIMA), t.alto)
    }

    @Test
    fun `una pagina muy apaisada no baja de un pixel de alto`() {
        assertEquals(1, CalculoMiniatura.calcular(100_000f, 1f)!!.alto)
    }

    @Test
    fun `tamanos invalidos no producen miniatura`() {
        assertNull(CalculoMiniatura.calcular(0f, 842f))
        assertNull(CalculoMiniatura.calcular(595f, 0f))
        assertNull(CalculoMiniatura.calcular(-595f, 842f))
        assertNull(CalculoMiniatura.calcular(Float.NaN, 842f))
        assertNull(CalculoMiniatura.calcular(595f, Float.POSITIVE_INFINITY))
        assertNull(CalculoMiniatura.calcular(595f, 842f, anchoPx = 0))
    }

    @Test
    fun `huerfanos detecta temporales y miniaturas de partituras eliminadas`() {
        val enCarpeta = listOf(
            CalculoMiniatura.nombreArchivo("aaa"),
            CalculoMiniatura.nombreArchivo("bbb"),
            CalculoMiniatura.nombreTemporal("ccc"),
            CalculoMiniatura.nombreTemporal("aaa"),
            "basura.txt",
        )
        val sobran = CalculoMiniatura.huerfanos(enCarpeta, hashesVigentes = setOf("aaa", "ccc"))
        assertEquals(setOf("bbb.png", "ccc.png.tmp", "aaa.png.tmp", "basura.txt"), sobran.toSet())
    }

    @Test
    fun `huerfanos no toca nada si todo esta vigente`() {
        val enCarpeta = listOf("aaa.png", "bbb.png")
        assertTrue(CalculoMiniatura.huerfanos(enCarpeta, setOf("aaa", "bbb")).isEmpty())
    }

    @Test
    fun `con la biblioteca vacia sobra todo`() {
        assertEquals(listOf("aaa.png"), CalculoMiniatura.huerfanos(listOf("aaa.png"), emptySet()))
    }
}
