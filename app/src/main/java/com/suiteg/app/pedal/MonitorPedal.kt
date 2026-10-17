package com.suiteg.app.pedal

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DispositivoEntrada(val id: Int, val descriptor: String, val nombre: String)

sealed interface EstadoPedal {
    /** Todavía no se ha asignado ninguna tecla. */
    data object SinConfigurar : EstadoPedal

    /** Hay teclas asignadas pero el dispositivo no está conectado. */
    data object Desconectado : EstadoPedal

    data class Conectado(val nombres: List<String>) : EstadoPedal
}

/**
 * Vigila la conexión del pedal (Bluetooth o USB-OTG: para Android son lo mismo, un dispositivo de teclado).
 *
 * El pedal se identifica por su `descriptor`, que es estable entre reconexiones, y no por el `deviceId`, que
 * cambia cada vez. Así, cuando el sistema reconecta el pedal ya emparejado, la asignación sigue valiendo sin
 * hacer nada. Android no permite a una app forzar la reconexión Bluetooth; lo que sí hace esta clase es
 * detectarla al instante y reflejarla en el indicador.
 */
class MonitorPedal(contexto: Context) {
    private val manager = contexto.getSystemService(Context.INPUT_SERVICE) as InputManager
    private var descriptores: Set<String> = emptySet()

    private val _dispositivos = MutableStateFlow<List<DispositivoEntrada>>(emptyList())
    val dispositivos: StateFlow<List<DispositivoEntrada>> = _dispositivos.asStateFlow()

    private val _estado = MutableStateFlow<EstadoPedal>(EstadoPedal.SinConfigurar)
    val estado: StateFlow<EstadoPedal> = _estado.asStateFlow()

    private val oyente = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refrescar()
        override fun onInputDeviceRemoved(deviceId: Int) = refrescar()
        override fun onInputDeviceChanged(deviceId: Int) = refrescar()
    }

    init {
        manager.registerInputDeviceListener(oyente, Handler(Looper.getMainLooper()))
        refrescar()
    }

    /** Descriptores de los dispositivos que tienen teclas asignadas. */
    fun establecerDescriptores(d: Set<String>) {
        descriptores = d
        recalcular()
    }

    fun refrescar() {
        _dispositivos.value = listar()
        recalcular()
    }

    private fun recalcular() {
        _estado.value = when {
            descriptores.isEmpty() -> EstadoPedal.SinConfigurar
            else -> {
                val activos = _dispositivos.value.filter { it.descriptor in descriptores }
                if (activos.isEmpty()) EstadoPedal.Desconectado else EstadoPedal.Conectado(activos.map { it.nombre }.distinct())
            }
        }
    }

    private fun listar(): List<DispositivoEntrada> = InputDevice.getDeviceIds().toList().mapNotNull { id ->
        val d = InputDevice.getDevice(id) ?: return@mapNotNull null
        val esEntrada = d.sources and (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_GAMEPAD) != 0
        if (d.isVirtual || !esEntrada) null else DispositivoEntrada(id, d.descriptor, d.name)
    }

    fun cerrar() = manager.unregisterInputDeviceListener(oyente)
}
