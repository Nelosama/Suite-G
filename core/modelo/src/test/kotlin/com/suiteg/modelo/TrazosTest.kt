package com.suiteg.modelo

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrazosTest {
    @Test
    fun `codec ida y vuelta`() {
        val puntos = floatArrayOf(0.1f, 0.2f, 0.5f, 0.3f, 0.4f, 1f, 0.9f, 0.95f, 0f)
        val bytes = CodecTrazos.codificar(puntos)
        assertContentEquals(puntos, assertNotNull(CodecTrazos.decodificar(bytes)))
    }

    @Test
    fun `codec rechaza datos corruptos sin lanzar`() {
        assertNull(CodecTrazos.decodificar(ByteArray(0)))
        assertNull(CodecTrazos.decodificar(ByteArray(20) { 7 }))
        val bien = CodecTrazos.codificar(floatArrayOf(0f, 0f, 1f))
        assertNull(CodecTrazos.decodificar(bien.copyOf(bien.size - 1)))
        // cabecera correcta pero conteo absurdo
        val raro = bien.copyOf().also { it[3] = 0x7f }
        assertNull(CodecTrazos.decodificar(raro))
    }

    @Test
    fun `distancia a un trazo mide contra segmentos y no solo vertices`() {
        val t = Trazo(pagina = 0, herramienta = Herramienta.LAPIZ, color = 0, grosor = 0.003f,
            puntos = floatArrayOf(0f, 0.5f, 1f, 1f, 0.5f, 1f))
        // El punto (0.5, 0.5) está sobre el segmento aunque no sea vértice
        assertEquals(0f, t.distanciaA(0.5f, 0.5f, aspecto = 1.4f), 1e-5f)
        assertTrue(t.distanciaA(0.5f, 0.6f, aspecto = 1.4f) > 0.1f)
    }

    @Test
    fun `trazo vacio queda lejos de todo`() {
        val t = Trazo(pagina = 0, herramienta = Herramienta.LAPIZ, color = 0, grosor = 0.003f, puntos = FloatArray(0))
        assertEquals(Float.MAX_VALUE, t.distanciaA(0f, 0f, 1f))
    }
}
