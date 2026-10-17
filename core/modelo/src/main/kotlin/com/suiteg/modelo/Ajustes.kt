package com.suiteg.modelo

enum class ModoInversion { NINGUNA, TODO, TONALIDAD, FONDO }

enum class Orientacion { RETRATO, PAISAJE, ACTUAL }

enum class PerfilUso { ENSAYO, CONCIERTO, FOSO_OSCURO, PERSONALIZADO }

/**
 * Ajustes de la aplicación. Cada función se puede activar o desactivar por separado;
 * los perfiles ([Perfiles]) son combinaciones predefinidas.
 */
data class AjustesApp(
    // --- Funciones opcionales ---
    val puntosGiro: Boolean = true,
    val marcadores: Boolean = true,
    val setlists: Boolean = true,
    val notas: Boolean = true,
    val anotaciones: Boolean = true,
    val modoConcierto: Boolean = true,
    val avanceTempo: Boolean = false,
    val metronomo: Boolean = false,
    val grabacion: Boolean = false,
    // --- Visualización ---
    val perfil: PerfilUso = PerfilUso.ENSAYO,
    val inversion: ModoInversion = ModoInversion.NINGUNA,
    val ajusteVista: AjusteVista = AjusteVista.PAGINA_COMPLETA,
    val orientacion: Orientacion = Orientacion.ACTUAL,
    val pantallaEncendida: Boolean = true,
    val zoomTactil: Boolean = true,
    val mostrarLatencia: Boolean = false,
    // --- Pedal ---
    val cooldownMs: Int = 250,
    val ventanaDuplicadoMs: Int = 40,
    val ventanaGrupoMs: Int = 150,
    val cruzarAlRetroceder: Boolean = false,
    // --- Anotaciones ---
    val dedoDibuja: Boolean = false,
    val rechazoPalma: Boolean = true,
    // --- Modo concierto ---
    val bloqueoAlAbrir: Boolean = false,
    val bloqueoMaximo: Boolean = false,
    val desbloqueoMs: Int = 3000,
    // --- Tempo, metrónomo, grabación ---
    val avisoGiroMs: Int = 2000,
    val pasoVelocidadPct: Int = 3,
    val volumenMetronomo: Float = 0.8f,
    val grabarMarcasGiro: Boolean = true,
)

object Perfiles {
    /** Devuelve [base] con la combinación de funciones propia del perfil. Los demás ajustes se conservan. */
    fun aplicar(perfil: PerfilUso, base: AjustesApp): AjustesApp = when (perfil) {
        PerfilUso.ENSAYO -> base.copy(
            perfil = perfil,
            puntosGiro = true, marcadores = true, setlists = true, notas = true,
            anotaciones = true, modoConcierto = true,
            avanceTempo = true, metronomo = true, grabacion = true,
            inversion = ModoInversion.NINGUNA,
            bloqueoAlAbrir = false,
            cooldownMs = 200,
        )
        PerfilUso.CONCIERTO -> base.copy(
            perfil = perfil,
            puntosGiro = true, marcadores = true, setlists = true, notas = true,
            anotaciones = false, modoConcierto = true,
            avanceTempo = false, metronomo = false, grabacion = false,
            inversion = ModoInversion.NINGUNA,
            bloqueoAlAbrir = true,
            cooldownMs = 350,
        )
        PerfilUso.FOSO_OSCURO -> base.copy(
            perfil = perfil,
            puntosGiro = true, marcadores = true, setlists = true, notas = true,
            anotaciones = false, modoConcierto = true,
            avanceTempo = false, metronomo = false, grabacion = false,
            inversion = ModoInversion.TODO,
            bloqueoAlAbrir = true,
            cooldownMs = 350,
        )
        PerfilUso.PERSONALIZADO -> base.copy(perfil = perfil)
    }
}

/**
 * Serialización de [AjustesApp] a pares clave/valor de texto (para DataStore).
 * Los valores desconocidos o corruptos caen al valor por defecto: nunca lanza.
 */
object AjustesCodec {

