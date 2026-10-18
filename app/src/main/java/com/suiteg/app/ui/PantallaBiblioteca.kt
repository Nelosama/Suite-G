package com.suiteg.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiteg.app.Contenedor
import com.suiteg.app.datos.DocumentoE
import com.suiteg.app.datos.ResultadoImportacion
import com.suiteg.app.datos.SesionGuardada
import kotlinx.coroutines.launch

private enum class Orden { RECIENTES, TITULO }

@Composable
fun PantallaBiblioteca(
    c: Contenedor,
    ultimaSesion: SesionGuardada?,
    alAbrir: (String) -> Unit,
    alContinuar: (SesionGuardada) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val docs by c.repoDocumentos.todos.collectAsState(emptyList())
    val ajustes by c.ajustes.collectAsState()
    var filtro by remember { mutableStateOf("") }
    var orden by remember { mutableStateOf(Orden.RECIENTES) }
    var cuadricula by remember { mutableStateOf(false) }
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

    val visibles = docs
        .filter { filtro.isBlank() || it.titulo.contains(filtro, ignoreCase = true) }
        .sortedWith(if (orden == Orden.TITULO) compareBy { it.titulo.lowercase() } else compareByDescending { it.importadoEn })
    val retomar = ultimaSesion?.let { s -> docs.firstOrNull { it.hash == s.hash } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 38.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 37.dp, bottom = 16.dp)) {
        item {
            EncabezadoPagina("Todo listo para tocar", "Biblioteca", "Menos preparar. Más hacer música.") {
                BotonOxido(if (importando) "Importando…" else "Importar partitura", { selector.launch(arrayOf("application/pdf")) }, habilitado = !importando, icono = TipoIcono.MAS)
            }
        }

        if (avisos.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp).background(Paleta.CarbonClaro, Formas.Media).border(1.dp, Paleta.Linea, Formas.Media).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                avisos.forEach { Text(it, fontSize = 12.sp, color = if (it.startsWith("✗")) Paleta.Alerta else Paleta.Texto) }
                Text("Cerrar", Modifier.clickable { avisos = emptyList() }.padding(top = 6.dp), color = Paleta.OxidoClaro, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (ultimaSesion != null && retomar != null) item { TarjetaContinuar(retomar, ultimaSesion) { alContinuar(ultimaSesion) } }

        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Tus partituras", style = MaterialTheme.typography.titleMedium)
                    Text("${docs.size}", fontSize = 9.sp, color = Paleta.TextoSuave, modifier = Modifier.border(1.dp, Paleta.Linea, Formas.Pequena).padding(horizontal = 6.dp, vertical = 3.dp))
                }
                Text("Tu repertorio, siempre a mano", fontSize = 11.sp, color = Paleta.TextoSuave)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CampoBusqueda(filtro, { filtro = it }, Modifier.weight(1f))
                Row(
                    Modifier.clickable { orden = if (orden == Orden.RECIENTES) Orden.TITULO else Orden.RECIENTES }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(if (orden == Orden.RECIENTES) "Más recientes" else "Título A–Z", fontSize = 11.sp, color = Color(0xFFB4B9AA))
                    Icono(TipoIcono.FLECHA_ABAJO, color = Color(0xFFB4B9AA), tamano = 12.dp)
                }
                Row(Modifier.border(1.dp, Color(0xFF35382E), Formas.Pequena).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    AlternadorVista(TipoIcono.LISTA, !cuadricula) { cuadricula = false }
                    AlternadorVista(TipoIcono.CUADRICULA, cuadricula) { cuadricula = true }
                }
            }
        }

        if (docs.isEmpty()) item {
            EstadoVacio(TipoIcono.CARPETA, "Todavía no hay partituras",
                "Pulsa «Importar partitura» y elige tus PDF. Se copian a la app, así que siguen disponibles aunque muevas o borres los originales.")
        } else if (visibles.isEmpty()) item {
            EstadoVacio(TipoIcono.BUSCAR, "No encontramos esa partitura", "Prueba con otro título.") {
                BotonContorno("Limpiar búsqueda", { filtro = "" })
            }
        } else if (cuadricula) {
            items(visibles.chunked(3), key = { fila -> fila.first().hash }) { fila ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (d in fila) Box(Modifier.weight(1f)) { TarjetaDocumento(d, ajustes.notas, { alAbrir(d.hash) }, { aEditarNotas = d }, { aEliminar = d }) }
                    repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    Text("PARTITURA", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color(0xFF767D6D), fontSize = 8.sp)
                    Text("PÁGINAS", Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF767D6D), fontSize = 8.sp)
                    Text("IMPORTADA", Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF767D6D), fontSize = 8.sp)
                    Spacer(Modifier.width(if (ajustes.notas) 150.dp else 80.dp))
                }
                HorizontalDivider(color = Paleta.Linea)
            }
            items(visibles, key = { it.hash }) { d ->
                FilaDocumento(d, ajustes.notas, { alAbrir(d.hash) }, { aEditarNotas = d }, { aEliminar = d })
                HorizontalDivider(color = Color(0xFF2C2F27))
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icono(TipoIcono.ESCUDO, color = Color(0xFF72796A), tamano = 12.dp)
                    Text("Hecho para la música. Sin distracciones.", fontSize = 9.sp, color = Color(0xFF72796A))
                }
                Text("Suite-G para Android", fontSize = 9.sp, color = Color(0xFF72796A))
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
private fun TarjetaContinuar(doc: DocumentoE, sesion: SesionGuardada, alContinuar: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 29.dp).heightIn(min = 232.dp).clip(Formas.Grande)
            .background(Color(0xFF252720)).border(1.dp, Color(0xFF3C3F32), Formas.Grande),
    ) {
        PartituraDecorativa(Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).width(250.dp).rotate(-9f))
        Column(Modifier.fillMaxWidth(0.62f).padding(29.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(5.dp).clip(Formas.Redonda).background(Color(0xFFA2AF87)))
                Text("RETOMA DONDE LO DEJASTE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB5BB9D), fontSize = 8.sp)
            }
            Text(doc.titulo, fontFamily = Serifa, fontSize = 29.sp, letterSpacing = (-0.6).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
            Row(Modifier.padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icono(TipoIcono.ARCHIVO, color = Color(0xFF8E9784), tamano = 12.dp)
                    Text("Vista ${sesion.vista + 1} · ${doc.numPaginas} páginas", fontSize = 10.sp, color = Color(0xFF8E9784))
                }
                if (sesion.bloqueado) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icono(TipoIcono.CANDADO, color = Color(0xFF8E9784), tamano = 12.dp)
                    Text("Modo concierto", fontSize = 10.sp, color = Color(0xFF8E9784))
                }
            }
            BotonOxido("Continuar ensayo", alContinuar, icono = TipoIcono.PLAY, iconoFinal = TipoIcono.SIGUIENTE)
        }
    }
}

