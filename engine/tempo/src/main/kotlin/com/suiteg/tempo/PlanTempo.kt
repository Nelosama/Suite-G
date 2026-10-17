package com.suiteg.tempo

import com.suiteg.modelo.Posicion
import com.suiteg.modelo.TramoTempo
import com.suiteg.navegacion.SecuenciaDeVistas

/**
 * Parámetros musicales para calcular el plan.
 * @param compasesPorVista compases que se asumen en las vistas que no indican los suyos.
 */
data class ParametrosPlan(
    val bpmBase: Double,
    val beatsPorCompas: Int = 4,
    val compasesPorVista: Int? = null,
    val tramos: List<TramoTempo> = emptyList(),
)

/**
 * Duración de cada vista en "tiempo de partitura" (ms a velocidad nominal).
 * El plan se construye SIEMPRE sobre la [SecuenciaDeVistas], es decir, sobre los puntos de giro que
 * marcó el usuario: el cálculo por tempo nunca inventa giros que no estén en la secuencia.
 */
class PlanTempo internal constructor(
    val duracionesMs: DoubleArray,
    val bpmPorVista: DoubleArray,
) {
    val numVistas: Int get() = duracionesMs.size

    /** inicios[i] = instante de partitura en el que empieza la vista i; inicios[n] = duración total. */
    private val inicios = DoubleArray(duracionesMs.size + 1).also { a ->
        for (i in duracionesMs.indices) a[i + 1] = a[i] + duracionesMs[i]
    }

    val totalMs: Double get() = inicios[numVistas]

    fun inicioDe(vista: Int): Double = inicios[vista.coerceIn(0, numVistas)]

    /** Vista que contiene el instante [ms] (acotado al rango). */
    fun vistaEn(ms: Double): Int {
        if (ms <= 0.0) return 0
        var lo = 0
        var hi = numVistas - 1
        var res = 0
        while (lo <= hi) {
            val medio = (lo + hi) ushr 1
            if (inicios[medio] <= ms) { res = medio; lo = medio + 1 } else hi = medio - 1
        }
        return res
    }
}

sealed interface ResultadoPlan {
    data class Listo(val plan: PlanTempo) : ResultadoPlan

    /** Hay vistas sin compases y no se indicó un valor por defecto. */
    data class Incompleto(val vistasSinCompases: List<Int>) : ResultadoPlan

    data class Invalido(val motivo: String) : ResultadoPlan
}

object CalculadorTempo {
    const val BPM_MIN = 20.0
    const val BPM_MAX = 400.0

    fun calcular(secuencia: SecuenciaDeVistas, p: ParametrosPlan): ResultadoPlan {
        if (!bpmValido(p.bpmBase)) return ResultadoPlan.Invalido("BPM fuera de rango (${p.bpmBase})")
        if (p.beatsPorCompas < 1) return ResultadoPlan.Invalido("Pulsos por compás inválidos")
        if (p.compasesPorVista != null && p.compasesPorVista < 1) return ResultadoPlan.Invalido("Compases por vista inválidos")
        p.tramos.firstOrNull { !bpmValido(it.bpm) }?.let { return ResultadoPlan.Invalido("BPM de tramo fuera de rango (${it.bpm})") }

        val n = secuencia.tamano
        // BPM de cada vista: el del último tramo que empieza en o antes de ella.
        val tramosPorVista = p.tramos
            .map { secuencia.indiceDe(it.desde) to it.bpm }
            .sortedBy { it.first } // estable: si dos tramos caen en la misma vista, gana el último definido
        val bpm = DoubleArray(n)
        var k = 0
        var actual = p.bpmBase
        for (i in 0 until n) {
            while (k < tramosPorVista.size && tramosPorVista[k].first <= i) { actual = tramosPorVista[k].second; k++ }
            bpm[i] = actual
        }

        val sinCompases = ArrayList<Int>()
        val dur = DoubleArray(n)
        for (i in 0 until n) {
            // Prioridad: los compases del punto marcado; si no, el valor por defecto.
            val compases = secuencia[i].compases ?: p.compasesPorVista
            if (compases == null) { sinCompases += i; continue }
            dur[i] = compases * p.beatsPorCompas * 60_000.0 / bpm[i]
        }
        if (sinCompases.isNotEmpty()) return ResultadoPlan.Incompleto(sinCompases)
        return ResultadoPlan.Listo(PlanTempo(dur, bpm))
    }

    private fun bpmValido(b: Double) = !b.isNaN() && b in BPM_MIN..BPM_MAX

    /**
     * Posición continua para el modo de desplazamiento suave: interpola dentro de la vista [vista].
     * Si la vista siguiente está en la misma página, el destino es su `y`; si está en otra, la página
     * se recorre hasta el final. En la última vista no se mueve.
     */
    fun posicionContinua(secuencia: SecuenciaDeVistas, vista: Int, fraccion: Double): Posicion {
        val v = vista.coerceIn(0, secuencia.ultimo)
        val actual = secuencia[v].posicion
        if (v == secuencia.ultimo) return actual
        val sig = secuencia[v + 1].posicion
        val destino = if (sig.pagina == actual.pagina) sig.y else 1f
        val y = actual.y + (destino - actual.y) * fraccion.coerceIn(0.0, 1.0).toFloat()
        return Posicion(actual.pagina, y.coerceIn(0f, 1f))
    }
}
