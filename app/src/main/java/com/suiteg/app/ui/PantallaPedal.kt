package com.suiteg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
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
fun PantallaPedal(c: Contenedor) {
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 38.dp, end = 38.dp, top = 37.dp, bottom = 24.dp)) {
        item {
            Column(Modifier.widthIn(max = 760.dp)) {
                EncabezadoPagina("Todo listo para tocar", "Pedal", "Las manos en el instrumento. El control a tus pies.")
                Tarjeta(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icono(TipoIcono.BLUETOOTH, color = Paleta.OxidoClaro, tamano = 30.dp)
                        Text(
                            when (estado) {
                                is EstadoPedal.Conectado -> "Listo para el siguiente compás"
                                EstadoPedal.Desconectado -> "Pedal desconectado"
                                EstadoPedal.SinConfigurar -> "Asigna tu pedal"
                            },
                            fontFamily = Serifa, fontSize = 25.sp,
                        )
                        Text(
                            when (val e = estado) {
                                is EstadoPedal.Conectado -> e.nombres.joinToString()
                                EstadoPedal.Desconectado -> "Enciende el pedal o conéctalo por USB-OTG: se reconoce solo."
                                EstadoPedal.SinConfigurar -> "Elige una acción abajo y pisa el pedal para enseñarle la tecla."
                            },
                            fontSize = 12.sp, color = Paleta.TextoSuave,
                        )
                        Text(
                            "Teclados detectados: " + dispositivos.joinToString { it.nombre }.ifEmpty { "ninguno" },
                            fontSize = 11.sp, color = Paleta.TextoSuave,
                        )
                        Row { BotonContorno("Buscar de nuevo", { c.monitorPedal.refrescar() }, icono = TipoIcono.BLUETOOTH) }
                    }
                }
                Text("Asignación de controles", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
            }
        }
        items(Accion.entries.toList()) { accion ->
            val teclas = mapa.teclasDe(accion)
            val esta = aprendiendo == accion
            Column(Modifier.widthIn(max = 760.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(accion.etiqueta, fontSize = 14.sp)
                        Text(
                            when {
                                esta -> "Pisa el pedal ahora…" + (rechazo?.let { "  $it" } ?: "")
                                teclas.isEmpty() -> "Sin asignar"
                                else -> teclas.joinToString { nombreTecla(it.keyCode) }
                            },
                            color = if (esta) Paleta.OxidoClaro else Paleta.TextoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (esta) {
                        BotonContorno("Cancelar", { aprendizaje.cancelar(); aprendiendo = null })
                    } else {
                        if (teclas.isNotEmpty()) BotonTexto("Quitar", { c.guardarMapa(c.mapaTeclas.value.quitar(accion)) })
                        if (teclas.isEmpty()) BotonOxido("Asignar", { aprendizaje.iniciar(accion); aprendiendo = accion })
                        else BotonContorno("Cambiar", { aprendizaje.iniciar(accion); aprendiendo = accion }, icono = TipoIcono.CHECK)
                    }
                }
                HorizontalDivider(color = Paleta.Linea)
            }
        }
        item {
            Column(Modifier.widthIn(max = 760.dp)) {
                Text("Prueba", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
                ProbarPedal(c)
                Spacer(Modifier.padding(8.dp))
                Franja("Funciona con pedales Bluetooth (emparejados antes en los ajustes de Android) y con cable USB-OTG. Cada pisada se consume: Android no cambiará el volumen ni hará nada con esas teclas.")
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pisa el pedal: la acción reconocida aparece aquí.", fontSize = 12.sp, color = Paleta.TextoSuave)
        Text(
            observada?.let { "Última acción: ${it.accion.etiqueta}  (#${it.n})" } ?: "Última acción: —",
            style = MaterialTheme.typography.titleMedium, color = Paleta.OxidoClaro,
        )
        Text(
            if (r.muestras == 0) "Latencia pedal → pantalla: sin medidas todavía (se miden en el lector)."
            else "Latencia pedal → pantalla (${r.muestras} giros): p50 %.0f ms · p95 %.0f ms · p99 %.0f ms".format(r.p50, r.p95, r.p99),
            fontSize = 12.sp,
        )
        Row { BotonContorno("Reiniciar medidas", { c.medidorLatencia.limpiar() }) }
    }
}
