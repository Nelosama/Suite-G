package com.suiteg.app.lector

import android.graphics.Bitmap
import com.suiteg.modelo.Posicion
import com.suiteg.modelo.ResumenLatencia
import com.suiteg.modelo.TransformacionVista
import com.suiteg.navegacion.FaseLector
import com.suiteg.tempo.InstantaneaTempo

enum class ModoEdicion { NINGUNO, GIROS, MARCADORES, DIBUJO }

/** Imagen ya rasterizada de la vista actual y la transformación que la generó. */
class ImagenVista(val bitmap: Bitmap, val transformacion: TransformacionVista, val pagina: Int)

/** Toque sobre la partitura en un modo de edición: la interfaz pregunta qué crear en esa posición. */
data class ToqueEdicion(val modo: ModoEdicion, val posicion: Posicion)

/** Todo lo que la interfaz del lector necesita pintar. La imagen de la página va por otra vía (más rápida). */
data class EstadoLectorUi(
    val cargando: Boolean = false,
    val hash: String = "",
    val titulo: String = "",
    val notas: String = "",
    val fase: FaseLector = FaseLector.SIN_DOCUMENTO,
    val error: String? = null,
    val indiceVista: Int = 0,
    val totalVistas: Int = 0,
    val pagina: Int = 0,
    val totalPaginas: Int = 0,
    val avisoSiguiente: String? = null,
    val posicionSetlist: String? = null,
    val mensaje: String? = null,
    val tempoDisponible: Boolean = false,
    val tempo: InstantaneaTempo? = null,
    /** Instante (reloj de la app) en el que se producirá el giro anunciado, o null si no hay aviso. */
    val avisoGiroHastaMs: Long? = null,
    val avisoGiroEsFin: Boolean = false,
    val metronomoActivo: Boolean = false,
    val bloqueado: Boolean = false,
    val modoEdicion: ModoEdicion = ModoEdicion.NINGUNO,
    val latencia: ResumenLatencia? = null,
)
