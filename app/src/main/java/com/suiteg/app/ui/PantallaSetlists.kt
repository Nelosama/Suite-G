package com.suiteg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiteg.app.Contenedor
import com.suiteg.app.datos.ItemConDocumento
import com.suiteg.app.datos.SetlistE
import kotlinx.coroutines.launch

private val RellenoPagina = PaddingValues(start = 38.dp, end = 38.dp, top = 37.dp, bottom = 24.dp)

@Composable
fun PantallaSetlists(c: Contenedor, alAbrir: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val listas by c.repoSetlists.todos.collectAsState(emptyList())
    var nueva by remember { mutableStateOf(false) }
    var aEliminar by remember { mutableStateOf<SetlistE?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = RellenoPagina) {
        item {
            EncabezadoPagina("Todo listo para tocar", "Setlists", "Cada pieza, en su lugar. Tú, en la música.") {
                BotonOxido("Nuevo setlist", { nueva = true }, icono = TipoIcono.MAS)
            }
        }
        if (listas.isEmpty()) item {
            Franja("Crea un setlist para agrupar tus partituras en el orden del concierto.", icono = TipoIcono.SETLISTS)
        }
        items2(listas) { fila, base ->
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                fila.forEachIndexed { i, s -> Box(Modifier.weight(1f)) { TarjetaSetlist(c, s, base + i + 1, { alAbrir(s.id) }, { aEliminar = s }) } }
                if (fila.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }

    if (nueva) {
        DialogoTexto("Un nuevo repertorio", "", "Nombre del setlist", alConfirmar = { n -> scope.launch { alAbrir(c.repoSetlists.crear(n)) }; nueva = false }, alCancelar = { nueva = false })
    }
    aEliminar?.let { s ->
        DialogoConfirmar("Eliminar setlist", "Se borrará «${s.nombre}». Las partituras siguen en la biblioteca.", "Eliminar",
            { scope.launch { c.repoSetlists.eliminar(s.id) }; aEliminar = null }, { aEliminar = null })
    }
}

/** Reparte una lista en filas de dos elementos para una cuadrícula perezosa. */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items2(lista: List<T>, contenido: @Composable (List<T>, Int) -> Unit) {
    val filas = lista.chunked(2)
    itemsIndexed(filas) { i, fila -> contenido(fila, i * 2) }
}

@Composable
private fun TarjetaSetlist(c: Contenedor, s: SetlistE, numero: Int, alAbrir: () -> Unit, alEliminar: () -> Unit) {
    val piezas by c.repoSetlists.observarItems(s.id).collectAsState(emptyList())
    Tarjeta(Modifier.fillMaxWidth(), alPulsar = alAbrir) {
        Column(Modifier.fillMaxWidth().padding(30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icono(TipoIcono.SETLISTS, color = Paleta.OxidoClaro, tamano = 33.dp)
            Text("PROGRAMA ${numero.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall, color = Paleta.TextoSuave, fontSize = 9.sp)
            Text(s.nombre, fontFamily = Serifa, fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${piezas.size} ${if (piezas.size == 1) "pieza" else "piezas"}", fontSize = 11.sp, color = Paleta.TextoSuave)
            HorizontalDivider(color = Paleta.Linea)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ver repertorio", fontSize = 12.sp)
                    Icono(TipoIcono.SIGUIENTE, tamano = 14.dp)
                }
                BotonTexto("Eliminar", alEliminar)
            }
        }
    }
}

@Composable
fun PantallaDetalleSetlist(c: Contenedor, id: Long, alAtras: () -> Unit, alTocar: (Long, Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val setlist by c.repoSetlists.observarSetlist(id).collectAsState(null)
    val items by c.repoSetlists.observarItems(id).collectAsState(emptyList())
    val ajustes by c.ajustes.collectAsState()
    var anadir by remember { mutableStateOf(false) }
    var renombrar by remember { mutableStateOf(false) }
    var notasDe by remember { mutableStateOf<ItemConDocumento?>(null) }

    val s = setlist
    if (s == null) {
        Box(Modifier.fillMaxSize().padding(38.dp)) { BotonTexto("Todos los setlists", alAtras, icono = TipoIcono.ATRAS) }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = RellenoPagina) {
        item {
            BotonTexto("Todos los setlists", alAtras, icono = TipoIcono.ATRAS, modifier = Modifier.padding(bottom = 14.dp))
            EncabezadoPagina("Programa", s.nombre, "${items.size} ${if (items.size == 1) "pieza" else "piezas"}") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonContorno("Renombrar", { renombrar = true })
                    BotonOxido("Tocar setlist", { alTocar(id, 0) }, habilitado = items.isNotEmpty(), icono = TipoIcono.PLAY)
                }
            }
            if (ajustes.avanceTempo) {
                FilaAjuste("Avance por tempo en todo el setlist", "Se suma al ajuste de cada canción. Desactivado por defecto.", s.tempoActivo) {
                    scope.launch { c.repoSetlists.fijarTempo(s, it, s.encadenarTempo) }
                }
                FilaAjuste("Pasar a la siguiente canción al terminar", "Solo con el avance por tempo: al llegar al final abre la siguiente.", s.encadenarTempo) {
                    scope.launch { c.repoSetlists.fijarTempo(s, s.tempoActivo, it) }
                }
            }
            Row(Modifier.padding(vertical = 16.dp)) { BotonContorno("Añadir partituras", { anadir = true }, icono = TipoIcono.MAS) }
        }
        itemsIndexed(items, key = { _, it -> it.idItem }) { i, it ->
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable { alTocar(id, i) }.padding(horizontal = 8.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("0${i + 1}".takeLast(2), fontFamily = Serifa, fontSize = 24.sp, color = Color(0xFF818873))
                    Column(Modifier.weight(1f)) {
                        Text(it.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (it.estado != "OK") Text(if (it.estado == "DANADO") "Dañado" else "Archivo no encontrado", color = Paleta.Alerta, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        if (ajustes.notas && it.notas.isNotBlank()) Text(it.notas, fontSize = 11.sp, color = Paleta.OxidoClaro, modifier = Modifier.padding(top = 6.dp))
                    }
                    BotonIcono(TipoIcono.FLECHA_ABAJO, "Bajar", { scope.launch { c.repoSetlists.mover(id, it.idItem, +1) } }, habilitado = i < items.lastIndex)
                    BotonIcono(TipoIcono.FLECHA_ABAJO, "Subir", { scope.launch { c.repoSetlists.mover(id, it.idItem, -1) } }, habilitado = i > 0, modifier = Modifier.rotate(180f))
                    if (ajustes.notas) BotonTexto("Notas", { notasDe = it })
                    BotonTexto("Quitar", { scope.launch { c.repoSetlists.quitar(it.idItem) } })
                    Text("Desde aquí", fontSize = 11.sp, color = Paleta.OxidoClaro)
                    Icono(TipoIcono.PLAY, color = Paleta.OxidoClaro, tamano = 16.dp)
                }
                HorizontalDivider(color = Paleta.Linea)
            }
        }
    }

    if (anadir) {
        val docs by c.repoDocumentos.todos.collectAsState(emptyList())
        val elegidos = remember { mutableStateListOf<String>() }
        DialogoContenido("Añadir al setlist", { anadir = false }) {
            if (docs.isEmpty()) Text("La biblioteca está vacía.")
            Column(Modifier.padding(vertical = 4.dp)) {
                docs.forEach { d ->
                    val marcado = d.hash in elegidos
                    Row(Modifier.fillMaxWidth().clickable { if (marcado) elegidos.remove(d.hash) else elegidos.add(d.hash) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 12.dp)) { Icono(if (marcado) TipoIcono.CHECK else TipoIcono.ARCHIVO, color = if (marcado) Paleta.Oxido else Paleta.TextoSuave, tamano = 18.dp) }
                        Text(d.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            BotonOxido("Añadir ${elegidos.size} partitura(s)", { scope.launch { c.repoSetlists.agregar(id, elegidos.toList()) }; anadir = false }, habilitado = elegidos.isNotEmpty())
        }
    }
    if (renombrar) {
        DialogoTexto("Renombrar setlist", s.nombre, "Nombre", alConfirmar = { n -> scope.launch { c.repoSetlists.renombrar(s, n) }; renombrar = false }, alCancelar = { renombrar = false })
    }
    notasDe?.let { it ->
        DialogoTexto("Notas de «${it.titulo}»", it.notas, "Tempo, cambios de arco, dinámicas…", lineas = 4,
            alConfirmar = { t -> scope.launch { c.repoDocumentos.guardarNotas(it.hash, t) }; notasDe = null }, alCancelar = { notasDe = null })
    }
}
