package com.suiteg.app

import android.app.Application
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SuiteGApp : Application() {

    /** Solo existe en el proceso principal; los procesos `:grabacion` y `:validador` no cargan nada de esto. */
    val contenedor: Contenedor by lazy { Contenedor(this) }

    override fun onCreate() {
        super.onCreate()
        if (getProcessName() == packageName) {
            registrarRegistroDeFallos()
        }
    }

    /**
     * Anota cualquier fallo no controlado en un archivo (visible en Ajustes) antes de que el sistema cierre la
     * app. Después de un cierre inesperado, la app reabre la partitura donde estaba (ver MainActivity).
     */
    private fun registrarRegistroDeFallos() {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching {
                val archivo = File(filesDir, ARCHIVO_FALLOS)
                if (archivo.length() > 200_000) archivo.delete()
                PrintWriter(FileWriter(archivo, true)).use { w ->
                    w.println("=== ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} hilo=${hilo.name}")
                    error.printStackTrace(w)
                }
            }
            anterior?.uncaughtException(hilo, error)
        }
    }

    companion object {
        const val ARCHIVO_FALLOS = "fallos.log"
    }
}
