package com.suiteg.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.suiteg.modelo.AjusteVista
import com.suiteg.modelo.AjustesApp
import com.suiteg.modelo.Herramienta
import com.suiteg.modelo.ModoAvance
import com.suiteg.modelo.ModoInversion
import com.suiteg.modelo.PerfilUso
import com.suiteg.modelo.Posicion
import com.suiteg.modelo.TipoGiro
import com.suiteg.navegacion.ComandoNavegacion
import com.suiteg.navegacion.FaseLector
import com.suiteg.navegacion.entrada.Accion
import com.suiteg.tempo.EstadoTempo
import kotlinx.coroutines.delay

/** Lo que el lector necesita de la ventana (lo implementa MainActivity). */
interface ControlVentana {
    fun inmersivo(activo: Boolean)
    fun fijarPantalla(activo: Boolean)
    fun pedirMicrofono(alResolver: (Boolean) -> Unit)
}

private enum class Pestana(val titulo: String) { TEMPO("Tempo"), TINTA("Anotaciones"), RECORRIDO("Partitura"), AUDIO("Audio") }

@Composable
fun PantallaLector(c: Contenedor, sesion: SesionLectura, ventana: ControlVentana, alSalir: () -> Unit) {
    val ui by sesion.ui.collectAsState()
    val a by c.ajustes.collectAsState()
    val pedal by c.monitorPedal.estado.collectAsState()
    val grab by c.grabacion.estado.collectAsState()
    val toque by sesion.toqueEdicion.collectAsState()

    var barra by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var pestana by remember { mutableStateOf(Pestana.RECORRIDO) }
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }
    // Estado de la tinta, elevado aquí para que no se pierda al cambiar de pestaña.
    var herramienta by remember { mutableStateOf(Herramienta.LAPIZ) }
    var colorTinta by remember { mutableStateOf(SesionLectura.COLORES_TINTA.first()) }
    var borrador by remember { mutableStateOf(false) }

    BackHandler { if (!ui.bloqueado) alSalir() }

    DisposableEffect(Unit) {
        ventana.inmersivo(true)
        onDispose { ventana.inmersivo(false); ventana.fijarPantalla(false) }
    }
    LaunchedEffect(ui.bloqueado, a.bloqueoMaximo) { ventana.fijarPantalla(ui.bloqueado && a.bloqueoMaximo) }
    LaunchedEffect(Unit) { if (a.bloqueoAlAbrir) sesion.bloquear() }
    LaunchedEffect(herramienta, colorTinta, borrador) { sesion.configurarTinta(herramienta, colorTinta, borrador) }
    LaunchedEffect(toque) {
        val t = toque ?: return@LaunchedEffect
        dialogo = if (t.modo == ModoEdicion.GIROS) Dialogo.NuevoGiro(t.posicion) else Dialogo.NuevoMarcador(t.posicion)
        sesion.toqueAtendido()
    }
    DisposableEffect(sesion) { onDispose { sesion.separar() } }

    Column(Modifier.fillMaxSize().background(Color(0xFF30322E))) {
        CabeceraLector(ui, pedal, grab, a, c, alSalir, { guardarVisual(c, a.copy(inversion = if (a.inversion == ModoInversion.NINGUNA) ModoInversion.TODO else ModoInversion.NINGUNA)) })
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
                        Modifier.background(Color(0xCC191B17)).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = Paleta.TextoSuave,
                    )
                }
                if (ui.modoEdicion == ModoEdicion.GIROS || ui.modoEdicion == ModoEdicion.MARCADORES) {
                    Text(
                        if (ui.modoEdicion == ModoEdicion.GIROS) "Toca la partitura donde debe cambiar la vista" else "Toca la partitura donde empieza la sección",
                        Modifier.fillMaxWidth().background(Paleta.Oxido).padding(10.dp), color = Paleta.TextoSobreOxido, style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (ui.fase == FaseLector.ERROR) PanelError(ui, sesion)
            if (ui.cargando) Text("Abriendo…", Modifier.align(Alignment.Center).background(Paleta.CarbonClaro, Formas.Media).padding(16.dp))

            if (panel && !ui.bloqueado) {
                PanelHerramientas(
                    c, sesion, ui, a, grab, pestana, { pestana = it }, { panel = false },
                    herramienta, { herramienta = it }, colorTinta, { colorTinta = it }, borrador, { borrador = it },
                    { dialogo = it }, ventana,
                    Modifier.align(Alignment.CenterEnd),
                )
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ui.avisoSiguiente?.let {
                    Text(
                        "Siguiente: $it   (pisa el pedal para abrirla)",
                        Modifier.background(Color(0xE6191B17), Formas.Media).border(2.dp, Paleta.Oxido, Formas.Media).padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                ui.mensaje?.let {
                    Text(it, Modifier.padding(top = 6.dp).background(Color(0xE620211F), Formas.Media).border(1.dp, Paleta.Linea, Formas.Media).padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                }
                if (!ui.bloqueado) {
                    if (barra) BarraInferior(ui, a, panel, { panel = !panel }, { sesion.bloquear() }, { barra = false }, { sesion.comandoTactil(it) })
                    else Row(
                        Modifier.padding(bottom = 8.dp).background(Color(0xE6191B17), Formas.Media).border(1.dp, Paleta.Linea, Formas.Media).clickable { barra = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { Icono(TipoIcono.OJO, tamano = 16.dp); Text("Mostrar controles", fontSize = 12.sp) }
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

@Composable
private fun Modifier.horizontalScrollSimple(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

// ---------------------------------------------------------------- Cabecera, avisos y barra inferior

@Composable
private fun CabeceraLector(
    ui: EstadoLectorUi, pedal: EstadoPedal, grab: EstadoGrabacion, a: AjustesApp, c: Contenedor,
    alSalir: () -> Unit, alNoche: () -> Unit,
) {
    val transcurrido by produceState(0L, grab.activa, grab.inicioMs) {
        while (grab.activa) { value = android.os.SystemClock.elapsedRealtime() - grab.inicioMs; delay(500) }
        value = 0L
    }
    Column {
        Row(Modifier.fillMaxWidth().background(Color(0xFF191B17)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonIcono(TipoIcono.ATRAS, "Volver a la biblioteca", alSalir, habilitado = !ui.bloqueado)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(ui.titulo.ifEmpty { "Abriendo…" }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (ui.totalVistas > 0) append("Vista ${ui.indiceVista + 1} de ${ui.totalVistas}")
                        if (ui.totalPaginas > 0) append(" · ${ui.totalPaginas} págs.")
                        ui.posicionSetlist?.let { append(" · canción $it") }
                    },
                    fontSize = 11.sp, color = Paleta.TextoSuave, maxLines = 1, modifier = Modifier.padding(top = 3.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (grab.activa) Text("● REC ${formatoDuracion(transcurrido)}", color = Paleta.Alerta, fontSize = 11.sp)
                if (ui.metronomoActivo) Text("♩ metrónomo", color = Paleta.OxidoClaro, fontSize = 11.sp)
                if (ui.tempo?.estado == EstadoTempo.CORRIENDO) Text("▶ %.0f BPM".format(ui.tempo.bpmEfectivo), color = Paleta.OxidoClaro, fontSize = 11.sp)
                if (ui.bloqueado) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icono(TipoIcono.CANDADO, color = Paleta.OxidoClaro, tamano = 13.dp); Text("Modo concierto", color = Paleta.OxidoClaro, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (color, texto) = when (pedal) {
                        is EstadoPedal.Conectado -> Paleta.Conectado to "Pedal conectado"
                        EstadoPedal.Desconectado -> Paleta.Alerta to "Pedal sin conexión"
                        EstadoPedal.SinConfigurar -> Paleta.TextoSuave to "Pedal sin asignar"
                    }
                    Box(Modifier.size(6.dp).clip(Formas.Redonda).background(color))
                    Text(texto, fontSize = 11.sp, color = if (pedal is EstadoPedal.Conectado) Color(0xFFA5B399) else color)
                }
            }
            BotonIcono(if (a.inversion == ModoInversion.NINGUNA) TipoIcono.LUNA else TipoIcono.SOL, "Foso oscuro", alNoche, habilitado = !ui.bloqueado)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3D4234)))
    }
}

@Composable
private fun AvisoGiro(ui: EstadoLectorUi, c: Contenedor) {
    val hasta = ui.avisoGiroHastaMs ?: return
    val ahora by produceState(c.reloj.ahoraMs(), hasta) { while (true) { value = c.reloj.ahoraMs(); delay(60) } }
    val restante = (hasta - ahora).coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().height(34.dp).background(Color(0xE6191B17))) {
        val total = 2500f
        Box(Modifier.fillMaxHeight().fillMaxWidth((restante / total).coerceIn(0f, 1f)).background(Paleta.Oxido))
        Text(
            (if (ui.avisoGiroEsFin) "FIN DE LA PIEZA en " else "GIRO en ") + "%.1f s".format(restante / 1000.0),
            Modifier.align(Alignment.Center), color = Paleta.TextoSobreOxido, style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun PanelError(ui: EstadoLectorUi, sesion: SesionLectura) {
    Box(Modifier.fillMaxSize().background(Paleta.Carbon), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp).border(1.dp, Paleta.Alerta, Formas.Grande).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No se puede mostrar esta partitura", fontFamily = Serifa, fontSize = 24.sp, color = Paleta.Alerta)
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

/** Barra inferior del lector: navegación a la izquierda; herramientas, bloqueo y ocultar a la derecha. */
@Composable
private fun BarraInferior(
    ui: EstadoLectorUi, a: AjustesApp, panelAbierto: Boolean,
    alHerramientas: () -> Unit, alBloquear: () -> Unit, alOcultar: () -> Unit, alMover: (ComandoNavegacion) -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3B4034)))
        Row(
            Modifier.fillMaxWidth().background(Color(0xF2191B17)).padding(horizontal = 26.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BotonContorno("Anterior", { alMover(ComandoNavegacion.Anterior) }, habilitado = ui.indiceVista > 0, icono = TipoIcono.ATRAS)
            Text(
                "${ui.indiceVista + 1} / ${ui.totalVistas}", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
            BotonContorno("Siguiente", { alMover(ComandoNavegacion.Siguiente) }, habilitado = ui.indiceVista < ui.totalVistas - 1 || ui.avisoSiguiente != null)
            Spacer(Modifier.weight(1f))
            Opcion("Herramientas", panelAbierto, alHerramientas)
            if (a.modoConcierto) BotonContorno("Bloquear", alBloquear, icono = TipoIcono.CANDADO)
            BotonIcono(TipoIcono.OJO, "Ocultar controles", alOcultar)
        }
    }
}

// ---------------------------------------------------------------- Panel de herramientas

@Composable
private fun PanelHerramientas(
    c: Contenedor, sesion: SesionLectura, ui: EstadoLectorUi, a: AjustesApp, grab: EstadoGrabacion,
    pestana: Pestana, alPestana: (Pestana) -> Unit, alCerrar: () -> Unit,
    herramienta: Herramienta, alHerramienta: (Herramienta) -> Unit,
    colorTinta: Int, alColor: (Int) -> Unit, borrador: Boolean, alBorrador: (Boolean) -> Unit,
    abrirDialogo: (Dialogo) -> Unit, ventana: ControlVentana, modifier: Modifier,
) {
    val disponibles = buildList {
        if (a.avanceTempo) add(Pestana.TEMPO)
        if (a.anotaciones) add(Pestana.TINTA)
        add(Pestana.RECORRIDO)
        if (a.metronomo || a.grabacion) add(Pestana.AUDIO)
    }
    val activa = if (pestana in disponibles) pestana else disponibles.first()

    Row(modifier.fillMaxHeight().width(290.dp)) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(Paleta.Linea))
        Column(Modifier.fillMaxHeight().weight(1f).background(Color(0xFF1D2019)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Herramientas", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                BotonIcono(TipoIcono.CERRAR, "Cerrar herramientas", alCerrar)
            }
            Row(Modifier.padding(top = 12.dp, bottom = 22.dp).fillMaxWidth().horizontalScrollSimple()) {
                for (p in disponibles) {
                    val sel = p == activa
                    Column(Modifier.clickable { alPestana(p) }.padding(horizontal = 8.dp)) {
                        Text(p.titulo, fontSize = 12.sp, color = if (sel) Paleta.OxidoClaro else Paleta.TextoSuave, modifier = Modifier.padding(vertical = 12.dp))
                        Box(Modifier.height(2.dp).fillMaxWidth().background(if (sel) Paleta.Oxido else Color.Transparent))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (activa) {
                    Pestana.TEMPO -> ContenidoTempo(sesion, ui, abrirDialogo)
                    Pestana.TINTA -> ContenidoTinta(sesion, ui, herramienta, alHerramienta, colorTinta, alColor, borrador, alBorrador)
                    Pestana.RECORRIDO -> ContenidoRecorrido(sesion, ui, a, abrirDialogo)
                    Pestana.AUDIO -> ContenidoAudio(c, sesion, ui, a, grab, ventana)
                }
            }
        }
    }
}

@Composable
private fun ContenidoTempo(sesion: SesionLectura, ui: EstadoLectorUi, abrirDialogo: (Dialogo) -> Unit) {
    val cfg by sesion.configTempo.collectAsState()
    val t = ui.tempo
    if (!ui.tempoDisponible || t == null) {
        Text("El avance por tempo no está activo en esta canción.", fontSize = 12.sp, color = Paleta.TextoSuave, lineHeight = 18.sp)
        BotonOxido("Configurar…", { abrirDialogo(Dialogo.ConfigTempo) })
        return
    }
    val enMarcha = t.estado == EstadoTempo.CORRIENDO || t.estado == EstadoTempo.CUENTA_ATRAS
    Text("PULSO DE ENSAYO", style = MaterialTheme.typography.labelSmall, color = Paleta.TextoSuave)
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%.0f".format(if (t.bpmEfectivo > 0) t.bpmEfectivo else (cfg?.bpm ?: 0.0)), fontSize = 66.sp, fontWeight = FontWeight.Light)
        Text("BPM  ·  ×%.2f".format(t.velocidad), fontSize = 11.sp, letterSpacing = 3.sp, color = Paleta.TextoSuave)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BotonIcono(TipoIcono.MENOS, "Más lento", { sesion.ejecutar(Accion.TEMPO_MAS_LENTO, null) })
        BotonOxido(if (enMarcha) "Pausar" else if (t.estado == EstadoTempo.PAUSADO) "Reanudar" else "Iniciar", { sesion.alternarTempo() }, icono = if (enMarcha) TipoIcono.PAUSA else TipoIcono.PLAY)
        BotonIcono(TipoIcono.MAS, "Más rápido", { sesion.ejecutar(Accion.TEMPO_MAS_RAPIDO, null) })
    }
    PuntosDePulso(enMarcha, t.bpmEfectivo)
    Text(
        when (t.estado) {
            EstadoTempo.DETENIDO -> "Empezará desde la vista actual."
            EstadoTempo.CUENTA_ATRAS -> "Cuenta de entrada… ${"%.1f".format((t.msHastaInicio ?: 0) / 1000.0)} s"
            EstadoTempo.CORRIENDO -> "En marcha. Avisa antes de cada giro."
            EstadoTempo.PAUSADO -> "En pausa."
            EstadoTempo.FINALIZADO -> "Pieza terminada."
        },
        fontSize = 12.sp, color = Paleta.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    BotonContorno("Estoy aquí (resincronizar)", { sesion.ejecutar(Accion.TEMPO_RESINCRONIZAR, null) }, Modifier.fillMaxWidth())
    BotonContorno("Configurar…", { abrirDialogo(Dialogo.ConfigTempo) }, Modifier.fillMaxWidth())
}

@Composable
private fun PuntosDePulso(enMarcha: Boolean, bpm: Double) {
    val transicion = rememberInfiniteTransition(label = "pulso")
    val fase by transicion.animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween((240_000.0 / bpm.coerceAtLeast(20.0)).toInt().coerceIn(500, 12_000), easing = LinearEasing), RepeatMode.Restart),
        label = "fase",
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(17.dp, Alignment.CenterHorizontally)) {
        for (i in 0 until 4) {
            Box(Modifier.size(8.dp).clip(Formas.Redonda).background(if (enMarcha && fase.toInt() == i) Paleta.Oxido else Color(0xFF565F48)))
        }
    }
}

@Composable
private fun ContenidoTinta(
    sesion: SesionLectura, ui: EstadoLectorUi,
    herramienta: Herramienta, alHerramienta: (Herramienta) -> Unit,
    colorTinta: Int, alColor: (Int) -> Unit, borrador: Boolean, alBorrador: (Boolean) -> Unit,
) {
    val dibujando = ui.modoEdicion == ModoEdicion.DIBUJO
    Opcion(if (dibujando) "Dibujo activado" else "Activar dibujo", dibujando, { sesion.fijarModoEdicion(ModoEdicion.DIBUJO) }, Modifier.fillMaxWidth())
    Text("HERRAMIENTA", style = MaterialTheme.typography.labelSmall, color = Paleta.TextoSuave, modifier = Modifier.padding(top = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Opcion("Lápiz", !borrador && herramienta == Herramienta.LAPIZ, { alHerramienta(Herramienta.LAPIZ); alBorrador(false) })
        Opcion("Resaltador", !borrador && herramienta == Herramienta.RESALTADOR, { alHerramienta(Herramienta.RESALTADOR); alBorrador(false) })
        Opcion("Borrador", borrador, { alBorrador(!borrador) })
    }
    Text("COLOR DE TINTA", style = MaterialTheme.typography.labelSmall, color = Paleta.TextoSuave, modifier = Modifier.padding(top = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollSimple()) {
        for (col in SesionLectura.COLORES_TINTA) {
            val sel = col == colorTinta && !borrador
            Box(
                Modifier.size(38.dp).clip(Formas.Redonda).background(Color(col)).border(if (sel) 3.dp else 1.dp, if (sel) Paleta.OxidoClaro else Paleta.Linea, Formas.Redonda)
                    .clickable { alColor(col); alBorrador(false) },
                contentAlignment = Alignment.Center,
            ) { if (sel) Icono(TipoIcono.CHECK, color = Color.White, tamano = 16.dp) }
        }
    }
    BotonContorno("Deshacer último trazo", { sesion.deshacerTrazo() }, Modifier.fillMaxWidth().padding(top = 10.dp))
    Text(
        "Con el lápiz se dibuja; el dedo mueve y hace zoom (o dibuja también si lo activas en Ajustes). La tinta se guarda en la partitura y no se invierte en el foso oscuro.",
        fontSize = 11.sp, color = Paleta.TextoSuave, lineHeight = 17.sp,
    )
}

@Composable
private fun ContenidoRecorrido(sesion: SesionLectura, ui: EstadoLectorUi, a: AjustesApp, abrirDialogo: (Dialogo) -> Unit) {
    if (a.puntosGiro) {
        Opcion(if (ui.modoEdicion == ModoEdicion.GIROS) "Marcando giros: toca la partitura" else "Marcar puntos de giro", ui.modoEdicion == ModoEdicion.GIROS, { sesion.fijarModoEdicion(ModoEdicion.GIROS) }, Modifier.fillMaxWidth())
        BotonContorno("Ver puntos de giro…", { abrirDialogo(Dialogo.Giros) }, Modifier.fillMaxWidth())
    }
    if (a.marcadores) {
        Opcion(if (ui.modoEdicion == ModoEdicion.MARCADORES) "Marcando secciones: toca la partitura" else "Marcar secciones de ensayo", ui.modoEdicion == ModoEdicion.MARCADORES, { sesion.fijarModoEdicion(ModoEdicion.MARCADORES) }, Modifier.fillMaxWidth())
        BotonContorno("Ir a un marcador…", { abrirDialogo(Dialogo.Marcadores) }, Modifier.fillMaxWidth())
    }
    if (a.notas) BotonContorno("Notas de la canción…", { abrirDialogo(Dialogo.Notas) }, Modifier.fillMaxWidth())
    HorizontalDivider(color = Paleta.Linea, modifier = Modifier.padding(vertical = 6.dp))
    BotonContorno("Colores y ajuste de página…", { abrirDialogo(Dialogo.Menu) }, Modifier.fillMaxWidth())
}

@Composable
private fun ContenidoAudio(c: Contenedor, sesion: SesionLectura, ui: EstadoLectorUi, a: AjustesApp, grab: EstadoGrabacion, ventana: ControlVentana) {
    if (a.metronomo) {
        Text("METRÓNOMO", style = MaterialTheme.typography.labelSmall, color = Paleta.TextoSuave)
        Opcion(if (ui.metronomoActivo) "Metrónomo encendido" else "Encender metrónomo", ui.metronomoActivo, { sesion.alternarMetronomo() }, Modifier.fillMaxWidth())
        Text("Sigue el tempo de la canción. Si grabas por el altavoz se grabará también: usa auriculares.", fontSize = 11.sp, color = Paleta.TextoSuave, lineHeight = 17.sp)
    }
    if (a.grabacion) {
        Text("GRABACIÓN", style = MaterialTheme.typography.labelSmall, color = Paleta.TextoSuave, modifier = Modifier.padding(top = 10.dp))
        Opcion(
            if (grab.activa) "Detener grabación" else "Grabar",
            grab.activa,
            {
                if (grab.activa || c.grabacion.tienePermiso()) sesion.alternarGrabacion()
                else ventana.pedirMicrofono { ok -> if (ok) sesion.alternarGrabacion() else sesion.mostrarMensaje("Sin permiso de micrófono") }
            },
            Modifier.fillMaxWidth(),
        )
        Text("Se guarda en la app, con una marca en cada giro de página. La escuchas en Grabaciones.", fontSize = 11.sp, color = Paleta.TextoSuave, lineHeight = 17.sp)
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
        shape = Formas.Pequena,
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
