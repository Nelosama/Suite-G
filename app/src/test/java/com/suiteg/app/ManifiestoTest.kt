package com.suiteg.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** La app es 100 % offline: el manifiesto no puede pedir INTERNET ni dejar que una librería lo añada. */
class ManifiestoTest {
    private val manifiesto: String = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        .map(::File).firstOrNull { it.exists() }?.readText() ?: fail("No se encontró AndroidManifest.xml")

    @Test
    fun `no declara el permiso INTERNET y elimina el de las librerias`() {
        val lineas = manifiesto.lines().filter { "android.permission.INTERNET" in it }
        assertTrue(lineas.isNotEmpty(), "debe haber una línea que elimine INTERNET para blindarlo")
        assertTrue(lineas.all { "tools:node=\"remove\"" in it }, "INTERNET solo puede aparecer con tools:node=\"remove\": $lineas")
    }

    @Test
    fun `no hay permisos de red ni de almacenamiento amplio`() {
        for (p in listOf("ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE")) {
            val lineas = manifiesto.lines().filter { "android.permission.$p" in it }
            assertTrue(lineas.all { "tools:node=\"remove\"" in it }, "$p no debería pedirse: $lineas")
        }
    }

    @Test
    fun `los servicios de grabacion y validacion viven en procesos propios`() {
        assertTrue(Regex("""ServicioGrabacion"[^>]*process=":grabacion"""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(manifiesto))
        assertTrue(Regex("""ServicioValidador"[^>]*process=":validador"""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(manifiesto))
    }

    @Test
    fun `la copia de seguridad en la nube esta desactivada`() {
        assertTrue("android:allowBackup=\"false\"" in manifiesto)
    }
}
