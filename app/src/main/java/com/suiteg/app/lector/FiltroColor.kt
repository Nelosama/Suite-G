package com.suiteg.app.lector

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.suiteg.modelo.MatricesColor
import com.suiteg.modelo.ModoInversion

object FiltroColor {
    /** Filtro para dibujar la página según el modo de inversión, o null si no hay que filtrar. */
    fun para(modo: ModoInversion): ColorMatrixColorFilter? =
        MatricesColor.para(modo)?.let { ColorMatrixColorFilter(ColorMatrix(it)) }
}
