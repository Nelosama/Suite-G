package com.suiteg.navegacion

import com.suiteg.modelo.Reloj

/**
 * Lógica del desbloqueo deliberado del modo concierto: mantener UN dedo pequeño dentro de la zona
 * durante [duracionMs]. Cualquier otra cosa (segundo dedo, palma, salir de la zona, soltar) reinicia.
 * Tras desbloquear hay que soltar antes de poder volver a empezar.
 */
class DetectorDesbloqueo(
    private val reloj: Reloj,
    var duracionMs: Long = 3000,
    /** Tamaño de contacto (en las unidades de la plataforma) a partir del cual se considera una palma. */
    private val tamanoMaximoContacto: Float = Float.MAX_VALUE,
) {
    private var inicioMs: Long? = null
    private var exigeSoltar = false

    val enCurso: Boolean get() = inicioMs != null

    /** Avance 0..1 de la pulsación sostenida. */
    fun progreso(): Float {
        val ini = inicioMs ?: return 0f
        return ((reloj.ahoraMs() - ini).toFloat() / duracionMs).coerceIn(0f, 1f)
    }

    fun completado(): Boolean = inicioMs != null && progreso() >= 1f

    fun alBajar(dentroDeZona: Boolean, punteros: Int, tamanoContacto: Float) {
        if (exigeSoltar) return
        inicioMs = if (dentroDeZona && punteros == 1 && tamanoContacto <= tamanoMaximoContacto) {
            reloj.ahoraMs()
        } else null
    }

    fun alMover(dentroDeZona: Boolean, punteros: Int) {
        if (!dentroDeZona || punteros != 1) inicioMs = null
    }

    fun alBajarOtroPuntero() {
        inicioMs = null
    }

    /** Soltar o cancelar. */
    fun alSoltar() {
        inicioMs = null
        exigeSoltar = false
    }

    /** Llamar al desbloquear: evita que se reinicie solo mientras el dedo siga apoyado. */
    fun consumir() {
        inicioMs = null
        exigeSoltar = true
    }
}
