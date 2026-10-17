package com.suiteg.navegacion

import com.suiteg.modelo.Posicion

/** Comandos de navegación. Todos los orígenes (pedal, táctil, tempo) usan estos mismos. */
sealed interface ComandoNavegacion {
    data object Siguiente : ComandoNavegacion
    data object Anterior : ComandoNavegacion
    data class IrAVista(val indice: Int) : ComandoNavegacion
    data class IrAMarcador(val posicion: Posicion) : ComandoNavegacion
    data object RepetirSeccion : ComandoNavegacion
    data object SiguienteCancion : ComandoNavegacion
    data object CancionAnterior : ComandoNavegacion
}

enum class OrigenComando { PEDAL, TACTIL, TEMPO }

enum class ResultadoComando {
    APLICADO,
    CAMBIO_DE_CANCION,
    SIN_MOVIMIENTO,
    IGNORADO_DUPLICADO,
    IGNORADO_COOLDOWN,
    IGNORADO_SIN_DOCUMENTO,
}

/** Efectos que el navegador pide al exterior. */
sealed interface EfectoNavegacion {
    /** Abrir la canción [delta] posiciones respecto a la actual (+1 siguiente, -1 anterior). */
    data class AbrirCancion(val delta: Int, val alFinal: Boolean = false) : EfectoNavegacion

    /** Se intentó avanzar tras la última canción del setlist. */
    data object FinDeSetlist : EfectoNavegacion

    /** La vista cambió. Útil para resincronizar el motor de tempo cuando el cambio no vino de él. */
    data class VistaCambiada(val indice: Int, val origen: OrigenComando) : EfectoNavegacion
}

enum class FaseLector { SIN_DOCUMENTO, LEYENDO, ERROR }

/** Posición de la canción dentro de su setlist (o aislada). */
data class ContextoSetlist(
    val hayAnterior: Boolean = false,
    val hayDespues: Boolean = false,
    val tituloSiguiente: String? = null,
)

data class EstadoNavegador(
    val fase: FaseLector = FaseLector.SIN_DOCUMENTO,
    val indiceVista: Int = 0,
    val totalVistas: Int = 0,
    val contexto: ContextoSetlist = ContextoSetlist(),
    val mensajeError: String? = null,
    /** Aumenta con cada cambio de vista, aunque el índice se repita (p. ej. repetir sección). */
    val generacion: Long = 0,
) {
    val enUltimaVista: Boolean get() = fase == FaseLector.LEYENDO && indiceVista == totalVistas - 1

    /** Aviso fijo "Siguiente: X" que se muestra en la última vista. */
    val avisoSiguiente: String?
        get() = if (enUltimaVista && contexto.hayDespues) contexto.tituloSiguiente else null
}

data class ConfigNavegacion(
    /** Tiempo mínimo entre dos comandos aplicados de pedal/táctil. */
    val cooldownMs: Long = 250,
    /** Si Anterior en la primera vista abre la canción previa (en su última vista). */
    val cruzarAlRetroceder: Boolean = false,
)
