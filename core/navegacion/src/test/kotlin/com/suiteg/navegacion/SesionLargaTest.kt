package com.suiteg.navegacion

import com.suiteg.modelo.RelojManual
import com.suiteg.navegacion.entrada.Accion
import com.suiteg.navegacion.entrada.ClaveTecla
import com.suiteg.navegacion.entrada.EventoTecla
import com.suiteg.navegacion.entrada.MapaTeclas
import com.suiteg.navegacion.entrada.ProcesadorPedal
import com.suiteg.navegacion.entrada.ResultadoTecla
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simula dos horas de concierto con un pisotón cada 2 s (3 600 pisadas) con rebotes del pedal y repeticiones,
 * recorriendo un setlist completo varias veces. Comprueba que nada se descuadra, que cada pisada real
 * produce exactamente una acción y que los rebotes nunca producen ninguna.
 */
class SesionLargaTest {
    private val reloj = RelojManual(0)
    private val pedal = "pedal"
    private var cancionesCargadas = 0
    private var cancionActual = 0
    private val totalCanciones = 12
    private val paginasPorCancion = 6

    private lateinit var nav: PageNavigator

    private fun cargarCancion(i: Int) {
        cancionActual = i
        cancionesCargadas++
        nav.cargar(
            SecuenciaDeVistas.construir(paginasPorCancion, emptyList()),
            emptyList(),
            ContextoSetlist(hayAnterior = i > 0, hayDespues = i < totalCanciones - 1, tituloSiguiente = "Canción ${i + 2}"),
        )
    }

    @Test
    fun `dos horas de pisadas con rebotes no descuadran ni duplican`() {
        nav = PageNavigator(reloj, ConfigNavegacion(cooldownMs = 250)) { e ->
            if (e is EfectoNavegacion.AbrirCancion) cargarCancion(cancionActual + e.delta)
            if (e is EfectoNavegacion.FinDeSetlist) cargarCancion(0) // el músico vuelve a empezar el setlist
        }
        cargarCancion(0)

        val mapa = MapaTeclas.VACIO.asignar(Accion.SIGUIENTE, listOf(ClaveTecla(pedal, 93)))
        val procesador = ProcesadorPedal(reloj, mapa) { accion, ev ->
            assertEquals(Accion.SIGUIENTE, accion)
            nav.enviar(ComandoNavegacion.Siguiente, OrigenComando.PEDAL, ev.downTimeMs)
        }

        val rnd = Random(7)
        var pisadas = 0
        var despachadas = 0
        var rebotesAbsorbidos = 0
        var downTime = 0L
        val duracionMs = 2L * 60 * 60 * 1000

        while (reloj.ahora < duracionMs) {
            reloj.avanzar(2000)
            downTime += 7
            pisadas++
            fun ev(abajo: Boolean, rep: Int = 0, t: Long = downTime) = EventoTecla(pedal, 3, 93, abajo, rep, t, reloj.ahora)

            if (procesador.procesar(ev(true)) is ResultadoTecla.Despachado) despachadas++

            // Repeticiones por mantener el pie apoyado
            repeat(rnd.nextInt(0, 4)) {
                reloj.avanzar(30)
                assertEquals(ResultadoTecla.Consumido, procesador.procesar(ev(true, rep = it + 1)))
            }
            // Rebote mecánico: una segunda pulsación casi inmediata con otro downTime
            if (rnd.nextInt(5) == 0) {
                reloj.avanzar(15)
                downTime += 1
                assertEquals(ResultadoTecla.Consumido, procesador.procesar(ev(true, t = downTime)))
                rebotesAbsorbidos++
            }
            assertEquals(ResultadoTecla.Consumido, procesador.procesar(ev(false)))

            val e = nav.estado.value
            assertTrue(e.indiceVista in 0 until e.totalVistas, "índice fuera de rango: $e")
        }

        assertEquals(pisadas, despachadas, "cada pisada real debe producir exactamente una acción")
        assertTrue(rebotesAbsorbidos > 100, "la prueba debe incluir rebotes")
        assertTrue(pisadas >= 3000, "dos horas a una pisada cada 2 s son unas 3 600 pisadas")
        assertTrue(cancionesCargadas > totalCanciones, "debió recorrer el setlist más de una vez")
    }
}
