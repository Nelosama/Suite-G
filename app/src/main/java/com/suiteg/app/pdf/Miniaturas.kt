package com.suiteg.app.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Miniaturas de la primera página de cada partitura, para la biblioteca.
 *
 * Se renderizan con el mismo [MotorPdf] que el lector (sin motor nuevo) y se guardan como PNG en
 * [carpeta], así que solo se rasterizan una vez por partitura. En memoria hay una caché LRU limitada por bytes.
 * Si el PDF no se puede leer, devuelve null y la biblioteca muestra el icono de siempre.
 *
 * Solo se renderiza una miniatura a la vez: [PdfRendererMotor] abre una página por motor y la biblioteca puede
 * pedir muchas a la vez al abrirse.
 */
class Miniaturas(private val carpeta: File) {
    private val memoria = object : LruCache<String, Bitmap>(PRESUPUESTO_MEMORIA_BYTES) {
        override fun sizeOf(clave: String, valor: Bitmap): Int = valor.allocationByteCount
    }
    private val exclusiva = Mutex()

    /** Miniatura de [hash] (el archivo PDF está en [rutaPdf]), o null si no se puede generar. */
    suspend fun obtener(hash: String, rutaPdf: String): Bitmap? {
        memoria.get(hash)?.let { return it }
        return withContext(Dispatchers.IO) {
            exclusiva.withLock {
                memoria.get(hash) ?: leerDeDisco(hash) ?: generar(hash, rutaPdf)?.also { memoria.put(hash, it) }
            }
        }
    }

    /** Quita la miniatura (de disco y de memoria) de una partitura eliminada. */
    fun borrar(hash: String) {
        memoria.remove(hash)
        archivo(hash).delete()
    }

    private fun archivo(hash: String) = File(carpeta, "$hash.png")

    private fun leerDeDisco(hash: String): Bitmap? {
        val f = archivo(hash)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)?.also { memoria.put(hash, it) }
    }

    private suspend fun generar(hash: String, rutaPdf: String): Bitmap? = try {
        carpeta.mkdirs()
        PdfRendererMotor.abrir(File(rutaPdf)).use { motor ->
            val tam = motor.tamanoPagina(0)
            val escala = ANCHO_PX / tam.ancho
            val alto = Math.round(tam.alto * escala).coerceAtLeast(1)
            val bmp = motor.renderizar(ParametrosRender(pagina = 0, escala = escala, origenX = 0f, origenY = 0f, ancho = ANCHO_PX, alto = alto))
            guardarPng(bmp, archivo(hash))
            bmp
        }
    } catch (t: Throwable) {
        // PDF dañado o motor sin memoria: no es fatal, la biblioteca usa el icono.
        null
    }

    private fun guardarPng(bmp: Bitmap, destino: File) {
        val tmp = File(destino.parentFile, "${destino.name}.tmp")
        tmp.outputStream().use { out ->
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("No se pudo guardar la miniatura")
        }
        if (!tmp.renameTo(destino)) {
            tmp.copyTo(destino, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        /** Ancho de la miniatura en píxeles: suficiente para la lista y las tarjetas en tablet. */
        const val ANCHO_PX = 240

        /** 8 MB: unas 25 miniaturas en memoria a la vez. */
        private const val PRESUPUESTO_MEMORIA_BYTES = 8 * 1024 * 1024
    }
}
