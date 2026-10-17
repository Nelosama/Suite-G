package com.suiteg.modelo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformacionVistaTest {
    private val eps = 0.001f

    @Test
    fun `ida y vuelta pantalla-pagina conserva la coordenada`() {
        val t = TransformacionVista.ajustar(595f, 842f, 1600f, 2400f, AjusteVista.PAGINA_COMPLETA, zoom = 2f, desplazX = 30f, desplazY = 50f)
        for ((nx, ny) in listOf(0.1f to 0.2f, 0.5f to 0.5f, 0.9f to 0.95f)) {
            assertEquals(nx, t.xAPagina(t.xAPantalla(nx)), eps)
            assertEquals(ny, t.yAPagina(t.yAPantalla(ny)), eps)
        }
    }

    @Test
    fun `pagina completa se centra cuando sobra espacio`() {
        val t = TransformacionVista.ajustar(100f, 200f, 400f, 400f, AjusteVista.PAGINA_COMPLETA)
        // escala = min(4, 2) = 2 -> página 200x400, centrada horizontalmente
        assertEquals(2f, t.escala, eps)
        assertEquals(100f, t.xAPantalla(0f), eps)
        assertEquals(300f, t.xAPantalla(1f), eps)
        assertEquals(0f, t.yAPantalla(0f), eps)
    }

    @Test
    fun `ajuste a ancho respeta yInicio y se limita al borde inferior`() {
        // Página 100x400 en ventana 200x200 -> escala 2, página 200x800
        val a = TransformacionVista.ajustar(100f, 400f, 200f, 200f, AjusteVista.ANCHO, yInicio = 0.5f)
        assertEquals(0f, a.yAPantalla(0.5f), eps) // el punto 0.5 queda arriba
        val b = TransformacionVista.ajustar(100f, 400f, 200f, 200f, AjusteVista.ANCHO, yInicio = 1f)
        assertEquals(200f, b.yAPantalla(1f), eps) // no se pasa del final: el borde inferior queda abajo
    }

    @Test
    fun `una anotacion sobrevive a rotar la ventana`() {
        val punto = 0.3f to 0.6f
        val vertical = TransformacionVista.ajustar(595f, 842f, 1200f, 1920f, AjusteVista.PAGINA_COMPLETA)
        val horizontal = TransformacionVista.ajustar(595f, 842f, 1920f, 1200f, AjusteVista.PAGINA_COMPLETA)
        // La posición normalizada recuperada desde cualquier orientación es la misma
        val sv = vertical.xAPantalla(punto.first) to vertical.yAPantalla(punto.second)
        val sh = horizontal.xAPantalla(punto.first) to horizontal.yAPantalla(punto.second)
        assertEquals(punto.first, vertical.xAPagina(sv.first), eps)
        assertEquals(punto.second, horizontal.yAPagina(sh.second), eps)
        assertTrue(sv != sh) // en pantalla están en sitios distintos
    }

    @Test
    fun `el zoom se limita al maximo`() {
        val t = TransformacionVista.ajustar(100f, 100f, 100f, 100f, AjusteVista.ANCHO, zoom = 99f)
        assertEquals(TransformacionVista.ZOOM_MAXIMO, t.escala / 1f, eps)
    }

    @Test
    fun `ajustar con origen limita el origen a los bordes`() {
        val t = TransformacionVista.ajustarConOrigen(100f, 400f, 200f, 200f, AjusteVista.ANCHO, 1f, origenX = -50f, origenY = 9999f)
        assertEquals(0f, t.origenX, eps) // la página escalada (200) coincide con la ventana: sin desplazamiento horizontal
        assertEquals(600f, t.origenY, eps) // 800 - 200
    }

    @Test
    fun `ajustar con origen centra si la pagina cabe`() {
        val t = TransformacionVista.ajustarConOrigen(100f, 200f, 400f, 400f, AjusteVista.PAGINA_COMPLETA, 1f, origenX = 500f, origenY = 500f)
        assertEquals(-100f, t.origenX, eps) // (200 - 400) / 2
        assertEquals(0f, t.origenY, eps)
    }
}
