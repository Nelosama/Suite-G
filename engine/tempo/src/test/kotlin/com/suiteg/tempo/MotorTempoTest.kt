package com.suiteg.tempo

import com.suiteg.modelo.Posicion
import com.suiteg.modelo.PuntoGiro
import com.suiteg.modelo.RelojManual
import com.suiteg.modelo.TipoGiro
import com.suiteg.navegacion.SecuenciaDeVistas
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MotorTempoTest {
    private val reloj = RelojManual(5_000)
    private val eventos = mutableListOf<Pair<Long, EventoTempo>>()
    private val motor = MotorTempo(reloj, ConfigMotorTempo(avisoMs = 2000, avisoMinimoMs = 800)) { eventos += reloj.ahora to it }

    /** 3 vistas de 10 s cada una (60 bpm, 1 pulso por compás, 10 compases). */
    private fun planTres(): PlanTempo {
        val sec = SecuenciaDeVistas.construir(3, emptyList())
        return (CalculadorTempo.calcular(sec, ParametrosPlan(60.0, 1, 10)) as ResultadoPlan.Listo).plan
    }

    private fun avanzar(totalMs: Long, paso: Long = 50) {
        var t = 0L
        while (t < totalMs) { reloj.avanzar(paso); motor.tick(); t += paso }
    }

    private fun cerca(esperado: Long, real: Long, tolerancia: Long) =
        assertTrue(kotlin.math.abs(esperado - real) <= tolerancia, "esperado $esperado ±$tolerancia pero fue $real")

    private fun tipos() = eventos.map { it.second }

    @Test
    fun `gira al terminar cada vista, con aviso antes`() {
        motor.configurar(planTres()); motor.iniciar(0)
        avanzar(10_000)
        val av = tipos().indexOfFirst { it is EventoTempo.AvisoGiro }
        val gi = tipos().indexOfFirst { it is EventoTempo.Girar }
        assertTrue(av in 0 until gi, "el aviso debe preceder al giro: $eventos")
        assertEquals(EventoTempo.Girar(1), tipos()[gi])
        // aviso ~2 s antes, giro a los ~10 s
        cerca(8_000, eventos[av].first - 5_000, 50)
        cerca(10_000, eventos[gi].first - 5_000, 50)
    }

    @Test
    fun `al final de la ultima vista emite Fin y se detiene sin mas eventos`() {
        motor.configurar(planTres()); motor.iniciar(0)
        avanzar(40_000)
        assertEquals(EstadoTempo.FINALIZADO, motor.estado)
        assertEquals(1, tipos().count { it == EventoTempo.Fin })
        assertEquals(2, tipos().count { it is EventoTempo.Girar })
        val n = eventos.size
        avanzar(10_000)
        assertEquals(n, eventos.size)
        // el aviso de la última vista no tiene destino: lo que viene es el final
        assertEquals(null, tipos().filterIsInstance<EventoTempo.AvisoGiro>().last().vistaDestino)
    }

    @Test
    fun `no hace nada hasta que se inicia`() {
        motor.configurar(planTres())
        avanzar(30_000)
        assertEquals(emptyList(), eventos)
        assertEquals(EstadoTempo.DETENIDO, motor.estado)
    }

    @Test
    fun `cuenta de entrada retrasa el inicio`() {
        motor.configurar(planTres()); motor.iniciar(0, cuentaEntradaMs = 4_000)
        assertEquals(EstadoTempo.CUENTA_ATRAS, motor.estado)
        assertEquals(4_000L, motor.instantanea().msHastaInicio)
        avanzar(3_950)
        assertEquals(EstadoTempo.CUENTA_ATRAS, motor.estado)
        avanzar(100)
        assertEquals(EstadoTempo.CORRIENDO, motor.estado)
        assertEquals(1, tipos().count { it == EventoTempo.Iniciado })
        // 10 s de vista contados desde el fin de la cuenta
        avanzar(10_000)
        assertEquals(1, motor.instantanea().vista)
    }

    @Test
    fun `pausar congela y reanudar continua donde se quedo`() {
        motor.configurar(planTres()); motor.iniciar(0)
        avanzar(5_000)
        motor.pausar()
        val antes = motor.instantanea().fraccion
        avanzar(60_000)
        assertEquals(antes, motor.instantanea().fraccion, 1e-9)
        assertEquals(EstadoTempo.PAUSADO, motor.estado)
        motor.reanudar()
        avanzar(5_050)
        assertEquals(1, motor.instantanea().vista) // 5 s + 5 s = 10 s
    }

    @Test
    fun `alternarPausa`() {
        motor.configurar(planTres()); motor.iniciar(0)
        motor.alternarPausa(); assertEquals(EstadoTempo.PAUSADO, motor.estado)
        motor.alternarPausa(); assertEquals(EstadoTempo.CORRIENDO, motor.estado)
    }

    @Test
    fun `ajustar la velocidad conserva la posicion y cambia el ritmo`() {
        motor.configurar(planTres()); motor.iniciar(0)
        avanzar(5_000)
        val antes = motor.instantanea().fraccion
        motor.ajustarVelocidad(+0.5) // 1.5x
        assertEquals(antes, motor.instantanea().fraccion, 1e-3) // no salta
        assertEquals(1.5, motor.velocidad, 1e-9)
        avanzar(3_400)
        // quedaban 5 s de partitura; a 1.5x se consumen en 3.33 s -> ya giró
        assertEquals(1, motor.instantanea().vista)
    }

    @Test
    fun `la velocidad se acota`() {
        motor.configurar(planTres()); motor.iniciar(0)
        repeat(50) { motor.ajustarVelocidad(+1.0) }
        assertEquals(4.0, motor.velocidad, 1e-9)
        repeat(100) { motor.ajustarVelocidad(-0.9) }
        assertEquals(0.25, motor.velocidad, 1e-9)
    }

    @Test
    fun `resincronizar mueve la posicion sin reiniciar ni cambiar la velocidad`() {
        motor.configurar(planTres()); motor.iniciar(0)
        avanzar(2_000)
        motor.ajustarVelocidad(0.2)
        motor.resincronizar(vista = 1, fraccion = 0.5)
        val i = motor.instantanea()
        assertEquals(1, i.vista)
        assertEquals(0.5, i.fraccion, 1e-6)
        assertEquals(1.2, motor.velocidad, 1e-9)
        assertEquals(EstadoTempo.CORRIENDO, motor.estado)
    }

    @Test
    fun `resincronizar durante la cuenta de entrada arranca ya`() {
        motor.configurar(planTres()); motor.iniciar(0, cuentaEntradaMs = 8_000)
        motor.resincronizar(0)
        assertEquals(EstadoTempo.CORRIENDO, motor.estado)
        assertTrue(EventoTempo.Iniciado in tipos())
    }

    @Test
    fun `si el usuario frena despues del aviso el aviso se cancela y vuelve a avisar mas tarde`() {
        motor.configurar(planTres()); motor.iniciar(0)
        avanzar(8_100) // ya avisó
        assertTrue(tipos().any { it is EventoTempo.AvisoGiro })
        motor.resincronizar(0, 0.2) // "todavía estoy en el principio"
        assertTrue(EventoTempo.AvisoCancelado in tipos())
        val giros = tipos().count { it is EventoTempo.Girar }
        avanzar(5_000)
        assertEquals(giros, tipos().count { it is EventoTempo.Girar }) // no giró
        avanzar(5_000)
        assertEquals(giros + 1, tipos().count { it is EventoTempo.Girar })
    }

    @Test
    fun `un reloj muy atrasado no gira sin aviso previo ni antes del minimo`() {
        motor.configurar(planTres()); motor.iniciar(0)
        reloj.avanzar(30_000) // la app estuvo parada 30 s: ya se pasó de largo
        motor.tick()
        assertTrue(tipos().any { it is EventoTempo.AvisoGiro })
        assertTrue(tipos().none { it is EventoTempo.Girar }, "no puede girar en el mismo tick que el aviso")
        avanzar(700)
        assertTrue(tipos().none { it is EventoTempo.Girar })
        avanzar(200)
        assertEquals(1, tipos().count { it is EventoTempo.Girar })
    }

    @Test
    fun `invariante con tics irregulares y ajustes aleatorios, todo giro tiene aviso previo de al menos el minimo`() {
        val rnd = Random(42)
        repeat(30) {
            eventos.clear()
            val sec = SecuenciaDeVistas.construir(8, emptyList())
            val plan = (CalculadorTempo.calcular(sec, ParametrosPlan(120.0, 4, 2)) as ResultadoPlan.Listo).plan // vistas de 4 s
            motor.configurar(plan); motor.iniciar(0)
            var guard = 0
            while (motor.estado != EstadoTempo.FINALIZADO && guard++ < 20_000) {
                reloj.avanzar(rnd.nextLong(10, 400))
                when (rnd.nextInt(40)) {
                    0 -> motor.ajustarVelocidad(rnd.nextDouble(-0.2, 0.3))
                    1 -> motor.resincronizar(rnd.nextInt(8), rnd.nextDouble())
                    2 -> motor.desplazar(rnd.nextLong(-3000, 3000))
                    3 -> motor.pausar()
                    4 -> motor.reanudar()
                }
                motor.tick()
            }
            var ultimoAviso: Long? = null
            for ((t, e) in eventos) {
                when (e) {
                    is EventoTempo.AvisoGiro -> ultimoAviso = t
                    EventoTempo.AvisoCancelado -> ultimoAviso = null
                    is EventoTempo.Girar, EventoTempo.Fin -> {
                        val a = ultimoAviso ?: error("giro sin aviso previo: $eventos")
                        assertTrue(t - a >= 800, "giro a ${t - a} ms del aviso")
                        ultimoAviso = null
                    }
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun `con puntos marcados el giro ocurre en las vistas marcadas`() {
        val puntos = listOf(
            PuntoGiro(hashArchivo = "h", posicion = Posicion(0, 0.5f), tipo = TipoGiro.LIBRE, compases = 5),
            PuntoGiro(hashArchivo = "h", posicion = Posicion(1, 0f), tipo = TipoGiro.PAGINA, compases = 5),
        )
        val sec = SecuenciaDeVistas.construir(2, puntos)
        // El inicio implícito no tiene compases: se le da el valor por defecto de 5
        val plan = (CalculadorTempo.calcular(sec, ParametrosPlan(60.0, 1, 5)) as ResultadoPlan.Listo).plan
        motor.configurar(plan); motor.iniciar(0)
        avanzar(6_000)
        val giro = tipos().filterIsInstance<EventoTempo.Girar>().first()
        assertEquals(1, giro.vistaDestino)
        assertEquals(Posicion(0, 0.5f), sec[giro.vistaDestino].posicion) // gira a mitad de la página 0, no a la página 1
    }

    @Test
    fun `desplazar adelanta la posicion`() {
        motor.configurar(planTres()); motor.iniciar(0)
        motor.desplazar(+12_000)
        assertEquals(1, motor.instantanea().vista)
    }

    @Test
    fun `instantanea de un motor sin plan`() {
        val i = MotorTempo(reloj).instantanea()
        assertEquals(EstadoTempo.DETENIDO, i.estado)
        assertIs<Double>(i.bpmEfectivo)
    }
}

class SesionLargaTempoTest {
    @Test
    fun `una pieza de dos horas avisa y gira en cada vista y termina sola`() {
        val reloj = RelojManual(0)
        val eventos = mutableListOf<EventoTempo>()
        val motor = MotorTempo(reloj) { eventos += it }
        // 120 vistas de 1 minuto (60 bpm, 1 pulso por compás, 60 compases)
        val sec = SecuenciaDeVistas.construir(120, emptyList())
        val plan = (CalculadorTempo.calcular(sec, ParametrosPlan(60.0, 1, 60)) as ResultadoPlan.Listo).plan
        motor.configurar(plan)
        motor.iniciar(0)

        // Ticks cada 50 ms durante algo más de dos horas; dos pausas de la persona en medio
        var t = 0L
        while (t < 2 * 3600_000L + 300_000L) { // + la pausa de 60 s y margen
            reloj.avanzar(50); t += 50
            motor.tick()
            if (t == 1_000_000L) motor.pausar()
            if (t == 1_060_000L) motor.reanudar()
        }
        assertEquals(EstadoTempo.FINALIZADO, motor.estado)
        assertEquals(119, eventos.count { it is EventoTempo.Girar })
        assertEquals(120, eventos.count { it is EventoTempo.AvisoGiro })
        assertEquals(1, eventos.count { it == EventoTempo.Fin })
        // El giro siempre es precedido por su aviso
        var avisado = false
        for (e in eventos) {
            when (e) {
                is EventoTempo.AvisoGiro -> avisado = true
                is EventoTempo.Girar, EventoTempo.Fin -> { assertTrue(avisado); avisado = false }
                else -> Unit
            }
        }
    }
}
