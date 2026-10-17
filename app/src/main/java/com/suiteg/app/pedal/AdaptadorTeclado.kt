package com.suiteg.app.pedal

import android.view.KeyEvent
import com.suiteg.navegacion.entrada.EventoTecla

/** Traduce un `KeyEvent` de Android al evento neutro que entiende el procesador del pedal. */
fun KeyEvent.aEventoTecla(): EventoTecla = EventoTecla(
    descriptorDispositivo = device?.descriptor.orEmpty(),
    deviceId = deviceId,
    keyCode = keyCode,
    esDown = action == KeyEvent.ACTION_DOWN,
    repeticion = repeatCount,
    downTimeMs = downTime,
    eventTimeMs = eventTime,
)

/** Nombre legible de una tecla, para mostrarlo en la pantalla de asignación del pedal. */
fun nombreTecla(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
