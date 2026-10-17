package com.suiteg.app.lector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.suiteg.modelo.Herramienta
import com.suiteg.modelo.TransformacionVista
import com.suiteg.modelo.Trazo
import kotlin.math.abs

/**
 * Capa transparente sobre la partitura. Hace dos cosas:
 *  1. Dibuja los trazos guardados (en coordenadas de página -> pantalla con [transformacion]) y el trazo en curso.
 *     Va aparte de la página, así que los filtros de inversión NO le afectan (no se invierten dos veces).
 *  2. Recibe todos los toques y decide: un lápiz/dedo que dibuja crea trazos; todo lo demás se reenvía a
 *     [VistaPartitura] como gesto (zoom, desplazamiento, toque).
 *
 * Rechazo de palma: se ignoran los punteros de tipo palma, los contactos grandes, los punteros cancelados
 * (FLAG_CANCELED) y, mientras el lápiz está apoyado, cualquier otro puntero.
 */
class CapaTinta(contexto: Context) : View(contexto) {
    var vista: VistaPartitura? = null

    var transformacion: TransformacionVista? = null
        set(valor) { field = valor; reconstruir() }
    var trazos: List<Trazo> = emptyList()
        set(valor) { field = valor; reconstruir() }

    var modoDibujo = false
    var dedoDibuja = false
    var rechazoPalma = true
    var borrador = false
    var herramienta = Herramienta.LAPIZ
    var color = Color.rgb(0xE0, 0x59, 0x2A)
    var grosorRelativo = 0.0028f
    var mostrarTrazos = true
        set(valor) { field = valor; invalidate() }

    var alTrazoTerminado: ((Trazo) -> Unit)? = null
    var alBorrar: ((Trazo) -> Unit)? = null

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var trazados: List<Pair<Trazo, Path>> = emptyList()

    // Trazo en curso
    private var idPuntero = -1
    private var herramientaActual = Herramienta.LAPIZ
    private val puntosNormalizados = ArrayList<Float>()
    private val caminoActual = Path()
    private var ultimoX = 0f
    private var ultimoY = 0f
    private var borrando = false
    private val yaBorrados = HashSet<Long>()
    private var reenviando = false

    private val umbralPalmaPx: Float get() = 18f / 25.4f * resources.displayMetrics.xdpi

    // --- Dibujo ---

    private fun reconstruir() {
        val t = transformacion
        trazados = if (t == null) emptyList() else trazos.map { it to caminoDe(it.puntos, t) }
        invalidate()
    }

    private fun caminoDe(p: FloatArray, t: TransformacionVista): Path {
        val c = Path()
        val n = p.size / 3
        if (n == 0) return c
        var x0 = t.xAPantalla(p[0]); var y0 = t.yAPantalla(p[1])
        c.moveTo(x0, y0)
        if (n == 1) { c.lineTo(x0 + 0.1f, y0 + 0.1f); return c }
        for (i in 1 until n) {
            val x1 = t.xAPantalla(p[i * 3]); val y1 = t.yAPantalla(p[i * 3 + 1])
            c.quadTo(x0, y0, (x0 + x1) / 2f, (y0 + y1) / 2f)
            x0 = x1; y0 = y1
        }
        c.lineTo(x0, y0)
        return c
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = transformacion ?: return
        if (mostrarTrazos) {
            for ((trazo, camino) in trazados) {
                configurarPincel(trazo.herramienta, trazo.color, trazo.grosor * t.anchoPaginaPx)
                canvas.drawPath(camino, pincel)
            }
        }
        if (idPuntero != -1 && !borrando) {
            configurarPincel(herramientaActual, color, grosorRelativo * t.anchoPaginaPx)
            canvas.drawPath(caminoActual, pincel)
        }
    }

    private fun configurarPincel(h: Herramienta, c: Int, ancho: Float) {
        pincel.color = c
        if (h == Herramienta.RESALTADOR) {
            pincel.alpha = 90
            pincel.strokeWidth = ancho * 5f
        } else {
            pincel.alpha = 255
            pincel.strokeWidth = ancho.coerceAtLeast(1.5f)
        }
    }

