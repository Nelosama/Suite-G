package com.suiteg.modelo

/**
 * Posición dentro de un documento: página (base 0) y fracción vertical (0..1) de su altura.
 * Es independiente del tamaño de pantalla, del zoom y de la rotación del dispositivo.
 */
data class Posicion(val pagina: Int, val y: Float = 0f) : Comparable<Posicion> {
    init {
        require(pagina >= 0) { "La página debe ser >= 0" }
        require(y in 0f..1f) { "y debe estar entre 0 y 1" }
    }

    override fun compareTo(other: Posicion): Int =
        compareValuesBy(this, other, { it.pagina }, { it.y })

    companion object {
        /** Construye una posición forzando los valores al rango válido (NaN cuenta como 0). */
        fun acotada(pagina: Int, y: Float): Posicion =
            Posicion(pagina.coerceAtLeast(0), if (y.isNaN()) 0f else y.coerceIn(0f, 1f))
    }
}

/** Cómo se definió un punto de giro. */
enum class TipoGiro { PAGINA, COMPAS, LIBRE }

/**
 * Punto donde la vista debe cambiar. [compases] (opcional) es la duración musical de la vista
 * que EMPIEZA en este punto; lo usa el avance por tempo.
 */
data class PuntoGiro(
    val id: Long = 0,
    val hashArchivo: String,
    val posicion: Posicion,
    val tipo: TipoGiro = TipoGiro.LIBRE,
    val compases: Int? = null,
)

/** Marcador de ensayo con nombre corto ("A", "ensayo 3"). */
data class Marcador(
    val id: Long = 0,
    val hashArchivo: String,
    val nombre: String,
    val posicion: Posicion,
)

/** A partir de [desde] la canción suena a [bpm] (hasta el siguiente tramo). */
data class TramoTempo(
    val id: Long = 0,
    val hashArchivo: String,
    val desde: Posicion,
    val bpm: Double,
)

enum class ModoAvance { PAGINA, CONTINUO }

/** Configuración de avance por tempo de una canción. Desactivado por defecto (opt-in). */
data class ConfigTempoCancion(
    val hashArchivo: String,
    val activo: Boolean = false,
    val bpm: Double = 100.0,
    val beatsPorCompas: Int = 4,
    /** Compases por vista cuando un punto de giro no indica los suyos (null = sin dato). */
    val compasesPorVista: Int? = null,
    val modo: ModoAvance = ModoAvance.PAGINA,
    /** Compases de cuenta de entrada antes de arrancar (0 = sin cuenta). */
    val cuentaEntradaCompases: Int = 0,
    /** Vista desde la que arranca el avance. */
    val vistaInicial: Int = 0,
)

enum class EstadoDocumento { OK, DANADO, FALTANTE }

data class InfoDocumento(
    val hash: String,
    val titulo: String,
    val numPaginas: Int,
    val estado: EstadoDocumento = EstadoDocumento.OK,
    val notas: String = "",
)

data class ItemSetlist(val idItem: Long, val documento: InfoDocumento, val orden: Int)

data class Setlist(
    val id: Long,
    val nombre: String,
    /** Avance por tempo activo para todo el setlist (se suma al flag por canción). */
    val tempoActivo: Boolean = false,
    /** Si el avance automático llega al final, pasar a la siguiente canción. */
    val encadenarTempo: Boolean = false,
    val items: List<ItemSetlist> = emptyList(),
)
