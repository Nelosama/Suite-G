package com.suiteg.app.audio

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.suiteg.app.datos.RepoGrabaciones
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoGrabacion(
    val activa: Boolean = false,
    val id: Long = 0,
    /** `SystemClock.elapsedRealtime()` al empezar; la interfaz calcula el tiempo transcurrido con él. */
    val inicioMs: Long = 0,
    val error: String? = null,
)

/**
 * Lado de la aplicación de la grabación: arranca y para el servicio (que vive en otro proceso), guarda la
 * grabación en la base de datos y se entera del final por un broadcast.
 */
class ClienteGrabacion(
    private val app: Application,
    private val repo: RepoGrabaciones,
    private val scope: CoroutineScope,
) {
    private val _estado = MutableStateFlow(EstadoGrabacion())
    val estado: StateFlow<EstadoGrabacion> = _estado.asStateFlow()

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(ServicioGrabacion.EXTRA_ID, -1L)
            val ok = intent.getBooleanExtra(ServicioGrabacion.EXTRA_OK, false)
            val dur = intent.getLongExtra(ServicioGrabacion.EXTRA_DURACION, 0L)
            val error = intent.getStringExtra(ServicioGrabacion.EXTRA_ERROR)
            scope.launch {
                if (id >= 0) repo.finalizar(id, dur, ok)
                soltarVigilancia()
                _estado.value = EstadoGrabacion(error = error)
            }
        }
    }

    // Vigila que el proceso de grabación siga vivo: si muere sin avisar, no se queda el "REC" encendido.
    private var vigilancia: ServiceConnection? = null

    init {
        ContextCompat.registerReceiver(
            app, receptor, IntentFilter(ServicioGrabacion.ACCION_TERMINADA), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun tienePermiso(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Espacio libre en MB. */
    fun espacioLibreMb(): Long = app.filesDir.usableSpace / (1024 * 1024)

    suspend fun iniciar(hash: String?, titulo: String): Boolean {
        if (_estado.value.activa) return true
        if (!tienePermiso()) {
            _estado.value = EstadoGrabacion(error = "Falta el permiso de micrófono")
            return false
        }
        if (espacioLibreMb() < ESPACIO_MINIMO_MB) {
            _estado.value = EstadoGrabacion(error = "Poco espacio libre (${espacioLibreMb()} MB)")
            return false
        }
        val carpeta = File(app.filesDir, "grabaciones").apply { mkdirs() }
        val archivo = File(carpeta, "grab_${System.currentTimeMillis()}.aac")
        val id = repo.crear(hash, titulo, archivo.absolutePath)
        val intent = Intent(app, ServicioGrabacion::class.java)
            .setAction(ServicioGrabacion.ACCION_INICIAR)
            .putExtra(ServicioGrabacion.EXTRA_RUTA, archivo.absolutePath)
            .putExtra(ServicioGrabacion.EXTRA_ID, id)
        try {
            ContextCompat.startForegroundService(app, intent)
        } catch (e: Exception) {
            repo.finalizar(id, 0, false)
            _estado.value = EstadoGrabacion(error = "No se pudo iniciar la grabación (${e.message ?: "error"})")
            return false
        }
        vigilar()
        _estado.value = EstadoGrabacion(activa = true, id = id, inicioMs = SystemClock.elapsedRealtime())
        return true
    }

    fun detener() {
        if (!_estado.value.activa) return
        runCatching { app.startService(Intent(app, ServicioGrabacion::class.java).setAction(ServicioGrabacion.ACCION_DETENER)) }
    }

    private fun vigilar() {
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
            override fun onServiceDisconnected(name: ComponentName) {
                val e = _estado.value
                if (!e.activa) return
                // El proceso murió sin avisar: se conserva lo grabado hasta ese momento.
                scope.launch {
                    repo.recuperarHuerfanas()
                    soltarVigilancia()
                    _estado.value = EstadoGrabacion(error = "La grabación se interrumpió; se conservó lo grabado")
                }
            }
        }
        vigilancia = c
        app.bindService(Intent(app, ServicioGrabacion::class.java), c, 0)
    }

    private fun soltarVigilancia() {
        vigilancia?.let { runCatching { app.unbindService(it) } }
        vigilancia = null
    }

    companion object {
        private const val ESPACIO_MINIMO_MB = 300L
    }
}
