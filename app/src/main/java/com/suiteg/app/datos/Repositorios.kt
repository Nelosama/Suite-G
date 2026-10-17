package com.suiteg.app.datos

import com.suiteg.modelo.ConfigTempoCancion
import com.suiteg.modelo.EstadoDocumento
import com.suiteg.modelo.InfoDocumento
import com.suiteg.modelo.Marcador
import com.suiteg.modelo.Posicion
import com.suiteg.modelo.PuntoGiro
import com.suiteg.modelo.TipoGiro
import com.suiteg.modelo.TramoTempo
import com.suiteg.modelo.Trazo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RepoDocumentos(private val dao: DocumentoDao) {
    val todos: Flow<List<DocumentoE>> = dao.observarTodos()

    suspend fun obtener(hash: String): DocumentoE? = dao.obtener(hash)

    suspend fun marcarEstado(hash: String, estado: EstadoDocumento) = dao.actualizarEstado(hash, estado.name)

    suspend fun guardarNotas(hash: String, notas: String) = dao.actualizarNotas(hash, notas.take(MAX_NOTAS))

    suspend fun renombrar(hash: String, titulo: String) {
        if (titulo.isNotBlank()) dao.renombrar(hash, titulo.trim().take(120))
    }

    /** Borra la fila (y por cascada puntos, marcadores, trazos y entradas de setlist) y el archivo copiado. */
    suspend fun eliminar(hash: String) {
        val d = dao.obtener(hash) ?: return
        dao.eliminar(hash)
        withContext(Dispatchers.IO) { runCatching { File(d.rutaInterna).delete() } }
    }

    companion object {
        const val MAX_NOTAS = 600
    }
}

class RepoPartitura(private val dao: PartituraDao) {
    suspend fun puntos(hash: String): List<PuntoGiro> = dao.puntos(hash).map { it.aModelo() }
    suspend fun marcadores(hash: String): List<Marcador> = dao.marcadores(hash).map { it.aModelo() }
    suspend fun tramos(hash: String): List<TramoTempo> = dao.tramos(hash).map { it.aModelo() }

    fun observarPuntos(hash: String): Flow<List<PuntoGiro>> = dao.observarPuntos(hash).map { l -> l.map { it.aModelo() } }
    fun observarMarcadores(hash: String): Flow<List<Marcador>> = dao.observarMarcadores(hash).map { l -> l.map { it.aModelo() } }

    suspend fun agregarPunto(hash: String, pos: Posicion, tipo: TipoGiro, compases: Int?) {
        val y = if (tipo == TipoGiro.PAGINA) 0f else pos.y
        dao.insertarPunto(PuntoGiroE(hash = hash, pagina = pos.pagina, y = y, tipo = tipo.name, compases = compases?.takeIf { it > 0 }))
    }

    suspend fun borrarPunto(id: Long) = dao.borrarPunto(id)

    suspend fun agregarMarcador(hash: String, nombre: String, pos: Posicion) {
        dao.insertarMarcador(MarcadorE(hash = hash, nombre = nombre.trim().take(24), pagina = pos.pagina, y = pos.y))
    }

    suspend fun borrarMarcador(id: Long) = dao.borrarMarcador(id)

    suspend fun agregarTramo(hash: String, pos: Posicion, bpm: Double) {
        dao.insertarTramo(TramoTempoE(hash = hash, pagina = pos.pagina, y = pos.y, bpm = bpm))
    }

    suspend fun borrarTramo(id: Long) = dao.borrarTramo(id)

    suspend fun config(hash: String): ConfigTempoCancion = dao.configTempo(hash)?.aModelo() ?: ConfigTempoCancion(hash)
    suspend fun guardarConfig(c: ConfigTempoCancion) = dao.guardarConfigTempo(c.aEntidad())
}

data class SetlistConItems(
    val id: Long,
    val nombre: String,
    val tempoActivo: Boolean,
    val encadenarTempo: Boolean,
    val items: List<ItemConDocumento>,
)

class RepoSetlists(private val dao: SetlistDao) {
    val todos: Flow<List<SetlistE>> = dao.observarSetlists()

    fun observarSetlist(id: Long): Flow<SetlistE?> = dao.observarSetlist(id)
    fun observarItems(id: Long): Flow<List<ItemConDocumento>> = dao.observarItems(id)

