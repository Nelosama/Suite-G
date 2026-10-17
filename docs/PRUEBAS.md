# Pruebas

## Automáticas (JVM)
`./gradlew test` (o `SUITEG_SOLO_JVM=1 ./gradlew test` sin SDK):
- `PageNavigatorTest`: FSM, dedup, cooldown, fin de canción, errores, marcadores y repetir sección.
- `SecuenciaDeVistasTest`: orden, fusión, duplicados, fuera de rango, `indiceDe`.
- `ProcesadorPedalTest`, `ModoAprendizajeTest`, `MapaTeclasTest`: solo DOWN, repeticiones, eco entre dispositivos, varias teclas por pisada.
- `CalculadorTempoTest`, `MotorTempoTest`: duraciones, tramos, prioridad de puntos marcados, cuenta de entrada, pausa, velocidad, resincronización, invariante «todo giro tiene aviso previo» con tics aleatorios.
- `MetronomoTest`: exactitud de golpes, sin deriva (10 min a 97 bpm), cambio de BPM.
- `SesionLargaTest` / `SesionLargaTempoTest`: 2 h simuladas.
- `ManifiestoTest`: sin `INTERNET`.

## En la tablet (manual)
1. **Latencia**: Ajustes → «Mostrar latencia del pedal»; pisa 50 veces. Se muestra p50/p95/p99 desde el `KeyEvent` hasta el fotograma. Objetivo: p95 < 50 ms con la página en caché.
2. **Resistencia**: lector abierto 2 h, pedal cada pocos segundos, `adb shell dumpsys meminfo com.suiteg.partituras` al inicio y cada 30 min: «Native Heap» y «Graphics» deben estabilizarse.
3. **PDF escaneado pesado** (300 dpi, 20+ páginas): comprueba el tiempo de la primera página y de las siguientes.
4. **Pedal**: apaga y enciende el pedal con el lector abierto; el indicador debe pasar a ○ y volver a ●. Prueba cada botón y que el volumen no cambie.
5. **Modo concierto**: intenta cerrar la app con toques, con la palma y con el arco; el desbloqueo debe exigir 3 s con un dedo.
6. **PDF dañado**: importa un archivo truncado; la importación debe rechazarlo con un aviso.
7. **Matar la app** con el lector abierto: al abrirla vuelve a la misma página.
