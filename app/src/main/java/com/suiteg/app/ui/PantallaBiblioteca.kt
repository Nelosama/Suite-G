package com.suiteg.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suiteg.app.Contenedor
import com.suiteg.app.datos.DocumentoE
import com.suiteg.app.datos.ResultadoImportacion
import com.suiteg.app.datos.SesionGuardada
import kotlinx.coroutines.launch

@Composable
fun PantallaBiblioteca(
    c: Contenedor,
    ultimaSesion: SesionGuardada?,
    alAbrir: (String) -> Unit,
    alContinuar: (SesionGuardada) -> Unit,
    alSetlists: () -> Unit,
    alGrabaciones: () -> Unit,
    alAjustes: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val docs by c.repoDocumentos.todos.collectAsState(emptyList())
    val ajustes by c.ajustes.collectAsState()
    var filtro by remember { mutableStateOf("") }
    var avisos by remember { mutableStateOf<List<String>>(emptyList()) }
    var importando by remember { mutableStateOf(false) }
    var aEliminar by remember { mutableStateOf<DocumentoE?>(null) }
    var aEditarNotas by remember { mutableStateOf<DocumentoE?>(null) }

    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importando = true
            val nuevos = mutableListOf<String>()
            for (u in uris) {
                nuevos += when (val r = c.importador.importar(u)) {
                    is ResultadoImportacion.Importado -> "✓ ${r.documento.titulo} (${r.documento.numPaginas} págs.)"
                    is ResultadoImportacion.YaExistia -> "= ${r.documento.titulo} ya estaba en la biblioteca"
                    is ResultadoImportacion.Fallo -> "✗ ${r.nombre}: ${r.motivo}"
                }
            }
            avisos = nuevos
            importando = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Cabecera("Biblioteca") {
            if (ajustes.setlists) BotonContorno("Setlists", alSetlists)
            Spacer8()
            if (ajustes.grabacion) { BotonContorno("Grabaciones", alGrabaciones); Spacer8() }
            BotonContorno("Ajustes", alAjustes)
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonOxido(if (importando) "Importando…" else "Importar PDF", { selector.launch(arrayOf("application/pdf")) }, habilitado = !importando)
            OutlinedTextField(
                value = filtro, onValueChange = { filtro = it }, label = { Text("Buscar") }, singleLine = true,
                shape = RectangleShape, modifier = Modifier.weight(1f),
            )
        }

        if (ultimaSesion != null && docs.any { it.hash == ultimaSesion.hash }) {
            val t = docs.first { it.hash == ultimaSesion.hash }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(Paleta.CarbonMedio).border(1.dp, Paleta.Oxido)
                    .clickable { alContinuar(ultimaSesion) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Continuar donde lo dejaste: ${t.titulo}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("▶", color = Paleta.OxidoClaro)
            }
        }

        if (avisos.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(12.dp).background(Paleta.CarbonClaro).border(1.dp, Paleta.Linea).padding(10.dp)) {
                avisos.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("✗")) Paleta.Alerta else Paleta.Texto) }
                Text("Cerrar", Modifier.clickable { avisos = emptyList() }.padding(top = 6.dp), color = Paleta.OxidoClaro, style = MaterialTheme.typography.labelLarge)
            }
        }

        val visibles = docs.filter { filtro.isBlank() || it.titulo.contains(filtro, ignoreCase = true) }
        if (docs.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("Todavía no hay partituras", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Pulsa «Importar PDF» y elige tus partituras. Se copian a la app, así que siguen disponibles aunque muevas o borres los originales.",
                    color = Paleta.TextoSuave, modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visibles, key = { it.hash }) { d ->
                    FilaDocumento(d, ajustes.notas, { alAbrir(d.hash) }, { aEditarNotas = d }, { aEliminar = d })
                    HorizontalDivider(color = Paleta.Linea)
                }
            }
        }
    }

    aEliminar?.let { d ->
        DialogoConfirmar(
            "Eliminar partitura", "«${d.titulo}» se quitará de la biblioteca y de los setlists, junto con sus anotaciones, giros y marcadores.",
            "Eliminar", { scope.launch { c.repoDocumentos.eliminar(d.hash) }; aEliminar = null }, { aEliminar = null },
        )
    }
    aEditarNotas?.let { d ->
        DialogoTexto("Notas de «${d.titulo}»", d.notas, "Tempo, cambios de arco, dinámicas…", lineas = 4,
            alConfirmar = { t -> scope.launch { c.repoDocumentos.guardarNotas(d.hash, t) }; aEditarNotas = null },
            alCancelar = { aEditarNotas = null })
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))

@Composable
private fun FilaDocumento(d: DocumentoE, mostrarNotas: Boolean, alAbrir: () -> Unit, alNotas: () -> Unit, alEliminar: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = alAbrir).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val estado = when (d.estado) {
                "DANADO" -> "  ·  DAÑADO"
                "FALTANTE" -> "  ·  ARCHIVO NO ENCONTRADO"
                else -> ""
            }
            Text("${d.numPaginas} páginas$estado", style = MaterialTheme.typography.bodySmall, color = if (estado.isEmpty()) Paleta.TextoSuave else Paleta.Alerta)
            if (mostrarNotas && d.notas.isNotBlank()) {
                Text(d.notas, style = MaterialTheme.typography.bodySmall, color = Paleta.OxidoClaro, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (mostrarNotas) BotonContorno("Notas", alNotas)
        Spacer8()
        BotonContorno("Eliminar", alEliminar)
    }
}