    // --- Toques ---

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val t = transformacion
        if (!modoDibujo || t == null) {
            reenviar(ev)
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                if (idPuntero != -1) return true // ya hay un lápiz apoyado: cualquier otro puntero es palma o accidente
                if (debeIgnorar(ev, i)) return true
                if (dibuja(ev, i)) {
                    if (reenviando) cancelarReenvio(ev)
                    iniciar(ev, i, t)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (idPuntero != -1) {
                seguir(ev, t)
                return true
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> if (idPuntero != -1) {
                val i = ev.actionIndex
                if (ev.getPointerId(i) == idPuntero) terminar(ev, i, t, cancelado = (ev.flags and FLAG_CANCELED) != 0)
                return true
            }
            MotionEvent.ACTION_CANCEL -> if (idPuntero != -1) {
                descartar()
                return true
            }
        }
        if (idPuntero == -1) reenviar(ev)
        return true
    }

    /** Palma o contacto demasiado grande. */
    private fun debeIgnorar(ev: MotionEvent, i: Int): Boolean {
        if (!rechazoPalma) return false
        val tipo = ev.getToolType(i)
        if (tipo == TOOL_TYPE_PALM) return true
        if (tipo == MotionEvent.TOOL_TYPE_FINGER && ev.getTouchMajor(i) > umbralPalmaPx) return true
        return false
    }

    private fun dibuja(ev: MotionEvent, i: Int): Boolean = when (ev.getToolType(i)) {
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> true
        MotionEvent.TOOL_TYPE_FINGER -> dedoDibuja
        else -> false
    }

    private fun iniciar(ev: MotionEvent, i: Int, t: TransformacionVista) {
        requestUnbufferedDispatch(ev) // menos latencia de entrada con lápiz
        idPuntero = ev.getPointerId(i)
        herramientaActual = herramienta
        borrando = borrador ||
            ev.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER ||
            (ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        puntosNormalizados.clear()
        caminoActual.reset()
        yaBorrados.clear()
        ultimoX = ev.getX(i); ultimoY = ev.getY(i)
        if (borrando) borrarEn(ultimoX, ultimoY, t) else {
            agregar(ultimoX, ultimoY, ev.getPressure(i), t)
            caminoActual.moveTo(ultimoX, ultimoY)
            caminoActual.lineTo(ultimoX + 0.1f, ultimoY + 0.1f)
        }
        invalidate()
    }

    private fun seguir(ev: MotionEvent, t: TransformacionVista) {
        val i = ev.findPointerIndex(idPuntero)
        if (i < 0) return
        // Los eventos históricos dan todos los puntos intermedios entre fotogramas: trazos más fieles.
        for (h in 0 until ev.historySize) {
            punto(ev.getHistoricalX(i, h), ev.getHistoricalY(i, h), ev.getHistoricalPressure(i, h), t)
        }
        punto(ev.getX(i), ev.getY(i), ev.getPressure(i), t)
        invalidate()
    }

    private fun punto(x: Float, y: Float, presion: Float, t: TransformacionVista) {
        if (borrando) { borrarEn(x, y, t); return }
        if (abs(x - ultimoX) < 0.5f && abs(y - ultimoY) < 0.5f) return
        agregar(x, y, presion, t)
        caminoActual.quadTo(ultimoX, ultimoY, (ultimoX + x) / 2f, (ultimoY + y) / 2f)
        ultimoX = x; ultimoY = y
    }

    private fun agregar(x: Float, y: Float, presion: Float, t: TransformacionVista) {
        val nx = t.xAPagina(x)
        val ny = t.yAPagina(y)
        // Fuera de la hoja (con un pequeño margen) no se guarda nada.
        if (nx < -0.05f || nx > 1.05f || ny < -0.05f || ny > 1.05f) return
        puntosNormalizados += nx; puntosNormalizados += ny; puntosNormalizados += presion.coerceIn(0f, 1f)
    }

    private fun terminar(ev: MotionEvent, i: Int, t: TransformacionVista, cancelado: Boolean) {
        if (!cancelado && !borrando) {
            val x = ev.getX(i); val y = ev.getY(i)
            if (abs(x - ultimoX) >= 0.5f || abs(y - ultimoY) >= 0.5f) agregar(x, y, ev.getPressure(i), t)
            if (puntosNormalizados.isNotEmpty()) {
                alTrazoTerminado?.invoke(
                    Trazo(
                        pagina = 0, // la sesión pone la página real
                        herramienta = herramientaActual,
                        color = color,
                        grosor = grosorRelativo,
                        puntos = puntosNormalizados.toFloatArray(),
                    ),
                )
            }
        }
        descartar()
    }

    private fun descartar() {
        idPuntero = -1
        borrando = false
        puntosNormalizados.clear()
        caminoActual.reset()
        invalidate()
    }

    private fun borrarEn(x: Float, y: Float, t: TransformacionVista) {
        val nx = t.xAPagina(x)
        val ny = t.yAPagina(y)
        val aspecto = t.altoPaginaPt / t.anchoPaginaPt
        for (trazo in trazos) {
            if (trazo.id in yaBorrados) continue
            if (trazo.distanciaA(nx, ny, aspecto) <= RADIO_BORRADO + trazo.grosor) {
                yaBorrados += trazo.id
                alBorrar?.invoke(trazo)
            }
        }
    }

    // --- Reenvío de gestos ---

    private fun reenviar(ev: MotionEvent) {
        val v = vista ?: return
        v.procesarToque(ev)
        reenviando = ev.actionMasked != MotionEvent.ACTION_UP && ev.actionMasked != MotionEvent.ACTION_CANCEL
    }

    private fun cancelarReenvio(ev: MotionEvent) {
        val copia = MotionEvent.obtain(ev)
        copia.action = MotionEvent.ACTION_CANCEL
        vista?.procesarToque(copia)
        copia.recycle()
        reenviando = false
    }

    companion object {
        /** `MotionEvent.TOOL_TYPE_PALM` existe desde la API 31; minSdk es 29. */
        private const val TOOL_TYPE_PALM = 5

        /** `MotionEvent.FLAG_CANCELED` es público desde la API 33. */
        private const val FLAG_CANCELED = 0x20

        /** Radio del borrador, en anchos de página. */
        private const val RADIO_BORRADO = 0.012f
    }
}
