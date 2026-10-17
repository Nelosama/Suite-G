package com.suiteg.navegacion.entrada

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapaTeclasTest {
    private val a = ClaveTecla("pedal", 93)
    private val b = ClaveTecla("pedal", 92)

    @Test
    fun `una tecla solo hace una cosa`() {
        val m = MapaTeclas.VACIO.asignar(Accion.SIGUIENTE, listOf(a)).asignar(Accion.ANTERIOR, listOf(a))
        assertEquals(Accion.ANTERIOR, m[a])
        assertEquals(emptyList(), m.teclasDe(Accion.SIGUIENTE))
    }

    @Test
    fun `reasignar una accion reemplaza sus teclas anteriores`() {
        val m = MapaTeclas.VACIO.asignar(Accion.SIGUIENTE, listOf(a)).asignar(Accion.SIGUIENTE, listOf(b))
        assertNull(m[a])
        assertEquals(Accion.SIGUIENTE, m[b])
    }

    @Test
    fun `serializacion ida y vuelta`() {
        val m = MapaTeclas.VACIO.asignar(Accion.SIGUIENTE, listOf(a, ClaveTecla("otro", 22))).asignar(Accion.ANTERIOR, listOf(b))
        assertEquals(m.asignaciones, MapaTeclas.deserializar(m.serializar()).asignaciones)
    }

    @Test
    fun `deserializar tolera basura, acciones desconocidas y teclas reservadas`() {
        val texto = "malo\npedal\t93\tSIGUIENTE\npedal\t92\tACCION_FUTURA\npedal\tx\tANTERIOR\npedal\t26\tANTERIOR"
        val m = MapaTeclas.deserializar(texto)
        assertEquals(mapOf(a to Accion.SIGUIENTE), m.asignaciones)
        assertTrue(MapaTeclas.deserializar(null).vacio)
    }
}
