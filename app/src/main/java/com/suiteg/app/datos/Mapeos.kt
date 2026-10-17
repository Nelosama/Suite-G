package com.suiteg.app.datos

import com.suiteg.modelo.CodecTrazos
import com.suiteg.modelo.ConfigTempoCancion
import com.suiteg.modelo.EstadoDocumento
import com.suiteg.modelo.Herramienta
import com.suiteg.modelo.InfoDocumento
import com.suiteg.modelo.Marcador
import com.suiteg.modelo.ModoAvance
import com.suiteg.modelo.Posicion
import com.suiteg.modelo.PuntoGiro
import com.suiteg.modelo.TipoGiro
import com.suiteg.modelo.TramoTempo
import com.suiteg.modelo.Trazo

/** Convierte un texto guardado en un enum, con valor por defecto si no se reconoce (datos de otra versión). */
private inline fun <reified E : Enum<E>> String.aEnum(porDefecto: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: porDefecto

fun DocumentoE.aInfo() = InfoDocumento(hash, titulo, numPaginas, estado.aEnum(EstadoDocumento.OK), notas)

fun ItemConDocumento.aInfo() = InfoDocumento(hash, titulo, numPaginas, estado.aEnum(EstadoDocumento.OK), notas)

fun PuntoGiroE.aModelo() = PuntoGiro(id, hash, Posicion.acotada(pagina, y), tipo.aEnum(TipoGiro.LIBRE), compases)

fun MarcadorE.aModelo() = Marcador(id, hash, nombre, Posicion.acotada(pagina, y))

fun TramoTempoE.aModelo() = TramoTempo(id, hash, Posicion.acotada(pagina, y), bpm)

fun ConfigTempoE.aModelo() = ConfigTempoCancion(
    hash, activo, bpm, beatsPorCompas, compasesPorVista, modo.aEnum(ModoAvance.PAGINA), cuentaEntradaCompases,
)

fun ConfigTempoCancion.aEntidad() = ConfigTempoE(
    hashArchivo, activo, bpm, beatsPorCompas, compasesPorVista, modo.name, cuentaEntradaCompases,
)

/** null si los puntos guardados están corruptos: ese trazo se omite, la página sigue mostrándose. */
fun TrazoE.aModelo(): Trazo? {
    val puntos = CodecTrazos.decodificar(datos) ?: return null
    return Trazo(id, pagina, herramienta.aEnum(Herramienta.LAPIZ), color, grosor, puntos)
}
