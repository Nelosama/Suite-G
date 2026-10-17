package com.suiteg.modelo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AjustesTest {
    @Test
    fun `ida y vuelta por el codec`() {
        val a = Perfiles.aplicar(PerfilUso.FOSO_OSCURO, AjustesApp()).copy(cooldownMs = 123, volumenMetronomo = 0.25f)
        assertEquals(a, AjustesCodec.desdeMapa(AjustesCodec.aMapa(a)))
    }

    @Test
    fun `valores corruptos caen a los de por defecto o se acotan`() {
        val m = mapOf("cooldownMs" to "abc", "inversion" to "XXX", "desbloqueoMs" to "999999", "grabacion" to "quizas")
        val a = AjustesCodec.desdeMapa(m)
        assertEquals(AjustesApp().cooldownMs, a.cooldownMs)
        assertEquals(ModoInversion.NINGUNA, a.inversion)
        assertEquals(10000, a.desbloqueoMs)
        assertFalse(a.grabacion)
    }

    @Test
    fun `avance por tempo esta desactivado por defecto`() {
        assertFalse(AjustesApp().avanceTempo)
        assertFalse(ConfigTempoCancion("h").activo)
    }

    @Test
    fun `los perfiles activan conjuntos de funciones y conservan el resto`() {
        val base = AjustesApp(dedoDibuja = true)
        val concierto = Perfiles.aplicar(PerfilUso.CONCIERTO, base)
        assertTrue(concierto.bloqueoAlAbrir)
        assertFalse(concierto.grabacion)
        assertTrue(concierto.dedoDibuja) // no tocado por el perfil
        assertEquals(ModoInversion.TODO, Perfiles.aplicar(PerfilUso.FOSO_OSCURO, base).inversion)
        assertTrue(Perfiles.aplicar(PerfilUso.ENSAYO, base).grabacion)
    }
}
