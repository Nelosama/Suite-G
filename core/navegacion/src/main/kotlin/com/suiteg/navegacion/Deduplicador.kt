package com.suiteg.navegacion

import com.suiteg.modelo.Reloj

/** Recuerda los últimos [capacidad] identificadores y detecta repeticiones exactas. */
class Deduplicador(private val capacidad: Int = 32) {
    private val vistos = ArrayDeque<Long>()

    /** true si [id] ya se había visto; si es nuevo, lo registra. */
    fun yaVisto(id: Long): Boolean {
        if (id in vistos) return true
        vistos.addLast(id)
        if (vistos.size > capacidad) vistos.removeFirst()
        return false
    }

    fun limpiar() = vistos.clear()
}

/** Permite como máximo una vez por [ventanaMs] cada clave. */
class FiltroVentana(private val reloj: Reloj, var ventanaMs: Long) {
    private val ultimo = HashMap<Any, Long>()

    /** true si la clave puede pasar (y registra el instante); false si cae dentro de la ventana. */
    fun permite(clave: Any): Boolean {
        val ahora = reloj.ahoraMs()
        val previo = ultimo[clave]
        if (previo != null && ahora - previo in 0 until ventanaMs) return false
        ultimo[clave] = ahora
        return true
    }

    fun limpiar() = ultimo.clear()
}
