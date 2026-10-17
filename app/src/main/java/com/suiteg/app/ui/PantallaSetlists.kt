package com.suiteg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suiteg.app.Contenedor
import com.suiteg.app.datos.ItemConDocumento
import com.suiteg.app.datos.SetlistE
import kotlinx.coroutines.launch

@Composable
fun PantallaSetlists(c: Contenedor, alAtras: () -> Unit, alAbrir: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val listas by c.repoSetlists.todos.collectAsState(emptyList())
    var nueva by remember { mutableStateOf(false) }
    var aEliminar by remember { mutableStateOf<SetlistE?>(null) }

    Column(Modifier.fillMaxSize()) {
        Cabecera("Setlists", alAtras) { BotonOxido("Nuevo setlist", { nueva = true }) }
        if (listas.isEmpty()) {
            Text("Crea un setlist para agrupar tus partituras en el orden del concierto.", Modifier.padding(24.dp), color = Paleta.TextoSuave)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(listas, key = { _, s -> s.id }) { _, s ->
                Row(Modifier.fillMaxWidth().clickable { alAbrir(s.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.nombre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    BotonContorno("Eliminar", { aEliminar = s })
                }
                HorizontalDivider(color = Paleta.Linea)
            }
        }
    }
    if (nueva) {
        DialogoTexto("Nuevo setlist", "", "Nombre", alConfirmar = { n -> scope.launch { alAbrir(c.repoSetlists.crear(n)) }; nueva = false }, alCancelar = { nueva = false })
    }
    aEliminar?.let { s ->
        DialogoConfirmar("Eliminar setlist", "Se borrará «${s.nombre}». Las partituras siguen en la biblioteca.", "Eliminar",
            { scope.launch { c.repoSetlists.eliminar(s.id) }; aEliminar = null }, { aEliminar = null })
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

    val s = setlist ?: run {
        Column(Modifier.fillMaxSize()) { Cabecera("Setlist", alAtras) }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Cabecera(s.nombre, alAtras) {
            BotonContorno("Renombrar", { renombrar = true })
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            BotonOxido("Tocar", { alTocar(id, 0) }, habilitado = items.isNotEmpty())
        }
        if (ajustes.avanceTempo) {
            FilaAjuste("Avance por tempo en todo el setlist", "Se suma al ajuste de cada canción. Desactivado por defecto.", s.tempoActivo) {
                scope.launch { c.repoSetlists.fijarTempo(s, it, s.encadenarTempo) }
            }
            FilaAjuste("Pasar a la siguiente canción al terminar", "Solo con el avance por tempo: al llegar al final abre la siguiente.", s.encadenarTempo) {
                scope.launch { c.repoSetlists.fijarTempo(s, s.tempoActivo, it) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp)) { BotonContorno("Añadir partituras", { anadir = true }) }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(items, key = { _, it -> it.idItem }) { i, it ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", Modifier.padding(end = 10.dp), color = Paleta.OxidoClaro, style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f)) {
                            Text(it.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (it.estado != "OK") Text(if (it.estado == "DANADO") "DAÑADO" else "ARCHIVO NO ENCONTRADO", color = Paleta.Alerta, style = MaterialTheme.typography.bodySmall)
                        }
                        BotonContorno("▲", { scope.launch { c.repoSetlists.mover(id, it.idItem, -1) } }, habilitado = i > 0)
                        BotonContorno("▼", { scope.launch { c.repoSetlists.mover(id, it.idItem, +1) } }, habilitado = i < items.lastIndex)
                        if (ajustes.notas) { androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp)); BotonContorno("Notas", { notasDe = it }) }
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp))
                        BotonContorno("▶ Desde aquí", { alTocar(id, i) })
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp))
                        BotonContorno("Quitar", { scope.launch { c.repoSetlists.quitar(it.idItem) } })
                    }
                    if (ajustes.notas && it.notas.isNotBlank()) {
                        Text(it.notas, Modifier.padding(start = 28.dp, top = 4.dp).fillMaxWidth().background(Paleta.CarbonClaro).border(1.dp, Paleta.Linea).padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium, color = Paleta.OxidoClaro)
                    }
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
                    Row(Modifier.fillMaxWidth().clickable { if (marcado) elegidos.remove(d.hash) else elegidos.add(d.hash) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (marcado) "■" else "□", color = Paleta.OxidoClaro, modifier = Modifier.padding(end = 10.dp))
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
