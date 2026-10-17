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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RectangleShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val Relleno = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

@Composable
fun Cabecera(titulo: String, alAtras: (() -> Unit)? = null, acciones: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(Paleta.CarbonClaro).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (alAtras != null) {
                TextButton(onClick = alAtras, shape = RectangleShape) { Text("← Atrás") }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            acciones()
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Paleta.Oxido))
    }
}

@Composable
fun BotonOxido(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    Button(
        onClick = alPulsar, modifier = modifier, enabled = habilitado, shape = RectangleShape, contentPadding = Relleno,
        colors = ButtonDefaults.buttonColors(containerColor = Paleta.Oxido, contentColor = androidx.compose.ui.graphics.Color.White),
    ) { Text(texto) }
}

@Composable
fun BotonContorno(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    OutlinedButton(
        onClick = alPulsar, modifier = modifier, enabled = habilitado, shape = RectangleShape, contentPadding = Relleno,
        border = BorderStroke(1.dp, Paleta.Linea),
    ) { Text(texto, color = Paleta.Texto) }
}

/** Botón de conmutación rectangular: relleno óxido si está activo. */
@Composable
fun Opcion(texto: String, activa: Boolean, alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(if (activa) Paleta.Oxido else Paleta.CarbonMedio)
            .border(1.dp, if (activa) Paleta.OxidoClaro else Paleta.Linea)
            .clickable(onClick = alPulsar)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activa) androidx.compose.ui.graphics.Color.White else Paleta.Texto, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun <T> Selector(opciones: List<Pair<T, String>>, seleccion: T, alElegir: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((valor, etiqueta) in opciones) Opcion(etiqueta, valor == seleccion, { alElegir(valor) })
    }
}

@Composable
fun Seccion(titulo: String) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp, start = 16.dp, end = 16.dp)) {
        Text(titulo.uppercase(), style = MaterialTheme.typography.labelLarge, color = Paleta.OxidoClaro)
        HorizontalDivider(Modifier.padding(top = 4.dp), color = Paleta.Linea)
    }
}

@Composable
fun FilaAjuste(titulo: String, descripcion: String? = null, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { alCambiar(!activo) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (descripcion != null) Text(descripcion, style = MaterialTheme.typography.bodySmall, color = Paleta.TextoSuave)
        }
        Switch(
            checked = activo, onCheckedChange = alCambiar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedTrackColor = Paleta.Oxido,
                uncheckedThumbColor = Paleta.TextoSuave, uncheckedTrackColor = Paleta.CarbonMedio, uncheckedBorderColor = Paleta.Linea,
            ),
        )
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
        shape = RectangleShape,
        containerColor = Paleta.CarbonClaro,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it }, label = { Text(etiqueta) },
                minLines = lineas, maxLines = if (lineas > 1) lineas + 3 else 1, singleLine = lineas == 1, shape = RectangleShape,
                keyboardOptions = KeyboardOptions(keyboardType = if (soloNumeros) KeyboardType.Decimal else KeyboardType.Text),
            )
        },
        confirmButton = { TextButton(onClick = { alConfirmar(texto) }, shape = RectangleShape) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = alCancelar, shape = RectangleShape) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoConfirmar(titulo: String, mensaje: String, textoConfirmar: String = "Aceptar", alConfirmar: () -> Unit, alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        shape = RectangleShape,
        containerColor = Paleta.CarbonClaro,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = { TextButton(onClick = alConfirmar, shape = RectangleShape) { Text(textoConfirmar, color = Paleta.OxidoClaro) } },
        dismissButton = { TextButton(onClick = alCancelar, shape = RectangleShape) { Text("Cancelar") } },
    )
}

/** Diálogo con contenido libre y un solo botón "Cerrar". */
@Composable
fun DialogoContenido(titulo: String, alCerrar: () -> Unit, contenido: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        shape = RectangleShape,
        containerColor = Paleta.CarbonClaro,
        title = { Text(titulo) },
        text = { Column { contenido() } },
        confirmButton = { TextButton(onClick = alCerrar, shape = RectangleShape) { Text("Cerrar") } },
    )
}

fun formatoDuracion(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val seg = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%d:%02d".format(m, seg)
}
