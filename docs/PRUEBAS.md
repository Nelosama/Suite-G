# Pruebas

## Automáticas (JVM)
`./gradlew test` (o `SUITEG_SOLO_JVM=1 ./gradlew test` sin SDK):
- `PageNavigatorTest`: FSM, dedup, cooldown, fin de canción, errores, marcadores y repetir sección.
- `SecuenciaDeVistasTest`: orden, fusión, duplicados, fuera de rango, `indiceDe`.
- `ProcesadorPedalTest`, `ModoAprendizajeTest`, `MapaTeclasTest`: solo DOWN, repeticiones, eco entre dispositivos, varias teclas por pisada.
- `CalculadorTempoTest`, `MotorTempoTest`: duraciones, tramos, prioridad de puntos marcados, cuenta de entrada, pausa, velocidad, resincronización, invariante «todo giro tiene aviso previo» con tics aleatorios.
- `MetronomoTest`: exactitud de golpes, sin deriva (10 min a 97 bpm), cambio de BPM.
- `SesionLargaTest` / `SesionLargaTempoTest`: 2 h simuladas.
- `CalculoMiniaturaTest`: tamaño y escala de la miniatura (A4, apaisada, escaneo en píxeles, páginas extremas e inválidas) y detección de archivos huérfanos.
- `EventoPedalTest` (módulo `app`, requiere SDK): transiciones conectado ↔ desconectado y texto/duración del aviso.
- `ManifiestoTest`: sin `INTERNET`.

El renderizado de miniaturas con `PdfRenderer` y el aviso en pantalla no se pueden probar en la JVM: van en las pruebas manuales.

## En la tablet (manual)
1. **Latencia**: Ajustes → «Mostrar latencia del pedal»; pisa 50 veces. Se muestra p50/p95/p99 desde el `KeyEvent` hasta el fotograma. Objetivo: p95 < 50 ms con la página en caché.
2. **Resistencia**: lector abierto 2 h, pedal cada pocos segundos, `adb shell dumpsys meminfo com.suiteg.partituras` al inicio y cada 30 min: «Native Heap» y «Graphics» deben estabilizarse.
3. **PDF escaneado pesado** (300 dpi, 20+ páginas): comprueba el tiempo de la primera página y de las siguientes.
4. **Pedal**: apaga y enciende el pedal con el lector abierto; el indicador debe pasar a ○ y volver a ●. Prueba cada botón y que el volumen no cambie.
5. **Modo concierto**: intenta cerrar la app con toques, con la palma y con el arco; el desbloqueo debe exigir 3 s con un dedo.
6. **PDF dañado**: importa un archivo truncado; la importación debe rechazarlo con un aviso.
7. **Matar la app** con el lector abierto: al abrirla vuelve a la misma página.
8. **Miniaturas con PDF pesado**: importa un PDF escaneado a 300 dpi con 20 páginas. La biblioteca debe seguir respondiendo mientras
   se genera la miniatura (primero se ve el icono y luego la primera página) y el lector debe abrir la partitura con normalidad.
9. **Biblioteca con 50 partituras**: en lista y en cuadrícula, haz scroll rápido arriba y abajo varias veces. Las miniaturas ya vistas no
   deben parpadear (icono → miniatura) al volver a la pantalla, ni aparecer la de otra partitura al filtrar. Cierra y abre la app: deben
   salir del disco sin volver a renderizarse. Mide `dumpsys meminfo` antes y después del scroll.
10. **Miniaturas y borrado**: elimina una partitura y comprueba con `adb shell run-as com.suiteg.partituras ls files/miniaturas`
    que su `<hash>.png` ha desaparecido y que no queda ningún `.tmp`. Importa un PDF dañado: no debe aparecer ninguna miniatura nueva.
11. **Aviso de reconexión del pedal**: con el lector abierto, en modo normal y en modo concierto, apaga el pedal (debe aparecer
    «Pedal desconectado» unos 5 s) y enciéndelo (debe aparecer «Pedal reconectado» unos 3 s). Mientras el aviso está en pantalla,
    pisa el pedal y toca la partitura: el pedal debe pasar página y el aviso no debe recibir toques ni desbloquear el modo concierto.
