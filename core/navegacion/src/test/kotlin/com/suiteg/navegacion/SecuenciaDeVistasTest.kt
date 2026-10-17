package com.suiteg.navegacion

import com.suiteg.modelo.Posicion
import com.suiteg.modelo.PuntoGiro
import com.suiteg.modelo.TipoGiro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SecuenciaDeVistasTest {
    private fun punto(p: Int, y: Float, tipo: TipoGiro = TipoGiro.LIBRE, compases: Int? = null) =
        PuntoGiro(hashArchivo = "h", posicion = Posicion(p, y), tipo = tipo, compases = compases)

    @Test
    fun `sin puntos hay una vista por pagina`() {
        val s = SecuenciaDeVistas.construir(3, emptyList())
        assertEquals(listOf(Posicion(0), Posicion(1), Posicion(2)), s.vistas.map { it.posicion })
    }

    @Test
    fun `con puntos solo se recorren los puntos mas el inicio`() {
        val s = SecuenciaDeVistas.construir(5, listOf(punto(2, 0f), punto(4, 0.5f)))
        assertEquals(listOf(Posicion(0), Posicion(2), Posicion(4, 0.5f)), s.vistas.map { it.posicion })
    }

    @Test
    fun `si el usuario marca el inicio no se duplica`() {
        val s = SecuenciaDeVistas.construir(2, listOf(punto(0, 0f), punto(1, 0.5f)))
        assertEquals(2, s.tamano)
    }

    @Test
    fun `puntos desordenados se ordenan y los duplicados se fusionan conservando los compases`() {
        val s = SecuenciaDeVistas.construir(
            3,
            listOf(punto(1, 0.5f), punto(0, 0f), punto(1, 0.5005f, compases = 8), punto(1, 0.7f)),
        )
        assertEquals(listOf(Posicion(0), Posicion(1, 0.5f), Posicion(1, 0.7f)), s.vistas.map { it.posicion })
        assertEquals(8, s[1].compases)
    }

    @Test
    fun `los giros de tipo pagina caen al inicio de la pagina`() {
        val s = SecuenciaDeVistas.construir(3, listOf(punto(1, 0.4f, TipoGiro.PAGINA)))
        assertEquals(Posicion(1, 0f), s[1].posicion)
    }

    @Test
    fun `puntos fuera del documento se descartan y no deja la secuencia vacia`() {
        val s = SecuenciaDeVistas.construir(2, listOf(punto(7, 0f)))
        assertEquals(2, s.tamano) // todos los puntos eran inválidos: se vuelve a una vista por página
    }

    @Test
    fun `compases no positivos se ignoran`() {
        val s = SecuenciaDeVistas.construir(2, listOf(punto(1, 0f, compases = 0)))
        assertEquals(null, s[1].compases)
    }

    @Test
    fun `indiceDe devuelve la vista que contiene la posicion`() {
        val s = SecuenciaDeVistas.construir(3, listOf(punto(0, 0f), punto(0, 0.5f), punto(2, 0f)))
        assertEquals(0, s.indiceDe(Posicion(0, 0.2f)))
        assertEquals(1, s.indiceDe(Posicion(0, 0.5f)))
        assertEquals(1, s.indiceDe(Posicion(1, 0.9f)))
        assertEquals(2, s.indiceDe(Posicion(2, 0f)))
    }

    @Test
    fun `documento sin paginas es invalido`() {
        assertFailsWith<IllegalArgumentException> { SecuenciaDeVistas.construir(0, emptyList()) }
    }
}
