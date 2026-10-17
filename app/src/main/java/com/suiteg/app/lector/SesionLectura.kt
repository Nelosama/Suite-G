package com.suiteg.app.lector

import android.graphics.Color
import android.os.SystemClock
import com.suiteg.app.datos.DocumentoE
import com.suiteg.app.datos.SesionGuardada
import com.suiteg.app.datos.SetlistConItems
import com.suiteg.app.pdf.CacheBitmaps
import com.suiteg.app.pdf.ClaveRender
import com.suiteg.app.pdf.MotorPdf
import com.suiteg.app.pdf.ParametrosRender
import com.suiteg.app.pdf.PdfRendererMotor
import com.suiteg.app.Contenedor
import com.suiteg.modelo.AjusteVista
import com.suiteg.modelo.AjustesApp
import com.suiteg.modelo.ConfigTempoCancion
import com.suiteg.modelo.EstadoDocumento
import com.suiteg.modelo.Herramienta
import com.suiteg.modelo.Marcador
import com.suiteg.modelo.ModoAvance
import com.suiteg.modelo.Posicion
import com.suiteg.modelo.PuntoGiro
import com.suiteg.modelo.TipoGiro
import com.suiteg.modelo.TramoTempo
import com.suiteg.modelo.TransformacionVista
import com.suiteg.modelo.Trazo
import com.suiteg.navegacion.ComandoNavegacion
import com.suiteg.navegacion.ConfigNavegacion
import com.suiteg.navegacion.ContextoSetlist
import com.suiteg.navegacion.EfectoNavegacion
import com.suiteg.navegacion.EstadoNavegador
import com.suiteg.navegacion.FaseLector
import com.suiteg.navegacion.OrigenComando
import com.suiteg.navegacion.PageNavigator
import com.suiteg.navegacion.SecuenciaDeVistas
import com.suiteg.navegacion.entrada.Accion
import com.suiteg.navegacion.entrada.EventoTecla
import com.suiteg.tempo.CalculadorTempo
import com.suiteg.tempo.ConfigMotorTempo
import com.suiteg.tempo.EstadoTempo
import com.suiteg.tempo.EventoTempo
import com.suiteg.tempo.MotorTempo
import com.suiteg.tempo.ParametrosPlan
import com.suiteg.tempo.PatronMetronomo
import com.suiteg.tempo.PlanTempo
import com.suiteg.tempo.ResultadoPlan
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orquesta todo lo que ocurre mientras se lee una partitura: abre el PDF, mantiene el [PageNavigator], pide
 * las imágenes al motor y a la caché, ejecuta las acciones del pedal y coordina avance por tempo, metrónomo,
 * grabación, anotaciones y modo concierto.
 *
 * Es la única clase que conoce a todas las demás; las piezas que importan (navegador, procesador del pedal,
 * motor de tempo) no se conocen entre sí. Se usa siempre desde el hilo principal.
 */
class SesionLectura(private val c: Contenedor) {
    // Ámbito propio, hijo del de la app: al cerrar el lector se cancelan todos sus trabajos y observadores.
    private val scope = CoroutineScope(c.scope.coroutineContext + SupervisorJob(c.scope.coroutineContext[Job]))
    private val ajustes: AjustesApp get() = c.ajustes.value

    val navegador = PageNavigator(c.reloj, ConfigNavegacion(), ::alEfectoNavegacion)
    private val tempo = MotorTempo(c.reloj, ConfigMotorTempo(), ::alEventoTempo)

    // --- Estado observable ---
    private val _ui = MutableStateFlow(EstadoLectorUi())
    val ui: StateFlow<EstadoLectorUi> = _ui.asStateFlow()

    private val _trazos = MutableStateFlow<List<Trazo>>(emptyList())
    private val _puntos = MutableStateFlow<List<PuntoGiro>>(emptyList())
    val puntos: StateFlow<List<PuntoGiro>> = _puntos.asStateFlow()
    private val _marcadores = MutableStateFlow<List<Marcador>>(emptyList())
    val marcadores: StateFlow<List<Marcador>> = _marcadores.asStateFlow()
    private val _tramos = MutableStateFlow<List<TramoTempo>>(emptyList())
    val tramos: StateFlow<List<TramoTempo>> = _tramos.asStateFlow()
    private val _config = MutableStateFlow<ConfigTempoCancion?>(null)
    val configTempo: StateFlow<ConfigTempoCancion?> = _config.asStateFlow()
    private val _toque = MutableStateFlow<ToqueEdicion?>(null)
    val toqueEdicion: StateFlow<ToqueEdicion?> = _toque.asStateFlow()

    // --- Documento abierto ---
    private var motor: MotorPdf? = null
    private var documento: DocumentoE? = null
    private var secuencia: SecuenciaDeVistas? = null
    private var planTempo: PlanTempo? = null
    private var setlist: SetlistConItems? = null
    private var indiceSetlist = -1
    private var cache = CacheBitmaps(16 * 1024 * 1024)

