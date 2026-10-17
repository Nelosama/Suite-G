package com.suiteg.modelo

/** Fuente de tiempo monótona en milisegundos. Se inyecta para poder probar con tiempo controlado. */
fun interface Reloj {
    fun ahoraMs(): Long
}

/** Reloj controlado a mano, para pruebas. */
class RelojManual(var ahora: Long = 0L) : Reloj {
    override fun ahoraMs(): Long = ahora

    fun avanzar(ms: Long) {
        ahora += ms
    }
}
