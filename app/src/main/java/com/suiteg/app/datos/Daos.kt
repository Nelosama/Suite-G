package com.suiteg.app.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentoDao {
    @Query("SELECT * FROM documentos ORDER BY titulo COLLATE NOCASE")
    fun observarTodos(): Flow<List<DocumentoE>>

    @Query("SELECT * FROM documentos WHERE hash = :hash")
    suspend fun obtener(hash: String): DocumentoE?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(d: DocumentoE): Long

    @Query("UPDATE documentos SET estado = :estado WHERE hash = :hash")
    suspend fun actualizarEstado(hash: String, estado: String)

    @Query("UPDATE documentos SET notas = :notas WHERE hash = :hash")
    suspend fun actualizarNotas(hash: String, notas: String)

    @Query("UPDATE documentos SET titulo = :titulo WHERE hash = :hash")
    suspend fun renombrar(hash: String, titulo: String)

    @Query("DELETE FROM documentos WHERE hash = :hash")
    suspend fun eliminar(hash: String)
}

@Dao
interface PartituraDao {
    @Query("SELECT * FROM puntos_giro WHERE hash = :hash ORDER BY pagina, y")
    fun observarPuntos(hash: String): Flow<List<PuntoGiroE>>

    @Query("SELECT * FROM puntos_giro WHERE hash = :hash ORDER BY pagina, y")
    suspend fun puntos(hash: String): List<PuntoGiroE>

    @Insert
    suspend fun insertarPunto(p: PuntoGiroE): Long

    @Query("DELETE FROM puntos_giro WHERE id = :id")
    suspend fun borrarPunto(id: Long)

    @Query("DELETE FROM puntos_giro WHERE hash = :hash")
    suspend fun borrarPuntosDe(hash: String)

    @Query("SELECT * FROM marcadores WHERE hash = :hash ORDER BY pagina, y")
    fun observarMarcadores(hash: String): Flow<List<MarcadorE>>

    @Query("SELECT * FROM marcadores WHERE hash = :hash ORDER BY pagina, y")
    suspend fun marcadores(hash: String): List<MarcadorE>

    @Insert
    suspend fun insertarMarcador(m: MarcadorE): Long

    @Query("DELETE FROM marcadores WHERE id = :id")
    suspend fun borrarMarcador(id: Long)

    @Query("SELECT * FROM tramos_tempo WHERE hash = :hash ORDER BY pagina, y")
    suspend fun tramos(hash: String): List<TramoTempoE>

    @Insert
    suspend fun insertarTramo(t: TramoTempoE): Long

    @Query("DELETE FROM tramos_tempo WHERE id = :id")
    suspend fun borrarTramo(id: Long)

    @Query("SELECT * FROM config_tempo WHERE hash = :hash")
    suspend fun configTempo(hash: String): ConfigTempoE?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarConfigTempo(c: ConfigTempoE)
}

@Dao
abstract class SetlistDao {
    @Query("SELECT * FROM setlists ORDER BY nombre COLLATE NOCASE")
    abstract fun observarSetlists(): Flow<List<SetlistE>>

    @Query("SELECT * FROM setlists WHERE id = :id")
    abstract fun observarSetlist(id: Long): Flow<SetlistE?>

    @Query("SELECT * FROM setlists WHERE id = :id")
    abstract suspend fun obtener(id: Long): SetlistE?

    @Insert
    abstract suspend fun insertar(s: SetlistE): Long

    @Update
    abstract suspend fun actualizar(s: SetlistE)

    @Query("DELETE FROM setlists WHERE id = :id")
    abstract suspend fun eliminar(id: Long)

    @Query(CONSULTA_ITEMS)
    abstract fun observarItems(id: Long): Flow<List<ItemConDocumento>>

    @Query(CONSULTA_ITEMS)
    abstract suspend fun items(id: Long): List<ItemConDocumento>

    @Insert
    abstract suspend fun insertarItem(i: SetlistItemE): Long

    @Query("DELETE FROM setlist_items WHERE id = :idItem")
    abstract suspend fun eliminarItem(idItem: Long)

    @Query("UPDATE setlist_items SET orden = :orden WHERE id = :idItem")
    abstract suspend fun fijarOrden(idItem: Long, orden: Int)

    @Query("SELECT COALESCE(MAX(orden), -1) + 1 FROM setlist_items WHERE setlistId = :id")
    abstract suspend fun siguienteOrden(id: Long): Int

    /** Reasigna el orden 0..n-1 según la lista dada, en una sola transacción. */
    @Transaction
    open suspend fun reordenar(idsEnOrden: List<Long>) {
        idsEnOrden.forEachIndexed { i, id -> fijarOrden(id, i) }
    }

    companion object {
        const val CONSULTA_ITEMS = """
            SELECT i.id AS idItem, i.orden AS orden, d.hash AS hash, d.titulo AS titulo,
                   d.numPaginas AS numPaginas, d.estado AS estado, d.notas AS notas
            FROM setlist_items i JOIN documentos d ON d.hash = i.hash
            WHERE i.setlistId = :id ORDER BY i.orden, i.id
        """
    }
}

@Dao
interface TrazoDao {
    @Query("SELECT * FROM trazos WHERE hash = :hash AND pagina = :pagina ORDER BY id")
    fun observarPagina(hash: String, pagina: Int): Flow<List<TrazoE>>

    @Insert
    suspend fun insertar(t: TrazoE): Long

    @Query("DELETE FROM trazos WHERE id = :id")
    suspend fun borrar(id: Long)
}

@Dao
interface GrabacionDao {
    @Query("SELECT * FROM grabaciones ORDER BY inicioEpochMs DESC")
    fun observarTodas(): Flow<List<GrabacionE>>

    @Query("SELECT * FROM grabaciones WHERE id = :id")
    suspend fun obtener(id: Long): GrabacionE?

    @Query("SELECT * FROM grabaciones WHERE estado = 'EN_CURSO'")
    suspend fun enCurso(): List<GrabacionE>

    @Insert
    suspend fun insertar(g: GrabacionE): Long

    @Update
    suspend fun actualizar(g: GrabacionE)

    @Query("DELETE FROM grabaciones WHERE id = :id")
    suspend fun eliminar(id: Long)

    @Insert
    suspend fun insertarMarca(m: MarcaGrabacionE): Long

    @Query("SELECT * FROM marcas_grabacion WHERE grabacionId = :id ORDER BY tMs")
    suspend fun marcas(id: Long): List<MarcaGrabacionE>
}
