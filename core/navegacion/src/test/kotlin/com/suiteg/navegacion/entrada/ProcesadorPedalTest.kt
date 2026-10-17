package com.suiteg.navegacion.entrada

import com.suiteg.modelo.RelojManual
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcesadorPedalTest {
    private val reloj = RelojManual(1_000)
    private val acciones = mutableListOf<Accion>()
    private val pedalId = "pedal-A"

    private fun procesador(
        mapa: MapaTeclas = MapaTeclas.VACIO.asignar(Accion.SIGUIENTE, listOf(ClaveTecla(pedalId, 93)))
            .asignar(Accion.ANTERIOR, listOf(ClaveTecla(pedalId, 92))),
        config: ConfigPedal = ConfigPedal(),
    ) = ProcesadorPedal(reloj, mapa, config, ModoAprendizaje(reloj, config.ventanaGrupoMs)) { a, _ -> acciones += a }

    private var downTime = 1L
    private fun down(key: Int, dev: Int = 5, desc: String = pedalId, rep: Int = 0, dt: Long = ++downTime) =
        EventoTecla(desc, dev, key, esDown = true, repeticion = rep, downTimeMs = dt, eventTimeMs = dt)

    private fun up(key: Int, dev: Int = 5, desc: String = pedalId) =
        EventoTecla(desc, dev, key, esDown = false, repeticion = 0, downTimeMs = downTime, eventTimeMs = downTime)

    @Test
    fun `una tecla no asignada no se consume`() {
        assertEquals(ResultadoTecla.NoManejado, procesador().procesar(down(24)))
    }

    @Test
    fun `una tecla de otro dispositivo con el mismo keycode no se consume`() {
        // Los botones de volumen del propio dispositivo no deben activar el pedal asignado a 93
        assertEquals(ResultadoTecla.NoManejado, procesador().procesar(down(93, desc = "tablet")))
    }

    @Test
    fun `solo ACTION_DOWN dispara y el UP se consume sin efecto`() {
        val p = procesador()
        assertEquals(ResultadoTecla.Despachado(Accion.SIGUIENTE), p.procesar(down(93)))
        assertEquals(ResultadoTecla.Consumido, p.procesar(up(93)))
        assertEquals(listOf(Accion.SIGUIENTE), acciones)
    }

    @Test
    fun `las repeticiones por mantener pulsado se consumen y no disparan`() {
        val p = procesador()
        val dt = 50L
        p.procesar(down(93, dt = dt))
        reloj.avanzar(500)
        repeat(5) { assertEquals(ResultadoTecla.Consumido, p.procesar(down(93, rep = it + 1, dt = dt))) }
        assertEquals(1, acciones.size)
    }

    @Test
    fun `el mismo evento entregado dos veces dispara una sola vez`() {
        val p = procesador(config = ConfigPedal(ventanaDuplicadoMs = 0, ventanaGrupoMs = 0))
        val ev = down(93, dt = 777)
        p.procesar(ev)
        reloj.avanzar(1000)
        assertEquals(ResultadoTecla.Consumido, p.procesar(ev))
        assertEquals(1, acciones.size)
    }

    @Test
    fun `eco de la misma tecla desde dos interfaces HID en pocos ms dispara una vez`() {
        val p = procesador()
        p.procesar(down(93, dev = 5))
        reloj.avanzar(10)
        assertEquals(ResultadoTecla.Consumido, p.procesar(down(93, dev = 6)))
        assertEquals(1, acciones.size)
    }

    @Test
    fun `una pisada que manda dos teclas asignadas a la misma accion dispara una vez`() {
        val mapa = MapaTeclas.VACIO.asignar(Accion.SIGUIENTE, listOf(ClaveTecla(pedalId, 22), ClaveTecla(pedalId, 66)))
        val p = procesador(mapa)
        p.procesar(down(22))
        reloj.avanzar(30)
        assertEquals(ResultadoTecla.Consumido, p.procesar(down(66)))
        assertEquals(listOf(Accion.SIGUIENTE), acciones)
    }

    @Test
    fun `dos pisadas separadas dejan pasar ambas`() {
        val p = procesador()
        p.procesar(down(93))
        reloj.avanzar(400)
        p.procesar(down(93))
        assertEquals(2, acciones.size)
    }

    @Test
    fun `acciones distintas casi simultaneas no se bloquean entre si`() {
        val p = procesador()
        p.procesar(down(93))
        reloj.avanzar(5)
        assertEquals(ResultadoTecla.Despachado(Accion.ANTERIOR), p.procesar(down(92)))
    }

    @Test
    fun `durante el aprendizaje el pedal no dispara acciones`() {
        val p = procesador()
        p.aprendizaje.iniciar(Accion.TEMPO_MAS_RAPIDO)
        assertEquals(ResultadoTecla.Consumido, p.procesar(down(93)))
        assertEquals(emptyList(), acciones)
    }

    @Test
    fun `una tecla reservada del sistema se deja pasar durante el aprendizaje`() {
        val p = procesador()
        p.aprendizaje.iniciar(Accion.SIGUIENTE)
        assertEquals(ResultadoTecla.NoManejado, p.procesar(down(3 /* HOME */)))
    }
}
