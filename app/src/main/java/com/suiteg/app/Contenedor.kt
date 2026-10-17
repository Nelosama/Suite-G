package com.suiteg.app

import android.app.Application
import android.os.SystemClock
import com.suiteg.app.audio.ClienteGrabacion
import com.suiteg.app.audio.MetronomoAudio
import com.suiteg.app.datos.AjustesRepositorio
import com.suiteg.app.datos.BaseDatos
import com.suiteg.app.datos.ClienteValidador
import com.suiteg.app.datos.ImportadorPdf
import com.suiteg.app.datos.RepoDocumentos
import com.suiteg.app.datos.RepoGrabaciones
import com.suiteg.app.datos.RepoPartitura
import com.suiteg.app.datos.RepoSetlists
import com.suiteg.app.datos.RepoTrazos
import com.suiteg.app.lector.SesionLectura
import com.suiteg.app.pedal.MonitorPedal
import com.suiteg.modelo.AjustesApp
import com.suiteg.modelo.MedidorLatencia
import com.suiteg.modelo.Reloj
import com.suiteg.navegacion.entrada.Accion
import com.suiteg.navegacion.entrada.ConfigPedal
import com.suiteg.navegacion.entrada.MapaTeclas
import com.suiteg.navegacion.entrada.ProcesadorPedal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class AccionObservada(val n: Int, val accion: Accion)

/**
 * Inyección de dependencias manual: un solo objeto, creado una vez en el proceso principal, que construye
 * y comparte todo. Sin generación de código ni reflexión: menos cosas que puedan fallar al arrancar.
 */
class Contenedor(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Reloj monótono (sigue contando con la pantalla apagada). */
    val reloj = Reloj { SystemClock.elapsedRealtime() }

    private val bd = BaseDatos.crear(app)
    val repoDocumentos = RepoDocumentos(bd.documentos())
    val repoPartitura = RepoPartitura(bd.partitura())
    val repoSetlists = RepoSetlists(bd.setlists())
    val repoTrazos = RepoTrazos(bd.trazos())
    val repoGrabaciones = RepoGrabaciones(bd.grabaciones())

    val ajustesRepo = AjustesRepositorio(app)
    val ajustes: StateFlow<AjustesApp>
    val mapaTeclas: StateFlow<MapaTeclas>

    val validador = ClienteValidador(app)
    val importador = ImportadorPdf(app, bd.documentos(), validador)

    val monitorPedal = MonitorPedal(app)
    val medidorLatencia = MedidorLatencia()
    val metronomo = MetronomoAudio()
    val grabacion = ClienteGrabacion(app, repoGrabaciones, scope)

    /** Sesión del lector abierta ahora mismo (null en la biblioteca). El pedal le entrega sus acciones. */
    @Volatile
    var sesion: SesionLectura? = null

    /** Última acción reconocida del pedal (para la pantalla de pruebas). */
    private val _accionObservada = MutableStateFlow<AccionObservada?>(null)
    val accionObservada: StateFlow<AccionObservada?> = _accionObservada.asStateFlow()

    val procesadorPedal = ProcesadorPedal(
        reloj = reloj,
        alAccion = { accion, evento ->
            _accionObservada.value = AccionObservada((_accionObservada.value?.n ?: 0) + 1, accion)
            sesion?.ejecutar(accion, evento)
        },
    )

    init {
        // Lectura inicial bloqueante (unos milisegundos): así la primera pulsación del pedal ya se reconoce.
        val ajustesIniciales = runBlocking(Dispatchers.IO) { ajustesRepo.ajustes.first() }
        val mapaInicial = runBlocking(Dispatchers.IO) { ajustesRepo.mapaTeclas.first() }
        ajustes = ajustesRepo.ajustes.stateIn(scope, SharingStarted.Eagerly, ajustesIniciales)
        mapaTeclas = ajustesRepo.mapaTeclas.stateIn(scope, SharingStarted.Eagerly, mapaInicial)
        aplicarMapa(mapaInicial)
        aplicarAjustesPedal(ajustesIniciales)

        scope.launch { mapaTeclas.collect(::aplicarMapa) }
        scope.launch { ajustes.collect(::aplicarAjustesPedal) }
        scope.launch { repoGrabaciones.recuperarHuerfanas() }
    }

    private fun aplicarMapa(m: MapaTeclas) {
        procesadorPedal.mapa = m
        monitorPedal.establecerDescriptores(m.descriptores())
    }

    private fun aplicarAjustesPedal(a: AjustesApp) {
        procesadorPedal.configurar(ConfigPedal(a.ventanaDuplicadoMs.toLong(), a.ventanaGrupoMs.toLong()))
    }

    /** Guarda las asignaciones del pedal (también las actualiza al instante, sin esperar a DataStore). */
    fun guardarMapa(m: MapaTeclas) {
        aplicarMapa(m)
        scope.launch { ajustesRepo.guardarMapa(m) }
    }

    fun guardarAjustes(a: AjustesApp) {
        scope.launch { ajustesRepo.guardar(a) }
    }
}
