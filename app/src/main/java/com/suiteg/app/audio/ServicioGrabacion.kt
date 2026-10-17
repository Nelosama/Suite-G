package com.suiteg.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.suiteg.app.MainActivity
import com.suiteg.app.R

/**
 * Grabación de audio en un servicio de primer plano que vive en su PROPIO proceso (`:grabacion`): si el
 * grabador falla o el sistema lo mata, la partitura sigue abierta.
 *
 * Formato AAC en flujo ADTS (.aac) a 128 kbps mono: a diferencia de MP4, un flujo ADTS se puede reproducir
 * aunque se corte a mitad (no necesita un índice final), así que una grabación interrumpida no se pierde.
 */
class ServicioGrabacion : Service() {
    private var grabadora: MediaRecorder? = null
    private var inicioMs = 0L
    private var idGrabacion = -1L

    // Un binder vacío: el cliente solo lo usa para enterarse de si este proceso muere (onServiceDisconnected).
    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_INICIAR -> iniciar(intent.getStringExtra(EXTRA_RUTA).orEmpty(), intent.getLongExtra(EXTRA_ID, -1L))
            ACCION_DETENER -> detener(ok = true)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun iniciar(ruta: String, id: Long) {
        // Hay que entrar en primer plano de inmediato (el sistema da pocos segundos tras startForegroundService).
        entrarEnPrimerPlano()
        if (grabadora != null || ruta.isEmpty()) return
        idGrabacion = id
        val mr = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val sinProcesar = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            // Sin procesar: sin control automático de ganancia, que aplasta la dinámica del chelo.
            mr.setAudioSource(if (sinProcesar) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(BITRATE)
            mr.setAudioSamplingRate(44_100)
            mr.setAudioChannels(1)
            mr.setOutputFile(ruta)
            mr.prepare()
            mr.start()
            grabadora = mr
            inicioMs = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            runCatching { mr.release() }
            avisar(ok = false, duracionMs = 0, error = "No se pudo iniciar la grabación (${e.message ?: e.javaClass.simpleName})")
            terminar()
        }
    }

    private fun detener(ok: Boolean) {
        val mr = grabadora
        if (mr == null) {
            terminar()
            return
        }
        grabadora = null
        val duracion = SystemClock.elapsedRealtime() - inicioMs
        var correcto = ok
        try {
            mr.stop()
        } catch (e: RuntimeException) {
            correcto = false // stop() lanza si no se llegó a grabar nada
        }
        runCatching { mr.release() }
        avisar(correcto, duracion, if (correcto) null else "La grabación quedó vacía")
        terminar()
    }

    private fun avisar(ok: Boolean, duracionMs: Long, error: String?) {
        sendBroadcast(
            Intent(ACCION_TERMINADA).setPackage(packageName)
                .putExtra(EXTRA_ID, idGrabacion)
                .putExtra(EXTRA_DURACION, duracionMs)
                .putExtra(EXTRA_OK, ok)
                .putExtra(EXTRA_ERROR, error),
        )
    }

    private fun terminar() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Si el servicio se destruye grabando, se cierra el archivo con elegancia.
        if (grabadora != null) detener(ok = true)
        super.onDestroy()
    }

    private fun entrarEnPrimerPlano() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CANAL, getString(R.string.canal_grabacion), NotificationManager.IMPORTANCE_LOW),
        )
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificacion: Notification = Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(getString(R.string.notificacion_grabando))
            .setContentText(getString(R.string.notificacion_grabando_detalle))
            .setOngoing(true)
            .setContentIntent(abrir)
            .build()
        startForeground(ID_NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    companion object {
        const val ACCION_INICIAR = "com.suiteg.partituras.GRABACION_INICIAR"
        const val ACCION_DETENER = "com.suiteg.partituras.GRABACION_DETENER"
        const val ACCION_TERMINADA = "com.suiteg.partituras.GRABACION_TERMINADA"
        const val EXTRA_RUTA = "ruta"
        const val EXTRA_ID = "id"
        const val EXTRA_DURACION = "duracionMs"
        const val EXTRA_OK = "ok"
        const val EXTRA_ERROR = "error"

        /** 128 kbps = 16 000 bytes por segundo (se usa para estimar la duración de grabaciones recuperadas). */
        const val BITRATE = 128_000

        private const val CANAL = "grabacion"
        private const val ID_NOTIFICACION = 7
    }
}
