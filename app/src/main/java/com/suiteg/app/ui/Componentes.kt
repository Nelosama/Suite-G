package com.suiteg.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RellenoBoton = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

/** Cabecera sencilla con botón atrás, para pantallas secundarias. */
@Composable
fun Cabecera(titulo: String, alAtras: (() -> Unit)? = null, acciones: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(Paleta.CarbonClaro).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (alAtras != null) TextButton(onClick = alAtras) { Icono(TipoIcono.ATRAS); Spacer(Modifier.width(6.dp)); Text("Atrás") }
            else Spacer(Modifier.width(8.dp))
            Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            acciones()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Linea))
    }
}

/** Bloque de título de página: antetítulo en mayúsculas, título grande, frase y una acción principal. */
@Composable
fun EncabezadoPagina(antetitulo: String, titulo: String, subtitulo: String, accion: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 27.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f)) {
            Text(antetitulo.uppercase(), style = MaterialTheme.typography.labelSmall, color = Paleta.TextoSuave)
            Text(titulo, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 9.dp, bottom = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = Paleta.TextoSuave)
        }
        accion()
    }
}

@Composable
fun BotonOxido(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, icono: TipoIcono? = null, iconoFinal: TipoIcono? = null) {
    Button(
        onClick = alPulsar, modifier = modifier.heightIn(min = 41.dp), enabled = habilitado, shape = Formas.Pequena, contentPadding = RellenoBoton,
        colors = ButtonDefaults.buttonColors(
            containerColor = Paleta.Oxido, contentColor = Paleta.TextoSobreOxido,
            disabledContainerColor = Paleta.Oxido.copy(alpha = 0.35f), disabledContentColor = Paleta.TextoSobreOxido.copy(alpha = 0.5f),
        ),
    ) {
        if (icono != null) { Icono(icono, tamano = 15.dp); Spacer(Modifier.width(9.dp)) }
        Text(texto, fontSize = 13.sp, style = MaterialTheme.typography.labelLarge)
        if (iconoFinal != null) { Spacer(Modifier.width(9.dp)); Icono(iconoFinal, tamano = 15.dp) }
    }
}

@Composable
fun BotonContorno(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, icono: TipoIcono? = null) {
    OutlinedButton(
        onClick = alPulsar, modifier = modifier.heightIn(min = 41.dp), enabled = habilitado, shape = Formas.Pequena, contentPadding = RellenoBoton,
        border = BorderStroke(1.dp, Paleta.LineaFuerte),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Paleta.CarbonMedio, contentColor = Paleta.Texto, disabledContentColor = Paleta.Texto.copy(alpha = 0.35f)),
    ) {
        if (icono != null) { Icono(icono, tamano = 15.dp); Spacer(Modifier.width(9.dp)) }
        Text(texto, fontSize = 13.sp, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botón sin marco, para acciones secundarias dentro de una fila. */
@Composable
fun BotonTexto(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, color: Color = Paleta.TextoSuave, icono: TipoIcono? = null) {
    Row(
        modifier.clickable(onClick = alPulsar).heightIn(min = 36.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) { Icono(icono, color = color, tamano = 15.dp); Spacer(Modifier.width(6.dp)) }
        Text(texto, color = color, fontSize = 12.sp)
    }
}

/** Botón cuadrado solo con icono. */
@Composable
fun BotonIcono(icono: TipoIcono, descripcion: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, color: Color = Paleta.Texto) {
    Box(
        modifier.size(44.dp).then(if (habilitado) Modifier.clickable(onClick = alPulsar) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icono(icono, color = if (habilitado) color else color.copy(alpha = 0.3f), tamano = 20.dp) }
}

/** Botón de conmutación: relleno óxido si está activo. */
@Composable
fun Opcion(texto: String, activa: Boolean, alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(if (activa) Paleta.Oxido else Paleta.CarbonMedio, Formas.Pequena)
            .border(1.dp, if (activa) Paleta.Oxido else Paleta.LineaFuerte, Formas.Pequena)
            .clickable(onClick = alPulsar)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activa) Paleta.TextoSobreOxido else Paleta.Texto, fontSize = 13.sp, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun <T> Selector(opciones: List<Pair<T, String>>, seleccion: T, alElegir: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((valor, etiqueta) in opciones) Opcion(etiqueta, valor == seleccion, { alElegir(valor) })
    }
}

/** Título de un grupo de ajustes. */
@Composable
fun TituloGrupo(titulo: String) {
    Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp))
}

