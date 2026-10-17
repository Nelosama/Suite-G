package com.suiteg.app.lector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.suiteg.modelo.Reloj
import com.suiteg.navegacion.DetectorDesbloqueo

/**
 * Modo concierto: superficie que cubre toda la pantalla y se come TODOS los toques, para que ni un roce
 * del arco ni una mano cierren o muevan la partitura. El pedal no pasa por aquí (llega por el teclado).
 *
 * Desbloqueo: mantener UN dedo (no una palma) en la esquina inferior derecha durante la duración
 * configurada (3 s por defecto). Salir de la zona, usar un segundo dedo o soltar reinicia la cuenta.
 */
class SuperficieBloqueo(contexto: Context, reloj: Reloj) : View(contexto) {
    var alDesbloquear: (() -> Unit)? = null

    var duracionMs: Long
        get() = detector.duracionMs
        set(valor) { detector.duracionMs = valor }

    private val detector = DetectorDesbloqueo(reloj, 3000, tamanoMaximoContacto = TAMANO_MAX_MM)
    private val densidad = resources.displayMetrics.density
    private val zona = RectF()
    private val pincelMarco = Paint().apply { style = Paint.Style.STROKE; color = Color.argb(70, 0xC4, 0x50, 0x1F); strokeWidth = 2f * densidad }
    private val pincelProgreso = Paint().apply { style = Paint.Style.FILL; color = Color.argb(200, 0xC4, 0x50, 0x1F) }

    private val vigilar = object : Runnable {
        override fun run() {
            if (detector.completado()) {
                detector.consumir()
                invalidate()
                alDesbloquear?.invoke()
            } else if (detector.enCurso) {
                invalidate()
                postDelayed(this, 40)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val lado = 112f * densidad
        zona.set(w - lado, h - lado, w.toFloat(), h.toFloat())
    }

    private fun milimetros(ev: MotionEvent): Float = ev.getTouchMajor(0) / resources.displayMetrics.xdpi * 25.4f

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                detector.alBajar(zona.contains(ev.x, ev.y), ev.pointerCount, milimetros(ev))
                if (detector.enCurso) {
                    removeCallbacks(vigilar)
                    post(vigilar)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> detector.alBajarOtroPuntero()
            MotionEvent.ACTION_MOVE -> detector.alMover(zona.contains(ev.x, ev.y), ev.pointerCount)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                detector.alSoltar()
                removeCallbacks(vigilar)
            }
        }
        invalidate()
        return true // consume todo, también lo que no sea desbloqueo
    }

    // Los lápices y ratones que "flotan" tampoco deben llegar a la partitura.
    override fun onGenericMotionEvent(event: MotionEvent): Boolean = true

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(zona, pincelMarco)
        val p = detector.progreso()
        if (p > 0f) canvas.drawRect(zona.left, zona.bottom - zona.height() * p, zona.right, zona.bottom, pincelProgreso)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(vigilar)
        super.onDetachedFromWindow()
    }

    companion object {
        /** Un dedo mide unos 8-12 mm; una palma, bastante más. */
        private const val TAMANO_MAX_MM = 16f
    }
}
