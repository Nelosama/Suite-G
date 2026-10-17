package com.suiteg.navegacion

import com.suiteg.modelo.RelojManual
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeduplicadorYDesbloqueoTest {
    @Test
    fun `deduplicador detecta repetidos y olvida los antiguos`() {
        val d = Deduplicador(capacidad = 2)
        assertFalse(d.yaVisto(1))
        assertTrue(d.yaVisto(1))
        d.yaVisto(2); d.yaVisto(3) // 1 sale
        assertFalse(d.yaVisto(1))
    }

    @Test
    fun `filtro de ventana`() {
        val r = RelojManual(0)
        val f = FiltroVentana(r, 100)
        assertTrue(f.permite("a"))
        r.avanzar(99)
        assertFalse(f.permite("a"))
        assertTrue(f.permite("b"))
        r.avanzar(1)
        assertTrue(f.permite("a"))
    }

    private val reloj = RelojManual(0)
    private val det = DetectorDesbloqueo(reloj, duracionMs = 3000, tamanoMaximoContacto = 0.3f)

    @Test
    fun `desbloquea tras mantener 3 segundos con un dedo en la zona`() {
        det.alBajar(dentroDeZona = true, punteros = 1, tamanoContacto = 0.1f)
        reloj.avanzar(2900)
        assertFalse(det.completado())
        assertEquals(0.97f, det.progreso(), 0.01f)
        reloj.avanzar(100)
        assertTrue(det.completado())
    }

    @Test
    fun `un toque fuera de la zona o con palma no inicia el desbloqueo`() {
        det.alBajar(false, 1, 0.1f)
        assertFalse(det.enCurso)
        det.alBajar(true, 1, 0.9f) // palma
        assertFalse(det.enCurso)
        det.alBajar(true, 2, 0.1f) // dos dedos
        assertFalse(det.enCurso)
    }

    @Test
    fun `salir de la zona, un segundo dedo o soltar reinician`() {
        det.alBajar(true, 1, 0.1f); reloj.avanzar(2000)
        det.alMover(dentroDeZona = false, punteros = 1)
        assertEquals(0f, det.progreso())

        det.alBajar(true, 1, 0.1f); reloj.avanzar(2000)
        det.alBajarOtroPuntero()
        assertFalse(det.enCurso)

        det.alBajar(true, 1, 0.1f); reloj.avanzar(2000)
        det.alSoltar()
        assertEquals(0f, det.progreso())
    }

    @Test
    fun `tras desbloquear hay que soltar antes de volver a empezar`() {
        det.alBajar(true, 1, 0.1f); reloj.avanzar(3000)
        assertTrue(det.completado())
        det.consumir()
        det.alBajar(true, 1, 0.1f)
        assertFalse(det.enCurso)
        det.alSoltar()
        det.alBajar(true, 1, 0.1f)
        assertTrue(det.enCurso)
    }
}
