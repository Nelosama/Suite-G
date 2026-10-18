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

/** Paleta del rediseño: carbón cálido con un único acento óxido y un cobre suave para detalles. */
object Paleta {
    val Carbon = Color(0xFF191A19)
    val Lateral = Color(0xFF151614)
    val CarbonClaro = Color(0xFF20211F)
    val CarbonMedio = Color(0xFF262822)
    val Linea = Color(0xFF30312E)
    val LineaFuerte = Color(0xFF3C3E36)
    val Oxido = Color(0xFFCA582E)
    val OxidoClaro = Color(0xFFDAA17D)
    val SeleccionFondo = Color(0xFF34261F)
    val SeleccionTexto = Color(0xFFE79870)
    val Texto = Color(0xFFE9E9E5)
    val TextoSuave = Color(0xFF92948C)
    val Conectado = Color(0xFF8DAA91)
    val Alerta = Color(0xFFD17A4D)
    val Papel = Color(0xFFEFEDDF)
    val TextoSobreOxido = Color(0xFFFFF5ED)
}

/**
 * Radio de esquina de toda la interfaz, en un solo sitio. El rediseño usa esquinas pequeñas (4–8 dp);
 * para volver a esquinas totalmente rectas basta con poner 0.dp.
 */
object Formas {
    private val radio = 4.dp
    val Pequena = RoundedCornerShape(radio)
    val Media = RoundedCornerShape(radio + 2.dp)
    val Grande = RoundedCornerShape(radio + 4.dp)
    val Redonda = RoundedCornerShape(percent = 50)
}

val Serifa = FontFamily.Serif

private val Esquema = darkColorScheme(
    primary = Paleta.Oxido,
    onPrimary = Paleta.TextoSobreOxido,
    primaryContainer = Paleta.SeleccionFondo,
    onPrimaryContainer = Paleta.SeleccionTexto,
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

private val FormasMaterial = Shapes(
    extraSmall = Formas.Pequena, small = Formas.Pequena, medium = Formas.Media,
    large = Formas.Grande, extraLarge = Formas.Grande,
)

private val Tipografia = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 33.sp, letterSpacing = (-1.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.5.sp),
)

@Composable
fun TemaSuiteG(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, shapes = FormasMaterial, typography = Tipografia, content = contenido)
}
