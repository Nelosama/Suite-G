package com.suiteg.app.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentoE::class, PuntoGiroE::class, MarcadorE::class, TramoTempoE::class, ConfigTempoE::class,
        SetlistE::class, SetlistItemE::class, TrazoE::class, GrabacionE::class, MarcaGrabacionE::class,
    ],
    version = 1,
    // Sin exportar: las variantes debug y release escriben a la vez el mismo archivo y Room falla con
    // "Empty schema file". Al añadir la primera migración, activar el plugin de Gradle de Room (esquemas por variante).
    exportSchema = false,
)
abstract class BaseDatos : RoomDatabase() {
    abstract fun documentos(): DocumentoDao
    abstract fun partitura(): PartituraDao
    abstract fun setlists(): SetlistDao
    abstract fun trazos(): TrazoDao
    abstract fun grabaciones(): GrabacionDao

    companion object {
        /**
         * Sin `fallbackToDestructiveMigration`: si una futura versión cambia el esquema, hay que escribir la
         * migración. Perder las anotaciones de un músico por una actualización no es aceptable.
         */
        fun crear(contexto: Context): BaseDatos =
            Room.databaseBuilder(contexto.applicationContext, BaseDatos::class.java, "suiteg.db").build()
    }
}
