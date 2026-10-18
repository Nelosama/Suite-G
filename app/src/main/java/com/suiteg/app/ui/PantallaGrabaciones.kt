package com.suiteg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun PantallaGrabaciones(c: Contenedor) {
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 38.dp, end = 38.dp, top = 37.dp, bottom = 24.dp)) {
        item {
            EncabezadoPagina("Todo listo para tocar", "Grabaciones", "Escucha tu progreso, ensayo a ensayo.")
            error?.let { Franja(it, Modifier.padding(bottom = 16.dp), color = Paleta.Alerta) }
            if (estado.activa) Franja("Grabando ahora mismo…", Modifier.padding(bottom = 16.dp), icono = TipoIcono.GRABACIONES, color = Paleta.Alerta)
            if (grabaciones.isEmpty()) Franja("Aún no hay grabaciones. Actívalas en Ajustes y usa «Grabar» dentro del lector.", icono = TipoIcono.AURICULARES)
        }
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
                    }.padding(vertical = 25.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        Modifier.size(43.dp).clip(Formas.Redonda).background(Color(0xFF352C22)).border(1.dp, Color(0xFF534432), Formas.Redonda)
                            .clickable {
                                if (sel) { reproductor.alternar(); sonando = reproductor.reproduciendo }
                                else {
                                    reproductor.alError = { error = it }
                                    reproductor.alTerminar = { sonando = false }
                                    if (reproductor.cargar(g.ruta)) {
                                        actual = g; error = null; duracion = reproductor.duracionMs.takeIf { it > 0 } ?: g.duracionMs.toInt()
                                        scope.launch { marcas = c.repoGrabaciones.marcas(g.id) }
                                        reproductor.alternar(); sonando = reproductor.reproduciendo
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icono(if (sel && sonando) TipoIcono.PAUSA else TipoIcono.PLAY, color = Paleta.OxidoClaro, tamano = 18.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(g.titulo, style = MaterialTheme.typography.titleMedium)
                        Text(
                            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(g.inicioEpochMs)) + " · " + formatoDuracion(g.duracionMs) +
                                when (g.estado) { "RECUPERADA" -> " · recuperada"; "ERROR" -> " · con errores"; "EN_CURSO" -> " · en curso"; else -> "" },
                            fontSize = 11.sp, color = Paleta.TextoSuave, modifier = Modifier.padding(top = 8.dp),
                        )
                        if (sel) {
                            Text("Posición: ${formatoDuracion(posicion.toLong())} / ${formatoDuracion(duracion.toLong())}", fontSize = 11.sp, color = Paleta.TextoSuave, modifier = Modifier.padding(top = 20.dp))
                            Slider(
                                value = posicion.toFloat(), onValueChange = { posicion = it.toInt(); reproductor.buscar(it.toInt()) },
                                valueRange = 0f..duracion.coerceAtLeast(1).toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Paleta.OxidoClaro, activeTrackColor = Paleta.Oxido, inactiveTrackColor = Paleta.Linea),
                            )
                            if (marcas.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                marcas.forEachIndexed { n, m ->
                                    Text(
                                        "${formatoDuracion(m.tMs)} · Giro ${n + 1}", fontSize = 10.sp,
                                        modifier = Modifier.background(Color(0xFF303329), Formas.Pequena).clickable { reproductor.buscar(m.tMs.toInt()); posicion = m.tMs.toInt() }.padding(6.dp),
                                    )
                                }
                            }
                        }
                    }
                    BotonTexto("Eliminar", { aEliminar = g })
                    Icono(TipoIcono.GRABACIONES, color = Paleta.TextoSuave, tamano = 18.dp)
                }
                HorizontalDivider(color = Paleta.Linea)
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
