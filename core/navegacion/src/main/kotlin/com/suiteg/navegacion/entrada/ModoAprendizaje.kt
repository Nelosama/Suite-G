package com.suiteg.navegacion.entrada

import com.suiteg.modelo.Reloj

/**
 * Modo de aprendizaje: el usuario elige una acción y pisa el pedal; la app asigna la(s) tecla(s)
 * recibida(s). No depende de ningún keycode fijo.
 *
 * Algunos pedales envían varias teclas por pisada (p. ej. flecha + Enter). Todas las que lleguen
 * dentro de [ventanaGrupoMs] desde la primera se asignan a la misma acción.
 */
class ModoAprendizaje(private val reloj: Reloj, var ventanaGrupoMs: Long = 150) {

    sealed interface Estado {
        data object Inactivo : Estado
        data class Esperando(val accion: Accion, val rechazo: String? = null) : Estado
        data class Capturando(val accion: Accion, val teclas: Set<ClaveTecla>, val desdeMs: Long) : Estado
        data class Completo(val accion: Accion, val teclas: Set<ClaveTecla>) : Estado
    }

    var estado: Estado = Estado.Inactivo
        private set

    val activo: Boolean get() = estado is Estado.Esperando || estado is Estado.Capturando

    fun iniciar(accion: Accion) {
        estado = Estado.Esperando(accion)
    }

    fun cancelar() {
        estado = Estado.Inactivo
    }

    /** Se consume el resultado y se vuelve a Inactivo. Null si todavía no hay resultado. */
    fun tomarResultado(): Estado.Completo? {
        actualizar()
        val e = estado as? Estado.Completo ?: return null
        estado = Estado.Inactivo
        return e
    }

    /**
     * Procesa un evento. Devuelve true si el modo de aprendizaje lo absorbe (debe consumirse),
     * false si la tecla no se puede asignar y se deja pasar al sistema.
     */
    fun alRecibir(ev: EventoTecla): Boolean {
        actualizar()
        val e = estado
        if (e !is Estado.Esperando && e !is Estado.Capturando) return false
        if (!TeclasNoAsignables.esAsignable(ev.keyCode)) {
            if (e is Estado.Esperando && ev.esDown && ev.repeticion == 0) {
                estado = e.copy(rechazo = "Esa tecla está reservada por el sistema. Prueba con otra.")
            }
            return false
        }
        if (ev.esDown && ev.repeticion == 0) {
            val clave = ClaveTecla(ev.descriptorDispositivo, ev.keyCode)
            estado = when (e) {
                is Estado.Esperando -> Estado.Capturando(e.accion, setOf(clave), reloj.ahoraMs())
                is Estado.Capturando -> e.copy(teclas = e.teclas + clave)
                else -> e
            }
        }
        return true
    }

    /** Cierra la ventana de captura si ya venció. Llamar periódicamente (o vía [tomarResultado]). */
    fun actualizar() {
        val e = estado
        if (e is Estado.Capturando && reloj.ahoraMs() - e.desdeMs >= ventanaGrupoMs) {
            estado = Estado.Completo(e.accion, e.teclas)
        }
    }
}
