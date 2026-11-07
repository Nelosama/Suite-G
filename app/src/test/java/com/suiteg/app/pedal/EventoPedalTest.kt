package com.suiteg.app.pedal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventoPedalTest {

    @Test
    fun `desconectado a conectado emite reconexion con los nombres`() {
        val evento = eventoTransicion(EstadoPedal.Desconectado, EstadoPedal.Conectado(listOf("Pedal BT")))
        assertEquals(EventoPedal.Reconectado(listOf("Pedal BT")), evento)
    }

    @Test
    fun `conectado a desconectado emite desconexion`() {
        assertEquals(EventoPedal.Desconectado, eventoTransicion(EstadoPedal.Conectado(listOf("Pedal BT")), EstadoPedal.Desconectado))
    }

    @Test
    fun `la primera lectura al arrancar no es un evento`() {
        assertNull(eventoTransicion(EstadoPedal.SinConfigurar, EstadoPedal.Conectado(listOf("Pedal BT"))))
        assertNull(eventoTransicion(EstadoPedal.SinConfigurar, EstadoPedal.Desconectado))
    }

    @Test
    fun `repetir el mismo estado no emite nada`() {
        assertNull(eventoTransicion(EstadoPedal.Desconectado, EstadoPedal.Desconectado))
        assertNull(eventoTransicion(EstadoPedal.Conectado(listOf("A")), EstadoPedal.Conectado(listOf("A"))))
    }

    @Test
    fun `cambiar de un pedal conectado a otro distinto no cuenta como desconexion`() {
        // Ambos estados son Conectado: el paso entre dispositivos se refleja en el indicador, no en el aviso.
        assertNull(eventoTransicion(EstadoPedal.Conectado(listOf("A")), EstadoPedal.Conectado(listOf("B"))))
    }
}
