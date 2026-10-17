pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
    // Versiones de plugins en un único sitio. Solo se resuelven cuando un módulo los aplica.
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "SuiteG"

// Módulos de lógica pura (Kotlin/JVM): se compilan y prueban sin SDK de Android.
include(":core:modelo", ":core:navegacion", ":engine:tempo")

// La app Android requiere el SDK. Con SUITEG_SOLO_JVM=1 se omite (útil en CI sin SDK).
if (System.getenv("SUITEG_SOLO_JVM") == null) {
    include(":app")
}