    // --- Vista ---
    private var contenedor: ContenedorLector? = null
    private var imagen: ImagenVista? = null
    private var anchoVentana = 0
    private var altoVentana = 0
    private var zoom = 1f
    private var origenManual: Pair<Float, Float>? = null
    private var yContinua: Float? = null
    private var ultimaGeneracion = -1L
    private var ultimoAjusteVista: AjusteVista? = null
    private var paginaDeTrazos = -1

    // --- Trabajos ---
    private var trabajoApertura: Job? = null
    private var trabajoRender: Job? = null
    private var trabajoTrazos: Job? = null
    private var trabajoTempo: Job? = null
    private var trabajoMensaje: Job? = null
    private var trabajoPersistir: Job? = null
    private var generacionApertura = 0

    private var ultimoEventoPedalMs: Long? = null
    private val pilaDeshacer = ArrayDeque<Long>()
    private var metronomoPorCuenta = false

    init {
        scope.launch { navegador.estado.collect(::alEstadoNavegador) }
        scope.launch { c.ajustes.collect(::alCambiarAjustes) }
        scope.launch {
            c.grabacion.estado.collect { e -> e.error?.let { mostrarMensaje(it) } }
        }
        c.metronomo.alError = { m -> scope.launch { mostrarMensaje(m); _ui.update { it.copy(metronomoActivo = false) } } }
    }

    // ============================================================ Vista (contenedor de capas)

    /** Conecta la sesión con las capas de dibujo. La imagen se entrega directamente, sin pasar por Compose. */
    fun adjuntar(v: ContenedorLector) {
        contenedor = v
        v.alCambiarTamano = ::establecerVentana
        v.pagina.alGesto = ::alGesto
        v.pagina.alToque = ::alToque
        v.pagina.alDobleToque = { zoom = 1f; origenManual = null; solicitarRender() }
        v.pagina.alPresentada = ::alPresentada
        v.tinta.alTrazoTerminado = ::guardarTrazo
        v.tinta.alBorrar = ::borrarTrazo
        v.bloqueo.alDesbloquear = ::desbloquear
        aplicarAjustesAVista()
        imagen?.let { v.pagina.mostrar(it.bitmap); v.tinta.transformacion = it.transformacion }
        v.tinta.trazos = _trazos.value
        if (v.width > 0 && v.height > 0) establecerVentana(v.width, v.height)
    }

    fun separar() {
        contenedor?.let {
            it.alCambiarTamano = null
            it.pagina.alGesto = null; it.pagina.alToque = null; it.pagina.alDobleToque = null; it.pagina.alPresentada = null
            it.tinta.alTrazoTerminado = null; it.tinta.alBorrar = null
            it.bloqueo.alDesbloquear = null
        }
        contenedor = null
    }

