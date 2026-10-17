package com.suiteg.tempo

import com.suiteg.modelo.Reloj
import kotlin.math.max

enum class EstadoTempo { DETENIDO, CUENTA_ATRAS, CORRIENDO, PAUSADO, FINALIZADO }

sealed interface EventoTempo {
    /** Terminó la cuenta de entrada (o no la había) y empieza el avance. */
    data object Iniciado : EventoTempo

    /** Aviso previo a un giro. [vistaDestino] es null si lo que viene es el final de la canción. */
    data class AvisoGiro(val vistaDestino: Int?, val msRestantes: Long) : EventoTempo

    /** El aviso ya no es válido (el usuario frenó o resincronizó): la UI debe quitarlo. */
    data object AvisoCancelado : EventoTempo

    /** Cambiar a la vista [vistaDestino]. Siempre va precedido de [AvisoGiro]. */
    data class Girar(val vistaDestino: Int) : EventoTempo

    /** Fin de la última vista: el avance se detiene solo. */
    data object Fin : EventoTempo
}

data class ConfigMotorTempo(
    /** Antelación (ms reales) del aviso respecto al giro. */
    val avisoMs: Long = 2000,
    /** Tiempo mínimo entre el aviso y el giro, pase lo que pase (retraso del reloj, resincronización...). */
    val avisoMinimoMs: Long = 800,
    val velocidadMin: Double = 0.25,
    val velocidadMax: Double = 4.0,
)

data class InstantaneaTempo(
    val estado: EstadoTempo,
    val vista: Int,
    /** Avance 0..1 dentro de la vista actual. */
    val fraccion: Double,
    val velocidad: Double,
    val bpmEfectivo: Double,
    /** Ms reales hasta el próximo giro (o hasta el fin); null si no está corriendo. */
    val msHastaGiro: Long?,
    /** Ms reales hasta que acaba la cuenta de entrada; null fuera de ella. */
    val msHastaInicio: Long?,
)

/**
 * Motor del avance automático por tempo. No sabe nada de PDF, pedal ni pantalla: se le da un [PlanTempo],
 * se le llama a [tick] con frecuencia (p. ej. cada 50 ms) y emite [EventoTempo] por [alEvento].
 *
 * La posición es una función del tiempo (ancla + transcurrido × velocidad), no un contador que se va
 * incrementando: por eso no acumula deriva si se pierden ticks.
 *
 * Garantía de seguridad: [EventoTempo.Girar] nunca se emite sin un [EventoTempo.AvisoGiro] previo emitido
 * al menos [ConfigMotorTempo.avisoMinimoMs] antes.
 *
 * No es seguro entre hilos: úsese siempre desde el mismo (el principal).
 */
