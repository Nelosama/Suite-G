package com.suiteg.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class TipoIcono {
    BIBLIOTECA, SETLISTS, GRABACIONES, PEDAL, AJUSTES, PLAY, PAUSA, MAS, MENOS, ATRAS, SIGUIENTE, BUSCAR,
    CANDADO, CANDADO_ABIERTO, CHECK, CERRAR, LUNA, SOL, OJO, LAPIZ, NOTA, LISTA, CUADRICULA, ESCUDO,
    FLECHA_ABAJO, FLECHA_DIAGONAL, BLUETOOTH, RELOJ, ARCHIVO, CARPETA, AURICULARES,
}

/**
 * Iconos dibujados a mano sobre una cuadrícula de 24 unidades (trazo fino, puntas redondas), en el estilo
 * lineal del rediseño. Sin librería de iconos: cero dependencias y siempre del mismo color que el texto.
 */
@Composable
fun Icono(tipo: TipoIcono, modifier: Modifier = Modifier, color: Color = LocalContentColor.current, tamano: Dp = 18.dp) {
    Canvas(modifier.size(tamano)) { dibujar(tipo, color) }
}

private fun DrawScope.dibujar(tipo: TipoIcono, color: Color) {
    val u = size.width / 24f
    val trazo = Stroke(width = 1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun linea(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = 1.7f * u, cap = StrokeCap.Round)
    fun poli(cerrado: Boolean, vararg v: Float) {
        val c = Path()
        c.moveTo(v[0] * u, v[1] * u)
        var i = 2
        while (i < v.size) { c.lineTo(v[i] * u, v[i + 1] * u); i += 2 }
        if (cerrado) c.close()
        drawPath(c, color, style = trazo)
    }
    fun rect(x: Float, y: Float, w: Float, h: Float, relleno: Boolean = false) =
        drawRect(color, p(x, y), Size(w * u, h * u), style = if (relleno) Fill else trazo)
    fun circulo(cx: Float, cy: Float, r: Float, relleno: Boolean = false) =
        drawCircle(color, r * u, p(cx, cy), style = if (relleno) Fill else trazo)

    when (tipo) {
        TipoIcono.BIBLIOTECA -> { rect(5f, 4f, 14f, 16f); linea(9f, 4f, 9f, 20f); linea(12f, 8f, 16f, 8f) }
        TipoIcono.SETLISTS, TipoIcono.LISTA -> {
            for (y in listOf(7f, 12f, 17f)) { circulo(5f, y, 1f, true); linea(9f, y, 20f, y) }
        }
        TipoIcono.GRABACIONES -> {
            val barras = listOf(5f to (10f to 14f), 8.5f to (7f to 17f), 12f to (4f to 20f), 15.5f to (8f to 16f), 19f to (10f to 14f))
            for ((x, ys) in barras) linea(x, ys.first, x, ys.second)
        }
        TipoIcono.PEDAL -> { poli(true, 5f, 18f, 8f, 8f, 16f, 8f, 19f, 18f); linea(4f, 18f, 20f, 18f); linea(12f, 8f, 12f, 5f) }
        TipoIcono.AJUSTES -> {
            linea(4f, 7f, 20f, 7f); linea(4f, 12f, 20f, 12f); linea(4f, 17f, 20f, 17f)
            drawCircle(Color(0xFF151614), 2.4f * u, p(9f, 7f)); circulo(9f, 7f, 2f)
            drawCircle(Color(0xFF151614), 2.4f * u, p(15f, 12f)); circulo(15f, 12f, 2f)
            drawCircle(Color(0xFF151614), 2.4f * u, p(8f, 17f)); circulo(8f, 17f, 2f)
        }
        TipoIcono.PLAY -> {
            val c = Path().apply { moveTo(8f * u, 5f * u); lineTo(19f * u, 12f * u); lineTo(8f * u, 19f * u); close() }
            drawPath(c, color, style = Fill)
        }
        TipoIcono.PAUSA -> { rect(7f, 5f, 3.5f, 14f, true); rect(13.5f, 5f, 3.5f, 14f, true) }
        TipoIcono.MAS -> { linea(12f, 5f, 12f, 19f); linea(5f, 12f, 19f, 12f) }
        TipoIcono.MENOS -> linea(5f, 12f, 19f, 12f)
        TipoIcono.ATRAS -> { linea(19f, 12f, 5f, 12f); poli(false, 11f, 6f, 5f, 12f, 11f, 18f) }
        TipoIcono.SIGUIENTE -> { linea(5f, 12f, 19f, 12f); poli(false, 13f, 6f, 19f, 12f, 13f, 18f) }
        TipoIcono.BUSCAR -> { circulo(11f, 11f, 6f); linea(16f, 16f, 20f, 20f) }
        TipoIcono.CANDADO -> {
            rect(5f, 11f, 14f, 9f)
            val c = Path().apply { moveTo(8f * u, 11f * u); lineTo(8f * u, 8f * u); arcTo(Rect(8f * u, 4f * u, 16f * u, 12f * u), 180f, 180f, false); lineTo(16f * u, 11f * u) }
            drawPath(c, color, style = trazo)
        }
        TipoIcono.CANDADO_ABIERTO -> {
            rect(5f, 11f, 14f, 9f)
            val c = Path().apply { moveTo(8f * u, 11f * u); lineTo(8f * u, 7f * u); arcTo(Rect(8f * u, 3f * u, 16f * u, 11f * u), 180f, 180f, false); lineTo(16f * u, 7f * u) }
            drawPath(c, color, style = trazo)
        }
        TipoIcono.CHECK -> poli(false, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
        TipoIcono.CERRAR -> { linea(6f, 6f, 18f, 18f); linea(18f, 6f, 6f, 18f) }
        TipoIcono.LUNA -> {
            val a = Path().apply { addOval(Rect(p(4f, 4f), Size(16f * u, 16f * u))) }
            val b = Path().apply { addOval(Rect(p(9f, 1f), Size(14f * u, 14f * u))) }
            drawPath(Path.combine(PathOperation.Difference, a, b), color, style = Fill)
        }
        TipoIcono.SOL -> {
            circulo(12f, 12f, 4f)
            for (i in 0 until 8) {
                val ang = Math.toRadians(i * 45.0)
                val c = Math.cos(ang).toFloat(); val s = Math.sin(ang).toFloat()
                linea(12f + c * 7f, 12f + s * 7f, 12f + c * 9.5f, 12f + s * 9.5f)
            }
        }
        TipoIcono.OJO -> {
            val c = Path().apply { addOval(Rect(p(3f, 7.5f), Size(18f * u, 9f * u))) }
            drawPath(c, color, style = trazo); circulo(12f, 12f, 2.4f, true)
        }
        TipoIcono.LAPIZ -> poli(true, 4f, 20f, 5f, 15f, 16f, 4f, 20f, 8f, 9f, 19f)
        TipoIcono.NOTA -> { circulo(8f, 18f, 3f, true); linea(11f, 18f, 11f, 5f); poli(false, 11f, 5f, 18f, 8f, 18f, 11f) }
        TipoIcono.CUADRICULA -> { rect(4f, 4f, 7f, 7f); rect(13f, 4f, 7f, 7f); rect(4f, 13f, 7f, 7f); rect(13f, 13f, 7f, 7f) }
        TipoIcono.ESCUDO -> poli(true, 12f, 3f, 19f, 6f, 19f, 12f, 12f, 21f, 5f, 12f, 5f, 6f)
        TipoIcono.FLECHA_ABAJO -> { linea(12f, 5f, 12f, 19f); poli(false, 6f, 13f, 12f, 19f, 18f, 13f) }
        TipoIcono.FLECHA_DIAGONAL -> { linea(7f, 17f, 17f, 7f); poli(false, 8f, 7f, 17f, 7f, 17f, 16f) }
        TipoIcono.BLUETOOTH -> poli(false, 7f, 7f, 17f, 17f, 12f, 22f, 12f, 2f, 17f, 7f, 7f, 17f)
        TipoIcono.RELOJ -> { circulo(12f, 12f, 9f); poli(false, 12f, 7f, 12f, 12f, 15f, 14f) }
        TipoIcono.ARCHIVO -> { rect(6f, 3f, 12f, 18f); linea(9f, 8f, 15f, 8f); linea(9f, 12f, 15f, 12f); linea(9f, 16f, 12f, 16f) }
        TipoIcono.CARPETA -> poli(true, 3f, 7f, 9f, 7f, 11f, 9f, 21f, 9f, 21f, 19f, 3f, 19f)
        TipoIcono.AURICULARES -> {
            val c = Path().apply { moveTo(4f * u, 14f * u); arcTo(Rect(4f * u, 4f * u, 20f * u, 20f * u), 180f, 180f, false) }
            drawPath(c, color, style = trazo); rect(4f, 13f, 3.5f, 6f); rect(16.5f, 13f, 3.5f, 6f)
        }
    }
}
