package com.suiteg.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.suiteg.app.Contenedor
import com.suiteg.app.audio.EstadoGrabacion
import com.suiteg.app.lector.ContenedorLector
import com.suiteg.app.lector.EstadoLectorUi
import com.suiteg.app.lector.ModoEdicion
import com.suiteg.app.lector.SesionLectura
import com.suiteg.app.pedal.EstadoPedal
import com.suiteg.modelo.AjustesApp
import com.suiteg.modelo.Herramienta
import com.suiteg.modelo.ModoAvance
import com.suiteg.modelo.ModoInversion
import com.suiteg.modelo.AjusteVista
import com.suiteg.modelo.PerfilUso
import com.suiteg.modelo.Posicion
import com.suiteg.modelo.TipoGiro
import com.suiteg.navegacion.ComandoNavegacion
import com.suiteg.navegacion.FaseLector
import com.suiteg.tempo.EstadoTempo
import kotlinx.coroutines.delay

/** Lo que el lector necesita de la ventana (lo implementa MainActivity). */
interface ControlVentana {
    fun inmersivo(activo: Boolean)
    fun fijarPantalla(activo: Boolean)
    fun pedirMicrofono(alResolver: (Boolean) -> Unit)
}

@Composable
fun PantallaLector(c: Contenedor, sesion: SesionLectura, ventana: ControlVentana, alSalir: () -> Unit) {
    val ui by sesion.ui.collectAsState()
    val a by c.ajustes.collectAsState()
    val pedal by c.monitorPedal.estado.collectAsState()
    val grab by c.grabacion.estado.collectAsState()
    val toque by sesion.toqueEdicion.collectAsState()

    var barra by remember { mutableStateOf(true) }
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }
    var panelTempo by remember { mutableStateOf(false) }

    BackHandler { if (!ui.bloqueado) alSalir() }

    DisposableEffect(Unit) {
        ventana.inmersivo(true)
        onDispose { ventana.inmersivo(false); ventana.fijarPantalla(false) }
    }
    LaunchedEffect(ui.bloqueado, a.bloqueoMaximo) { ventana.fijarPantalla(ui.bloqueado && a.bloqueoMaximo) }
    LaunchedEffect(Unit) { if (a.bloqueoAlAbrir) sesion.bloquear() }
    LaunchedEffect(toque) {
        val t = toque ?: return@LaunchedEffect
        dialogo = if (t.modo == ModoEdicion.GIROS) Dialogo.NuevoGiro(t.posicion) else Dialogo.NuevoMarcador(t.posicion)
        sesion.toqueAtendido()
    }
    DisposableEffect(sesion) { onDispose { sesion.separar() } }

    Column(Modifier.fillMaxSize().background(Paleta.Carbon)) {
    BarraSuperior(ui, pedal, grab, a)
    Box(Modifier.fillMaxWidth().weight(1f)) {
        AndroidView(
            factory = { ctx -> ContenedorLector(ctx, c.reloj).also { sesion.adjuntar(it) } },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxWidth().align(Alignment.TopStart)) {
            AvisoGiro(ui, c)
            if (a.mostrarLatencia) ui.latencia?.let {
                Text(
                    "pedal→pantalla p50 %.0f · p95 %.0f · p99 %.0f ms (n=%d)".format(it.p50, it.p95, it.p99, it.muestras),
                    Modifier.background(Color(0xCC1B1B1B)).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = Paleta.TextoSuave,
                )
            }
            if (ui.modoEdicion == ModoEdicion.GIROS || ui.modoEdicion == ModoEdicion.MARCADORES) {
                Text(
                    if (ui.modoEdicion == ModoEdicion.GIROS) "Toca la partitura donde debe cambiar la vista" else "Toca la partitura donde empieza la sección",
                    Modifier.fillMaxWidth().background(Paleta.Oxido).padding(8.dp), color = Color.White, style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (ui.fase == FaseLector.ERROR) PanelError(ui, sesion)
        if (ui.cargando) Text("Abriendo…", Modifier.align(Alignment.Center).background(Paleta.CarbonClaro).padding(16.dp))

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ui.avisoSiguiente?.let {
                Text(
                    "Siguiente: $it   (pisa el pedal para abrirla)",
                    Modifier.background(Color(0xE61B1B1B)).border(2.dp, Paleta.Oxido).padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            ui.mensaje?.let {
                Text(it, Modifier.padding(top = 6.dp).background(Color(0xE6262626)).border(1.dp, Paleta.Linea).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            if (!ui.bloqueado) {
                if (ui.modoEdicion == ModoEdicion.DIBUJO && a.anotaciones) PanelTinta(sesion)
                if (panelTempo && a.avanceTempo) PanelTempo(c, sesion, ui, { dialogo = Dialogo.ConfigTempo })
                if (barra) {
                    BarraHerramientas(
                        ui, a, grab,
                        alMenu = { dialogo = Dialogo.Menu },
                        alModo = { sesion.fijarModoEdicion(it) },
                        alTempo = { panelTempo = !panelTempo },
                        alMetronomo = { sesion.alternarMetronomo() },
                        alGrabar = {
                            if (grab.activa || c.grabacion.tienePermiso()) sesion.alternarGrabacion()
                            else ventana.pedirMicrofono { ok -> if (ok) sesion.alternarGrabacion() else sesion.mostrarMensaje("Sin permiso de micrófono") }
                        },
                        alBloquear = { sesion.bloquear() },
                        alOcultar = { barra = false },
                        alMover = { sesion.comandoTactil(it) },
                    )
                } else {
                    Text("▴ herramientas", Modifier.clickable { barra = true }.background(Paleta.CarbonClaro).padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = Paleta.TextoSuave)
                }
            }
        }
    }
    }

    when (val d = dialogo) {
        null -> Unit
        Dialogo.Menu -> DialogoMenuLector(c, sesion, ui, a, { dialogo = it }, { dialogo = null }, alSalir)
        Dialogo.Giros -> DialogoGiros(sesion, { dialogo = null })
        Dialogo.Marcadores -> DialogoMarcadores(sesion, { dialogo = null })
        Dialogo.Notas -> DialogoTexto("Notas de «${ui.titulo}»", ui.notas, "Tempo, cambios de arco, dinámicas…", lineas = 4,
            alConfirmar = { sesion.guardarNotas(it); dialogo = null }, alCancelar = { dialogo = null })
        Dialogo.ConfigTempo -> DialogoConfigTempo(sesion, { dialogo = null })
        is Dialogo.NuevoGiro -> DialogoNuevoGiro(d.posicion, { pos, tipo, compases -> sesion.agregarPuntoGiro(pos, tipo, compases); dialogo = null }, { dialogo = null })
        is Dialogo.NuevoMarcador -> DialogoTexto("Nuevo marcador", "", "Nombre corto (A, ensayo 3…)",
            alConfirmar = { sesion.agregarMarcador(it, d.posicion); dialogo = null }, alCancelar = { dialogo = null })
    }
}

private sealed interface Dialogo {
    data object Menu : Dialogo
    data object Giros : Dialogo
    data object Marcadores : Dialogo
    data object Notas : Dialogo
    data object ConfigTempo : Dialogo
    data class NuevoGiro(val posicion: Posicion) : Dialogo
    data class NuevoMarcador(val posicion: Posicion) : Dialogo
}

// ---------------------------------------------------------------- Barra superior y avisos

@Composable
private fun BarraSuperior(ui: EstadoLectorUi, pedal: EstadoPedal, grab: EstadoGrabacion, a: AjustesApp) {
    val reproduccion by produceState(0L, grab.activa, grab.inicioMs) {
        while (grab.activa) { value = android.os.SystemClock.elapsedRealtime() - grab.inicioMs; delay(500) }
        value = 0L
    }
    Row(
        Modifier.fillMaxWidth().background(Color(0xCC1B1B1B)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                append(ui.titulo)
                if (ui.totalVistas > 0) append("   ${ui.indiceVista + 1}/${ui.totalVistas}")
                ui.posicionSetlist?.let { append("   ·   canción $it") }
            },
            Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Paleta.Texto,
        )
        if (grab.activa) Text("●REC ${formatoDuracion(reproduccion)}  ", color = Paleta.Alerta, fontSize = 13.sp)
        if (ui.metronomoActivo) Text("♩ ", color = Paleta.OxidoClaro, fontSize = 13.sp)
        if (ui.tempo?.estado == EstadoTempo.CORRIENDO) Text("▶ %.0f  ".format(ui.tempo.bpmEfectivo), color = Paleta.OxidoClaro, fontSize = 13.sp)
        if (ui.bloqueado) Text("BLOQUEADO  ", color = Paleta.OxidoClaro, fontSize = 13.sp)
        when (pedal) {
            EstadoPedal.SinConfigurar -> Text("PEDAL – sin asignar", color = Paleta.TextoSuave, fontSize = 13.sp)
            EstadoPedal.Desconectado -> Text("PEDAL ○ SIN CONEXIÓN", color = Paleta.Alerta, fontSize = 13.sp)
            is EstadoPedal.Conectado -> Text("PEDAL ●", color = Paleta.Conectado, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AvisoGiro(ui: EstadoLectorUi, c: Contenedor) {
    val hasta = ui.avisoGiroHastaMs ?: return
    val ahora by produceState(c.reloj.ahoraMs(), hasta) { while (true) { value = c.reloj.ahoraMs(); delay(60) } }
    val restante = (hasta - ahora).coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().height(34.dp).background(Color(0xE61B1B1B))) {
        val total = 2500f
        Box(Modifier.fillMaxHeight().fillMaxWidth((restante / total).coerceIn(0f, 1f)).background(Paleta.Oxido))
        Text(
            (if (ui.avisoGiroEsFin) "FIN DE LA PIEZA en " else "GIRO en ") + "%.1f s".format(restante / 1000.0),
            Modifier.align(Alignment.Center), color = Color.White, style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun PanelError(ui: EstadoLectorUi, sesion: SesionLectura) {
    Box(Modifier.fillMaxSize().background(Paleta.Carbon), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp).border(2.dp, Paleta.Alerta).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No se puede mostrar esta partitura", style = MaterialTheme.typography.titleLarge, color = Paleta.Alerta)
            Text(ui.error ?: "Error desconocido")
            Text(
                if (ui.posicionSetlist != null) "El resto del setlist no se ve afectado. Pisa el pedal de avanzar para pasar a la siguiente canción."
                else "Vuelve atrás para elegir otra partitura.",
                color = Paleta.TextoSuave,
            )
            BotonOxido("Siguiente canción", { sesion.comandoTactil(ComandoNavegacion.SiguienteCancion) })
        }
    }
}

// ---------------------------------------------------------------- Barra de herramientas

@Composable
private fun BarraHerramientas(
    ui: EstadoLectorUi, a: AjustesApp, grab: EstadoGrabacion,
    alMenu: () -> Unit, alModo: (ModoEdicion) -> Unit, alTempo: () -> Unit, alMetronomo: () -> Unit,
    alGrabar: () -> Unit, alBloquear: () -> Unit, alOcultar: () -> Unit, alMover: (ComandoNavegacion) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xF21B1B1B)).border(1.dp, Paleta.Linea).padding(6.dp)
            .horizontalScrollSimple(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Opcion("☰ Menú", false, alMenu)
        Opcion("◀", false, { alMover(ComandoNavegacion.Anterior) })
        Opcion("▶", false, { alMover(ComandoNavegacion.Siguiente) })
        if (a.puntosGiro) Opcion("Giros", ui.modoEdicion == ModoEdicion.GIROS, { alModo(ModoEdicion.GIROS) })
        if (a.marcadores) Opcion("Marcas", ui.modoEdicion == ModoEdicion.MARCADORES, { alModo(ModoEdicion.MARCADORES) })
        if (a.anotaciones) Opcion("Dibujar", ui.modoEdicion == ModoEdicion.DIBUJO, { alModo(ModoEdicion.DIBUJO) })
        if (a.avanceTempo) Opcion("Tempo", ui.tempo?.estado == EstadoTempo.CORRIENDO, alTempo)
        if (a.metronomo) Opcion("♩ Metrónomo", ui.metronomoActivo, alMetronomo)
        if (a.grabacion) Opcion(if (grab.activa) "■ Parar" else "● Grabar", grab.activa, alGrabar)
        if (a.modoConcierto) Opcion("🔒 Concierto", false, alBloquear)
        Opcion("▾", false, alOcultar)
    }
}

@Composable
private fun Modifier.horizontalScrollSimple(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

// ---------------------------------------------------------------- Tinta

@Composable
private fun PanelTinta(sesion: SesionLectura) {
    var herramienta by remember { mutableStateOf(Herramienta.LAPIZ) }
    var color by remember { mutableStateOf(SesionLectura.COLORES_TINTA.first()) }
    var borrador by remember { mutableStateOf(false) }
    LaunchedEffect(herramienta, color, borrador) { sesion.configurarTinta(herramienta, color, borrador) }
    Row(
        Modifier.fillMaxWidth().background(Color(0xF21B1B1B)).border(1.dp, Paleta.Linea).padding(6.dp).horizontalScrollSimple(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Opcion("Lápiz", !borrador && herramienta == Herramienta.LAPIZ, { herramienta = Herramienta.LAPIZ; borrador = false })
        Opcion("Resaltador", !borrador && herramienta == Herramienta.RESALTADOR, { herramienta = Herramienta.RESALTADOR; borrador = false })
        Opcion("Borrador", borrador, { borrador = !borrador })
        for (col in SesionLectura.COLORES_TINTA) {
            Box(
                Modifier.size(36.dp).background(Color(col)).border(if (col == color && !borrador) 3.dp else 1.dp, if (col == color && !borrador) Paleta.OxidoClaro else Paleta.Linea)
                    .clickable { color = col; borrador = false },
            )
        }
        Opcion("↶ Deshacer", false, { sesion.deshacerTrazo() })
    }
}

// ---------------------------------------------------------------- Tempo

@Composable
private fun PanelTempo(c: Contenedor, sesion: SesionLectura, ui: EstadoLectorUi, alConfigurar: () -> Unit) {
    val t = ui.tempo
    Column(Modifier.fillMaxWidth().background(Color(0xF21B1B1B)).border(1.dp, Paleta.Linea).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!ui.tempoDisponible || t == null) {
            Text("El avance por tempo no está activo en esta canción.", color = Paleta.TextoSuave)
            BotonOxido("Configurar", alConfigurar)
            return@Column
        }
        Text(
            when (t.estado) {
                EstadoTempo.DETENIDO -> "Detenido (empezará desde la vista actual)"
                EstadoTempo.CUENTA_ATRAS -> "Cuenta de entrada… ${((t.msHastaInicio ?: 0) / 1000.0).let { "%.1f".format(it) }} s"
                EstadoTempo.CORRIENDO -> "En marcha"
                EstadoTempo.PAUSADO -> "En pausa"
                EstadoTempo.FINALIZADO -> "Finalizado"
            } + "   ♩ = %.0f  (×%.2f)".format(t.bpmEfectivo, t.velocidad),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val enMarcha = t.estado == EstadoTempo.CORRIENDO || t.estado == EstadoTempo.CUENTA_ATRAS
            BotonOxido(if (enMarcha) "⏸ Pausa" else if (t.estado == EstadoTempo.PAUSADO) "▶ Reanudar" else "▶ Iniciar", { sesion.alternarTempo() })
            BotonContorno("− Más lento", { sesion.ejecutar(com.suiteg.navegacion.entrada.Accion.TEMPO_MAS_LENTO, null) })
            BotonContorno("+ Más rápido", { sesion.ejecutar(com.suiteg.navegacion.entrada.Accion.TEMPO_MAS_RAPIDO, null) })
            BotonContorno("⟲ Estoy aquí", { sesion.ejecutar(com.suiteg.navegacion.entrada.Accion.TEMPO_RESINCRONIZAR, null) })
            BotonContorno("Configurar", alConfigurar)
        }
    }
}

@Composable
private fun DialogoConfigTempo(sesion: SesionLectura, alCerrar: () -> Unit) {
    val cfg by sesion.configTempo.collectAsState()
    val tramos by sesion.tramos.collectAsState()
    val actual = cfg ?: return alCerrar()
    var bpm by remember(actual) { mutableStateOf(actual.bpm.toString().removeSuffix(".0")) }
    var beats by remember(actual) { mutableStateOf(actual.beatsPorCompas.toString()) }
    var compases by remember(actual) { mutableStateOf(actual.compasesPorVista?.toString().orEmpty()) }
    var cuenta by remember(actual) { mutableStateOf(actual.cuentaEntradaCompases.toString()) }
    var nuevoTramo by remember { mutableStateOf("") }

    DialogoContenido("Avance por tempo", alCerrar) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilaAjuste("Activar en esta canción", "Desactivado por defecto.", actual.activo) { sesion.guardarConfigTempo(actual.copy(activo = it)) }
            Campo("Tempo inicial (BPM)", bpm) { bpm = it }
            Campo("Pulsos por compás", beats) { beats = it }
            Campo("Compases por vista (si el giro no indica los suyos)", compases) { compases = it }
            Campo("Cuenta de entrada (compases)", cuenta) { cuenta = it }
            Text("Modo de avance")
            Selector(listOf(ModoAvance.PAGINA to "Cambio de vista", ModoAvance.CONTINUO to "Desplazamiento suave"), actual.modo) { sesion.guardarConfigTempo(actual.copy(modo = it)) }
            BotonOxido("Guardar", {
                sesion.guardarConfigTempo(
                    actual.copy(
                        bpm = bpm.replace(',', '.').toDoubleOrNull() ?: actual.bpm,
                        beatsPorCompas = beats.toIntOrNull()?.coerceIn(1, 32) ?: actual.beatsPorCompas,
                        compasesPorVista = compases.toIntOrNull()?.takeIf { it > 0 },
                        cuentaEntradaCompases = cuenta.toIntOrNull()?.coerceIn(0, 8) ?: 0,
                    ),
                )
            })
            HorizontalDivider(color = Paleta.Linea)
            Text("Cambios de tempo (ritardando / accelerando)", style = MaterialTheme.typography.titleMedium)
            Text("Se aplican desde la vista donde estés leyendo ahora.", color = Paleta.TextoSuave)
            tramos.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pág. ${t.desde.pagina + 1}, %.0f %%  →  ♩ = %.0f".format(t.desde.y * 100, t.bpm), Modifier.weight(1f))
                    BotonContorno("Quitar", { sesion.borrarTramo(t.id) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Campo("Nuevo BPM", nuevoTramo, Modifier.weight(1f)) { nuevoTramo = it }
                BotonContorno("Añadir aquí", {
                    nuevoTramo.replace(',', '.').toDoubleOrNull()?.let { sesion.agregarTramoAqui(it); nuevoTramo = "" }
                })
            }
        }
    }
}

@Composable
private fun Campo(etiqueta: String, valor: String, modifier: Modifier = Modifier.fillMaxWidth(), alCambiar: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = valor, onValueChange = alCambiar, label = { Text(etiqueta) }, singleLine = true, modifier = modifier,
        shape = androidx.compose.ui.graphics.RectangleShape,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
    )
}

// ---------------------------------------------------------------- Diálogos

@Composable
private fun DialogoMenuLector(
    c: Contenedor, sesion: SesionLectura, ui: EstadoLectorUi, a: AjustesApp,
    ir: (Dialogo) -> Unit, cerrar: () -> Unit, salir: () -> Unit,
) {
    DialogoContenido("«${ui.titulo}»", cerrar) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.notas) {
                if (ui.notas.isNotBlank()) Text(ui.notas, Modifier.fillMaxWidth().background(Paleta.CarbonMedio).padding(8.dp), color = Paleta.OxidoClaro)
                BotonContorno("Notas de la canción", { ir(Dialogo.Notas) }, Modifier.fillMaxWidth())
            }
            if (a.marcadores) BotonContorno("Marcadores…", { ir(Dialogo.Marcadores) }, Modifier.fillMaxWidth())
            if (a.puntosGiro) BotonContorno("Puntos de giro…", { ir(Dialogo.Giros) }, Modifier.fillMaxWidth())
            if (a.avanceTempo) BotonContorno("Avance por tempo…", { ir(Dialogo.ConfigTempo) }, Modifier.fillMaxWidth())
            Text("Colores", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollSimple()) {
                Opcion("Normal", a.inversion == ModoInversion.NINGUNA, { guardarVisual(c, a.copy(inversion = ModoInversion.NINGUNA)) })
                Opcion("Invertir", a.inversion == ModoInversion.TODO, { guardarVisual(c, a.copy(inversion = ModoInversion.TODO)) })
                Opcion("Tonalidad", a.inversion == ModoInversion.TONALIDAD, { guardarVisual(c, a.copy(inversion = ModoInversion.TONALIDAD)) })
                Opcion("Bajo contraste", a.inversion == ModoInversion.FONDO, { guardarVisual(c, a.copy(inversion = ModoInversion.FONDO)) })
            }
            Text("Ajuste de la página", style = MaterialTheme.typography.titleMedium)
            Selector(listOf(AjusteVista.PAGINA_COMPLETA to "Página completa", AjusteVista.ANCHO to "Al ancho"), a.ajusteVista) { guardarVisual(c, a.copy(ajusteVista = it)) }
            HorizontalDivider(color = Paleta.Linea)
            BotonOxido("Salir de la partitura", { cerrar(); salir() }, Modifier.fillMaxWidth())
        }
    }
}

private fun guardarVisual(c: Contenedor, nuevo: AjustesApp) = c.guardarAjustes(nuevo.copy(perfil = PerfilUso.PERSONALIZADO))

@Composable
private fun DialogoNuevoGiro(pos: Posicion, alConfirmar: (Posicion, TipoGiro, Int?) -> Unit, alCancelar: () -> Unit) {
    var tipo by remember { mutableStateOf(TipoGiro.LIBRE) }
    var compases by remember { mutableStateOf("") }
    DialogoContenido("Nuevo punto de giro", alCancelar) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Página ${pos.pagina + 1}, a un ${"%.0f".format(pos.y * 100)} % de la altura.")
            Selector(listOf(TipoGiro.PAGINA to "Por página", TipoGiro.COMPAS to "Por compás", TipoGiro.LIBRE to "Posición libre"), tipo) { tipo = it }
            Campo("Compases hasta el siguiente giro (para el tempo, opcional)", compases) { compases = it }
            BotonOxido("Añadir", { alConfirmar(pos, tipo, compases.toIntOrNull()) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DialogoGiros(sesion: SesionLectura, alCerrar: () -> Unit) {
    val puntos by sesion.puntos.collectAsState()
    DialogoContenido("Puntos de giro", alCerrar) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (puntos.isEmpty()) "Sin puntos: el pedal avanza de página en página. Pulsa «Giros» en la barra y toca la partitura para marcar los tuyos."
                else "El pedal recorre solo estos puntos (más el inicio).",
                color = Paleta.TextoSuave,
            )
            puntos.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pág. ${p.posicion.pagina + 1} · ${"%.0f".format(p.posicion.y * 100)} % · ${when (p.tipo) { TipoGiro.PAGINA -> "página"; TipoGiro.COMPAS -> "compás"; TipoGiro.LIBRE -> "libre" }}" +
                            (p.compases?.let { " · $it comp." } ?: ""),
                        Modifier.weight(1f),
                    )
                    BotonContorno("Quitar", { sesion.borrarPuntoGiro(p.id) })
                }
            }
            BotonContorno("Marcar el inicio de todas las páginas", { sesion.marcarInicioDeTodasLasPaginas() }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DialogoMarcadores(sesion: SesionLectura, alCerrar: () -> Unit) {
    val marcadores by sesion.marcadores.collectAsState()
    DialogoContenido("Marcadores de ensayo", alCerrar) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (marcadores.isEmpty()) Text("Pulsa «Marcas» en la barra y toca la partitura para crear uno.", color = Paleta.TextoSuave)
            marcadores.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${m.nombre}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("pág. ${m.posicion.pagina + 1}", color = Paleta.TextoSuave)
                    BotonOxido("Ir", { sesion.irAMarcador(m); alCerrar() })
                    BotonContorno("Quitar", { sesion.borrarMarcador(m.id) })
                }
            }
            Text("Con el pedal: asigna «marcador siguiente / anterior» y «repetir sección» en Ajustes → Pedal.", color = Paleta.TextoSuave, style = MaterialTheme.typography.bodySmall)
        }
    }
}
