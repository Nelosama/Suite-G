package com.suiteg.navegacion.entrada

/**
 * Evento de teclado simplificado, independiente de Android.
 * El adaptador de Android traduce `KeyEvent` a esto.
 */
data class EventoTecla(
    /** `InputDevice.getDescriptor()`: estable entre reconexiones, a diferencia de deviceId. */
    val descriptorDispositivo: String,
    val deviceId: Int,
    val keyCode: Int,
    val esDown: Boolean,
    /** `KeyEvent.getRepeatCount()`: 0 en la pulsación original, >0 en repeticiones por mantener pulsado. */
    val repeticion: Int,
    val downTimeMs: Long,
    val eventTimeMs: Long,
)

/** Una tecla concreta de un dispositivo concreto: no depende de ningún keycode de fabricante. */
data class ClaveTecla(val descriptorDispositivo: String, val keyCode: Int)

/** Acciones que se pueden asignar al pedal. */
enum class Accion(val etiqueta: String) {
    SIGUIENTE("Avanzar"),
    ANTERIOR("Retroceder"),
    SIGUIENTE_CANCION("Siguiente canción"),
    CANCION_ANTERIOR("Canción anterior"),
    REPETIR_SECCION("Repetir sección"),
    TEMPO_INICIAR_PAUSAR("Tempo: iniciar / pausar"),
    TEMPO_MAS_RAPIDO("Tempo: más rápido"),
    TEMPO_MAS_LENTO("Tempo: más lento"),
    TEMPO_RESINCRONIZAR("Tempo: resincronizar aquí"),
    METRONOMO_ALTERNAR("Metrónomo: encender / apagar"),
    GRABACION_ALTERNAR("Grabación: iniciar / detener"),
}

/**
 * Teclas que Android reserva y no se pueden consumir desde una app (o que no deben usarse).
 * Valores de `KeyEvent.KEYCODE_*`, copiados aquí para mantener este módulo libre de Android.
 */
object TeclasNoAsignables {
    val codigos: Set<Int> = setOf(
        3,   // HOME
        4,   // BACK
        26,  // POWER
        120, // SYSRQ
        187, // APP_SWITCH
        219, // ASSIST
        223, // SLEEP
        224, // WAKEUP
        231, // VOICE_ASSIST
        276, // SOFT_SLEEP
    )

    fun esAsignable(keyCode: Int) = keyCode !in codigos
}
