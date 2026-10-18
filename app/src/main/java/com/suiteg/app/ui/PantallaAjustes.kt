package com.suiteg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suiteg.app.Contenedor
import com.suiteg.app.SuiteGApp
import com.suiteg.modelo.AjusteVista
import com.suiteg.modelo.AjustesApp
import com.suiteg.modelo.ModoInversion
import com.suiteg.modelo.Orientacion
import com.suiteg.modelo.PerfilUso
import com.suiteg.modelo.Perfiles
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PantallaAjustes(c: Contenedor, alPedal: () -> Unit) {
    val a by c.ajustes.collectAsState()
    val pedal by c.monitorPedal.estado.collectAsState()

    /** Cualquier cambio manual pasa el perfil a "Personalizado". */
    fun cambiar(f: (AjustesApp) -> AjustesApp) = c.guardarAjustes(f(a).copy(perfil = PerfilUso.PERSONALIZADO))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 38.dp, end = 38.dp, top = 37.dp, bottom = 32.dp)) {
        Column(Modifier.widthIn(max = 760.dp)) {
            EncabezadoPagina("Todo listo para tocar", "Ajustes", "Un espacio que se adapta a tu forma de tocar.")

            Text("Tu forma de tocar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaPerfil("Ensayo", "Herramientas a mano", a.perfil == PerfilUso.ENSAYO, Modifier.weight(1f)) { c.guardarAjustes(Perfiles.aplicar(PerfilUso.ENSAYO, a)) }
                TarjetaPerfil("Concierto", "Solo tú y la partitura", a.perfil == PerfilUso.CONCIERTO, Modifier.weight(1f)) { c.guardarAjustes(Perfiles.aplicar(PerfilUso.CONCIERTO, a)) }
                TarjetaPerfil("Foso oscuro", "Menos luz, más enfoque", a.perfil == PerfilUso.FOSO_OSCURO, Modifier.weight(1f)) { c.guardarAjustes(Perfiles.aplicar(PerfilUso.FOSO_OSCURO, a)) }
            }
            if (a.perfil == PerfilUso.PERSONALIZADO) Text("Perfil personalizado: has cambiado ajustes a mano.", fontSize = 11.sp, color = Paleta.TextoSuave, modifier = Modifier.padding(top = 10.dp))

            TituloGrupo("Pedal")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (val p = pedal) {
                        com.suiteg.app.pedal.EstadoPedal.SinConfigurar -> "Sin configurar"
                        com.suiteg.app.pedal.EstadoPedal.Desconectado -> "Configurado, pero desconectado"
                        is com.suiteg.app.pedal.EstadoPedal.Conectado -> "Conectado: ${p.nombres.joinToString()}"
                    },
                )
                BotonOxido("Asignar teclas del pedal", alPedal)
            }
            Etiqueta("Tiempo mínimo entre giros: ${a.cooldownMs} ms")
            DeslizadorEntero(a.cooldownMs, 0..1000, 50) { v -> cambiar { it.copy(cooldownMs = v) } }
            FilaAjuste("Retroceder cruza a la canción anterior", "Con «Anterior» en la primera vista, abre la canción previa en su última vista.", a.cruzarAlRetroceder) { v -> cambiar { it.copy(cruzarAlRetroceder = v) } }

            TituloGrupo("Funciones")
            FilaAjuste("Puntos de giro", "El pedal avanza entre los puntos que marques en lugar de página a página.", a.puntosGiro) { v -> cambiar { it.copy(puntosGiro = v) } }
            FilaAjuste("Marcadores de ensayo", "Saltar a una sección con nombre y repetirla.", a.marcadores) { v -> cambiar { it.copy(marcadores = v) } }
            FilaAjuste("Setlists", "Agrupar partituras y pasar a la siguiente con el pedal.", a.setlists) { v -> cambiar { it.copy(setlists = v) } }
            FilaAjuste("Notas por canción", null, a.notas) { v -> cambiar { it.copy(notas = v) } }
            FilaAjuste("Anotaciones", "Dibujar sobre la partitura con lápiz óptico.", a.anotaciones) { v -> cambiar { it.copy(anotaciones = v) } }
            FilaAjuste("Modo concierto", "Bloqueo táctil con desbloqueo deliberado.", a.modoConcierto) { v -> cambiar { it.copy(modoConcierto = v) } }
            FilaAjuste("Avance automático por tempo", "Opcional. Se activa además por canción o por setlist.", a.avanceTempo) { v -> cambiar { it.copy(avanceTempo = v) } }
            FilaAjuste("Metrónomo", null, a.metronomo) { v -> cambiar { it.copy(metronomo = v) } }
            FilaAjuste("Grabación de audio", "Para escucharte después. Pide permiso de micrófono al usarla.", a.grabacion) { v -> cambiar { it.copy(grabacion = v) } }

            TituloGrupo("Visualización")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Inversión de colores (foso oscuro)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Opcion("Ninguna", a.inversion == ModoInversion.NINGUNA, { cambiar { it.copy(inversion = ModoInversion.NINGUNA) } })
                    Opcion("Invertir todo", a.inversion == ModoInversion.TODO, { cambiar { it.copy(inversion = ModoInversion.TODO) } })
                    Opcion("Conservar tonalidad", a.inversion == ModoInversion.TONALIDAD, { cambiar { it.copy(inversion = ModoInversion.TONALIDAD) } })
                    Opcion("Solo fondo (bajo contraste)", a.inversion == ModoInversion.FONDO, { cambiar { it.copy(inversion = ModoInversion.FONDO) } })
                }
                Text("Las anotaciones se dibujan aparte y no se invierten.", color = Paleta.TextoSuave)
                Text("Ajuste de la página")
                Selector(listOf(AjusteVista.PAGINA_COMPLETA to "Página completa", AjusteVista.ANCHO to "Ajustar al ancho"), a.ajusteVista) { v -> cambiar { it.copy(ajusteVista = v) } }
                Text("Orientación (bloqueada)")
                Selector(listOf(Orientacion.ACTUAL to "La actual", Orientacion.PAISAJE to "Horizontal", Orientacion.RETRATO to "Vertical"), a.orientacion) { v -> cambiar { it.copy(orientacion = v) } }
            }
            FilaAjuste("Mantener la pantalla encendida", null, a.pantallaEncendida) { v -> cambiar { it.copy(pantallaEncendida = v) } }
            FilaAjuste("Zoom y desplazamiento táctil", "Se desactivan solos en modo concierto.", a.zoomTactil) { v -> cambiar { it.copy(zoomTactil = v) } }
            FilaAjuste("Mostrar latencia del pedal", "Muestra p50/p95/p99 del pedal al fotograma.", a.mostrarLatencia) { v -> cambiar { it.copy(mostrarLatencia = v) } }

            if (a.anotaciones) {
                TituloGrupo("Anotaciones")
                FilaAjuste("Dibujar también con el dedo", "Por defecto solo dibuja el lápiz y el dedo hace zoom y desplazamiento.", a.dedoDibuja) { v -> cambiar { it.copy(dedoDibuja = v) } }
                FilaAjuste("Rechazo de palma", null, a.rechazoPalma) { v -> cambiar { it.copy(rechazoPalma = v) } }
            }

            if (a.modoConcierto) {
                TituloGrupo("Modo concierto")
                FilaAjuste("Bloquear al abrir una partitura", null, a.bloqueoAlAbrir) { v -> cambiar { it.copy(bloqueoAlAbrir = v) } }
                FilaAjuste("Bloqueo máximo (fijar pantalla)", "Usa la fijación de pantalla de Android: bloquea también Inicio y Recientes. El sistema mostrará su propio aviso.", a.bloqueoMaximo) { v -> cambiar { it.copy(bloqueoMaximo = v) } }
                Etiqueta("Mantener pulsada la esquina inferior derecha ${a.desbloqueoMs / 1000.0} s para desbloquear")
                DeslizadorEntero(a.desbloqueoMs, 1000..8000, 500) { v -> cambiar { it.copy(desbloqueoMs = v) } }
            }

            if (a.avanceTempo) {
                TituloGrupo("Avance por tempo")
                Etiqueta("Aviso antes de cada giro: ${a.avisoGiroMs / 1000.0} s")
                DeslizadorEntero(a.avisoGiroMs, 500..6000, 250) { v -> cambiar { it.copy(avisoGiroMs = v) } }
                Etiqueta("Paso de corrección con el pedal: ${a.pasoVelocidadPct} %")
                DeslizadorEntero(a.pasoVelocidadPct, 1..15, 1) { v -> cambiar { it.copy(pasoVelocidadPct = v) } }
            }
            if (a.metronomo) {
                TituloGrupo("Metrónomo")
                Etiqueta("Volumen: ${(a.volumenMetronomo * 100).toInt()} %")
                Slider(
                    value = a.volumenMetronomo, onValueChange = { v -> cambiar { it.copy(volumenMetronomo = v) } },
                    modifier = Modifier, colors = coloresDeslizador(),
                )
            }
            if (a.grabacion) {
                TituloGrupo("Grabación")
                FilaAjuste("Anotar los giros de página durante la grabación", "Permite saltar a cada página al escuchar.", a.grabarMarcasGiro) { v -> cambiar { it.copy(grabarMarcasGiro = v) } }
            }

            TituloGrupo("Diagnóstico")
            val ruta = remember { File(c.app.filesDir, SuiteGApp.ARCHIVO_FALLOS) }
            Etiqueta(if (ruta.exists() && ruta.length() > 0) "Hay un registro de errores en: ${ruta.absolutePath}" else "Sin errores registrados.")
        }
    }
}

