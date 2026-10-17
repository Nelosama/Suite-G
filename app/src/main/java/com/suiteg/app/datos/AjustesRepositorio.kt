package com.suiteg.app.datos

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.suiteg.modelo.AjustesApp
import com.suiteg.modelo.AjustesCodec
import com.suiteg.navegacion.entrada.MapaTeclas
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.almacen: DataStore<Preferences> by preferencesDataStore("ajustes")

/** Qué estaba abierto cuando la app se cerró, para volver exactamente ahí. */
data class SesionGuardada(
    val hash: String,
    val setlistId: Long,
    val indiceSetlist: Int,
    val vista: Int,
    val bloqueado: Boolean,
)

class AjustesRepositorio(private val contexto: Context) {
    private val prefijo = "aj_"
    private val kMapa = stringPreferencesKey("mapa_teclas")
    private val kSesionHash = stringPreferencesKey("ses_hash")
    private val kSesionSetlist = longPreferencesKey("ses_setlist")
    private val kSesionIndice = intPreferencesKey("ses_indice")
    private val kSesionVista = intPreferencesKey("ses_vista")
    private val kSesionBloqueo = booleanPreferencesKey("ses_bloqueado")
    private val kSesionAbierta = booleanPreferencesKey("ses_abierta")

    private val datos: Flow<Preferences> = contexto.almacen.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val ajustes: Flow<AjustesApp> = datos.map { p ->
        val m = p.asMap()
            .filterKeys { it.name.startsWith(prefijo) }
            .entries.associate { (k, v) -> k.name.removePrefix(prefijo) to v.toString() }
        AjustesCodec.desdeMapa(m)
    }

    val mapaTeclas: Flow<MapaTeclas> = datos.map { MapaTeclas.deserializar(it[kMapa]) }

    suspend fun guardar(a: AjustesApp) {
        contexto.almacen.edit { p ->
            for ((k, v) in AjustesCodec.aMapa(a)) p[stringPreferencesKey(prefijo + k)] = v
        }
    }

    suspend fun guardarMapa(m: MapaTeclas) {
        contexto.almacen.edit { it[kMapa] = m.serializar() }
    }

    suspend fun guardarSesion(s: SesionGuardada) {
        contexto.almacen.edit {
            it[kSesionHash] = s.hash
            it[kSesionSetlist] = s.setlistId
            it[kSesionIndice] = s.indiceSetlist
            it[kSesionVista] = s.vista
            it[kSesionBloqueo] = s.bloqueado
            it[kSesionAbierta] = true
        }
    }

    /** El lector se cerró de forma normal: en el próximo arranque no se reabre solo. */
    suspend fun marcarLectorCerrado() {
        contexto.almacen.edit { it[kSesionAbierta] = false }
    }

    /** Sesión interrumpida (la app murió con el lector abierto), o null si se cerró con normalidad. */
    val sesionInterrumpida: Flow<SesionGuardada?> = datos.map { p ->
        val hash = p[kSesionHash]
        if (p[kSesionAbierta] != true || hash == null) null
        else SesionGuardada(
            hash, p[kSesionSetlist] ?: 0L, p[kSesionIndice] ?: 0, p[kSesionVista] ?: 0, p[kSesionBloqueo] ?: false,
        )
    }

    /** Última sesión, abierta o no (para el botón "continuar"). */
    val ultimaSesion: Flow<SesionGuardada?> = datos.map { p ->
        val hash = p[kSesionHash] ?: return@map null
        SesionGuardada(
            hash, p[kSesionSetlist] ?: 0L, p[kSesionIndice] ?: 0, p[kSesionVista] ?: 0, p[kSesionBloqueo] ?: false,
        )
    }
}
