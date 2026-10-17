package com.suiteg.app.datos

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una fila por PDF importado. La clave es el SHA-256 del contenido: identifica el archivo, no su nombre. */
@Entity(tableName = "documentos")
data class DocumentoE(
    @PrimaryKey val hash: String,
    val titulo: String,
    val rutaInterna: String,
    val uriOrigen: String?,
    val numPaginas: Int,
    val estado: String,
    val bytes: Long,
    val importadoEn: Long,
    val notas: String = "",
)

private const val HASH = "hash"

@Entity(
    tableName = "puntos_giro",
    foreignKeys = [ForeignKey(DocumentoE::class, [HASH], [HASH], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HASH)],
)
data class PuntoGiroE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hash: String,
    val pagina: Int,
    val y: Float,
    val tipo: String,
    val compases: Int?,
)

@Entity(
    tableName = "marcadores",
    foreignKeys = [ForeignKey(DocumentoE::class, [HASH], [HASH], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HASH)],
)
data class MarcadorE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hash: String,
    val nombre: String,
    val pagina: Int,
    val y: Float,
)

@Entity(
    tableName = "tramos_tempo",
    foreignKeys = [ForeignKey(DocumentoE::class, [HASH], [HASH], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HASH)],
)
data class TramoTempoE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hash: String,
    val pagina: Int,
    val y: Float,
    val bpm: Double,
)

@Entity(
    tableName = "config_tempo",
    foreignKeys = [ForeignKey(DocumentoE::class, [HASH], [HASH], onDelete = ForeignKey.CASCADE)],
)
data class ConfigTempoE(
    @PrimaryKey val hash: String,
    val activo: Boolean,
    val bpm: Double,
    val beatsPorCompas: Int,
    val compasesPorVista: Int?,
    val modo: String,
    val cuentaEntradaCompases: Int,
)

@Entity(tableName = "setlists")
data class SetlistE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val tempoActivo: Boolean = false,
    val encadenarTempo: Boolean = false,
    val creadoEn: Long,
)

@Entity(
    tableName = "setlist_items",
    foreignKeys = [
        ForeignKey(SetlistE::class, ["id"], ["setlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(DocumentoE::class, [HASH], [HASH], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("setlistId"), Index(HASH)],
)
data class SetlistItemE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setlistId: Long,
    val hash: String,
    val orden: Int,
)

/** Fila de unión que leen las pantallas del setlist. */
data class ItemConDocumento(
    val idItem: Long,
    val orden: Int,
    val hash: String,
    val titulo: String,
    val numPaginas: Int,
    val estado: String,
    val notas: String,
)

@Entity(
    tableName = "trazos",
    foreignKeys = [ForeignKey(DocumentoE::class, [HASH], [HASH], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HASH, "pagina")],
)
data class TrazoE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hash: String,
    val pagina: Int,
    val herramienta: String,
    val color: Int,
    val grosor: Float,
    /** Puntos en [com.suiteg.modelo.CodecTrazos]. */
    val datos: ByteArray,
    val creadoEn: Long,
)

@Entity(tableName = "grabaciones")
data class GrabacionE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hash: String?,
    val titulo: String,
    val ruta: String,
    val inicioEpochMs: Long,
    val duracionMs: Long,
    /** EN_CURSO, OK, ERROR o RECUPERADA (el proceso murió y se estimó la duración). */
    val estado: String,
)

@Entity(
    tableName = "marcas_grabacion",
    foreignKeys = [ForeignKey(GrabacionE::class, ["id"], ["grabacionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("grabacionId")],
)
data class MarcaGrabacionE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val grabacionId: Long,
    val tMs: Long,
    val vistaIndice: Int,
    val etiqueta: String,
)
