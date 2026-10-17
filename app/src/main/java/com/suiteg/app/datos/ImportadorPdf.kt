package com.suiteg.app.datos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.suiteg.modelo.EstadoDocumento
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ResultadoImportacion {
    data class Importado(val documento: DocumentoE) : ResultadoImportacion
    data class YaExistia(val documento: DocumentoE) : ResultadoImportacion
    data class Fallo(val nombre: String, val motivo: String) : ResultadoImportacion
}

/**
 * Importa PDFs con el Storage Access Framework.
 *
 * Se pide permiso persistente sobre el URI, pero el PDF se COPIA al almacenamiento interno de la app:
 * PdfRenderer necesita acceso aleatorio, y un permiso puede revocarse o una tarjeta SD retirarse en pleno
 * concierto. El URI original solo se guarda como referencia.
 */
class ImportadorPdf(
    private val contexto: Context,
    private val dao: DocumentoDao,
    private val validador: ClienteValidador,
) {
    private val carpeta = File(contexto.filesDir, "partituras").apply { mkdirs() }
    private val temporales = File(contexto.filesDir, "importando").apply { mkdirs() }

    suspend fun importar(uri: Uri): ResultadoImportacion = withContext(Dispatchers.IO) {
        val nombre = nombreDe(uri)
        try {
            persistirPermiso(uri)
            val tmp = File.createTempFile("imp", ".pdf", temporales)
            try {
                val (hash, bytes) = copiarYCalcularHash(uri, tmp)
                if (bytes == 0L) return@withContext ResultadoImportacion.Fallo(nombre, "El archivo está vacío")
                if (!parecePdf(tmp)) return@withContext ResultadoImportacion.Fallo(nombre, "No es un PDF válido")

                dao.obtener(hash)?.let { return@withContext ResultadoImportacion.YaExistia(it) }

                val destino = File(carpeta, "$hash.pdf")
                if (!tmp.renameTo(destino)) {
                    tmp.copyTo(destino, overwrite = true)
                }
                when (val v = validador.validar(destino)) {
                    is ResultadoValidacion.Fallo -> {
                        destino.delete()
                        ResultadoImportacion.Fallo(nombre, v.motivo)
                    }
                    is ResultadoValidacion.Correcto -> {
                        val doc = DocumentoE(
                            hash = hash,
                            titulo = nombre.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "Sin título" },
                            rutaInterna = destino.absolutePath,
                            uriOrigen = uri.toString(),
                            numPaginas = v.paginas,
                            estado = EstadoDocumento.OK.name,
                            bytes = bytes,
                            importadoEn = System.currentTimeMillis(),
                        )
                        dao.insertar(doc)
                        ResultadoImportacion.Importado(doc)
                    }
                }
            } finally {
                tmp.delete()
            }
        } catch (e: IOException) {
            ResultadoImportacion.Fallo(nombre, "No se pudo leer el archivo (${e.message ?: "error de E/S"})")
        } catch (e: SecurityException) {
            ResultadoImportacion.Fallo(nombre, "Sin permiso para leer el archivo")
        }
    }

    private fun persistirPermiso(uri: Uri) {
        // No todos los URI admiten permiso persistente (p. ej. los que llegan por ACTION_VIEW): no es un error.
        try {
            contexto.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun nombreDe(uri: Uri): String {
        runCatching {
            contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "partitura.pdf"
    }

    private fun copiarYCalcularHash(uri: Uri, destino: File): Pair<String, Long> {
        val sha = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val entrada = contexto.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
        entrada.use { ins ->
            destino.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    if (total + n > contexto.filesDir.usableSpace - MARGEN_LIBRE) throw IOException("Sin espacio suficiente")
                    sha.update(buf, 0, n)
                    out.write(buf, 0, n)
                    total += n
                }
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) } to total
    }

    /** La especificación admite hasta 1024 bytes de basura antes de "%PDF-". */
    private fun parecePdf(f: File): Boolean {
        val cabecera = ByteArray(1024)
        val n = f.inputStream().use { it.read(cabecera) }
        if (n < 5) return false
        return String(cabecera, 0, n, Charsets.ISO_8859_1).contains("%PDF-")
    }

    companion object {
        private const val MARGEN_LIBRE = 50L * 1024 * 1024
    }
}
