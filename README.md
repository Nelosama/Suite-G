# Suite-G

Lector de partituras en PDF para **chelistas profesionales**, en tablet Android. Se maneja con un **pedal** (Bluetooth o USB-OTG)
que se comporta como teclado, porque durante la lectura las dos manos están ocupadas.

- 100 % offline: el manifiesto elimina el permiso `INTERNET`. Sin cuentas ni nube.
- Prioridad absoluta: que no falle en directo.
- Código, comentarios y documentación en español.

## Qué hace

| Área | Funciones |
|---|---|
| Pedal | Aprendizaje de teclas (sin keycodes fijos), consumo de eventos, solo `ACTION_DOWN`, antirrebote y cooldown configurable, indicador de conexión, USB-OTG |
| Lectura | PdfRenderer, caché de bitmaps por bytes, precarga de siguiente y anterior, zoom/desplazamiento, pantalla siempre encendida, orientación bloqueada |
| Giros | Puntos de giro por página, compás o posición libre; el pedal recorre esos puntos |
| Ensayo | Marcadores con nombre, saltar con pedal o menú, repetir sección |
| Setlists | Orden, notas por canción, paso automático a la siguiente al final con aviso previo |
| Anotaciones | Tinta vectorial en coordenadas de página, rechazo de palma, borrador, deshacer |
| Concierto | Bloqueo táctil propio + fijación de pantalla opcional, desbloqueo manteniendo una esquina 3 s |
| Colores | Invertir todo, invertir conservando tonalidad, bajo contraste (solo fondo); la tinta no se invierte dos veces |
| Tempo (opcional) | Avance automático por BPM y tramos, cuenta de entrada, pausa/reanudación, corrección en vivo, resincronización, aviso antes de cada giro |
| Metrónomo | Clic sintetizado por `AudioTrack` de baja latencia, acento de compás, sigue el BPM del tempo |
| Grabación | Para escucharte después: servicio en proceso propio, AAC/ADTS, marcas de cada giro de página |
| Perfiles | Ensayo, Concierto, Foso oscuro; cada función se activa/desactiva en Ajustes |

## Estructura

```
core/modelo       Kotlin puro: dominio, geometría página↔pantalla, trazos, ajustes, matrices de color
core/navegacion   Kotlin puro: PageNavigator (FSM), SecuenciaDeVistas, ProcesadorPedal, ModoAprendizaje, DetectorDesbloqueo
engine/tempo      Kotlin puro: CalculadorTempo, MotorTempo, secuenciador PCM del metrónomo
app               Android: Room, DataStore, PdfRenderer, vistas, audio, Compose
```

El pedal es un **adaptador**: `MainActivity.dispatchKeyEvent` → `ProcesadorPedal` → `Accion` → `SesionLectura` → `PageNavigator`.
Ni el navegador ni el motor de tempo conocen el pedal. Más en [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md).

## Compilar

Requisitos: JDK 17 y el SDK de Android (compileSdk 35). Lo más fácil es abrir la carpeta en **Android Studio**.

```bash
./gradlew test                  # pruebas unitarias (lógica pura, manifiesto)
./gradlew :app:assembleDebug    # APK en app/build/outputs/apk/debug/app-debug.apk
```

Sin SDK, los módulos de lógica se prueban igualmente: `SUITEG_SOLO_JVM=1 ./gradlew test`.

El repositorio incluye un flujo de GitHub Actions (`.github/workflows/android.yml`) que ejecuta las pruebas, compila el APK y lo
publica como artefacto `suiteg-debug-apk`. Para instalarlo: descarga el `.apk`, cópialo a la tablet y ábrelo
(permite «instalar apps de origen desconocido» para tu gestor de archivos).

## Primer uso

1. **Importar PDF** en la biblioteca. Se copian a la app.
2. **Ajustes → Asignar teclas del pedal**: elige «Avanzar», pisa el pedal; repite con «Retroceder».
   Empareja antes el pedal por Bluetooth en los ajustes de Android (o conéctalo por USB-OTG).
3. Abre la partitura. Con **Giros** marca dónde debe cambiar la vista; con **Marcas**, las secciones.
4. Para el concierto: perfil **Concierto** (o botón «🔒 Concierto»). Desbloqueo: mantén pulsada la esquina inferior derecha 3 s.

Detalles de decisiones y riesgos: [docs/DECISIONES.md](docs/DECISIONES.md), [docs/RIESGOS.md](docs/RIESGOS.md), [docs/PRUEBAS.md](docs/PRUEBAS.md).

No se incluye contenido con derechos de autor: solo trabaja con los PDF que importes.
