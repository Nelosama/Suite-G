package com.suiteg.tempo

import com.suiteg.modelo.Posicion
import com.suiteg.modelo.PuntoGiro
import com.suiteg.modelo.TipoGiro
import com.suiteg.modelo.TramoTempo
import com.suiteg.navegacion.SecuenciaDeVistas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CalculadorTempoTest {
    private fun giro(p: Int, y: Float = 0f, compases: Int? = null) =
        PuntoGiro(hashArchivo = "h", posicion = Posicion(p, y), tipo = TipoGiro.LIBRE, compases = compases)

    private fun plan(sec: SecuenciaDeVistas, params: ParametrosPlan): PlanTempo =
        assertIs<ResultadoPlan.Listo>(CalculadorTempo.calcular(sec, params)).plan

    @Test
    fun `duracion de una vista es compases por pulsos por 60000 entre bpm`() {
        val sec = SecuenciaDeVistas.construir(3, emptyList())
        val p = plan(sec, ParametrosPlan(bpmBase = 120.0, beatsPorCompas = 4, compasesPorVista = 8))
        // 8 compases * 4 pulsos = 32 pulsos a 120 bpm = 16 s
        assertEquals(16_000.0, p.duracionesMs[0], 1e-6)
        assertEquals(48_000.0, p.totalMs, 1e-6)
    }

    @Test
    fun `sin compases ni valor por defecto el plan queda incompleto y dice cuales faltan`() {
        val sec = SecuenciaDeVistas.construir(3, listOf(giro(1, compases = 4)))
        val r = assertIs<ResultadoPlan.Incompleto>(CalculadorTempo.calcular(sec, ParametrosPlan(bpmBase = 100.0)))
        assertEquals(listOf(0), r.vistasSinCompases) // la vista de inicio implícita no tiene compases
    }

    @Test
    fun `los compases del punto marcado tienen prioridad sobre el valor por defecto`() {
        val sec = SecuenciaDeVistas.construir(2, listOf(giro(0, 0f, compases = 4), giro(1, 0f)))
        val p = plan(sec, ParametrosPlan(bpmBase = 60.0, beatsPorCompas = 4, compasesPorVista = 10))
        assertEquals(16_000.0, p.duracionesMs[0], 1e-6) // 4 compases (marcado), no 10
        assertEquals(40_000.0, p.duracionesMs[1], 1e-6) // 10 compases (por defecto)
    }

    @Test
    fun `el plan sigue las vistas marcadas, no las paginas`() {
        val sec = SecuenciaDeVistas.construir(4, listOf(giro(0, 0f, 2), giro(0, 0.5f, 2), giro(3, 0f, 2)))
        val p = plan(sec, ParametrosPlan(bpmBase = 120.0))
        assertEquals(3, p.numVistas) // no 4: las páginas 1 y 2 no están marcadas
    }

    @Test
    fun `cambios de tempo por tramos`() {
        val sec = SecuenciaDeVistas.construir(4, emptyList())
        val tramos = listOf(
            TramoTempo(hashArchivo = "h", desde = Posicion(2, 0f), bpm = 60.0),   // ritardando
            TramoTempo(hashArchivo = "h", desde = Posicion(3, 0f), bpm = 30.0),
        )
        val p = plan(sec, ParametrosPlan(bpmBase = 120.0, compasesPorVista = 2, tramos = tramos))
        assertEquals(listOf(120.0, 120.0, 60.0, 30.0), p.bpmPorVista.toList())
        assertEquals(listOf(4000.0, 4000.0, 8000.0, 16000.0), p.duracionesMs.toList())
    }

    @Test
    fun `un tramo a mitad de pagina aplica desde la vista que lo contiene`() {
        val sec = SecuenciaDeVistas.construir(2, listOf(giro(0, 0f), giro(0, 0.5f), giro(1, 0f)))
        val tramos = listOf(TramoTempo(hashArchivo = "h", desde = Posicion(0, 0.7f), bpm = 90.0))
        val p = plan(sec, ParametrosPlan(bpmBase = 120.0, compasesPorVista = 1, tramos = tramos))
        assertEquals(listOf(120.0, 90.0, 90.0), p.bpmPorVista.toList())
    }

    @Test
    fun `parametros invalidos`() {
        val sec = SecuenciaDeVistas.construir(1, emptyList())
        assertIs<ResultadoPlan.Invalido>(CalculadorTempo.calcular(sec, ParametrosPlan(bpmBase = 0.0, compasesPorVista = 1)))
        assertIs<ResultadoPlan.Invalido>(CalculadorTempo.calcular(sec, ParametrosPlan(bpmBase = Double.NaN, compasesPorVista = 1)))
        assertIs<ResultadoPlan.Invalido>(CalculadorTempo.calcular(sec, ParametrosPlan(bpmBase = 100.0, beatsPorCompas = 0, compasesPorVista = 1)))
        assertIs<ResultadoPlan.Invalido>(CalculadorTempo.calcular(sec, ParametrosPlan(bpmBase = 100.0, compasesPorVista = 0)))
        assertIs<ResultadoPlan.Invalido>(
            CalculadorTempo.calcular(sec, ParametrosPlan(100.0, compasesPorVista = 1, tramos = listOf(TramoTempo(hashArchivo = "h", desde = Posicion(0), bpm = 9999.0)))),
        )
    }

    @Test
    fun `vistaEn localiza el instante`() {
        val sec = SecuenciaDeVistas.construir(3, emptyList())
        val p = plan(sec, ParametrosPlan(bpmBase = 60.0, beatsPorCompas = 1, compasesPorVista = 10)) // 10 s por vista
        assertEquals(0, p.vistaEn(-5.0))
        assertEquals(0, p.vistaEn(9_999.0))
        assertEquals(1, p.vistaEn(10_000.0))
        assertEquals(2, p.vistaEn(1e9))
    }

    @Test
    fun `desplazamiento continuo interpola dentro de la pagina y recorre la pagina hasta el final si la vista sigue en otra`() {
        val sec = SecuenciaDeVistas.construir(3, listOf(giro(0, 0f), giro(0, 0.5f), giro(1, 0f)))
        assertEquals(Posicion(0, 0.25f), CalculadorTempo.posicionContinua(sec, 0, 0.5))
        assertEquals(Posicion(0, 0.75f), CalculadorTempo.posicionContinua(sec, 1, 0.5)) // 0.5 -> 1.0 (sigue la página 1)
        assertEquals(Posicion(1, 0f), CalculadorTempo.posicionContinua(sec, 2, 0.9)) // última vista: quieta
    }
}
