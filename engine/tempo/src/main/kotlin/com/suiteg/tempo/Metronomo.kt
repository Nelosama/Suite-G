package com.suiteg.tempo

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TipoClic { ACENTO, NORMAL, SUBDIVISION }

data class PatronMetronomo(
    val bpm: Double = 100.0,
    val beatsPorCompas: Int = 4,
    /** Golpes por pulso (1 = solo pulsos; 2 = corcheas; 3 = tresillos...). */
    val subdivision: Int = 1,
    val acentuarPrimero: Boolean = true,
) {
    init {
        require(bpm in CalculadorTempo.BPM_MIN..CalculadorTempo.BPM_MAX) { "BPM fuera de rango" }
        require(beatsPorCompas >= 1)
        require(subdivision in 1..8)
    }

    /** Tipo de golpe número [k] (0 = primer golpe del compás). */
    fun tipoDe(k: Long): TipoClic {
        val enPulso = (k % subdivision).toInt() == 0
        if (!enPulso) return TipoClic.SUBDIVISION
        val pulso = (k / subdivision) % beatsPorCompas
        return if (acentuarPrimero && pulso == 0L) TipoClic.ACENTO else TipoClic.NORMAL
    }
}

/** Genera los clics como PCM de 16 bits mono: una envolvente sinusoidal corta, sin ficheros de audio. */
class SintetizadorClic(val frecuenciaMuestreo: Int) {
    private val cache = HashMap<TipoClic, ShortArray>()

    fun clic(tipo: TipoClic): ShortArray = cache.getOrPut(tipo) {
        val (frecuencia, amplitud) = when (tipo) {
            TipoClic.ACENTO -> 1600.0 to 0.90
            TipoClic.NORMAL -> 1000.0 to 0.70
            TipoClic.SUBDIVISION -> 800.0 to 0.40
        }
        val n = (frecuenciaMuestreo * DURACION_S).roundToInt()
        val ataque = (frecuenciaMuestreo * 0.001).roundToInt().coerceAtLeast(1) // 1 ms: evita el "pop"
        ShortArray(n) { i ->
            val t = i.toDouble() / frecuenciaMuestreo
            val env = exp(-t / 0.008) * if (i < ataque) i.toDouble() / ataque else 1.0
            (sin(2 * PI * frecuencia * t) * env * amplitud * Short.MAX_VALUE).roundToInt().toShort()
        }
    }

    companion object {
        const val DURACION_S = 0.030
    }
}

/**
 * Genera el flujo continuo de audio del metrónomo. Los instantes de los golpes se calculan en
 * muestras con aritmética de coma flotante a partir del inicio (no sumando intervalos redondeados),
 * así que no hay deriva por redondeo aunque el BPM no divida el muestreo.
 *
 * No es seguro entre hilos: [establecerPatron] y [establecerVolumen] deben llamarse desde el hilo que
 * llama a [llenar], o encolarse allí.
 */
class SecuenciadorMetronomo(
    val frecuenciaMuestreo: Int,
    patron: PatronMetronomo,
    volumen: Float = 1f,
) {
    private val sintetizador = SintetizadorClic(frecuenciaMuestreo)
    private var patron = patron
    private var volumen = volumen.coerceIn(0f, 1f)

    private var muestraActual = 0L
    private var proximoGolpe = 0.0 // en muestras absolutas
    private var ultimoGolpe = 0.0
    private var indiceGolpe = 0L

    private var clicActual: ShortArray? = null
    private var posicionClic = 0

    private var totalGolpes = 0L

    /** Golpes ya iniciados desde el arranque. */
    val golpesEmitidos: Long get() = totalGolpes

    private fun intervalo(): Double = frecuenciaMuestreo * 60.0 / patron.bpm / patron.subdivision

    /** El cambio de BPM aplica al siguiente golpe: se recoloca a un intervalo nuevo del último golpe. */
    fun establecerPatron(nuevo: PatronMetronomo) {
        val anterior = patron
        patron = nuevo
        if (totalGolpes > 0) {
            proximoGolpe = maxOf(ultimoGolpe + intervalo(), muestraActual.toDouble())
        }
        // Si cambia la métrica, el siguiente golpe pasa a ser el primero de un compás.
        if (anterior.beatsPorCompas != nuevo.beatsPorCompas || anterior.subdivision != nuevo.subdivision) {
            indiceGolpe = (indiceGolpe / (nuevo.subdivision * nuevo.beatsPorCompas)) * (nuevo.subdivision * nuevo.beatsPorCompas)
        }
    }

    fun establecerVolumen(v: Float) {
        volumen = v.coerceIn(0f, 1f)
    }

    /** Rellena [destino] con [cantidad] muestras a partir de [desde]. */
    fun llenar(destino: ShortArray, desde: Int = 0, cantidad: Int = destino.size - desde) {
        for (i in 0 until cantidad) {
            if (muestraActual >= proximoGolpe) {
                clicActual = sintetizador.clic(patron.tipoDe(indiceGolpe))
                posicionClic = 0
                ultimoGolpe = proximoGolpe
                indiceGolpe++
                totalGolpes++
                proximoGolpe += intervalo()
            }
            val clic = clicActual
            destino[desde + i] = if (clic != null && posicionClic < clic.size) {
                (clic[posicionClic++] * volumen).roundToInt().toShort()
            } else {
                0
            }
            muestraActual++
        }
    }
}
