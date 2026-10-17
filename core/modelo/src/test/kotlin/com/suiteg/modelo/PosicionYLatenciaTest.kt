package com.suiteg.modelo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PosicionYLatenciaTest {
    @Test
    fun `orden de posiciones`() {
        assertTrue(Posicion(0, 0.9f) < Posicion(1, 0f))
        assertTrue(Posicion(2, 0.1f) < Posicion(2, 0.2f))
    }

    @Test
    fun `posicion invalida falla y acotada la corrige`() {
        assertFailsWith<IllegalArgumentException> { Posicion(-1, 0f) }
        assertFailsWith<IllegalArgumentException> { Posicion(0, 1.5f) }
        assertEquals(Posicion(0, 1f), Posicion.acotada(-3, 7f))
        assertEquals(Posicion(0, 0f), Posicion.acotada(0, Float.NaN))
    }

    @Test
    fun `percentiles de latencia`() {
        val m = MedidorLatencia()
        assertEquals(0.0, m.percentil(95.0))
        for (i in 1..100) m.registrar(i.toDouble())
        val r = m.resumen()
        assertEquals(100, r.muestras)
        assertEquals(50.0, r.p50)
        assertEquals(95.0, r.p95)
        assertEquals(99.0, r.p99)
        assertEquals(100.0, r.max)
    }

    @Test
    fun `el medidor ignora basura y es circular`() {
        val m = MedidorLatencia(capacidad = 3)
        m.registrar(Double.NaN); m.registrar(-5.0)
        assertEquals(0, m.resumen().muestras)
        listOf(1.0, 2.0, 3.0, 100.0).forEach(m::registrar)
        assertEquals(3, m.resumen().muestras)
        assertEquals(100.0, m.resumen().max) // el 1.0 salió del buffer
    }
}