/** Tarjeta de superficie con borde fino. */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, seleccionada: Boolean = false, alPulsar: (() -> Unit)? = null, contenido: @Composable () -> Unit) {
    Box(
        modifier
            .background(if (seleccionada) Paleta.SeleccionFondo else Paleta.CarbonClaro, Formas.Grande)
            .border(1.dp, if (seleccionada) Color(0xFFA25232) else Paleta.Linea, Formas.Grande)
            .then(if (alPulsar != null) Modifier.clickable(onClick = alPulsar) else Modifier),
    ) { contenido() }
}

/** Aviso informativo en una franja. */
@Composable
fun Franja(texto: String, modifier: Modifier = Modifier, icono: TipoIcono = TipoIcono.ESCUDO, color: Color = Paleta.TextoSuave) {
    Row(
        modifier.fillMaxWidth().background(Paleta.CarbonClaro, Formas.Pequena).border(1.dp, Paleta.Linea, Formas.Pequena).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icono(icono, color = color, tamano = 16.dp)
        Text(texto, color = color, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

@Composable
fun FilaAjuste(titulo: String, descripcion: String? = null, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { alCambiar(!activo) }.heightIn(min = 72.dp).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, fontSize = 14.sp)
                if (descripcion != null) Text(descripcion, fontSize = 12.sp, color = Paleta.TextoSuave, modifier = Modifier.padding(top = 6.dp), lineHeight = 17.sp)
            }
            Switch(
                checked = activo, onCheckedChange = alCambiar,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = Paleta.Oxido, checkedBorderColor = Paleta.Oxido,
                    uncheckedThumbColor = Paleta.TextoSuave, uncheckedTrackColor = Paleta.CarbonMedio, uncheckedBorderColor = Paleta.LineaFuerte,
                ),
            )
        }
        HorizontalDivider(color = Paleta.Linea)
    }
}

@Composable
fun DialogoTexto(
    titulo: String,
    valorInicial: String = "",
    etiqueta: String = "",
    lineas: Int = 1,
    soloNumeros: Boolean = false,
    alConfirmar: (String) -> Unit,
    alCancelar: () -> Unit,
) {
    var texto by remember { mutableStateOf(valorInicial) }
    AlertDialog(
        onDismissRequest = alCancelar,
        shape = Formas.Grande,
        containerColor = Color(0xFF242620),
        title = { Text(titulo, fontFamily = Serifa, fontSize = 24.sp) },
        text = {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it }, label = { Text(etiqueta) },
                minLines = lineas, maxLines = if (lineas > 1) lineas + 3 else 1, singleLine = lineas == 1, shape = Formas.Pequena,
                keyboardOptions = KeyboardOptions(keyboardType = if (soloNumeros) KeyboardType.Decimal else KeyboardType.Text),
            )
        },
        confirmButton = { TextButton(onClick = { alConfirmar(texto) }) { Text("Aceptar", color = Paleta.OxidoClaro) } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoConfirmar(titulo: String, mensaje: String, textoConfirmar: String = "Aceptar", alConfirmar: () -> Unit, alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        shape = Formas.Grande,
        containerColor = Color(0xFF242620),
        title = { Text(titulo, fontFamily = Serifa, fontSize = 24.sp) },
        text = { Text(mensaje, color = Color(0xFFA6ACA0), lineHeight = 22.sp) },
        confirmButton = { TextButton(onClick = alConfirmar) { Text(textoConfirmar, color = Paleta.OxidoClaro) } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

/** Diálogo con contenido libre y un solo botón "Cerrar". */
@Composable
fun DialogoContenido(titulo: String, alCerrar: () -> Unit, contenido: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        shape = Formas.Grande,
        containerColor = Color(0xFF242620),
        title = { Text(titulo, fontFamily = Serifa, fontSize = 24.sp) },
        text = { Column { contenido() } },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar", color = Paleta.OxidoClaro) } },
    )
}

fun formatoDuracion(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val seg = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%d:%02d".format(m, seg)
}

/** "Hoy", "Ayer" o "14 oct." a partir de una marca de tiempo. */
fun fechaRelativa(epochMs: Long, ahora: Long = System.currentTimeMillis()): String {
    val cal = java.util.Calendar.getInstance()
    fun dia(t: Long): Long { cal.timeInMillis = t; cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0); return cal.timeInMillis }
    val dias = ((dia(ahora) - dia(epochMs)) / 86_400_000L).toInt()
    return when {
        dias <= 0 -> "Hoy"
        dias == 1 -> "Ayer"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
    }
}
