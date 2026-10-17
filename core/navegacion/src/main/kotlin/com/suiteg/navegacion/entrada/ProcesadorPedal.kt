package com.suiteg.navegacion.entrada

import com.suiteg.modelo.Reloj
import com.suiteg.navegacion.Deduplicador
import com.suiteg.navegacion.FiltroVentana

/** Resultado de procesar una tecla. */
sealed interface ResultadoTecla {
    /** La tecla no es del pedal: el sistema debe tratarla normalmente. */
    data object NoManejado : ResultadoTecla

    /** Es del pedal y se consume sin hacer nada (ACTION_UP, repetición, duplicado). */
    data object Consumido : ResultadoTecla

    /** Es del pedal, se consume y dispara [accion]. */
    data class Despachado(val accion: Accion) : ResultadoTecla
}

data class ConfigPedal(
    /** Una misma tecla no puede disparar dos veces dentro de esta ventana (p. ej. eco de dos interfaces HID). */
    val ventanaDuplicadoMs: Long = 40,
    /** Una misma acción no puede dispararse dos veces dentro de esta ventana (pedal que manda varias teclas). */
    val ventanaGrupoMs: Long = 150,
)

/**
 * Adaptador del pedal: convierte [EventoTecla] en [Accion]. Es el único sitio que decide si una tecla
 * se consume. No conoce el navegador ni el motor de tempo; solo entrega acciones a [alAccion].
 *
 * Reglas:
 *  - solo ACTION_DOWN con repeticion == 0 dispara; las repeticiones y los UP se consumen sin efecto,
 *  - toda tecla asignada se consume (Android no ejecuta su acción, p. ej. cambiar el volumen),
 *  - deduplica por (dispositivo, tecla, downTime) y por ventana.
 */
class ProcesadorPedal(
    private val reloj: Reloj,
    mapa: MapaTeclas = MapaTeclas.VACIO,
    config: ConfigPedal = ConfigPedal(),
    val aprendizaje: ModoAprendizaje = ModoAprendizaje(reloj),
    private val alAccion: (Accion, EventoTecla) -> Unit = { _, _ -> },
) {
    @Volatile
    var mapa: MapaTeclas = mapa

    private val exactos = Deduplicador()
    private val porTecla = FiltroVentana(reloj, config.ventanaDuplicadoMs)
    private val porAccion = FiltroVentana(reloj, config.ventanaGrupoMs)

    fun configurar(config: ConfigPedal) {
        porTecla.ventanaMs = config.ventanaDuplicadoMs
        porAccion.ventanaMs = config.ventanaGrupoMs
        aprendizaje.ventanaGrupoMs = config.ventanaGrupoMs
    }

    fun procesar(ev: EventoTecla): ResultadoTecla {
        if (aprendizaje.activo) {
            return if (aprendizaje.alRecibir(ev)) ResultadoTecla.Consumido else ResultadoTecla.NoManejado
        }
        val accion = mapa[ClaveTecla(ev.descriptorDispositivo, ev.keyCode)] ?: return ResultadoTecla.NoManejado

        if (!ev.esDown || ev.repeticion > 0) return ResultadoTecla.Consumido
        if (exactos.yaVisto(idExacto(ev))) return ResultadoTecla.Consumido
        if (!porTecla.permite(ev.keyCode)) return ResultadoTecla.Consumido
        if (!porAccion.permite(accion)) return ResultadoTecla.Consumido

        alAccion(accion, ev)
        return ResultadoTecla.Despachado(accion)
    }

    private fun idExacto(ev: EventoTecla): Long {
        var h = ev.deviceId.toLong()
        h = h * 1_000_003L + ev.keyCode
        h = h * 1_000_003L + ev.downTimeMs
        return h
    }
}