    fun aMapa(a: AjustesApp): Map<String, String> = mapOf(
        "puntosGiro" to a.puntosGiro.toString(),
        "marcadores" to a.marcadores.toString(),
        "setlists" to a.setlists.toString(),
        "notas" to a.notas.toString(),
        "anotaciones" to a.anotaciones.toString(),
        "modoConcierto" to a.modoConcierto.toString(),
        "avanceTempo" to a.avanceTempo.toString(),
        "metronomo" to a.metronomo.toString(),
        "grabacion" to a.grabacion.toString(),
        "perfil" to a.perfil.name,
        "inversion" to a.inversion.name,
        "ajusteVista" to a.ajusteVista.name,
        "orientacion" to a.orientacion.name,
        "pantallaEncendida" to a.pantallaEncendida.toString(),
        "zoomTactil" to a.zoomTactil.toString(),
        "mostrarLatencia" to a.mostrarLatencia.toString(),
        "cooldownMs" to a.cooldownMs.toString(),
        "ventanaDuplicadoMs" to a.ventanaDuplicadoMs.toString(),
        "ventanaGrupoMs" to a.ventanaGrupoMs.toString(),
        "cruzarAlRetroceder" to a.cruzarAlRetroceder.toString(),
        "dedoDibuja" to a.dedoDibuja.toString(),
        "rechazoPalma" to a.rechazoPalma.toString(),
        "bloqueoAlAbrir" to a.bloqueoAlAbrir.toString(),
        "bloqueoMaximo" to a.bloqueoMaximo.toString(),
        "desbloqueoMs" to a.desbloqueoMs.toString(),
        "avisoGiroMs" to a.avisoGiroMs.toString(),
        "pasoVelocidadPct" to a.pasoVelocidadPct.toString(),
        "volumenMetronomo" to a.volumenMetronomo.toString(),
        "grabarMarcasGiro" to a.grabarMarcasGiro.toString(),
    )

    fun desdeMapa(m: Map<String, String>, d: AjustesApp = AjustesApp()): AjustesApp {
        fun bool(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
        fun int(k: String, def: Int, r: IntRange) = (m[k]?.toIntOrNull() ?: def).coerceIn(r)
        fun flt(k: String, def: Float) = (m[k]?.toFloatOrNull() ?: def).coerceIn(0f, 1f)
        fun <E : Enum<E>> enum(k: String, def: E, valores: List<E>): E =
            m[k]?.let { v -> valores.firstOrNull { it.name == v } } ?: def
        return AjustesApp(
            puntosGiro = bool("puntosGiro", d.puntosGiro),
            marcadores = bool("marcadores", d.marcadores),
            setlists = bool("setlists", d.setlists),
            notas = bool("notas", d.notas),
            anotaciones = bool("anotaciones", d.anotaciones),
            modoConcierto = bool("modoConcierto", d.modoConcierto),
            avanceTempo = bool("avanceTempo", d.avanceTempo),
            metronomo = bool("metronomo", d.metronomo),
            grabacion = bool("grabacion", d.grabacion),
            perfil = enum("perfil", d.perfil, PerfilUso.entries),
            inversion = enum("inversion", d.inversion, ModoInversion.entries),
            ajusteVista = enum("ajusteVista", d.ajusteVista, AjusteVista.entries),
            orientacion = enum("orientacion", d.orientacion, Orientacion.entries),
            pantallaEncendida = bool("pantallaEncendida", d.pantallaEncendida),
            zoomTactil = bool("zoomTactil", d.zoomTactil),
            mostrarLatencia = bool("mostrarLatencia", d.mostrarLatencia),
            cooldownMs = int("cooldownMs", d.cooldownMs, 0..1000),
            ventanaDuplicadoMs = int("ventanaDuplicadoMs", d.ventanaDuplicadoMs, 0..500),
            ventanaGrupoMs = int("ventanaGrupoMs", d.ventanaGrupoMs, 50..500),
            cruzarAlRetroceder = bool("cruzarAlRetroceder", d.cruzarAlRetroceder),
            dedoDibuja = bool("dedoDibuja", d.dedoDibuja),
            rechazoPalma = bool("rechazoPalma", d.rechazoPalma),
            bloqueoAlAbrir = bool("bloqueoAlAbrir", d.bloqueoAlAbrir),
            bloqueoMaximo = bool("bloqueoMaximo", d.bloqueoMaximo),
            desbloqueoMs = int("desbloqueoMs", d.desbloqueoMs, 1000..10000),
            avisoGiroMs = int("avisoGiroMs", d.avisoGiroMs, 500..10000),
            pasoVelocidadPct = int("pasoVelocidadPct", d.pasoVelocidadPct, 1..20),
            volumenMetronomo = flt("volumenMetronomo", d.volumenMetronomo),
            grabarMarcasGiro = bool("grabarMarcasGiro", d.grabarMarcasGiro),
        )
    }
}
