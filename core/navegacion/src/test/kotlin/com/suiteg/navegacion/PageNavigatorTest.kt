package com.suiteg.navegacion

import com.suiteg.modelo.Marcador
import com.suiteg.modelo.Posicion
import com.suiteg.modelo.PuntoGiro
import com.suiteg.modelo.RelojManual
import com.suiteg.modelo.TipoGiro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageNavigatorTest {
    private val reloj = RelojManual(10_000)
    private val efectos = mutableListOf<EfectoNavegacion>()
    private val nav = PageNavigator(reloj, ConfigNavegacion(cooldownMs = 250)) { efectos += it }

    private fun cargar(paginas: Int = 4, ctx: ContextoSetlist = ContextoSetlist(), inicial: Int = 0) =
        nav.cargar(SecuenciaDeVistas.construir(paginas, emptyList()), emptyList(), ctx, inicial)

    private fun pedal(c: ComandoNavegacion, id: Long? = null) = nav.enviar(c, OrigenComando.PEDAL, id)

    @Test
    fun `sin documento ignora los comandos`() {
        assertEquals(ResultadoComando.IGNORADO_SIN_DOCUMENTO, pedal(ComandoNavegacion.Siguiente))
    }

    @Test
    fun `avanza y retrocede de vista en vista`() {
        cargar()
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Siguiente))
        reloj.avanzar(300)
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Siguiente))
        assertEquals(2, nav.estado.value.indiceVista)
        reloj.avanzar(300)
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Anterior))
        assertEquals(1, nav.estado.value.indiceVista)
    }

    @Test
    fun `cooldown ignora la segunda pulsacion cercana y no la alarga`() {
        cargar()
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Siguiente))
        reloj.avanzar(100)
        assertEquals(ResultadoComando.IGNORADO_COOLDOWN, pedal(ComandoNavegacion.Siguiente))
        reloj.avanzar(100)
        assertEquals(ResultadoComando.IGNORADO_COOLDOWN, pedal(ComandoNavegacion.Siguiente))
        reloj.avanzar(60) // 260 ms desde el último APLICADO: ya cuenta
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Siguiente))
        assertEquals(2, nav.estado.value.indiceVista)
    }

    @Test
    fun `el cooldown es configurable y cero lo desactiva`() {
        cargar()
        nav.config = ConfigNavegacion(cooldownMs = 0)
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Siguiente))
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Siguiente))
    }

    @Test
    fun `el mismo evento no se aplica dos veces aunque pase el cooldown`() {
        cargar()
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Siguiente, id = 77))
        reloj.avanzar(1000)
        assertEquals(ResultadoComando.IGNORADO_DUPLICADO, pedal(ComandoNavegacion.Siguiente, id = 77))
        assertEquals(1, nav.estado.value.indiceVista)
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Siguiente, id = 78))
    }

    @Test
    fun `el avance por tempo no esta sujeto al cooldown ni lo reinicia`() {
        cargar()
        assertEquals(ResultadoComando.APLICADO, nav.enviar(ComandoNavegacion.Siguiente, OrigenComando.TEMPO))
        assertEquals(ResultadoComando.APLICADO, nav.enviar(ComandoNavegacion.Siguiente, OrigenComando.TEMPO))
        // El pedal sigue disponible justo después: el tempo no inició cooldown
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.Anterior))
    }

    @Test
    fun `no retrocede antes de la primera vista`() {
        cargar()
        assertEquals(ResultadoComando.SIN_MOVIMIENTO, pedal(ComandoNavegacion.Anterior))
        assertEquals(0, nav.estado.value.indiceVista)
    }

    @Test
    fun `en la ultima vista muestra el aviso y una pulsacion mas abre la siguiente cancion`() {
        cargar(paginas = 2, ctx = ContextoSetlist(hayDespues = true, tituloSiguiente = "Bach Suite 1"))
        pedal(ComandoNavegacion.Siguiente)
        assertEquals("Bach Suite 1", nav.estado.value.avisoSiguiente)
        reloj.avanzar(300)
        assertEquals(ResultadoComando.CAMBIO_DE_CANCION, pedal(ComandoNavegacion.Siguiente))
        assertEquals(EfectoNavegacion.AbrirCancion(+1), efectos.last())
    }

    @Test
    fun `no hay aviso fuera de la ultima vista`() {
        cargar(paginas = 3, ctx = ContextoSetlist(hayDespues = true, tituloSiguiente = "X"))
        assertNull(nav.estado.value.avisoSiguiente)
    }

    @Test
    fun `al final del ultimo tema avisa del fin del setlist y no se mueve`() {
        cargar(paginas = 1, ctx = ContextoSetlist(hayDespues = false))
        assertEquals(ResultadoComando.SIN_MOVIMIENTO, pedal(ComandoNavegacion.Siguiente))
        assertEquals(EfectoNavegacion.FinDeSetlist, efectos.last())
    }

    @Test
    fun `tempo nunca abre otra cancion por su cuenta`() {
        cargar(paginas = 1, ctx = ContextoSetlist(hayDespues = true, tituloSiguiente = "X"))
        assertEquals(ResultadoComando.SIN_MOVIMIENTO, nav.enviar(ComandoNavegacion.Siguiente, OrigenComando.TEMPO))
        assertEquals(emptyList(), efectos.filterIsInstance<EfectoNavegacion.AbrirCancion>())
    }

    @Test
    fun `retroceder cruza a la cancion anterior solo si esta activado`() {
        cargar(ctx = ContextoSetlist(hayAnterior = true))
        assertEquals(ResultadoComando.SIN_MOVIMIENTO, pedal(ComandoNavegacion.Anterior))
        nav.config = ConfigNavegacion(cooldownMs = 0, cruzarAlRetroceder = true)
        assertEquals(ResultadoComando.CAMBIO_DE_CANCION, pedal(ComandoNavegacion.Anterior))
        assertEquals(EfectoNavegacion.AbrirCancion(-1, alFinal = true), efectos.last())
    }

    @Test
    fun `un documento danado no bloquea el setlist y el pedal salta de cancion`() {
        nav.marcarError("PDF dañado", ContextoSetlist(hayAnterior = true, hayDespues = true, tituloSiguiente = "Siguiente"))
        assertEquals(FaseLector.ERROR, nav.estado.value.fase)
        assertEquals(ResultadoComando.CAMBIO_DE_CANCION, pedal(ComandoNavegacion.Siguiente))
        assertEquals(EfectoNavegacion.AbrirCancion(+1), efectos.last())
        reloj.avanzar(300)
        assertEquals(ResultadoComando.CAMBIO_DE_CANCION, pedal(ComandoNavegacion.Anterior))
        assertEquals(EfectoNavegacion.AbrirCancion(-1), efectos.last())
    }

    @Test
    fun `en error los comandos de pagina no hacen nada`() {
        nav.marcarError("x")
        assertEquals(ResultadoComando.SIN_MOVIMIENTO, pedal(ComandoNavegacion.IrAVista(0)))
        assertEquals(ResultadoComando.SIN_MOVIMIENTO, pedal(ComandoNavegacion.Siguiente)) // sin siguiente: tampoco rompe
    }

    @Test
    fun `salta a un marcador y repite la seccion`() {
        val marcas = listOf(
            Marcador(1, "h", "A", Posicion(1, 0f)),
            Marcador(2, "h", "B", Posicion(3, 0f)),
        )
        nav.cargar(SecuenciaDeVistas.construir(5, emptyList()), marcas)
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.IrAMarcador(Posicion(3, 0f))))
        assertEquals(3, nav.estado.value.indiceVista)

        reloj.avanzar(300)
        pedal(ComandoNavegacion.Siguiente) // vista 4, dentro de la sección B
        reloj.avanzar(300)
        val antes = nav.estado.value.generacion
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.RepetirSeccion))
        assertEquals(3, nav.estado.value.indiceVista) // vuelve al inicio de B

        reloj.avanzar(300)
        // Ya en el inicio: repetir vuelve a pedir el render (la generación cambia) sin cambiar de índice
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.RepetirSeccion))
        assertEquals(3, nav.estado.value.indiceVista)
        assertEquals(true, nav.estado.value.generacion > antes)
    }

    @Test
    fun `repetir seccion sin marcadores previos vuelve al inicio`() {
        nav.cargar(SecuenciaDeVistas.construir(5, emptyList()), listOf(Marcador(1, "h", "A", Posicion(3, 0f))), ContextoSetlist(), 2)
        assertEquals(ResultadoComando.APLICADO, pedal(ComandoNavegacion.RepetirSeccion))
        assertEquals(0, nav.estado.value.indiceVista)
    }

    @Test
    fun `ir a una vista fuera de rango no hace nada`() {
        cargar()
        assertEquals(ResultadoComando.SIN_MOVIMIENTO, pedal(ComandoNavegacion.IrAVista(99)))
    }

    @Test
    fun `cada cambio notifica con su origen`() {
        cargar()
        nav.enviar(ComandoNavegacion.Siguiente, OrigenComando.TEMPO)
        assertEquals(EfectoNavegacion.VistaCambiada(1, OrigenComando.TEMPO), efectos.last())
    }

    @Test
    fun `puntos de giro marcan el recorrido del pedal`() {
        val puntos = listOf(
            PuntoGiro(hashArchivo = "h", posicion = Posicion(0, 0.5f), tipo = TipoGiro.LIBRE),
            PuntoGiro(hashArchivo = "h", posicion = Posicion(1, 0f), tipo = TipoGiro.PAGINA),
        )
        nav.cargar(SecuenciaDeVistas.construir(3, puntos))
        assertEquals(3, nav.estado.value.totalVistas) // inicio implícito + 2 puntos
        pedal(ComandoNavegacion.Siguiente)
        assertEquals(Posicion(0, 0.5f), nav.vistaActual()?.posicion)
        reloj.avanzar(300)
        pedal(ComandoNavegacion.Siguiente)
        assertEquals(Posicion(1, 0f), nav.vistaActual()?.posicion)
    }

    @Test
    fun `editar los puntos conserva la posicion de lectura`() {
        nav.cargar(SecuenciaDeVistas.construir(4, emptyList()), emptyList(), ContextoSetlist(), 2)
        val nueva = SecuenciaDeVistas.construir(
            4, listOf(PuntoGiro(hashArchivo = "h", posicion = Posicion(2, 0.5f)), PuntoGiro(hashArchivo = "h", posicion = Posicion(3, 0f))),
        )
        nav.reemplazarSecuencia(nueva)
        // Estaba en (2, 0): la vista que la contiene es el inicio implícito (0)
        assertEquals(0, nav.estado.value.indiceVista)
        assertEquals(3, nav.estado.value.totalVistas)
    }
}