/** Hoja ilustrativa (pentagramas y notas) para la tarjeta de «continuar». No es contenido musical real. */
@Composable
private fun PartituraDecorativa(modifier: Modifier) {
    Canvas(modifier.height(190.dp)) {
        val tinta = Color(0xFF55564A)
        drawRect(Paleta.Papel, Offset.Zero, Size(size.width, size.height))
        val margen = size.width * 0.1f
        for (fila in 0 until 4) {
            val y0 = size.height * (0.22f + fila * 0.19f)
            for (l in 0 until 5) {
                val y = y0 + l * 4.dp.toPx()
                drawLine(tinta, Offset(margen, y), Offset(size.width - margen, y), strokeWidth = 1f)
            }
            for (n in 0 until 9) {
                val x = margen + (size.width - 2 * margen) * (0.12f + n * 0.1f)
                val y = y0 + ((n * 7 + fila * 3) % 9) * 2.dp.toPx()
                drawCircle(Color(0xFF292A26), 2.4.dp.toPx(), Offset(x, y))
                drawLine(Color(0xFF292A26), Offset(x + 2.2.dp.toPx(), y), Offset(x + 2.2.dp.toPx(), y - 11.dp.toPx()), strokeWidth = 1.2f)
            }
        }
    }
}

@Composable
private fun CampoBusqueda(valor: String, alCambiar: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.height(36.dp).background(Color(0xFF1E201C), Formas.Pequena).border(1.dp, Color(0xFF363830), Formas.Pequena).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icono(TipoIcono.BUSCAR, color = Color(0xFF878D7C), tamano = 14.dp)
        Box(Modifier.weight(1f)) {
            if (valor.isEmpty()) Text("Buscar por título…", fontSize = 11.sp, color = Color(0xFF74796E))
            BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Color(0xFFDFDFD7), fontSize = 11.sp), cursorBrush = SolidColor(Paleta.Oxido), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AlternadorVista(icono: TipoIcono, activo: Boolean, alPulsar: () -> Unit) {
    Box(
        Modifier.size(width = 28.dp, height = 25.dp).clip(Formas.Pequena).background(if (activo) Color(0xFF34382D) else Color.Transparent).clickable(onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) { Icono(icono, color = if (activo) Color(0xFFDFDFD2) else Color(0xFF747C6A), tamano = 14.dp) }
}

@Composable
private fun EstadoVacio(icono: TipoIcono, titulo: String, texto: String, accion: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(45.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icono(icono, color = Paleta.TextoSuave, tamano = 26.dp)
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(texto, fontSize = 12.sp, color = Paleta.TextoSuave, lineHeight = 18.sp)
        accion()
    }
}

private fun detalle(d: DocumentoE): Pair<String, Color> = when (d.estado) {
    "DANADO" -> "Dañado: no se puede abrir" to Paleta.Alerta
    "FALTANTE" -> "Archivo no encontrado" to Paleta.Alerta
    else -> "PDF en este dispositivo" to Color(0xFF7F8773)
}

@Composable
private fun IconoDocumento() {
    Box(Modifier.size(width = 34.dp, height = 40.dp).background(Color(0xFF282C22), Formas.Pequena).border(1.dp, Color(0xFF3C4033), Formas.Pequena), contentAlignment = Alignment.Center) {
        Icono(TipoIcono.ARCHIVO, color = Color(0xFFA7AF94), tamano = 17.dp)
    }
}

@Composable
private fun FilaDocumento(d: DocumentoE, mostrarNotas: Boolean, alAbrir: () -> Unit, alNotas: () -> Unit, alEliminar: () -> Unit) {
    val (sub, colorSub) = detalle(d)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = alAbrir).padding(horizontal = 13.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            IconoDocumento()
            Column {
                Text(d.titulo, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (mostrarNotas && d.notas.isNotBlank()) d.notas else sub, fontSize = 10.sp, color = if (mostrarNotas && d.notas.isNotBlank()) Paleta.OxidoClaro else colorSub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text("${d.numPaginas}", Modifier.width(85.dp), fontSize = 11.sp, color = Color(0xFFA0A78F))
        Text(fechaRelativa(d.importadoEn), Modifier.width(100.dp), fontSize = 11.sp, color = Color(0xFFA0A78F))
        Row {
            if (mostrarNotas) BotonTexto("Notas", alNotas)
            BotonTexto("Eliminar", alEliminar)
        }
    }
}

@Composable
private fun TarjetaDocumento(d: DocumentoE, mostrarNotas: Boolean, alAbrir: () -> Unit, alNotas: () -> Unit, alEliminar: () -> Unit) {
    val (sub, colorSub) = detalle(d)
    Tarjeta(Modifier.fillMaxWidth(), alPulsar = alAbrir) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                IconoDocumento()
                Column {
                    Text(d.titulo, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(sub, fontSize = 10.sp, color = colorSub, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (mostrarNotas && d.notas.isNotBlank()) Text(d.notas, fontSize = 11.sp, color = Paleta.OxidoClaro, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${d.numPaginas} páginas · ${fechaRelativa(d.importadoEn)}", fontSize = 10.sp, color = Color(0xFFA0A78F), modifier = Modifier.weight(1f))
                if (mostrarNotas) BotonTexto("Notas", alNotas)
                BotonTexto("Eliminar", alEliminar)
            }
        }
    }
}
