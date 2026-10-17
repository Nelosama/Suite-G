package com.suiteg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suiteg.app.Contenedor
import com.suiteg.app.audio.ReproductorAudio
import com.suiteg.app.datos.GrabacionE
import com.suiteg.app.datos.MarcaGrabacionE
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PantallaGrabaciones(c: Contenedor, alAtras: () -> Unit) {
    val scope = rememberCoroutineScope()
    val grabaciones by c.repoGrabaciones.todas.collectAsState(emptyList())
    val estado by c.grabacion.estado.collectAsState()
    val reproductor = remember { ReproductorAudio() }
    var actual by remember { mutableStateOf<GrabacionE?>(null) }
    var marcas by remember { mutableStateOf<List<MarcaGrabacionE>>(emptyList()) }
    var posicion by remember { mutableStateOf(0) }
    var duracion by remember { mutableStateOf(0) }
    var sonando by remember { mutableStateOf(false) }
    var aEliminar by remember { mutableStateOf<GrabacionE?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { reproductor.liberar() } }
    LaunchedEffect(actual) {
        while (actual != null) {
            posicion = reproductor.posicionMs
            sonando = reproductor.reproduciendo
            delay(250)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Cabecera("Grabaciones", alAtras)
        error?.let { Text(it, Modifier.padding(16.dp), color = Paleta.Alerta) }
        if (estado.activa) Text("● Grabando ahora mismo…", Modifier.padding(16.dp), color = Paleta.Alerta, style = MaterialTheme.typography.titleMedium)
        if (grabaciones.isEmpty()) Text("Aún no hay grabaciones. Actívalas en Ajustes y usa «Grabar» dentro del lector.", Modifier.padding(24.dp), color = Paleta.TextoSuave)

        LazyColumn(Modifier.fillMaxSize()) {
            items(grabaciones, key = { it.id }) { g ->
                val sel = actual?.id == g.id
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (sel) return@clickable
                            reproductor.alError = { error = it }
                            reproductor.alTerminar = { sonando = false }
                            if (reproductor.cargar(g.ruta)) {
                                actual = g; error = null; duracion = reproductor.duracionMs.takeIf { it > 0 } ?: g.duracionMs.toInt()
                                scope.launch { marcas = c.repoGrabaciones.marcas(g.id) }
                            }
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.titulo, style = MaterialTheme.typography.titleMedium)
                            Text(
                                SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(g.inicioEpochMs)) + "  ·  " + formatoDuracion(g.duracionMs) +
                                    when (g.estado) { "RECUPERADA" -> "  ·  recuperada"; "ERROR" -> "  ·  con errores"; "EN_CURSO" -> "  ·  en curso"; else -> "" },
                                style = MaterialTheme.typography.bodySmall, color = Paleta.TextoSuave,
                            )
                        }
                        BotonContorno("Eliminar", { aEliminar = g })
                    }
                    if (sel) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BotonOxido(if (sonando) "⏸ Pausa" else "▶ Reproducir", { reproductor.alternar(); sonando = reproductor.reproduciendo })
                                Text("  ${formatoDuracion(posicion.toLong())} / ${formatoDuracion(duracion.toLong())}")
                            }
                            Slider(
                                value = posicion.toFloat(), onValueChange = { posicion = it.toInt(); reproductor.buscar(it.toInt()) },
                                valueRange = 0f..duracion.coerceAtLeast(1).toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Paleta.OxidoClaro, activeTrackColor = Paleta.Oxido, inactiveTrackColor = Paleta.Linea),
                            )
                            if (marcas.isNotEmpty()) {
                                Text("Giros de página (toca para saltar):", color = Paleta.TextoSuave, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                    marcas.take(12).forEach { m -> Opcion(formatoDuracion(m.tMs), false, { reproductor.buscar(m.tMs.toInt()); posicion = m.tMs.toInt() }) }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Paleta.Linea)
                }
            }
        }
    }
    aEliminar?.let { g ->
        DialogoConfirmar("Eliminar grabación", "Se borrará el audio de «${g.titulo}».", "Eliminar", {
            if (actual?.id == g.id) { reproductor.liberar(); actual = null }
            scope.launch { c.repoGrabaciones.eliminar(g) }; aEliminar = null
        }, { aEliminar = null })
    }
}
