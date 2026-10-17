package com.suiteg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
fun PantallaAjustes(c: Contenedor, alAtras: () -> Unit, alPedal: () -> Unit) {
    val a by c.ajustes.collectAsState()
    val pedal by c.monitorPedal.estado.collectAsState()

    /** Cualquier cambio manual pasa el perfil a "Personalizado". */
    fun cambiar(f: (AjustesApp) -> AjustesApp) = c.guardarAjustes(f(a).copy(perfil = PerfilUso.PERSONALIZADO))

    Column(Modifier.fillMaxSize()) {
        Cabecera("Ajustes", alAtras)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {

            Seccion("Perfil de uso")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Opcion("Ensayo", a.perfil == PerfilUso.ENSAYO, { c.guardarAjustes(Perfiles.aplicar(PerfilUso.ENSAYO, a)) })
                    Opcion("Concierto", a.perfil == PerfilUso.CONCIERTO, { c.guardarAjustes(Perfiles.aplicar(PerfilUso.CONCIERTO, a)) })
                    Opcion("Foso oscuro", a.perfil == PerfilUso.FOSO_OSCURO, { c.guardarAjustes(Perfiles.aplicar(PerfilUso.FOSO_OSCURO, a)) })
                    if (a.perfil == PerfilUso.PERSONALIZADO) Opcion("Personalizado", true, {})
                }
                Text(
                    when (a.perfil) {
                        PerfilUso.ENSAYO -> "Todo disponible: marcadores, anotaciones, tempo, metrónomo y grabación."
                        PerfilUso.CONCIERTO -> "Solo lo imprescindible, bloqueo táctil al abrir y pedal con antirrebote más alto."
                        PerfilUso.FOSO_OSCURO -> "Como concierto, con la partitura invertida para no deslumbrar."
                        PerfilUso.PERSONALIZADO -> "Combinación propia."
                    },
                    color = Paleta.TextoSuave,
                )
            }

            Seccion("Pedal")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Seccion("Funciones")
            FilaAjuste("Puntos de giro", "El pedal avanza entre los puntos que marques en lugar de página a página.", a.puntosGiro) { v -> cambiar { it.copy(puntosGiro = v) } }
            FilaAjuste("Marcadores de ensayo", "Saltar a una sección con nombre y repetirla.", a.marcadores) { v -> cambiar { it.copy(marcadores = v) } }
            FilaAjuste("Setlists", "Agrupar partituras y pasar a la siguiente con el pedal.", a.setlists) { v -> cambiar { it.copy(setlists = v) } }
            FilaAjuste("Notas por canción", null, a.notas) { v -> cambiar { it.copy(notas = v) } }
            FilaAjuste("Anotaciones", "Dibujar sobre la partitura con lápiz óptico.", a.anotaciones) { v -> cambiar { it.copy(anotaciones = v) } }
            FilaAjuste("Modo concierto", "Bloqueo táctil con desbloqueo deliberado.", a.modoConcierto) { v -> cambiar { it.copy(modoConcierto = v) } }
            FilaAjuste("Avance automático por tempo", "Opcional. Se activa además por canción o por setlist.", a.avanceTempo) { v -> cambiar { it.copy(avanceTempo = v) } }
            FilaAjuste("Metrónomo", null, a.metronomo) { v -> cambiar { it.copy(metronomo = v) } }
            FilaAjuste("Grabación de audio", "Para escucharte después. Pide permiso de micrófono al usarla.", a.grabacion) { v -> cambiar { it.copy(grabacion = v) } }

            Seccion("Visualización")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Seccion("Anotaciones")
                FilaAjuste("Dibujar también con el dedo", "Por defecto solo dibuja el lápiz y el dedo hace zoom y desplazamiento.", a.dedoDibuja) { v -> cambiar { it.copy(dedoDibuja = v) } }
                FilaAjuste("Rechazo de palma", null, a.rechazoPalma) { v -> cambiar { it.copy(rechazoPalma = v) } }
            }

            if (a.modoConcierto) {
                Seccion("Modo concierto")
                FilaAjuste("Bloquear al abrir una partitura", null, a.bloqueoAlAbrir) { v -> cambiar { it.copy(bloqueoAlAbrir = v) } }
                FilaAjuste("Bloqueo máximo (fijar pantalla)", "Usa la fijación de pantalla de Android: bloquea también Inicio y Recientes. El sistema mostrará su propio aviso.", a.bloqueoMaximo) { v -> cambiar { it.copy(bloqueoMaximo = v) } }
                Etiqueta("Mantener pulsada la esquina inferior derecha ${a.desbloqueoMs / 1000.0} s para desbloquear")
                DeslizadorEntero(a.desbloqueoMs, 1000..8000, 500) { v -> cambiar { it.copy(desbloqueoMs = v) } }
            }

            if (a.avanceTempo) {
                Seccion("Avance por tempo")
                Etiqueta("Aviso antes de cada giro: ${a.avisoGiroMs / 1000.0} s")
                DeslizadorEntero(a.avisoGiroMs, 500..6000, 250) { v -> cambiar { it.copy(avisoGiroMs = v) } }
                Etiqueta("Paso de corrección con el pedal: ${a.pasoVelocidadPct} %")
                DeslizadorEntero(a.pasoVelocidadPct, 1..15, 1) { v -> cambiar { it.copy(pasoVelocidadPct = v) } }
            }
            if (a.metronomo) {
                Seccion("Metrónomo")
                Etiqueta("Volumen: ${(a.volumenMetronomo * 100).toInt()} %")
                Slider(
                    value = a.volumenMetronomo, onValueChange = { v -> cambiar { it.copy(volumenMetronomo = v) } },
                    modifier = Modifier.padding(horizontal = 16.dp), colors = coloresDeslizador(),
                )
            }
            if (a.grabacion) {
                Seccion("Grabación")
                FilaAjuste("Anotar los giros de página durante la grabación", "Permite saltar a cada página al escuchar.", a.grabarMarcasGiro) { v -> cambiar { it.copy(grabarMarcasGiro = v) } }
            }

            Seccion("Diagnóstico")
            val ruta = remember { File(c.app.filesDir, SuiteGApp.ARCHIVO_FALLOS) }
            Etiqueta(if (ruta.exists() && ruta.length() > 0) "Hay un registro de errores en: ${ruta.absolutePath}" else "Sin errores registrados.")
        }
    }
}

@Composable
private fun Etiqueta(texto: String) = Text(texto, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), color = Paleta.TextoSuave)

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
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = coloresDeslizador(),
    )
}