    suspend fun crear(nombre: String): Long =
        dao.insertar(SetlistE(nombre = nombre.trim().ifBlank { "Setlist" }.take(60), creadoEn = System.currentTimeMillis()))

    suspend fun renombrar(s: SetlistE, nombre: String) {
        if (nombre.isNotBlank()) dao.actualizar(s.copy(nombre = nombre.trim().take(60)))
    }

    suspend fun fijarTempo(s: SetlistE, activo: Boolean, encadenar: Boolean) =
        dao.actualizar(s.copy(tempoActivo = activo, encadenarTempo = encadenar))

    suspend fun eliminar(id: Long) = dao.eliminar(id)

    suspend fun agregar(setlistId: Long, hashes: List<String>) {
        var orden = dao.siguienteOrden(setlistId)
        for (h in hashes) dao.insertarItem(SetlistItemE(setlistId = setlistId, hash = h, orden = orden++))
    }

    suspend fun quitar(idItem: Long) = dao.eliminarItem(idItem)

    /** Intercambia la posición de un elemento con su vecino ([delta] = -1 sube, +1 baja). */
    suspend fun mover(setlistId: Long, idItem: Long, delta: Int) {
        val ids = dao.items(setlistId).map { it.idItem }.toMutableList()
        val i = ids.indexOf(idItem)
        val j = i + delta
        if (i < 0 || j !in ids.indices) return
        ids[i] = ids[j].also { ids[j] = ids[i] }
        dao.reordenar(ids)
    }

    suspend fun obtener(id: Long): SetlistConItems? {
        val s = dao.obtener(id) ?: return null
        return SetlistConItems(s.id, s.nombre, s.tempoActivo, s.encadenarTempo, dao.items(id))
    }
}

class RepoTrazos(private val dao: TrazoDao) {
    fun observarPagina(hash: String, pagina: Int): Flow<List<Trazo>> =
        dao.observarPagina(hash, pagina).map { l -> l.mapNotNull { it.aModelo() } }

    suspend fun guardar(hash: String, t: Trazo): Long = dao.insertar(
        TrazoE(
            hash = hash, pagina = t.pagina, herramienta = t.herramienta.name, color = t.color, grosor = t.grosor,
            datos = com.suiteg.modelo.CodecTrazos.codificar(t.puntos), creadoEn = System.currentTimeMillis(),
        ),
    )

    suspend fun borrar(id: Long) = dao.borrar(id)
}

class RepoGrabaciones(private val dao: GrabacionDao) {
    val todas: Flow<List<GrabacionE>> = dao.observarTodas()

    suspend fun crear(hash: String?, titulo: String, ruta: String): Long = dao.insertar(
        GrabacionE(hash = hash, titulo = titulo, ruta = ruta, inicioEpochMs = System.currentTimeMillis(), duracionMs = 0, estado = "EN_CURSO"),
    )

    suspend fun finalizar(id: Long, duracionMs: Long, ok: Boolean) {
        val g = dao.obtener(id) ?: return
        dao.actualizar(g.copy(duracionMs = duracionMs, estado = if (ok) "OK" else "ERROR"))
    }

    suspend fun marca(id: Long, tMs: Long, vista: Int, etiqueta: String) {
        dao.insertarMarca(MarcaGrabacionE(grabacionId = id, tMs = tMs, vistaIndice = vista, etiqueta = etiqueta))
    }

    suspend fun marcas(id: Long) = dao.marcas(id)

    suspend fun eliminar(g: GrabacionE) {
        dao.eliminar(g.id)
        withContext(Dispatchers.IO) { runCatching { File(g.ruta).delete() } }
    }

    /**
     * Grabaciones que quedaron "EN_CURSO" porque la app murió: se conserva el audio (el flujo ADTS se puede
     * reproducir aunque se cortara) y se estima la duración por el tamaño del archivo (128 kbps = 16 000 B/s).
     */
    suspend fun recuperarHuerfanas() {
        for (g in dao.enCurso()) {
            val f = File(g.ruta)
            if (f.exists() && f.length() > 0) {
                dao.actualizar(g.copy(duracionMs = f.length() * 1000L / 16_000L, estado = "RECUPERADA"))
            } else {
                dao.eliminar(g.id)
            }
        }
    }
}
