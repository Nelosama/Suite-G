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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.suiteg.app.Contenedor
import com.suiteg.app.datos.ResultadoImportacion
import com.suiteg.app.datos.SesionGuardada
import com.suiteg.app.lector.SesionLectura
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface Pantalla {
    data object Biblioteca : Pantalla
    data object Setlists : Pantalla
    data class DetalleSetlist(val id: Long) : Pantalla
    data object Ajustes : Pantalla
    data object Pedal : Pantalla
    data object Grabaciones : Pantalla
    data class Lector(val hash: String, val setlistId: Long?, val indice: Int, val reanudar: SesionGuardada? = null) : Pantalla
}

/** Raíz de la interfaz: una pila de pantallas sencilla (sin librería de navegación). */
@Composable
fun AppRaiz(c: Contenedor, ventana: ControlVentana, pdfEntrante: StateFlow<Uri?>, arranqueEnFrio: Boolean) {
    val pila = remember { mutableStateListOf<Pantalla>(Pantalla.Biblioteca) }
    val actual = pila.last()
    val ultimaSesion by c.ajustesRepo.ultimaSesion.collectAsState(null)

    fun ir(p: Pantalla) { pila.add(p) }
    fun atras() { if (pila.size > 1) pila.removeAt(pila.lastIndex) }

    BackHandler(enabled = pila.size > 1 && actual !is Pantalla.Lector) { atras() }

    // Si la app murió con el lector abierto (o con el modo concierto activo), vuelve exactamente ahí.
    LaunchedEffect(Unit) {
        if (!arranqueEnFrio) return@LaunchedEffect
        c.ajustesRepo.sesionInterrumpida.first()?.let { s ->
            if (c.repoDocumentos.obtener(s.hash) != null) ir(Pantalla.Lector(s.hash, s.setlistId.takeIf { it > 0 }, s.indiceSetlist, s))
        }
    }

    // PDF recibido desde el gestor de archivos: se importa y se abre.
    val entrante by pdfEntrante.collectAsState()
    LaunchedEffect(entrante) {
        val uri = entrante ?: return@LaunchedEffect
        when (val r = c.importador.importar(uri)) {
            is ResultadoImportacion.Importado -> ir(Pantalla.Lector(r.documento.hash, null, 0))
            is ResultadoImportacion.YaExistia -> ir(Pantalla.Lector(r.documento.hash, null, 0))
            is ResultadoImportacion.Fallo -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Paleta.Carbon)) {
        when (actual) {
            Pantalla.Biblioteca -> PantallaBiblioteca(
                c, ultimaSesion,
                alAbrir = { ir(Pantalla.Lector(it, null, 0)) },
                alContinuar = { ir(Pantalla.Lector(it.hash, it.setlistId.takeIf { id -> id > 0 }, it.indiceSetlist, it)) },
                alSetlists = { ir(Pantalla.Setlists) },
                alGrabaciones = { ir(Pantalla.Grabaciones) },
                alAjustes = { ir(Pantalla.Ajustes) },
            )
            Pantalla.Setlists -> PantallaSetlists(c, ::atras) { ir(Pantalla.DetalleSetlist(it)) }
            is Pantalla.DetalleSetlist -> PantallaDetalleSetlist(c, actual.id, ::atras) { id, indice ->
                scopeAbrirSetlist(c, id, indice) { hash -> ir(Pantalla.Lector(hash, id, indice)) }
            }
            Pantalla.Ajustes -> PantallaAjustes(c, ::atras) { ir(Pantalla.Pedal) }
            Pantalla.Pedal -> PantallaPedal(c, ::atras)
            Pantalla.Grabaciones -> PantallaGrabaciones(c, ::atras)
            is Pantalla.Lector -> key(actual) {
                val sesion = remember { SesionLectura(c) }
                DisposableEffect(sesion) {
                    c.sesion = sesion
                    onDispose {
                        sesion.cerrar()
                        if (c.sesion === sesion) c.sesion = null
                    }
                }
                LaunchedEffect(sesion) {
                    if (actual.reanudar != null) sesion.reanudar(actual.reanudar) else sesion.abrir(actual.hash, actual.setlistId, actual.indice)
                }
                PantallaLector(c, sesion, ventana, ::atras)
            }
        }
    }
}

/** Busca el hash de la canción [indice] del setlist para abrirla. */
private fun scopeAbrirSetlist(c: Contenedor, id: Long, indice: Int, abrir: (String) -> Unit) {
    c.scope.launch {
        val s = c.repoSetlists.obtener(id) ?: return@launch
        s.items.getOrNull(indice)?.let { abrir(it.hash) }
    }
}
