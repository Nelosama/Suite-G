package com.suiteg.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta utilitaria: fondos carbón y un único acento óxido. */
object Paleta {
    val Carbon = Color(0xFF1B1B1B)
    val CarbonClaro = Color(0xFF262626)
    val CarbonMedio = Color(0xFF303030)
    val Linea = Color(0xFF4A4A4A)
    val Oxido = Color(0xFFC4501F)
    val OxidoClaro = Color(0xFFE2703A)
    val Texto = Color(0xFFE8E8E8)
    val TextoSuave = Color(0xFFA8A8A8)
    val Conectado = Color(0xFF7CCB6F)
    val Alerta = Color(0xFFFF7043)
}

private val Esquema = darkColorScheme(
    primary = Paleta.Oxido,
    onPrimary = Color.White,
    primaryContainer = Paleta.CarbonMedio,
    onPrimaryContainer = Paleta.OxidoClaro,
    secondary = Paleta.OxidoClaro,
    onSecondary = Color.Black,
    background = Paleta.Carbon,
    onBackground = Paleta.Texto,
    surface = Paleta.CarbonClaro,
    onSurface = Paleta.Texto,
    surfaceVariant = Paleta.CarbonMedio,
    onSurfaceVariant = Paleta.TextoSuave,
    outline = Paleta.Linea,
    outlineVariant = Paleta.Linea,
    error = Paleta.Alerta,
    onError = Color.Black,
)

// Sin esquinas redondeadas en ningún componente.
// Material 3 exige formas con esquinas; con radio 0 son rectángulos puros.
private val Recta = RoundedCornerShape(0.dp)
private val SinEsquinas = Shapes(extraSmall = Recta, small = Recta, medium = Recta, large = Recta, extraLarge = Recta)

private val Tipografia = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

@Composable
fun TemaSuiteG(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, shapes = SinEsquinas, typography = Tipografia, content = contenido)
}
