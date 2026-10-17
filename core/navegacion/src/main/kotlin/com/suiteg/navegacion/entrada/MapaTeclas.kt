package com.suiteg.navegacion.entrada

/** Asignación inmutable tecla -> acción. Varias teclas pueden llevar a la misma acción. */
class MapaTeclas private constructor(private val mapa: Map<ClaveTecla, Accion>) {
    operator fun get(clave: ClaveTecla): Accion? = mapa[clave]
    val asignaciones: Map<ClaveTecla, Accion> get() = mapa
    val vacio: Boolean get() = mapa.isEmpty()

    fun teclasDe(accion: Accion): List<ClaveTecla> = mapa.filterValues { it == accion }.keys.toList()

    fun descriptores(): Set<String> = mapa.keys.mapTo(HashSet()) { it.descriptorDispositivo }

    /**
     * Asigna [teclas] a [accion]. Reemplaza lo que [accion] tuviera antes, y quita esas teclas de
     * cualquier otra acción (una tecla solo puede hacer una cosa).
     */
    fun asignar(accion: Accion, teclas: Collection<ClaveTecla>): MapaTeclas {
        val nuevo = LinkedHashMap<ClaveTecla, Accion>()
        for ((k, v) in mapa) if (v != accion && k !in teclas) nuevo[k] = v
        for (t in teclas) nuevo[t] = accion
        return MapaTeclas(nuevo)
    }

    fun quitar(accion: Accion): MapaTeclas = MapaTeclas(mapa.filterValues { it != accion })

    /** Texto estable para guardar: una línea por asignación `descriptor<TAB>keyCode<TAB>ACCION`. */
    fun serializar(): String = mapa.entries.joinToString("\n") { (k, v) ->
        "${k.descriptorDispositivo.replace('\t', ' ').replace('\n', ' ')}\t${k.keyCode}\t${v.name}"
    }

    companion object {
        val VACIO = MapaTeclas(emptyMap())

        /** Ignora líneas corruptas o acciones desconocidas (p. ej. de una versión futura). */
        fun deserializar(texto: String?): MapaTeclas {
            if (texto.isNullOrBlank()) return VACIO
            val m = LinkedHashMap<ClaveTecla, Accion>()
            for (linea in texto.lineSequence()) {
                val partes = linea.split('\t')
                if (partes.size != 3) continue
                val codigo = partes[1].toIntOrNull() ?: continue
                val accion = Accion.entries.firstOrNull { it.name == partes[2] } ?: continue
                if (!TeclasNoAsignables.esAsignable(codigo)) continue
                m[ClaveTecla(partes[0], codigo)] = accion
            }
            return MapaTeclas(m)
        }
    }
}
