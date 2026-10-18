package com.suiteg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiteg.app.Contenedor
import com.suiteg.app.pedal.EstadoPedal
import com.suiteg.modelo.PerfilUso

enum class Seccion(val titulo: String, val icono: TipoIcono) {
    BIBLIOTECA("Biblioteca", TipoIcono.BIBLIOTECA),
    SETLISTS("Setlists", TipoIcono.SETLISTS),
    GRABACIONES("Grabaciones", TipoIcono.GRABACIONES),
    PEDAL("Pedal", TipoIcono.PEDAL),
    AJUSTES("Ajustes", TipoIcono.AJUSTES),
}

private fun nombrePerfil(p: PerfilUso) = when (p) {
    PerfilUso.ENSAYO -> "Ensayo"
    PerfilUso.CONCIERTO -> "Concierto"
    PerfilUso.FOSO_OSCURO -> "Foso oscuro"
    PerfilUso.PERSONALIZADO -> "Personalizado"
}

/**
 * Armazón de las pantallas principales: barra lateral con la navegación, barra superior con el estado del pedal
 * y el perfil, y el contenido de la sección. En pantallas estrechas la barra lateral se reduce a una columna de iconos.
 */
@Composable
fun ShellApp(c: Contenedor, seccion: Seccion, alElegir: (Seccion) -> Unit, contenido: @Composable () -> Unit) {
    val ajustes by c.ajustes.collectAsState()
    val pedal by c.monitorPedal.estado.collectAsState()
    val documentos by c.repoDocumentos.todos.collectAsState(emptyList())

    BoxWithConstraints(Modifier.fillMaxSize().background(Paleta.Carbon)) {
        val ancha = maxWidth >= 840.dp
        val visibles = Seccion.entries.filter {
            when (it) {
                Seccion.SETLISTS -> ajustes.setlists
                Seccion.GRABACIONES -> ajustes.grabacion
                else -> true
            }
        }
        Row(Modifier.fillMaxSize()) {
            BarraLateral(ancha, visibles, seccion, documentos.size, nombrePerfil(ajustes.perfil), alElegir)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BarraSuperiorApp(seccion, pedal, nombrePerfil(ajustes.perfil), alElegir)
                Box(Modifier.weight(1f).fillMaxWidth()) { contenido() }
            }
        }
    }
}

@Composable
private fun BarraLateral(ancha: Boolean, secciones: List<Seccion>, actual: Seccion, totalDocumentos: Int, perfil: String, alElegir: (Seccion) -> Unit) {
    Row(Modifier.fillMaxHeight().width(if (ancha) 218.dp else 72.dp).background(Paleta.Lateral)) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 31.dp, start = if (ancha) 17.dp else 10.dp, end = if (ancha) 17.dp else 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = if (ancha) 13.dp else 0.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (ancha) Arrangement.Start else Arrangement.Center) {
                Box(Modifier.size(width = 32.dp, height = 35.dp).background(Paleta.Oxido, Formas.Pequena), contentAlignment = Alignment.Center) {
                    Icono(TipoIcono.NOTA, color = Color.White, tamano = 22.dp)
                }
                if (ancha) {
                    Spacer(Modifier.width(10.dp))
                    Text("Suite", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp)
                    Text("-G", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp, color = Paleta.Oxido)
                }
            }
            if (ancha) Text("TU ESPACIO MUSICAL", style = MaterialTheme.typography.labelSmall, color = Color(0xFF777B72), fontSize = 9.sp, modifier = Modifier.padding(start = 13.dp, top = 49.dp, bottom = 15.dp))
            else Spacer(Modifier.height(40.dp))

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (s in secciones) {
                    val sel = s == actual
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 45.dp).clip(Formas.Pequena)
                            .background(if (sel) Paleta.SeleccionFondo else Color.Transparent)
                            .clickable { alElegir(s) }.padding(horizontal = if (ancha) 14.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (ancha) Arrangement.Start else Arrangement.Center,
                    ) {
                        Icono(s.icono, color = if (sel) Color(0xFFDA7248) else Color(0xFFA4A69D), tamano = 19.dp)
                        if (ancha) {
                            Spacer(Modifier.width(12.dp))
                            Text(s.titulo, fontSize = 13.sp, color = if (sel) Paleta.SeleccionTexto else Color(0xFFA4A69D), modifier = Modifier.weight(1f))
                            if (s == Seccion.BIBLIOTECA && totalDocumentos > 0) {
                                Text(
                                    "$totalDocumentos", fontSize = 10.sp, color = Paleta.SeleccionTexto,
                                    modifier = Modifier.background(Color(0xFF513124), Formas.Pequena).border(1.dp, Color(0xFF62402D), Formas.Pequena).padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            if (ancha) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 25.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icono(TipoIcono.ESCUDO, color = Color(0xFF808D78), tamano = 16.dp)
                    Column {
                        Text("Tu música, contigo", fontSize = 11.sp, color = Color(0xFFB8BBAE))
                        Text("Diseñado para funcionar offline", fontSize = 9.sp, color = Color(0xFF74776D), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Linea))
            Row(
                Modifier.fillMaxWidth().clickable { alElegir(Seccion.AJUSTES) }.padding(vertical = 20.dp, horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (ancha) Arrangement.Start else Arrangement.Center,
            ) {
                Box(Modifier.size(32.dp).clip(Formas.Redonda).background(Color(0xFF34362F)).border(1.dp, Color(0xFF484B40), Formas.Redonda), contentAlignment = Alignment.Center) {
                    Text("M", fontSize = 12.sp, color = Color(0xFFC4C7B8))
                }
                if (ancha) {
                    Column(Modifier.padding(start = 11.dp)) {
                        Text("Mi espacio", fontSize = 12.sp)
                        Text("Perfil ${perfil.lowercase()}", fontSize = 10.sp, color = Paleta.TextoSuave, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(Paleta.Linea))
    }
}

@Composable
private fun BarraSuperiorApp(seccion: Seccion, pedal: EstadoPedal, perfil: String, alElegir: (Seccion) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().height(65.dp).padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mi espacio", fontSize = 11.sp, color = Color(0xFF81847B))
                Icono(TipoIcono.SIGUIENTE, color = Color(0xFF81847B), tamano = 12.dp)
                Text(seccion.titulo, fontSize = 11.sp, color = Color(0xFFC9CBC1), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.clickable { alElegir(Seccion.PEDAL) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    val (color, texto) = when (pedal) {
                        is EstadoPedal.Conectado -> Paleta.Conectado to "Pedal conectado"
                        EstadoPedal.Desconectado -> Color(0xFFD17A4D) to "Pedal desconectado"
                        EstadoPedal.SinConfigurar -> Paleta.TextoSuave to "Pedal sin asignar"
                    }
                    Box(Modifier.size(6.dp).clip(Formas.Redonda).background(color))
                    Text(texto, fontSize = 11.sp, color = Color(0xFFB0B5A8))
                }
                Box(Modifier.height(14.dp).width(1.dp).background(Paleta.Linea))
                Row(Modifier.clickable { alElegir(Seccion.AJUSTES) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icono(TipoIcono.AJUSTES, color = Color(0xFFB0B5A8), tamano = 14.dp)
                    Text(perfil, fontSize = 11.sp, color = Color(0xFFB0B5A8))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Linea))
    }
}
