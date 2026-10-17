package com.suiteg.modelo

/**
 * Matrices de color 4x5 (formato de `android.graphics.ColorMatrix`: filas R, G, B, A; columnas R, G, B, A, desplazamiento 0..255)
 * para los modos de inversión. Están aquí, sin Android, para poder probarlas.
 *
 * Nota: Android aplica la matriz combinada y recorta una sola vez al final (CSS recorta entre filtros). Para
 * partituras (negro sobre blanco) el resultado es idéntico; difiere solo en colores muy saturados.
 */
object MatricesColor {
    private val INVERTIR = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Rotación de matiz de 180°: tras invertir, devuelve a cada color su matiz original (el gris no cambia). */
    private val MATIZ_180 = floatArrayOf(
        -0.574f, 1.430f, 0.144f, 0f, 0f,
        0.426f, 0.430f, 0.144f, 0f, 0f,
        0.426f, 1.430f, -0.856f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Comprime el rango 0..255 a 30..200: el "negro" del fondo pasa a carbón y el "blanco" de la tinta a gris claro. */
    private val ATENUAR = floatArrayOf(
        170f / 255f, 0f, 0f, 0f, 30f,
        0f, 170f / 255f, 0f, 0f, 30f,
        0f, 0f, 170f / 255f, 0f, 30f,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * - TODO: negativo completo.
     * - TONALIDAD: invierte la luminosidad pero conserva el matiz de cada color.
     * - FONDO: como TONALIDAD, con bajo contraste (fondo carbón, tinta gris claro) para fosos oscuros.
     */
    fun para(modo: ModoInversion): FloatArray? = when (modo) {
        ModoInversion.NINGUNA -> null
        ModoInversion.TODO -> INVERTIR.copyOf()
        ModoInversion.TONALIDAD -> multiplicar(MATIZ_180, INVERTIR)
        ModoInversion.FONDO -> multiplicar(ATENUAR, multiplicar(MATIZ_180, INVERTIR))
    }

    /** Devuelve la matriz que aplica primero [b] y después [a]. */
    fun multiplicar(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(20)
        for (fila in 0 until 4) {
            for (col in 0 until 5) {
                var s = 0f
                for (j in 0 until 4) s += a[fila * 5 + j] * b[j * 5 + col]
                if (col == 4) s += a[fila * 5 + 4]
                r[fila * 5 + col] = s
            }
        }
        return r
    }

    /** Aplica la matriz a un color (0..255 por canal) con recorte final; devuelve (r, g, b). Para pruebas. */
    fun aplicar(m: FloatArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        fun canal(f: Int) = Math.round(m[f * 5] * r + m[f * 5 + 1] * g + m[f * 5 + 2] * b + m[f * 5 + 4]).coerceIn(0, 255)
        return Triple(canal(0), canal(1), canal(2))
    }
}