    private fun establecerVentana(w: Int, h: Int) {
        if (w == anchoVentana && h == altoVentana) return
        anchoVentana = w
        altoVentana = h
        val memoria = (c.app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass
        cache = CacheBitmaps(CacheBitmaps.presupuesto(w, h, memoria))
        zoom = 1f; origenManual = null
        solicitarRender()
    }

    /** Ajustes que se aplican directamente a las vistas. */
    private fun aplicarAjustesAVista() {
        val v = contenedor ?: return
        val a = ajustes
        v.pagina.zoomTactil = a.zoomTactil
        v.pagina.establecerFiltro(FiltroColor.para(a.inversion))
        v.tinta.dedoDibuja = a.dedoDibuja
        v.tinta.rechazoPalma = a.rechazoPalma
        v.tinta.mostrarTrazos = a.anotaciones
        v.tinta.modoDibujo = a.anotaciones && _ui.value.modoEdicion == ModoEdicion.DIBUJO && !_ui.value.bloqueado
        v.bloqueo.duracionMs = a.desbloqueoMs.toLong()
        v.bloqueo.visibility = if (_ui.value.bloqueado) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun alCambiarAjustes(a: AjustesApp) {
        navegador.config = ConfigNavegacion(a.cooldownMs.toLong(), a.cruzarAlRetroceder)
        tempo.config = ConfigMotorTempo(avisoMs = a.avisoGiroMs.toLong())
        c.metronomo.actualizar(patronActual(), a.volumenMetronomo)
        aplicarAjustesAVista()
        if (ultimoAjusteVista != null && ultimoAjusteVista != a.ajusteVista) {
            zoom = 1f; origenManual = null
            solicitarRender()
        }
        ultimoAjusteVista = a.ajusteVista
        if (documento != null && secuencia != null) {
            // Activar o desactivar funciones puede cambiar el recorrido del pedal o la disponibilidad del tempo.
            val nueva = construirSecuencia()
            if (nueva.vistas != secuencia?.vistas) {
                secuencia = nueva
                navegador.reemplazarSecuencia(nueva, marcadoresActivos())
                prepararTempo()
            } else if (_ui.value.tempoDisponible != tempoActivoParaCancion()) {
                prepararTempo()
            }
        }
    }

    // ============================================================ Apertura

    /** Abre una partitura suelta o una canción de un setlist. */
    fun abrir(hash: String, setlistId: Long? = null, indice: Int = 0, alFinal: Boolean = false, vistaInicial: Int? = null) {
        trabajoApertura?.cancel()
        val gen = ++generacionApertura
        trabajoApertura = scope.launch {
            detenerTempo()
            _ui.update { it.copy(cargando = true, error = null, mensaje = null) }

            if (setlistId != null && ajustes.setlists) {
                if (setlist?.id != setlistId) setlist = c.repoSetlists.obtener(setlistId)
                indiceSetlist = indice
            } else {
                setlist = null
                indiceSetlist = -1
            }

            val d = c.repoDocumentos.obtener(hash)
            documento = d
            if (d == null) {
                fallo(hash, "", "Esta partitura ya no está en la biblioteca")
                return@launch
            }
            val archivo = File(d.rutaInterna)
            if (!archivo.exists() || archivo.length() == 0L) {
                c.repoDocumentos.marcarEstado(hash, EstadoDocumento.FALTANTE)
                fallo(hash, d.titulo, "No se encuentra el archivo de «${d.titulo}»")
                return@launch
            }
            val nuevo = try {
                PdfRendererMotor.abrir(archivo)
            } catch (e: Exception) {
                c.repoDocumentos.marcarEstado(hash, EstadoDocumento.DANADO)
                fallo(hash, d.titulo, "«${d.titulo}» está dañado y no se puede abrir")
                return@launch
            }
            if (gen != generacionApertura) {
                nuevo.close()
                return@launch
            }
            motor?.close()
            motor = nuevo
            cache.vaciar()
            imagen = null
            zoom = 1f; origenManual = null; yContinua = null
            pilaDeshacer.clear()
            paginaDeTrazos = -1
            if (d.estado != EstadoDocumento.OK.name) c.repoDocumentos.marcarEstado(hash, EstadoDocumento.OK)

            _puntos.value = c.repoPartitura.puntos(hash)
            _marcadores.value = c.repoPartitura.marcadores(hash)
            _tramos.value = c.repoPartitura.tramos(hash)
            _config.value = c.repoPartitura.config(hash)
            val sec = construirSecuencia(nuevo.numPaginas)
            secuencia = sec

            val inicio = when {
                alFinal -> sec.ultimo
                vistaInicial != null -> vistaInicial
                else -> 0
            }
            _ui.update {
                it.copy(
                    cargando = false, hash = hash, titulo = d.titulo, notas = d.notas, totalPaginas = nuevo.numPaginas,
                    posicionSetlist = setlist?.let { s -> "${indiceSetlist + 1}/${s.items.size}" },
                    mensaje = null,
                )
            }
            ultimaGeneracion = -1
            navegador.cargar(sec, marcadoresActivos(), contextoSetlist(), inicio)
            prepararTempo()
            persistirSesion()
        }
    }

    /** Reabre lo que estaba abierto cuando la app murió o se cerró. */
    fun reanudar(s: SesionGuardada) {
        if (s.bloqueado) bloquear()
        abrir(s.hash, s.setlistId.takeIf { it > 0 }, s.indiceSetlist, vistaInicial = s.vista)
    }

    private fun fallo(hash: String, titulo: String, motivo: String) {
        motor?.close(); motor = null
        secuencia = null
        imagen = null
        contenedor?.pagina?.mostrar(null)
        navegador.marcarError(motivo, contextoSetlist())
        _ui.update {
            it.copy(cargando = false, hash = hash, titulo = titulo, error = motivo, tempoDisponible = false, tempo = null)
        }
    }

    private fun construirSecuencia(paginas: Int = motor?.numPaginas ?: 1): SecuenciaDeVistas =
        SecuenciaDeVistas.construir(paginas, if (ajustes.puntosGiro) _puntos.value else emptyList())

    private fun marcadoresActivos(): List<Marcador> = if (ajustes.marcadores) _marcadores.value else emptyList()

    private fun contextoSetlist(): ContextoSetlist {
        val s = setlist ?: return ContextoSetlist()
        return ContextoSetlist(
            hayAnterior = indiceSetlist > 0,
            hayDespues = indiceSetlist < s.items.lastIndex,
            tituloSiguiente = s.items.getOrNull(indiceSetlist + 1)?.titulo,
        )
    }

    private suspend fun cambiarCancion(delta: Int, alFinal: Boolean) {
        val s = setlist ?: return
        val nuevo = indiceSetlist + delta
        val item = s.items.getOrNull(nuevo) ?: return
        abrir(item.hash, s.id, nuevo, alFinal)
    }

    /** Libera todo al salir del lector. La grabación, si está en marcha, continúa. */
    fun cerrar() {
        trabajoApertura?.cancel(); trabajoRender?.cancel(); trabajoTrazos?.cancel(); trabajoPersistir?.cancel()
        detenerTempo()
        if (c.metronomo.encendido) c.metronomo.detener()
        motor?.close(); motor = null
        cache.vaciar()
        imagen = null
        contenedor?.pagina?.mostrar(null)
        separar()
        c.metronomo.alError = null
        c.scope.launch { c.ajustesRepo.marcarLectorCerrado() }
        scope.cancel()
    }

    fun alPocaMemoria() = cache.vaciar()

    // ============================================================ Navegación y render

    private fun alEstadoNavegador(e: EstadoNavegador) {
        _ui.update {
            it.copy(
                fase = e.fase, indiceVista = e.indiceVista, totalVistas = e.totalVistas,
                avisoSiguiente = e.avisoSiguiente,
                error = if (e.fase == FaseLector.ERROR) e.mensajeError else null,
            )
        }
        if (e.fase != FaseLector.LEYENDO) return
        if (e.generacion != ultimaGeneracion) {
            val primera = ultimaGeneracion < 0
            ultimaGeneracion = e.generacion
            zoom = 1f; origenManual = null; yContinua = null
            solicitarRender()
            if (!primera) {
                persistirSesion()
                registrarMarcaGrabacion(e.indiceVista)
            }
        }
    }

    private fun alEfectoNavegacion(e: EfectoNavegacion) {
        when (e) {
            is EfectoNavegacion.AbrirCancion -> scope.launch { cambiarCancion(e.delta, e.alFinal) }
            EfectoNavegacion.FinDeSetlist -> mostrarMensaje("Fin del setlist")
            is EfectoNavegacion.VistaCambiada -> {
                // Si el cambio no lo provocó el propio avance por tempo, la música manda: se resincroniza.
                if (e.origen != OrigenComando.TEMPO && tempo.estado in ESTADOS_ACTIVOS) tempo.resincronizar(e.indice, 0.0)
            }
        }
    }

    private fun solicitarRender() {
        val m = motor ?: return
        val sec = secuencia ?: return
        val e = navegador.estado.value
        if (e.fase != FaseLector.LEYENDO || anchoVentana <= 0 || altoVentana <= 0) return
        val indice = e.indiceVista.coerceIn(0, sec.ultimo)
        val vista = sec[indice]
        trabajoRender?.cancel()
        trabajoRender = scope.launch {
            try {
                val img = obtenerImagen(m, vista.posicion.pagina, yContinua ?: vista.posicion.y, zoom, origenManual)
                if (!isActive) return@launch
                publicar(img)
                precargar(m, sec, indice)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                alFalloDeRender(ex)
            }
        }
    }

    private suspend fun obtenerImagen(m: MotorPdf, pagina: Int, y: Float, zoomActual: Float, origen: Pair<Float, Float>?): ImagenVista {
        val tam = m.tamanoCacheado(pagina) ?: m.tamanoPagina(pagina)
        val w = anchoVentana
        val h = altoVentana
        val t = if (origen == null) {
            TransformacionVista.ajustar(tam.ancho, tam.alto, w.toFloat(), h.toFloat(), ajustes.ajusteVista, y, zoomActual)
        } else {
            TransformacionVista.ajustarConOrigen(tam.ancho, tam.alto, w.toFloat(), h.toFloat(), ajustes.ajusteVista, zoomActual, origen.first, origen.second)
        }
        val p = ParametrosRender(pagina, t.escala, t.origenX, t.origenY, w, h)
        val clave = ClaveRender.de(p)
        val bmp = cache.obtener(clave) ?: rasterizar(m, p).also { cache.poner(clave, it) }
        return ImagenVista(bmp, t, pagina)
    }

    /** Si falta memoria, vacía la caché y reintenta una vez antes de rendirse (la imagen anterior sigue en pantalla). */
    private suspend fun rasterizar(m: MotorPdf, p: ParametrosRender) = try {
        m.renderizar(p)
    } catch (e: OutOfMemoryError) {
        cache.vaciar()
        m.renderizar(p)
    }

    private suspend fun precargar(m: MotorPdf, sec: SecuenciaDeVistas, indice: Int) {
        for (vecino in intArrayOf(indice + 1, indice - 1)) {
            if (vecino !in 0..sec.ultimo) continue
            val v = sec[vecino]
            try {
                obtenerImagen(m, v.posicion.pagina, v.posicion.y, 1f, null)
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Throwable) {
                return // la precarga es oportunista: un fallo aquí no debe molestar
            }
        }
    }

    private fun publicar(img: ImagenVista) {
        imagen = img
        val v = contenedor
        v?.pagina?.mostrar(img.bitmap)
        v?.tinta?.transformacion = img.transformacion
        _ui.update { it.copy(pagina = img.pagina) }
        if (img.pagina != paginaDeTrazos) observarTrazos(img.pagina)
    }

    private fun alFalloDeRender(e: Throwable) {
        mostrarMensaje(if (e is OutOfMemoryError) "Memoria insuficiente: se mantiene la vista anterior" else "No se pudo dibujar la página")
    }

    /** Se llama cuando la imagen nueva ya está en pantalla: cierra la medida de latencia del pedal. */
    private fun alPresentada() {
        val t0 = ultimoEventoPedalMs ?: return
        ultimoEventoPedalMs = null
        c.medidorLatencia.registrar((SystemClock.uptimeMillis() - t0).toDouble())
        if (ajustes.mostrarLatencia) _ui.update { it.copy(latencia = c.medidorLatencia.resumen()) }
    }

    // --- Zoom y toques ---

    private fun alGesto(g: GestoPagina) {
        val img = imagen ?: return
        val t = img.transformacion
        val nuevoZoom = (zoom * g.factorZoom).coerceIn(1f, TransformacionVista.ZOOM_MAXIMO)
        val r = nuevoZoom / zoom
        val ox = (t.origenX + g.focoX) * r - g.focoX + g.dx
        val oy = (t.origenY + g.focoY) * r - g.focoY + g.dy
        zoom = nuevoZoom
        origenManual = ox to oy
        solicitarRender()
    }

    private fun alToque(xPx: Float, yPx: Float) {
        val img = imagen ?: return
        val modo = _ui.value.modoEdicion
        if (modo != ModoEdicion.GIROS && modo != ModoEdicion.MARCADORES) return
        val t = img.transformacion
        val nx = t.xAPagina(xPx)
        val ny = t.yAPagina(yPx)
        if (nx !in 0f..1f || ny !in 0f..1f) return // fuera de la hoja
        _toque.value = ToqueEdicion(modo, Posicion.acotada(img.pagina, ny))
    }

    fun toqueAtendido() { _toque.value = null }

    // ============================================================ Pedal

    /** Punto de entrada de las acciones del pedal. Llega ya filtrado, deduplicado y consumido. */
    fun ejecutar(accion: Accion, ev: EventoTecla?) {
        val a = ajustes
        ultimoEventoPedalMs = ev?.eventTimeMs
        val id = ev?.let { it.downTimeMs * 31L + it.keyCode }
        when (accion) {
            Accion.SIGUIENTE -> navegador.enviar(ComandoNavegacion.Siguiente, OrigenComando.PEDAL, id)
            Accion.ANTERIOR -> navegador.enviar(ComandoNavegacion.Anterior, OrigenComando.PEDAL, id)
            Accion.SIGUIENTE_CANCION -> if (a.setlists) navegador.enviar(ComandoNavegacion.SiguienteCancion, OrigenComando.PEDAL, id)
            Accion.CANCION_ANTERIOR -> if (a.setlists) navegador.enviar(ComandoNavegacion.CancionAnterior, OrigenComando.PEDAL, id)
            Accion.REPETIR_SECCION -> if (a.marcadores) navegador.enviar(ComandoNavegacion.RepetirSeccion, OrigenComando.PEDAL, id)
            Accion.MARCADOR_SIGUIENTE -> if (a.marcadores) saltarMarcador(+1, id)
            Accion.MARCADOR_ANTERIOR -> if (a.marcadores) saltarMarcador(-1, id)
            Accion.TEMPO_INICIAR_PAUSAR -> alternarTempo()
            Accion.TEMPO_MAS_RAPIDO -> ajustarTempo(+a.pasoVelocidadPct / 100.0)
            Accion.TEMPO_MAS_LENTO -> ajustarTempo(-a.pasoVelocidadPct / 100.0)
            Accion.TEMPO_RESINCRONIZAR -> resincronizarTempo()
            Accion.METRONOMO_ALTERNAR -> alternarMetronomo()
            Accion.GRABACION_ALTERNAR -> alternarGrabacion()
        }
    }

    fun comandoTactil(cmd: ComandoNavegacion) {
        navegador.enviar(cmd, OrigenComando.TACTIL)
    }

    private fun saltarMarcador(dir: Int, id: Long?) {
        val sec = secuencia ?: return
        val actual = navegador.estado.value.indiceVista
        val lista = marcadoresActivos().sortedBy { it.posicion }
        val destino = if (dir > 0) lista.firstOrNull { sec.indiceDe(it.posicion) > actual }
        else lista.lastOrNull { sec.indiceDe(it.posicion) < actual }
        if (destino == null) {
            mostrarMensaje(if (dir > 0) "No hay más marcadores" else "No hay marcadores anteriores")
            return
        }
        navegador.enviar(ComandoNavegacion.IrAMarcador(destino.posicion), OrigenComando.PEDAL, id)
    }

    fun irAMarcador(m: Marcador) {
        navegador.enviar(ComandoNavegacion.IrAMarcador(m.posicion), OrigenComando.TACTIL)
    }

    // ============================================================ Edición: giros, marcadores, notas

    fun fijarModoEdicion(modo: ModoEdicion) {
        _ui.update { it.copy(modoEdicion = if (it.modoEdicion == modo) ModoEdicion.NINGUNO else modo) }
        _toque.value = null
        aplicarAjustesAVista()
    }

    fun agregarPuntoGiro(pos: Posicion, tipo: TipoGiro, compases: Int?) {
        val hash = _ui.value.hash
        scope.launch {
            c.repoPartitura.agregarPunto(hash, pos, tipo, compases)
            recargarRecorrido()
        }
    }

    /** Añade un giro "por página" en cada página que todavía no empiece por uno. */
    fun marcarInicioDeTodasLasPaginas() {
        val hash = _ui.value.hash
        val total = motor?.numPaginas ?: return
        scope.launch {
            val existentes = _puntos.value.filter { it.posicion.y < SecuenciaDeVistas.EPS }.map { it.posicion.pagina }.toSet()
            for (p in 0 until total) if (p !in existentes) c.repoPartitura.agregarPunto(hash, Posicion(p, 0f), TipoGiro.PAGINA, null)
            recargarRecorrido()
        }
    }

    fun borrarPuntoGiro(id: Long) {
        scope.launch {
            c.repoPartitura.borrarPunto(id)
            recargarRecorrido()
        }
    }

    fun agregarMarcador(nombre: String, pos: Posicion) {
        if (nombre.isBlank()) return
        val hash = _ui.value.hash
        scope.launch {
            c.repoPartitura.agregarMarcador(hash, nombre, pos)
            recargarRecorrido()
        }
    }

    fun borrarMarcador(id: Long) {
        scope.launch {
            c.repoPartitura.borrarMarcador(id)
            recargarRecorrido()
        }
    }

    /** Recarga puntos y marcadores y reconstruye el recorrido del pedal conservando dónde se está leyendo. */
    private suspend fun recargarRecorrido() {
        val hash = _ui.value.hash
        _puntos.value = c.repoPartitura.puntos(hash)
        _marcadores.value = c.repoPartitura.marcadores(hash)
        val sec = construirSecuencia()
        secuencia = sec
        navegador.reemplazarSecuencia(sec, marcadoresActivos())
        prepararTempo() // editar el recorrido detiene el avance por tempo
    }

    fun guardarNotas(texto: String) {
        val hash = _ui.value.hash
        scope.launch {
            c.repoDocumentos.guardarNotas(hash, texto)
            _ui.update { it.copy(notas = texto.take(com.suiteg.app.datos.RepoDocumentos.MAX_NOTAS)) }
        }
    }

    // ============================================================ Anotaciones

    private fun observarTrazos(pagina: Int) {
        paginaDeTrazos = pagina
        trabajoTrazos?.cancel()
        _trazos.value = emptyList()
        contenedor?.tinta?.trazos = emptyList()
        val hash = _ui.value.hash
        trabajoTrazos = scope.launch {
            c.repoTrazos.observarPagina(hash, pagina).collect { lista ->
                _trazos.value = lista
                contenedor?.tinta?.trazos = lista
            }
        }
    }

    private fun guardarTrazo(t: Trazo) {
        val hash = _ui.value.hash
        val pagina = imagen?.pagina ?: return
        scope.launch {
            val id = c.repoTrazos.guardar(hash, Trazo(0, pagina, t.herramienta, t.color, t.grosor, t.puntos))
            pilaDeshacer.addLast(id)
            if (pilaDeshacer.size > 200) pilaDeshacer.removeFirst()
        }
    }

    private fun borrarTrazo(t: Trazo) {
        scope.launch { c.repoTrazos.borrar(t.id) }
    }

    fun deshacerTrazo() {
        val id = pilaDeshacer.removeLastOrNull() ?: return
        scope.launch { c.repoTrazos.borrar(id) }
    }

    fun configurarTinta(herramienta: Herramienta, color: Int, borrador: Boolean) {
        contenedor?.tinta?.let {
            it.herramienta = herramienta
            it.color = color
            it.borrador = borrador
        }
    }

    // ============================================================ Modo concierto

    fun bloquear() {
        if (!ajustes.modoConcierto) return
        _ui.update { it.copy(bloqueado = true, modoEdicion = ModoEdicion.NINGUNO) }
        aplicarAjustesAVista()
        persistirSesion()
    }

    fun desbloquear() {
        _ui.update { it.copy(bloqueado = false) }
        aplicarAjustesAVista()
        persistirSesion()
    }

    // ============================================================ Avance por tempo

    private fun tempoActivoParaCancion(): Boolean =
        ajustes.avanceTempo && ((_config.value?.activo == true) || (setlist?.tempoActivo == true))

    private fun prepararTempo() {
        detenerTempo()
        val sec = secuencia
        val cfg = _config.value
        planTempo = null
        if (sec == null || cfg == null || !tempoActivoParaCancion()) {
            tempo.configurar(null)
            _ui.update { it.copy(tempoDisponible = false, tempo = null) }
            return
        }
        when (val r = CalculadorTempo.calcular(sec, ParametrosPlan(cfg.bpm, cfg.beatsPorCompas, cfg.compasesPorVista, _tramos.value))) {
            is ResultadoPlan.Listo -> {
                planTempo = r.plan
                tempo.configurar(r.plan)
                _ui.update { it.copy(tempoDisponible = true, tempo = tempo.instantanea()) }
            }
            is ResultadoPlan.Incompleto -> {
                tempo.configurar(null)
                _ui.update { it.copy(tempoDisponible = false, tempo = null) }
                mostrarMensaje("Tempo: faltan los compases de ${r.vistasSinCompases.size} vista(s). Indícalos en los giros o por página.")
            }
            is ResultadoPlan.Invalido -> {
                tempo.configurar(null)
                _ui.update { it.copy(tempoDisponible = false, tempo = null) }
                mostrarMensaje("Tempo: ${r.motivo}")
            }
        }
    }

    fun guardarConfigTempo(cfg: ConfigTempoCancion) {
        scope.launch {
            c.repoPartitura.guardarConfig(cfg)
            _config.value = cfg
            prepararTempo()
        }
    }

    fun agregarTramoAqui(bpm: Double) {
        val v = navegador.vistaActual() ?: return
        val hash = _ui.value.hash
        scope.launch {
            c.repoPartitura.agregarTramo(hash, v.posicion, bpm)
            _tramos.value = c.repoPartitura.tramos(hash)
            prepararTempo()
        }
    }

    fun borrarTramo(id: Long) {
        val hash = _ui.value.hash
        scope.launch {
            c.repoPartitura.borrarTramo(id)
            _tramos.value = c.repoPartitura.tramos(hash)
            prepararTempo()
        }
    }

    fun alternarTempo() {
        if (!_ui.value.tempoDisponible) {
            mostrarMensaje("El avance por tempo no está activado para esta canción")
            return
        }
        when (tempo.estado) {
            EstadoTempo.DETENIDO, EstadoTempo.FINALIZADO -> iniciarTempo()
            else -> {
                tempo.alternarPausa()
                if (tempo.estado == EstadoTempo.PAUSADO && metronomoPorCuenta) detenerMetronomoDeCuenta()
                publicarTempo()
            }
        }
    }

    private fun iniciarTempo() {
        val plan = planTempo ?: return
        val cfg = _config.value ?: return
        val indice = navegador.estado.value.indiceVista.coerceIn(0, plan.numVistas - 1)
        val bpm = plan.bpmPorVista[indice]
        val cuentaMs = if (cfg.cuentaEntradaCompases > 0) {
            (cfg.cuentaEntradaCompases * cfg.beatsPorCompas * 60_000.0 / bpm).toLong()
        } else 0L
        if (cuentaMs > 0 && !c.metronomo.encendido && ajustes.metronomo) {
            c.metronomo.iniciar(PatronMetronomo(bpm.coerceIn(CalculadorTempo.BPM_MIN, CalculadorTempo.BPM_MAX), cfg.beatsPorCompas), ajustes.volumenMetronomo)
            metronomoPorCuenta = true
            _ui.update { it.copy(metronomoActivo = true) }
        }
        tempo.iniciar(indice, cuentaMs)
        arrancarTicker()
        publicarTempo()
    }

    private fun ajustarTempo(delta: Double) {
        if (tempo.estado !in ESTADOS_ACTIVOS) return
        tempo.ajustarVelocidad(delta)
        publicarTempo()
    }

    private fun resincronizarTempo() {
        if (tempo.estado !in ESTADOS_ACTIVOS) return
        // "Estoy en el principio de la vista que veo ahora": la música manda, la posición se reajusta.
        tempo.resincronizar(navegador.estado.value.indiceVista, 0.0)
        publicarTempo()
    }

    private fun detenerTempo() {
        trabajoTempo?.cancel()
        tempo.detener()
        if (metronomoPorCuenta) detenerMetronomoDeCuenta()
        _ui.update { it.copy(avisoGiroHastaMs = null, tempo = if (planTempo != null) tempo.instantanea() else null) }
    }

    private fun arrancarTicker() {
        trabajoTempo?.cancel()
        trabajoTempo = scope.launch {
            var n = 0
            while (isActive && tempo.estado in ESTADOS_ACTIVOS) {
                tempo.tick()
                if (n++ % 4 == 0) publicarTempo()
                moverContinuo()
                delay(50)
            }
            publicarTempo()
        }
    }

    private fun publicarTempo() {
        val inst = tempo.instantanea()
        _ui.update { it.copy(tempo = inst) }
        // El metrónomo sigue el BPM efectivo (con tramos y correcciones en vivo) mientras el avance corre.
        if (c.metronomo.encendido && tempo.estado == EstadoTempo.CORRIENDO) {
            c.metronomo.actualizar(patronActual(), ajustes.volumenMetronomo)
        }
    }

    /** Modo continuo: desplaza suavemente la vista dentro de la página, a saltos de al menos 24 px para no saturar el render. */
    private fun moverContinuo() {
        val cfg = _config.value ?: return
        if (cfg.modo != ModoAvance.CONTINUO || tempo.estado != EstadoTempo.CORRIENDO) return
        val sec = secuencia ?: return
        val img = imagen ?: return
        val inst = tempo.instantanea()
        val pos = CalculadorTempo.posicionContinua(sec, inst.vista, inst.fraccion)
        if (pos.pagina != img.pagina) return
        val actual = yContinua ?: sec[inst.vista].posicion.y
        if (abs(pos.y - actual) * img.transformacion.altoPaginaPx >= 24f) {
            yContinua = pos.y
            solicitarRender()
        }
    }

    private fun alEventoTempo(e: EventoTempo) {
        when (e) {
            is EventoTempo.Girar -> navegador.enviar(ComandoNavegacion.IrAVista(e.vistaDestino), OrigenComando.TEMPO)
            is EventoTempo.AvisoGiro -> _ui.update {
                it.copy(avisoGiroHastaMs = c.reloj.ahoraMs() + e.msRestantes, avisoGiroEsFin = e.vistaDestino == null)
            }
            EventoTempo.AvisoCancelado -> _ui.update { it.copy(avisoGiroHastaMs = null) }
            EventoTempo.Iniciado -> if (metronomoPorCuenta) detenerMetronomoDeCuenta()
            EventoTempo.Fin -> {
                _ui.update { it.copy(avisoGiroHastaMs = null) }
                val s = setlist
                if (s != null && s.encadenarTempo && indiceSetlist < s.items.lastIndex) {
                    mostrarMensaje("Fin de la pieza: abriendo la siguiente")
                    scope.launch { cambiarCancion(+1, false) }
                } else {
                    mostrarMensaje("Fin de la pieza")
                }
            }
        }
    }

    // ============================================================ Metrónomo

    private fun patronActual(): PatronMetronomo {
        val cfg = _config.value
        val bpm = if (tempo.estado == EstadoTempo.CORRIENDO) tempo.instantanea().bpmEfectivo else cfg?.bpm ?: 100.0
        return PatronMetronomo(bpm.coerceIn(CalculadorTempo.BPM_MIN, CalculadorTempo.BPM_MAX), cfg?.beatsPorCompas ?: 4)
    }

    fun alternarMetronomo() {
        if (!ajustes.metronomo) return
        if (c.metronomo.encendido) {
            metronomoPorCuenta = false
            c.metronomo.detener()
        } else {
            c.metronomo.iniciar(patronActual(), ajustes.volumenMetronomo)
        }
        _ui.update { it.copy(metronomoActivo = c.metronomo.encendido) }
    }

    private fun detenerMetronomoDeCuenta() {
        metronomoPorCuenta = false
        c.metronomo.detener()
        _ui.update { it.copy(metronomoActivo = false) }
    }

    // ============================================================ Grabación

    fun alternarGrabacion() {
        if (!ajustes.grabacion) return
        scope.launch {
            if (c.grabacion.estado.value.activa) {
                c.grabacion.detener()
            } else {
                val d = _ui.value
                c.grabacion.iniciar(d.hash.ifEmpty { null }, d.titulo.ifEmpty { "Grabación" })
            }
        }
    }

    private fun registrarMarcaGrabacion(indiceVista: Int) {
        val g = c.grabacion.estado.value
        if (!g.activa || !ajustes.grabarMarcasGiro) return
        val t = SystemClock.elapsedRealtime() - g.inicioMs
        scope.launch { c.repoGrabaciones.marca(g.id, t, indiceVista, "Vista ${indiceVista + 1}") }
    }

    // ============================================================ Utilidades

    fun mostrarMensaje(texto: String) {
        trabajoMensaje?.cancel()
        _ui.update { it.copy(mensaje = texto) }
        trabajoMensaje = scope.launch {
            delay(3500)
            _ui.update { it.copy(mensaje = null) }
        }
    }

    private fun persistirSesion() {
        val hash = _ui.value.hash
        if (hash.isEmpty()) return
        trabajoPersistir?.cancel()
        trabajoPersistir = scope.launch {
            delay(400) // junta ráfagas de giros en una sola escritura
            c.ajustesRepo.guardarSesion(
                SesionGuardada(hash, setlist?.id ?: 0L, indiceSetlist.coerceAtLeast(0), navegador.estado.value.indiceVista, _ui.value.bloqueado),
            )
        }
    }

    companion object {
        private val ESTADOS_ACTIVOS = setOf(EstadoTempo.CORRIENDO, EstadoTempo.CUENTA_ATRAS, EstadoTempo.PAUSADO)
        val COLORES_TINTA = listOf(
            Color.rgb(0xE0, 0x59, 0x2A), Color.rgb(0x3B, 0x82, 0xF6), Color.rgb(0x2F, 0xA8, 0x4F),
            Color.rgb(0xFF, 0xD6, 0x0A), Color.rgb(0xF2, 0xF2, 0xF2), Color.rgb(0x10, 0x10, 0x10),
        )
    }
}
