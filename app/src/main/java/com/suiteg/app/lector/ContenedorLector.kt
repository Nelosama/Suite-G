package com.suiteg.app.lector

import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout
import com.suiteg.modelo.Reloj

/** Las tres capas del lector, de abajo arriba: página, tinta y bloqueo del modo concierto. */
class ContenedorLector(contexto: Context, reloj: Reloj) : FrameLayout(contexto) {
    val pagina = VistaPartitura(contexto)
    val tinta = CapaTinta(contexto)
    val bloqueo = SuperficieBloqueo(contexto, reloj)

    /** Tamaño útil de la ventana, para que la sesión rasterice a esa resolución. */
    var alCambiarTamano: ((Int, Int) -> Unit)? = null

    init {
        setBackgroundColor(Color.rgb(0x1B, 0x1B, 0x1B))
        tinta.vista = pagina
        val llenar = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(pagina, llenar)
        addView(tinta, LayoutParams(llenar))
        addView(bloqueo, LayoutParams(llenar))
        bloqueo.visibility = GONE
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0) alCambiarTamano?.invoke(w, h)
    }
}
