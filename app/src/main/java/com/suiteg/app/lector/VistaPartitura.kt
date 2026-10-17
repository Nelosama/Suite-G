package com.suiteg.app.lector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/** Resultado de un gesto de zoom/desplazamiento, para que la sesión recalcule y vuelva a rasterizar. */
data class GestoPagina(val factorZoom: Float, val focoX: Float, val focoY: Float, val dx: Float, val dy: Float)

/**
 * Muestra la imagen ya rasterizada de la página. No decide qué mostrar: la sesión le entrega bitmaps del
 * tamaño de la ventana. Durante un gesto aplica una matriz provisional (feedback inmediato) y, al soltar,
 * avisa con [alGesto] para que se rasterice en nitidez.
 *
 * Los toques le llegan por [procesarToque]; los reenvía la capa de tinta, que es la que decide si un
 * puntero dibuja o hace gestos.
 */
class VistaPartitura(contexto: Context) : View(contexto) {
    private var imagen: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val provisional = Matrix()
    private var presentacionPendiente = false

    var zoomTactil = true
    var alGesto: ((GestoPagina) -> Unit)? = null
    var alToque: ((Float, Float) -> Unit)? = null
    var alDobleToque: (() -> Unit)? = null

    /** Se llama cuando una imagen nueva ya está en pantalla (para medir la latencia de extremo a extremo). */
    var alPresentada: (() -> Unit)? = null

    private var zoomAcum = 1f
    private var focoX = 0f
    private var focoY = 0f
    private var dxAcum = 0f
    private var dyAcum = 0f
    private var gestoActivo = false

    private val detectorZoom = ScaleGestureDetector(contexto, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            if (!zoomTactil) return false
            val f = d.scaleFactor
            provisional.postScale(f, f, d.focusX, d.focusY)
            zoomAcum *= f
            focoX = d.focusX
            focoY = d.focusY
            gestoActivo = true
            invalidate()
            return true
        }
    })

    private val detectorGestos = GestureDetector(contexto, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanciaX: Float, distanciaY: Float): Boolean {
            if (!zoomTactil || detectorZoom.isInProgress) return false
            provisional.postTranslate(-distanciaX, -distanciaY)
            dxAcum += distanciaX
            dyAcum += distanciaY
            gestoActivo = true
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            alToque?.invoke(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            alDobleToque?.invoke()
            return true
        }
    })

    init {
        setBackgroundColor(Color.rgb(0x1B, 0x1B, 0x1B))
    }

    fun mostrar(b: Bitmap?) {
        imagen = b
        provisional.reset()
        zoomAcum = 1f; dxAcum = 0f; dyAcum = 0f; gestoActivo = false
        presentacionPendiente = b != null
        invalidate()
    }

    fun establecerFiltro(filtro: ColorFilter?) {
        paint.colorFilter = filtro
        invalidate()
    }

    /** Alimenta los detectores de gestos. Devuelve siempre true: la vista consume el flujo. */
    fun procesarToque(ev: MotionEvent): Boolean {
        detectorZoom.onTouchEvent(ev)
        detectorGestos.onTouchEvent(ev)
        if ((ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) && gestoActivo) {
            gestoActivo = false
            val g = GestoPagina(zoomAcum, focoX, focoY, dxAcum, dyAcum)
            zoomAcum = 1f; dxAcum = 0f; dyAcum = 0f
            alGesto?.invoke(g) // la matriz provisional se mantiene hasta que llegue la imagen nueva
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = imagen ?: return
        canvas.save()
        canvas.concat(provisional)
        canvas.drawBitmap(b, 0f, 0f, paint)
        canvas.restore()
        if (presentacionPendiente) {
            presentacionPendiente = false
            // El callback se ejecuta al inicio del siguiente fotograma: es la mejor medida de "ya en pantalla" sin APIs de display.
            Choreographer.getInstance().postFrameCallback { alPresentada?.invoke() }
        }
    }
}
