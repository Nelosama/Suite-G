package com.suiteg.navegacion.entrada

import com.suiteg.modelo.RelojManual
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModoAprendizajeTest {
    private val reloj = RelojManual(0)
    private val ap = ModoAprendizaje(reloj, ventanaGrupoMs = 150)

    private fun ev(key: Int, desc: String = "pedal", down: Boolean = true, rep: Int = 0) =
        EventoTecla(desc, 1, key, down, rep, 0, 0)

    @Test
    fun `asigna la tecla recibida sin depender de keycodes fijos`() {
        ap.iniciar(Accion.SIGUIENTE)
        assertTrue(ap.alRecibir(ev(12345)))
        assertNull(ap.tomarResultado()) // la ventana sigue abierta
        reloj.avanzar(150)
        val r = ap.tomarResultado()!!
        assertEquals(Accion.SIGUIENTE, r.accion)
        assertEquals(setOf(ClaveTecla("pedal", 12345)), r.teclas)
        assertFalse(ap.activo)
    }

    @Test
    fun `agrupa varias teclas de una misma pisada`() {
        ap.iniciar(Accion.SIGUIENTE)
        ap.alRecibir(ev(22))
        reloj.avanzar(40)
        ap.alRecibir(ev(66))
        reloj.avanzar(200)
        ap.alRecibir(ev(77)) // fuera de ventana: llega ya sin modo activo
        assertEquals(setOf(ClaveTecla("pedal", 22), ClaveTecla("pedal", 66)), ap.tomarResultado()!!.teclas)
    }

    @Test
    fun `rechaza teclas reservadas y sigue esperando`() {
        ap.iniciar(Accion.SIGUIENTE)
        assertFalse(ap.alRecibir(ev(26 /* POWER */)))
        val e = assertIs<ModoAprendizaje.Estado.Esperando>(ap.estado)
        assertTrue(e.rechazo != null)
        assertTrue(ap.alRecibir(ev(93)))
    }

    @Test
    fun `ignora UP y repeticiones`() {
        ap.iniciar(Accion.ANTERIOR)
        ap.alRecibir(ev(93, down = false))
        ap.alRecibir(ev(93, rep = 3))
        assertIs<ModoAprendizaje.Estado.Esperando>(ap.estado)
    }

    @Test
    fun `cancelar descarta el aprendizaje`() {
        ap.iniciar(Accion.ANTERIOR)
        ap.alRecibir(ev(93))
        ap.cancelar()
        assertNull(ap.tomarResultado())
        assertFalse(ap.alRecibir(ev(93)))
    }

    @Test
    fun `fuera del aprendizaje no absorbe nada`() {
        assertFalse(ap.alRecibir(ev(93)))
    }
}
