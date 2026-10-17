package com.suiteg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suiteg.app.Contenedor
import com.suiteg.app.pedal.EstadoPedal
import com.suiteg.app.pedal.nombreTecla
import com.suiteg.navegacion.entrada.Accion
import com.suiteg.navegacion.entrada.ModoAprendizaje
import kotlinx.coroutines.delay

/**
 * Asignación de teclas del pedal con el modo de aprendizaje: se elige una acción, se pisa el pedal y la app
 * guarda la tecla recibida. No hay ningún keycode de fabricante en el código.
 */
@Composable
fun PantallaPedal(c: Contenedor, alAtras: () -> Unit) {
    val mapa by c.mapaTeclas.collectAsState()
    val estado by c.monitorPedal.estado.collectAsState()
    val dispositivos by c.monitorPedal.dispositivos.collectAsState()
    var aprendiendo by remember { mutableStateOf<Accion?>(null) }
    var rechazo by remember { mutableStateOf<String?>(null) }
    val aprendizaje = c.procesadorPedal.aprendizaje

    // Mientras se aprende, se comprueba cada 40 ms si la ventana de captura ya cerró.
    LaunchedEffect(aprendiendo) {
        if (aprendiendo == null) return@LaunchedEffect
        while (aprendiendo != null) {
            val r = aprendizaje.tomarResultado()
            if (r != null) {
                c.guardarMapa(c.mapaTeclas.value.asignar(r.accion, r.teclas))
                aprendiendo = null
                rechazo = null
                break
            }
            rechazo = (aprendizaje.estado as? ModoAprendizaje.Estado.Esperando)?.rechazo
            delay(40)
        }
    }
    DisposableEffect(Unit) { onDispose { aprendizaje.cancelar() } }

    Column(Modifier.fillMaxSize()) {
        Cabecera("Pedal", { aprendizaje.cancelar(); alAtras() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Seccion("Estado")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (val e = estado) {
                        EstadoPedal.SinConfigurar -> "● Sin configurar: asigna una tecla a una acción."
                        EstadoPedal.Desconectado -> "○ Desconectado. Enciende el pedal o conéctalo por USB-OTG; se reconoce solo."
                        is EstadoPedal.Conectado -> "● Conectado: ${e.nombres.joinToString()}"
                    },
                    color = if (estado is EstadoPedal.Conectado) Paleta.Conectado else Paleta.Alerta,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Dispositivos de teclado detectados: " + (dispositivos.joinToString { it.nombre }.ifEmpty { "ninguno" }), color = Paleta.TextoSuave)
                BotonContorno("Buscar de nuevo", { c.monitorPedal.refrescar() })
                Text(
                    "Funciona con pedales Bluetooth (emparejados en los ajustes de Android) y con cable USB-OTG. " +
                        "Cada pisada se consume: Android no cambiará el volumen ni hará nada con esas teclas.",
                    color = Paleta.TextoSuave, style = MaterialTheme.typography.bodySmall,
                )
            }

            Seccion("Acciones")
            for (accion in Accion.entries) {
                val teclas = mapa.teclasDe(accion)
                val esta = aprendiendo == accion
                Row(
                    Modifier.fillMaxWidth().then(if (esta) Modifier.background(Paleta.CarbonMedio).border(1.dp, Paleta.Oxido) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(accion.etiqueta, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                esta -> "Pisa el pedal ahora…" + (rechazo?.let { "  $it" } ?: "")
                                teclas.isEmpty() -> "Sin asignar"
                                else -> teclas.joinToString { nombreTecla(it.keyCode) }
                            },
                            color = if (esta) Paleta.OxidoClaro else Paleta.TextoSuave, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (esta) {
                        BotonContorno("Cancelar", { aprendizaje.cancelar(); aprendiendo = null })
                    } else {
                        BotonOxido(if (teclas.isEmpty()) "Asignar" else "Cambiar", { aprendizaje.iniciar(accion); aprendiendo = accion })
                        if (teclas.isNotEmpty()) {
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp))
                            BotonContorno("Quitar", { c.guardarMapa(c.mapaTeclas.value.quitar(accion)) })
                        }
                    }
                }
                HorizontalDivider(color = Paleta.Linea)
            }

            Seccion("Prueba")
            ProbarPedal(c)
        }
    }
}

/** Muestra la última acción detectada y la latencia medida, para comprobar el pedal sin abrir una partitura. */
@Composable
private fun ProbarPedal(c: Contenedor) {
    val observada by c.accionObservada.collectAsState()
    var r by remember { mutableStateOf(c.medidorLatencia.resumen()) }
    LaunchedEffect(Unit) {
        while (true) {
            r = c.medidorLatencia.resumen()
            delay(1000)
        }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pisa el pedal: la acción reconocida aparece aquí.", color = Paleta.TextoSuave)
        Text(
            observada?.let { "Última acción: ${it.accion.etiqueta}  (#${it.n})" } ?: "Última acción: —",
            style = MaterialTheme.typography.titleMedium, color = Paleta.OxidoClaro,
        )
        Text(
            if (r.muestras == 0) "Latencia pedal → pantalla: sin medidas todavía (se miden en el lector)."
            else "Latencia pedal → pantalla (${r.muestras} giros): p50 %.0f ms · p95 %.0f ms · p99 %.0f ms".format(r.p50, r.p95, r.p99),
        )
        BotonContorno("Reiniciar medidas", { c.medidorLatencia.limpiar() })
    }
}