@Composable
private fun Etiqueta(texto: String) = Text(texto, Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Paleta.TextoSuave)

@Composable
private fun coloresDeslizador() = SliderDefaults.colors(
    thumbColor = Paleta.OxidoClaro, activeTrackColor = Paleta.Oxido, inactiveTrackColor = Paleta.Linea,
)

/** Deslizador de enteros que avanza de [paso] en [paso] dentro de [rango]. */
@Composable
private fun DeslizadorEntero(valor: Int, rango: IntRange, paso: Int, alCambiar: (Int) -> Unit) {
    Slider(
        value = valor.toFloat(),
        onValueChange = { alCambiar(((it / paso).roundToInt() * paso).coerceIn(rango.first, rango.last)) },
        valueRange = rango.first.toFloat()..rango.last.toFloat(),
        steps = ((rango.last - rango.first) / paso - 1).coerceAtLeast(0),
        modifier = Modifier,
        colors = coloresDeslizador(),
    )
}

@Composable
private fun TarjetaPerfil(nombre: String, descripcion: String, elegido: Boolean, modifier: Modifier, alPulsar: () -> Unit) {
    Tarjeta(modifier, seleccionada = elegido, alPulsar = alPulsar) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(nombre, Modifier.weight(1f), fontSize = 14.sp, color = if (elegido) Paleta.SeleccionTexto else Paleta.Texto)
                if (elegido) Icono(TipoIcono.CHECK, color = Paleta.SeleccionTexto, tamano = 14.dp)
            }
            Text(descripcion, fontSize = 10.sp, color = Paleta.TextoSuave)
        }
    }
}
