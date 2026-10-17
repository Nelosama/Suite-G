package com.suiteg.modelo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatricesColorTest {
    private fun aplicar(modo: ModoInversion, r: Int, g: Int, b: Int) =
        MatricesColor.aplicar(MatricesColor.para(modo)!!, r, g, b)

    @Test
    fun `sin inversion no hay matriz`() {
        assertNull(MatricesColor.para(ModoInversion.NINGUNA))
    }

    @Test
    fun `invertir todo intercambia blanco y negro`() {
        assertEquals(Triple(0, 0, 0), aplicar(ModoInversion.TODO, 255, 255, 255))
        assertEquals(Triple(255, 255, 255), aplicar(ModoInversion.TODO, 0, 0, 0))
        assertEquals(Triple(205, 155, 55), aplicar(ModoInversion.TODO, 50, 100, 200))
    }

    @Test
    fun `invertir conservando tonalidad deja los grises igual que el negativo`() {
        for (v in listOf(0, 64, 128, 200, 255)) {
            val (r, g, b) = aplicar(ModoInversion.TONALIDAD, v, v, v)
            for (canal in listOf(r, g, b)) {
                assertTrue(kotlin.math.abs(canal - (255 - v)) <= 1, "gris $v -> $r,$g,$b")
            }
        }
    }

    @Test
    fun `invertir conservando tonalidad mantiene un rojo siendo rojo`() {
        val (r, g, b) = aplicar(ModoInversion.TONALIDAD, 255, 0, 0)
        assertTrue(r > g && r > b, "el matiz sigue siendo rojo: $r,$g,$b")
        val negativo = aplicar(ModoInversion.TODO, 255, 0, 0) // cian
        assertTrue(negativo.first < negativo.second)
    }

    @Test
    fun `modo fondo da fondo carbon y tinta gris claro`() {
        assertEquals(Triple(30, 30, 30), aplicar(ModoInversion.FONDO, 255, 255, 255))
        assertEquals(Triple(200, 200, 200), aplicar(ModoInversion.FONDO, 0, 0, 0))
    }

    @Test
    fun `el alfa no se toca`() {
        for (modo in ModoInversion.entries.drop(1)) {
            val m = MatricesColor.para(modo)!!
            assertEquals(listOf(0f, 0f, 0f, 1f, 0f), m.slice(15..19))
        }
    }
}
