package com.suiteg.modelo

data class ResumenLatencia(val muestras: Int, val p50: Double, val p95: Double, val p99: Double, val max: Double)

/**
 * Acumula latencias (ms) de extremo a extremo (evento de pedal -> fotograma) en un buffer circular
 * y calcula percentiles. No es seguro entre hilos: úsese desde el hilo principal.
 */
class MedidorLatencia(private val capacidad: Int = 512) {
    private val datos = DoubleArray(capacidad)
    private var cuenta = 0
    private var cabeza = 0

    fun registrar(ms: Double) {
        if (ms.isNaN() || ms < 0) return
        datos[cabeza] = ms
        cabeza = (cabeza + 1) % capacidad
        if (cuenta < capacidad) cuenta++
    }

    fun limpiar() {
        cuenta = 0
        cabeza = 0
    }

    /** Percentil por rango más cercano; p en 0..100. Devuelve 0 si no hay datos. */
    fun percentil(p: Double): Double {
        if (cuenta == 0) return 0.0
        val orden = datos.copyOfRange(0, cuenta).also { it.sort() }
        val rango = Math.ceil(p / 100.0 * cuenta).toInt().coerceIn(1, cuenta)
        return orden[rango - 1]
    }

    fun resumen(): ResumenLatencia = ResumenLatencia(
        cuenta, percentil(50.0), percentil(95.0), percentil(99.0), percentil(100.0),
    )
}
