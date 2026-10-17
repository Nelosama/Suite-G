package com.suiteg.tempo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MetronomoTest {
    private val sr = 44_100

    private fun cerca(esperado: Int, real: Int, tolerancia: Int) =
        assertTrue(kotlin.math.abs(esperado - real) <= tolerancia, "esperado $esperado ±$tolerancia pero fue $real")

    /** Posiciones (en muestras) donde empieza un clic, detectadas como saltos de silencio a señal. */
    private fun inicios(pcm: ShortArray): List<Int> {
        val r = ArrayList<Int>()
        var i = 1
        while (i < pcm.size) {
            if (pcm[i - 1].toInt() == 0 && pcm[i].toInt() == 0) { i++; continue }
            i++
        }
        // Más simple: un golpe empieza donde hay señal y las 200 muestras anteriores eran silencio
        var silencio = 1000
        for (k in pcm.indices) {
            if (pcm[k].toInt() == 0) silencio++ else { if (silencio >= 200) r += k; silencio = 0 }
        }
        return r
    }

    private fun generar(s: SecuenciadorMetronomo, muestras: Int): ShortArray =
        ShortArray(muestras).also { s.llenar(it) }

    @Test
    fun `120 bpm produce un golpe cada medio segundo exacto`() {
        val s = SecuenciadorMetronomo(sr, PatronMetronomo(120.0, 4))
        val pcm = generar(s, sr * 4)
        val ini = inicios(pcm)
        assertEquals(8, ini.size)
        ini.zipWithNext().forEach { (a, b) -> cerca(22_050, b - a, 1) }
        cerca(0, ini.first(), 2)
    }

    @Test
    fun `sin deriva con un bpm que no divide el muestreo`() {
        val s = SecuenciadorMetronomo(sr, PatronMetronomo(97.0, 4))
        val minutos = 10
        val tamano = sr * 60
        val buf = ShortArray(tamano)
        repeat(minutos) { s.llenar(buf) }
        // 10 min a 97 bpm = 970 golpes: en t=0 y uno cada 60/97 s; el que cae justo en el minuto 10 queda fuera
        assertEquals(970L, s.golpesEmitidos)
    }

    @Test
    fun `acento en el primer pulso de cada compas`() {
        val p = PatronMetronomo(100.0, 3, acentuarPrimero = true)
        val tipos = (0L until 7L).map { p.tipoDe(it) }
        assertEquals(
            listOf(TipoClic.ACENTO, TipoClic.NORMAL, TipoClic.NORMAL, TipoClic.ACENTO, TipoClic.NORMAL, TipoClic.NORMAL, TipoClic.ACENTO),
            tipos,
        )
    }

    @Test
    fun `sin acento y con subdivision`() {
        val sinAcento = PatronMetronomo(100.0, 4, acentuarPrimero = false)
        assertTrue((0L until 8L).all { sinAcento.tipoDe(it) == TipoClic.NORMAL })
        val corcheas = PatronMetronomo(100.0, 2, subdivision = 2)
        assertEquals(
            listOf(TipoClic.ACENTO, TipoClic.SUBDIVISION, TipoClic.NORMAL, TipoClic.SUBDIVISION, TipoClic.ACENTO),
            (0L until 5L).map { corcheas.tipoDe(it) },
        )
    }

    @Test
    fun `el cambio de bpm aplica desde el siguiente golpe sin saltos`() {
        val s = SecuenciadorMetronomo(sr, PatronMetronomo(60.0, 4))
        val a = generar(s, sr + 100) // golpe en 0 y en 44100
        s.establecerPatron(PatronMetronomo(120.0, 4))
        val b = generar(s, sr * 2)
        val iniB = inicios(b).filter { it > 1_000 } // se descarta la cola del clic anterior
        // tras el golpe de 44100 el siguiente llega medio segundo después (22050), no un segundo
        assertTrue(iniB.isNotEmpty())
        cerca(22_050 - 100, iniB.first(), 2)
        cerca(22_050, iniB[1] - iniB[0], 1)
        assertTrue(a.isNotEmpty())
    }

    @Test
    fun `el volumen escala la salida y cero la silencia`() {
        val fuerte = generar(SecuenciadorMetronomo(sr, PatronMetronomo(60.0), 1f), sr / 2)
        val bajo = generar(SecuenciadorMetronomo(sr, PatronMetronomo(60.0), 0.25f), sr / 2)
        val mudo = generar(SecuenciadorMetronomo(sr, PatronMetronomo(60.0), 0f), sr / 2)
        assertEquals(0.25, bajo.maxOf { it.toInt() }.toDouble() / fuerte.maxOf { it.toInt() }, 0.02)
        assertTrue(mudo.all { it.toInt() == 0 })
    }

    @Test
    fun `llenar en trozos pequenos equivale a llenar de una vez`() {
        val a = SecuenciadorMetronomo(sr, PatronMetronomo(133.0, 4, 2))
        val b = SecuenciadorMetronomo(sr, PatronMetronomo(133.0, 4, 2))
        val total = generar(a, 30_000)
        val trozos = ShortArray(30_000)
        var pos = 0
        val tam = listOf(1, 7, 512, 1000, 4096)
        var k = 0
        while (pos < trozos.size) {
            val n = minOf(tam[k++ % tam.size], trozos.size - pos)
            b.llenar(trozos, pos, n); pos += n
        }
        assertTrue(total.contentEquals(trozos))
    }

    @Test
    fun `patron invalido`() {
        assertFailsWith<IllegalArgumentException> { PatronMetronomo(5.0) }
        assertFailsWith<IllegalArgumentException> { PatronMetronomo(100.0, 0) }
        assertFailsWith<IllegalArgumentException> { PatronMetronomo(100.0, 4, 9) }
    }

    @Test
    fun `el clic no tiene discontinuidades al inicio`() {
        val c = SintetizadorClic(sr).clic(TipoClic.ACENTO)
        assertEquals(0, c[0].toInt())
        assertTrue(c.size in 1000..2000)
    }
}
