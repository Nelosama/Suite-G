package com.suiteg.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.suiteg.app.Contenedor
import com.suiteg.app.datos.ResultadoImportacion
import com.suiteg.app.datos.SesionGuardada
import com.suiteg.app.lector.SesionLectura
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Lo que hay que abrir en el lector. */
private data class AperturaLector(val hash: String, val setlistId: Long?, val indice: Int, val reanudar: SesionGuardada? = null)

/**
 * Raíz de la interfaz. Las cinco secciones principales viven dentro del armazón con barra lateral; el lector ocupa
 * toda la pantalla por encima de ellas, y al salir se vuelve a la sección en la que se estaba.
 */
@Composable
fun AppRaiz(c: Contenedor, ventana: ControlVentana, pdfEntrante: StateFlow<Uri?>, arranqueEnFrio: Boolean) {
    var seccion by remember { mutableStateOf(Seccion.BIBLIOTECA) }
    var setlistAbierto by remember { mutableStateOf<Long?>(null) }
    var lector by remember { mutableStateOf<AperturaLector?>(null) }
    val ultimaSesion by c.ajustesRepo.ultimaSesion.collectAsState(null)
    val ajustes by c.ajustes.collectAsState()

    // Atrás: de un setlist abierto a la lista; de cualquier sección a la biblioteca.
    BackHandler(enabled = lector == null && (setlistAbierto != null || seccion != Seccion.BIBLIOTECA)) {
        if (setlistAbierto != null) setlistAbierto = null else seccion = Seccion.BIBLIOTECA
    }

    // Si se desactiva una función cuya sección está abierta, se vuelve a la biblioteca.
    LaunchedEffect(ajustes.setlists, ajustes.grabacion, seccion) {
        if ((seccion == Seccion.SETLISTS && !ajustes.setlists) || (seccion == Seccion.GRABACIONES && !ajustes.grabacion)) seccion = Seccion.BIBLIOTECA
    }

    // Si la app murió con el lector abierto (o con el modo concierto activo), vuelve exactamente ahí.
    LaunchedEffect(Unit) {
        if (!arranqueEnFrio) return@LaunchedEffect
        c.ajustesRepo.sesionInterrumpida.first()?.let { s ->
            if (c.repoDocumentos.obtener(s.hash) != null) lector = AperturaLector(s.hash, s.setlistId.takeIf { it > 0 }, s.indiceSetlist, s)
        }
    }

    // PDF recibido desde el gestor de archivos: se importa y se abre.
    val entrante by pdfEntrante.collectAsState()
    LaunchedEffect(entrante) {
        val uri = entrante ?: return@LaunchedEffect
        when (val r = c.importador.importar(uri)) {
            is ResultadoImportacion.Importado -> lector = AperturaLector(r.documento.hash, null, 0)
            is ResultadoImportacion.YaExistia -> lector = AperturaLector(r.documento.hash, null, 0)
            is ResultadoImportacion.Fallo -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Paleta.Carbon)) {
        val abierto = lector
        if (abierto != null) {
            key(abierto) {
                val sesion = remember { SesionLectura(c) }
                DisposableEffect(sesion) {
                    c.sesion = sesion
                    onDispose {
                        sesion.cerrar()
                        if (c.sesion === sesion) c.sesion = null
                    }
                }
                LaunchedEffect(sesion) {
                    if (abierto.reanudar != null) sesion.reanudar(abierto.reanudar) else sesion.abrir(abierto.hash, abierto.setlistId, abierto.indice)
                }
                PantallaLector(c, sesion, ventana) { lector = null }
            }
        } else {
            ShellApp(c, seccion, { seccion = it; setlistAbierto = null }) {
                when (seccion) {
                    Seccion.BIBLIOTECA -> PantallaBiblioteca(
                        c, ultimaSesion,
                        alAbrir = { lector = AperturaLector(it, null, 0) },
                        alContinuar = { lector = AperturaLector(it.hash, it.setlistId.takeIf { id -> id > 0 }, it.indiceSetlist, it) },
                    )
                    Seccion.SETLISTS -> {
                        val id = setlistAbierto
                        if (id == null) PantallaSetlists(c) { setlistAbierto = it }
                        else PantallaDetalleSetlist(c, id, { setlistAbierto = null }) { setlist, indice ->
                            abrirDeSetlist(c, setlist, indice) { hash -> lector = AperturaLector(hash, setlist, indice) }
                        }
                    }
                    Seccion.GRABACIONES -> PantallaGrabaciones(c)
                    Seccion.PEDAL -> PantallaPedal(c)
                    Seccion.AJUSTES -> PantallaAjustes(c) { seccion = Seccion.PEDAL }
                }
            }
        }
    }
}

/** Busca el hash de la canción [indice] del setlist para abrirla. */
private fun abrirDeSetlist(c: Contenedor, id: Long, indice: Int, abrir: (String) -> Unit) {
    c.scope.launch {
        val s = c.repoSetlists.obtener(id) ?: return@launch
        s.items.getOrNull(indice)?.let { abrir(it.hash) }
    }
}
