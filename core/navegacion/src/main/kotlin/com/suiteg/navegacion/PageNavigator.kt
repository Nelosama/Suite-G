package com.suiteg.navegacion

import com.suiteg.modelo.Marcador
import com.suiteg.modelo.Reloj
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ÚNICO punto de entrada de los comandos de navegación (pedal, táctil y avance por tempo).
 *
 * Es una máquina de estados ([FaseLector]) sin dependencias de Android:
 *  - deduplica eventos por identificador,
 *  - aplica un cooldown mínimo a los comandos manuales,
 *  - recorre la [SecuenciaDeVistas] (puntos de giro) y gestiona el cambio de canción en un setlist.
 *
 * No es seguro entre hilos: debe llamarse siempre desde el mismo (el principal).
 */
class PageNavigator(
    private val reloj: Reloj,
    config: ConfigNavegacion = ConfigNavegacion(),
    private val alEfecto: (EfectoNavegacion) -> Unit = {},
) {
    private val _estado = MutableStateFlow(EstadoNavegador())
    val estado: StateFlow<EstadoNavegador> = _estado.asStateFlow()

    var config: ConfigNavegacion = config
    private var secuencia: SecuenciaDeVistas? = null
    private var marcadores: List<Marcador> = emptyList()
    private val dedup = Deduplicador()
    private var ultimoAplicadoMs: Long? = null

    /** Carga un documento. [indiceInicial] se acota al rango de la secuencia. */
    fun cargar(
        secuencia: SecuenciaDeVistas,
        marcadores: List<Marcador> = emptyList(),
        contexto: ContextoSetlist = ContextoSetlist(),
        indiceInicial: Int = 0,
    ) {
        this.secuencia = secuencia
        this.marcadores = marcadores.sortedBy { it.posicion }
        dedup.limpiar()
        ultimoAplicadoMs = null
        val actual = _estado.value
        _estado.value = EstadoNavegador(
            fase = FaseLector.LEYENDO,
            indiceVista = indiceInicial.coerceIn(0, secuencia.ultimo),
            totalVistas = secuencia.tamano,
            contexto = contexto,
            generacion = actual.generacion + 1,
        )
    }

    /** Se editaron los puntos de giro de la canción abierta: reconstruye conservando la posición. */
    fun reemplazarSecuencia(nueva: SecuenciaDeVistas, marcadores: List<Marcador> = this.marcadores) {
        val vieja = secuencia
        val actual = _estado.value
        if (vieja == null || actual.fase != FaseLector.LEYENDO) return
        val pos = vieja[actual.indiceVista.coerceIn(0, vieja.ultimo)].posicion
        this.secuencia = nueva
        this.marcadores = marcadores.sortedBy { it.posicion }
        _estado.value = actual.copy(
            indiceVista = nueva.indiceDe(pos),
            totalVistas = nueva.tamano,
            generacion = actual.generacion + 1,
        )
    }

    fun actualizarMarcadores(marcadores: List<Marcador>) {
        this.marcadores = marcadores.sortedBy { it.posicion }
    }

    /** El documento no se pudo abrir. El pedal sigue operativo para saltar de canción. */
    fun marcarError(motivo: String, contexto: ContextoSetlist = ContextoSetlist()) {
        secuencia = null
        marcadores = emptyList()
        dedup.limpiar()
        ultimoAplicadoMs = null
        _estado.value = EstadoNavegador(
            fase = FaseLector.ERROR,
            contexto = contexto,
            mensajeError = motivo,
            generacion = _estado.value.generacion + 1,
        )
    }

    fun vistaActual(): Vista? {
        val s = secuencia ?: return null
        val e = _estado.value
        return if (e.fase == FaseLector.LEYENDO) s[e.indiceVista] else null
    }

    /**
     * Procesa un comando.
     * @param idEvento identificador del evento físico (para deduplicar); null si no aplica.
     */
    fun enviar(
        comando: ComandoNavegacion,
        origen: OrigenComando,
        idEvento: Long? = null,
    ): ResultadoComando {
        if (idEvento != null && dedup.yaVisto(idEvento)) return ResultadoComando.IGNORADO_DUPLICADO

        val estado = _estado.value
        if (estado.fase == FaseLector.SIN_DOCUMENTO) return ResultadoComando.IGNORADO_SIN_DOCUMENTO

        val manual = origen != OrigenComando.TEMPO
        val ahora = reloj.ahoraMs()
        if (manual) {
            val previo = ultimoAplicadoMs
            if (previo != null && ahora - previo in 0 until config.cooldownMs) {
                return ResultadoComando.IGNORADO_COOLDOWN
            }
        }

        val resultado = if (estado.fase == FaseLector.ERROR) {
            enError(comando)
        } else {
            enLectura(comando, estado, origen)
        }

        if (manual && (resultado == ResultadoComando.APLICADO || resultado == ResultadoComando.CAMBIO_DE_CANCION)) {
            ultimoAplicadoMs = ahora
        }
        return resultado
    }

    // En ERROR solo tienen sentido los saltos de canción: así un PDF dañado no bloquea el setlist.
    private fun enError(comando: ComandoNavegacion): ResultadoComando {
        val ctx = _estado.value.contexto
        return when (comando) {
            ComandoNavegacion.Siguiente, ComandoNavegacion.SiguienteCancion -> saltarCancion(+1, ctx.hayDespues)
            ComandoNavegacion.Anterior, ComandoNavegacion.CancionAnterior -> saltarCancion(-1, ctx.hayAnterior)
            else -> ResultadoComando.SIN_MOVIMIENTO
        }
    }

    private fun enLectura(c: ComandoNavegacion, e: EstadoNavegador, origen: OrigenComando): ResultadoComando {
        val s = secuencia ?: return ResultadoComando.IGNORADO_SIN_DOCUMENTO
        return when (c) {
            ComandoNavegacion.Siguiente ->
                if (e.indiceVista < s.ultimo) irA(e.indiceVista + 1, origen)
                else if (origen == OrigenComando.TEMPO) ResultadoComando.SIN_MOVIMIENTO
                else saltarCancion(+1, e.contexto.hayDespues)

            ComandoNavegacion.Anterior ->
                if (e.indiceVista > 0) irA(e.indiceVista - 1, origen)
                else if (config.cruzarAlRetroceder && e.contexto.hayAnterior) saltarCancion(-1, true, alFinal = true)
                else ResultadoComando.SIN_MOVIMIENTO

            is ComandoNavegacion.IrAVista ->
                if (c.indice in 0..s.ultimo) irA(c.indice, origen) else ResultadoComando.SIN_MOVIMIENTO

            is ComandoNavegacion.IrAMarcador -> irA(s.indiceDe(c.posicion), origen)

            ComandoNavegacion.RepetirSeccion -> {
                // Inicio de la sección = último marcador en o antes de la vista actual; si no hay, el inicio.
                val inicio = marcadores.lastOrNull { s.indiceDe(it.posicion) <= e.indiceVista }
                irA(if (inicio != null) s.indiceDe(inicio.posicion) else 0, origen, forzarGeneracion = true)
            }

            ComandoNavegacion.SiguienteCancion -> saltarCancion(+1, e.contexto.hayDespues)
            ComandoNavegacion.CancionAnterior -> saltarCancion(-1, e.contexto.hayAnterior)
        }
    }

    private fun irA(indice: Int, origen: OrigenComando, forzarGeneracion: Boolean = false): ResultadoComando {
        val e = _estado.value
        if (indice == e.indiceVista && !forzarGeneracion) return ResultadoComando.SIN_MOVIMIENTO
        _estado.value = e.copy(indiceVista = indice, generacion = e.generacion + 1)
        alEfecto(EfectoNavegacion.VistaCambiada(indice, origen))
        return ResultadoComando.APLICADO
    }

    private fun saltarCancion(delta: Int, hay: Boolean, alFinal: Boolean = false): ResultadoComando {
        if (!hay) {
            if (delta > 0) alEfecto(EfectoNavegacion.FinDeSetlist)
            return ResultadoComando.SIN_MOVIMIENTO
        }
        alEfecto(EfectoNavegacion.AbrirCancion(delta, alFinal))
        return ResultadoComando.CAMBIO_DE_CANCION
    }
}
