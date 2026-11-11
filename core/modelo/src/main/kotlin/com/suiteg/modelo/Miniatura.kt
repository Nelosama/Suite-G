package com.suiteg.modelo

/**
 * Cálculos puros de las miniaturas de la biblioteca (sin Android, para probarlos en la JVM).
 * El renderizado con PdfRenderer vive en la app (`pdf/Miniaturas.kt`) y no se puede probar aquí.
 */
object CalculoMiniatura {
    /** Ancho de la miniatura en píxeles: suficiente para la lista y las tarjetas en tablet. */
    const val ANCHO_PX = 240

    /**
     * Alto máximo respecto al ancho. Una página anómala (p. ej. un rollo de 1 m de alto) no debe producir un
     * bitmap gigante: se recorta por abajo y se conserva la parte superior, que es donde están título y comienzo.
     */
    const val PROPORCION_MAXIMA = 3f

    private const val EXTENSION = ".png"
    private const val SUFIJO_TEMPORAL = ".tmp"

    /**
     * Tamaño en píxeles y escala (píxeles por punto) de la miniatura de una página de [anchoPuntos] x [altoPuntos].
     * Devuelve null si el tamaño de la página no es válido (cero, negativo, NaN o infinito): la biblioteca
     * muestra entonces el icono genérico.
     */
    fun calcular(anchoPuntos: Float, altoPuntos: Float, anchoPx: Int = ANCHO_PX): TamanoMiniatura? {
        if (anchoPx <= 0) return null
        if (!anchoPuntos.isFinite() || !altoPuntos.isFinite() || anchoPuntos <= 0f || altoPuntos <= 0f) return null
        val escala = anchoPx / anchoPuntos
        val altoMax = Math.round(anchoPx * PROPORCION_MAXIMA)
        val alto = Math.round(altoPuntos * escala).coerceIn(1, altoMax)
        return TamanoMiniatura(anchoPx, alto, escala)
    }

    /** Nombre del archivo en disco de la miniatura de la partitura con SHA-256 [hash]. */
    fun nombreArchivo(hash: String): String = hash + EXTENSION

    /** Nombre del archivo temporal mientras se escribe la miniatura (luego se renombra). */
    fun nombreTemporal(hash: String): String = nombreArchivo(hash) + SUFIJO_TEMPORAL

    /**
     * Qué archivos de la carpeta de miniaturas sobran: los temporales (una escritura que se cortó) y las
     * miniaturas de partituras que ya no están en la biblioteca ([hashesVigentes]). Cualquier otro archivo
     * desconocido también se considera sobrante: la carpeta es solo de miniaturas.
     */
    fun huerfanos(nombresEnCarpeta: Collection<String>, hashesVigentes: Set<String>): List<String> =
        nombresEnCarpeta.filter { nombre ->
            if (!nombre.endsWith(EXTENSION)) return@filter true
            nombre.removeSuffix(EXTENSION) !in hashesVigentes
        }
}

/** Tamaño de la miniatura en píxeles y [escala] en píxeles por punto PDF. */
data class TamanoMiniatura(val ancho: Int, val alto: Int, val escala: Float)
