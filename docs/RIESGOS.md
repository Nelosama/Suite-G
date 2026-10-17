# Riesgos técnicos

## Pedal
- **R1 Pulsación larga**: ver DECISIONES. Con un solo botón hay que sacrificar latencia.
- **R2 Varias teclas por pisada** (p. ej. flecha + Enter): el aprendizaje agrupa las recibidas en `ventanaGrupoMs` y las asigna a la misma acción; el procesador deja pasar solo una por ventana.
- **R3 Teclas del sistema** (Inicio, Encendido, Asistente…): no se pueden consumir; el aprendizaje las rechaza con aviso.
- **R4 Reconexión**: la reconecta Android, no la app (sin APIs ocultas). La app detecta el dispositivo por su `descriptor` y actualiza el indicador al instante. Muchos pedales se duermen por inactividad y **pierden la primera pisada al despertar**: desactiva el ahorro del pedal.
- **R5 Teclado en pantalla**: con un teclado físico conectado Android puede no mostrar el teclado virtual al escribir notas; se arregla en Ajustes de Android → teclado físico.
- **R6 Pocos botones**: tempo (iniciar, más rápido, más lento, resincronizar) pide 4 teclas; usa un pedal de 3–4 botones o asigna solo lo imprescindible.

## Sistema
- **R7 Orientación**: ver `targetSdk 35`.
- **R10 Gesto de Inicio**: sin `startLockTask` no se puede bloquear al 100 %. Si se sale de la app, al volver reabre donde estaba; si la app se cierra con el lector abierto, se reabre sola.
- Activa **No molestar** en la tablet: las notificaciones sobre la partitura son un riesgo que la app no puede evitar.

## Memoria con PDF escaneados
- **R8** Una imagen de ventana ocupa ~16 MB (2560×1600). Cache limitada por bytes (≤ 3 imágenes y ≤ ¼ de la memoria de la app), `onTrimMemory` la vacía, `OutOfMemoryError` en el render vacía y reintenta una vez y, si falla, **conserva la vista anterior**. La memoria nativa del decodificador no aparece en el heap de Java pero cuenta para que el sistema mate la app.
- **R9 PDF que rompe el motor nativo**: se detecta al importar (proceso `:validador`). Un PDF que se corrompa después puede caerse en el proceso principal: la app se reabre en la misma posición.
- La validación al importar toca una muestra de hasta 40 páginas, no todas.

## Avance por tempo
- **R11 La app no escucha la música.** Rubato, calderones y respiraciones se desvían de cualquier cálculo. Mitigaciones: pedal «estoy aquí» (resincroniza sin reiniciar), aviso visible antes de cada giro (≥ 800 ms garantizado por el motor y probado), los puntos de giro marcados mandan sobre el cálculo, el avance se detiene solo al final. Es una ayuda, no algo en lo que confiar a ciegas.
- **R12 Reloj**: la posición es función de `elapsedRealtime`, no un contador; no acumula deriva si se pierden ticks.
- El modo «desplazamiento suave» no es fluido: re-rasteriza a saltos de ≥ 24 px.
- Editar giros o marcadores detiene el avance por tempo.

## Metrónomo y grabación
- El clic sale por el altavoz: **si grabas con el altavoz, el metrónomo se graba**. Usa auriculares.
- La latencia del audio (decenas de ms) hace que la cuenta de entrada y el primer pulso del tempo no estén alineados al milisegundo.
- Micrófono de tablet: sirve para escucharte, no para producir. Se usa `UNPROCESSED` si existe (sin control de ganancia): el volumen de reproducción puede ser bajo.
- 128 kbps ≈ 58 MB por hora. Se exige ≥ 300 MB libres para empezar.
- AAC/ADTS permite recuperar una grabación cortada, pero no se busca con precisión de milisegundo.
- La grabación **no se puede sacar de la app** (no hay exportación, por requisito).

## Datos
- **R13** La identidad es el SHA-256: si editas el PDF fuera, es otro archivo y no hereda anotaciones.

## Entorno de desarrollo (honestidad)
- El código de Android (Compose, Room, DataStore, PdfRenderer, audio) **se escribió sin poder compilarlo en el entorno de creación** (sin acceso al SDK). La lógica pura sí se compiló y probó (117 pruebas). El código que solo usa el framework se comprobó contra los stubs de Android 15. **Nada se ha probado en un dispositivo.** Antes de un concierto, pruébalo con tu pedal y tu tablet (ver PRUEBAS.md).
