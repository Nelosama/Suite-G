package com.suiteg.app

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.suiteg.app.pedal.aEventoTecla
import com.suiteg.app.ui.AppRaiz
import com.suiteg.app.ui.ControlVentana
import com.suiteg.app.ui.TemaSuiteG
import com.suiteg.modelo.AjustesApp
import com.suiteg.modelo.Orientacion
import com.suiteg.navegacion.entrada.ResultadoTecla
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), ControlVentana {
    private val contenedor: Contenedor get() = (application as SuiteGApp).contenedor
    private val pdfEntrante = MutableStateFlow<Uri?>(null)
    private var alResolverMicro: ((Boolean) -> Unit)? = null
    private var fijada = false

    private val permisoMicro = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        alResolverMicro?.invoke(ok)
        alResolverMicro = null
    }
    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recogerIntent(intent)
        setContent {
            TemaSuiteG {
                AppRaiz(contenedor, this, pdfEntrante, arranqueEnFrio = savedInstanceState == null)
            }
        }
        lifecycleScope.launch { contenedor.ajustes.collect(::aplicarAjustesDeVentana) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recogerIntent(intent)
    }

    private fun recogerIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { pdfEntrante.value = it }
    }

    /**
     * ÚNICA puerta del teclado. Se intercepta aquí, antes de que el evento llegue a ninguna vista ni al
     * sistema: si la tecla pertenece al pedal se consume (así Android no sube el volumen, etc.).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        when (contenedor.procesadorPedal.procesar(event.aEventoTecla())) {
            ResultadoTecla.NoManejado -> super.dispatchKeyEvent(event)
            else -> true
        }

    private fun aplicarAjustesDeVentana(a: AjustesApp) {
        if (a.pantallaEncendida) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = when (a.orientacion) {
            Orientacion.RETRATO -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Orientacion.PAISAJE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Orientacion.ACTUAL -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) contenedor.sesion?.alPocaMemoria()
    }

    // --- ControlVentana ---

    override fun inmersivo(activo: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (activo) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Fijación de pantalla (`startLockTask`): bloquea Inicio, Recientes y las notificaciones. Android muestra su propio
     * aviso la primera vez y puede estar desactivada en Ajustes; si falla, queda el bloqueo propio de la app.
     */
    override fun fijarPantalla(activo: Boolean) {
        if (activo == fijada) return
        try {
            if (activo) startLockTask() else stopLockTask()
            fijada = activo
        } catch (_: IllegalStateException) {
            fijada = false
        } catch (_: SecurityException) {
            fijada = false
        }
    }

    override fun pedirMicrofono(alResolver: (Boolean) -> Unit) {
        alResolverMicro = alResolver
        permisoMicro.launch(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33) permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