class MotorTempo(
    private val reloj: Reloj,
    var config: ConfigMotorTempo = ConfigMotorTempo(),
    private val alEvento: (EventoTempo) -> Unit = {},
) {
    var estado: EstadoTempo = EstadoTempo.DETENIDO
        private set
    var velocidad: Double = 1.0
        private set

    private var plan: PlanTempo? = null
    private var anclaScoreMs = 0.0
    private var anclaRealMs = 0L
    private var inicioScoreMs = 0.0
    private var scorePausaMs = 0.0
    private var vistaLogica = 0
    private var avisoEmitidoMs: Long? = null

    /** Cambia el plan (otra canción o puntos de giro editados). Detiene el avance. */
    fun configurar(nuevo: PlanTempo?) {
        detener()
        plan = nuevo
        velocidad = 1.0
        estado = EstadoTempo.DETENIDO
    }

    fun iniciar(vistaInicial: Int, cuentaEntradaMs: Long = 0) {
        val p = plan ?: return
        val v = vistaInicial.coerceIn(0, p.numVistas - 1)
        vistaLogica = v
        inicioScoreMs = p.inicioDe(v)
        avisoEmitidoMs = null
        anclaRealMs = reloj.ahoraMs()
        anclaScoreMs = inicioScoreMs - max(0L, cuentaEntradaMs) * velocidad
        if (cuentaEntradaMs > 0) {
            estado = EstadoTempo.CUENTA_ATRAS
        } else {
            estado = EstadoTempo.CORRIENDO
            alEvento(EventoTempo.Iniciado)
        }
    }

    fun pausar() {
        if (estado != EstadoTempo.CORRIENDO && estado != EstadoTempo.CUENTA_ATRAS) return
        scorePausaMs = scoreAhora()
        estado = EstadoTempo.PAUSADO
    }

    fun reanudar() {
        if (estado != EstadoTempo.PAUSADO) return
        fijarAncla(scorePausaMs)
        estado = if (scorePausaMs < inicioScoreMs) EstadoTempo.CUENTA_ATRAS else EstadoTempo.CORRIENDO
        revisarAviso()
    }

    fun alternarPausa() {
        when (estado) {
            EstadoTempo.CORRIENDO, EstadoTempo.CUENTA_ATRAS -> pausar()
            EstadoTempo.PAUSADO -> reanudar()
            else -> Unit
        }
    }

    fun detener() {
        if (avisoEmitidoMs != null) alEvento(EventoTempo.AvisoCancelado)
        avisoEmitidoMs = null
        estado = EstadoTempo.DETENIDO
    }

    /**
     * Corrección en vivo: multiplica la velocidad por (1 + [delta]). Conserva la posición actual:
     * solo cambia a qué ritmo avanza desde ahora.
     */
    fun ajustarVelocidad(delta: Double) {
        if (plan == null || estado == EstadoTempo.DETENIDO || estado == EstadoTempo.FINALIZADO) return
        val s = scoreAhora()
        velocidad = (velocidad * (1.0 + delta)).coerceIn(config.velocidadMin, config.velocidadMax)
        if (estado == EstadoTempo.PAUSADO) scorePausaMs = s else fijarAncla(s)
        revisarAviso()
    }

    /**
     * Resincroniza con la música: "estoy ahora en la fracción [fraccion] de la vista [vista]".
     * No reinicia la canción ni toca la velocidad. Si estaba en cuenta de entrada, el avance arranca ya.
     */
    fun resincronizar(vista: Int, fraccion: Double = 0.0) {
        val p = plan ?: return
        if (estado == EstadoTempo.DETENIDO || estado == EstadoTempo.FINALIZADO) return
        val v = vista.coerceIn(0, p.numVistas - 1)
        val s = p.inicioDe(v) + fraccion.coerceIn(0.0, 1.0) * p.duracionesMs[v]
        vistaLogica = v
        if (estado == EstadoTempo.CUENTA_ATRAS) {
            estado = EstadoTempo.CORRIENDO
            alEvento(EventoTempo.Iniciado)
        }
        inicioScoreMs = minOf(inicioScoreMs, s)
        if (estado == EstadoTempo.PAUSADO) scorePausaMs = s else fijarAncla(s)
        revisarAviso()
    }

    /** Adelanta (+) o atrasa (−) la posición [deltaMs] de tiempo de partitura. */
    fun desplazar(deltaMs: Long) {
        val p = plan ?: return
        if (estado == EstadoTempo.DETENIDO || estado == EstadoTempo.FINALIZADO) return
        val s = (scoreAhora() + deltaMs).coerceIn(0.0, p.totalMs - 1.0)
        vistaLogica = p.vistaEn(s)
        if (estado == EstadoTempo.PAUSADO) scorePausaMs = s else fijarAncla(s)
        revisarAviso()
    }

    fun tick() {
        val p = plan ?: return
        if (estado == EstadoTempo.CUENTA_ATRAS) {
            if (scoreAhora() < inicioScoreMs) return
            fijarAncla(inicioScoreMs)
            estado = EstadoTempo.CORRIENDO
            alEvento(EventoTempo.Iniciado)
        }
        if (estado != EstadoTempo.CORRIENDO) return

        val ahora = reloj.ahoraMs()
        val s = scoreAhora()
        val v = vistaLogica
        val fin = p.inicioDe(v + 1)
        val esUltima = v >= p.numVistas - 1

        if (avisoEmitidoMs == null && s >= max(fin - config.avisoMs * velocidad, p.inicioDe(v))) {
            val restante = ((fin - s) / velocidad).toLong().coerceAtLeast(0)
            alEvento(EventoTempo.AvisoGiro(if (esUltima) null else v + 1, restante))
            avisoEmitidoMs = ahora
        }

        if (s >= fin) {
            // El aviso siempre existe aquí: s >= fin implica s >= fin - avisoMs.
            val girarDesde = (avisoEmitidoMs ?: ahora) + config.avisoMinimoMs
            if (ahora < girarDesde) {
                // Reloj atrasado o vista muy corta: se retiene la posición en el final hasta cumplir el mínimo.
                fijarAncla(fin)
                return
            }
            avisoEmitidoMs = null
            if (esUltima) {
                estado = EstadoTempo.FINALIZADO
                alEvento(EventoTempo.Fin)
            } else {
                vistaLogica = v + 1
                alEvento(EventoTempo.Girar(v + 1))
            }
        }
    }

    fun instantanea(): InstantaneaTempo {
        val p = plan
        if (p == null) {
            return InstantaneaTempo(estado, 0, 0.0, velocidad, 0.0, null, null)
        }
        val s = when (estado) {
            EstadoTempo.PAUSADO -> scorePausaMs
            EstadoTempo.DETENIDO -> p.inicioDe(vistaLogica)
            EstadoTempo.FINALIZADO -> p.totalMs
            else -> scoreAhora()
        }
        val v = vistaLogica.coerceIn(0, p.numVistas - 1)
        val dur = p.duracionesMs[v]
        val frac = ((s - p.inicioDe(v)) / dur).coerceIn(0.0, 1.0)
        val corriendo = estado == EstadoTempo.CORRIENDO
        return InstantaneaTempo(
            estado = estado,
            vista = v,
            fraccion = frac,
            velocidad = velocidad,
            bpmEfectivo = p.bpmPorVista[v] * velocidad,
            msHastaGiro = if (corriendo) ((p.inicioDe(v + 1) - s) / velocidad).toLong().coerceAtLeast(0) else null,
            msHastaInicio = if (estado == EstadoTempo.CUENTA_ATRAS) ((inicioScoreMs - s) / velocidad).toLong().coerceAtLeast(0) else null,
        )
    }

    // --- internos ---

    private fun scoreAhora(): Double =
        if (estado == EstadoTempo.PAUSADO) scorePausaMs
        else anclaScoreMs + (reloj.ahoraMs() - anclaRealMs) * velocidad

    private fun fijarAncla(scoreMs: Double) {
        anclaScoreMs = scoreMs
        anclaRealMs = reloj.ahoraMs()
    }

    /** Tras mover la posición o la velocidad: si el aviso emitido ya no corresponde, se cancela. */
    private fun revisarAviso() {
        val p = plan ?: return
        if (avisoEmitidoMs == null) return
        val s = if (estado == EstadoTempo.PAUSADO) scorePausaMs else scoreAhora()
        val v = vistaLogica.coerceIn(0, p.numVistas - 1)
        val umbral = max(p.inicioDe(v + 1) - config.avisoMs * velocidad, p.inicioDe(v))
        if (s < umbral) {
            avisoEmitidoMs = null
            alEvento(EventoTempo.AvisoCancelado)
        }
    }
}
