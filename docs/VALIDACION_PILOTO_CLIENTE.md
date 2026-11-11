# Validación piloto con cliente

**Objetivo:** confirmar que Suite-G funciona en el equipo y con las partituras del cliente antes de usarla en un ensayo importante o concierto. Esta hoja registra una prueba piloto; completarla no sustituye una validación en escenario real.

## Estado actual

- [ ] Pruebas físicas realizadas.
- [ ] Cliente acepta el piloto.
- [ ] Bloqueos críticos resueltos.
- [ ] Autorización para uso en concierto.

No marcar una casilla sin evidencia. Si una prueba falla o no puede ejecutarse, anotar el motivo y dejarla pendiente.

## Datos de la sesión

| Dato | Registro |
|---|---|
| Cliente / músico | |
| Fecha y lugar | |
| Responsable de la prueba | |
| Tablet (marca, modelo, Android) | |
| Versión de Suite-G / SHA del APK | |
| Pedal (marca, modelo, Bluetooth o USB-OTG) | |
| PDF de prueba (páginas, escaneado o vectorial) | |
| Perfil usado | |
| Espacio libre antes de la prueba | |

## Preparación

- [ ] Instalar el APK generado por el workflow de GitHub Actions y anotar el SHA del commit.
- [ ] Confirmar que Android permite abrir la app y que la partitura de prueba pertenece al cliente o se tiene autorización para usarla.
- [ ] Emparejar o conectar el pedal en Ajustes de Android.
- [ ] En Suite-G, asignar y comprobar las teclas de avanzar y retroceder.
- [ ] Desactivar el ahorro de energía del pedal, si ofrece esa opción.
- [ ] Activar «No molestar» en Android para evitar notificaciones sobre la partitura.
- [ ] Desactivar el modo concierto durante la preparación; comprobar luego su bloqueo y desbloqueo.
- [ ] Tener disponible un segundo método para pasar páginas si la prueba falla.
- [ ] Registrar una línea base de memoria: `adb shell dumpsys meminfo com.suiteg.partituras`.

## Pruebas de aceptación

Anotar resultado **Aprobada**, **Fallida** o **No ejecutada**, más evidencia o incidencia. No usar la app en un concierto hasta aprobar las pruebas críticas de lectura, pedal, recuperación y modo concierto.

| ID | Prueba y procedimiento | Criterio de aceptación | Resultado / evidencia |
|---|---|---|---|
| P1 | Abrir una partitura habitual y avanzar/retroceder 50 veces con el pedal. Repetir con la página ya cargada; en Ajustes se puede activar la medición de latencia. | Cada pisada produce como máximo una acción esperada, sin cambio de volumen. Latencia p95 < 50 ms con página en caché. Si el equipo no permite medirla, registrar «no medida». | |
| P2 | Apagar y volver a encender el pedal con el lector abierto. Probar cada botón asignado. Repetir en modo normal y concierto. | Indicador y avisos de desconexión/reconexión corresponden al estado real; el pedal vuelve a operar. Registrar si se pierde la primera pisada al despertar. | |
| P3 | Durante el aviso del pedal, pasar página y tocar la partitura. Repetir con modo concierto activado. | El aviso no bloquea el pedal ni recibe toques; tocar el aviso no desbloquea el modo concierto. | |
| P4 | Activar modo concierto e intentar salir tocando la pantalla, con la palma y con el arco. Luego mantener un dedo en la esquina de desbloqueo durante 3 segundos. | Los toques accidentales no alteran la lectura; se puede desbloquear con el gesto indicado. Verificar también el comportamiento al pulsar Inicio: Android puede permitir salir de la app si la fijación de pantalla no está habilitada. | |
| P5 | Importar un PDF escaneado realista del cliente (idealmente 300 dpi y 20 o más páginas). Abrirlo y pasar varias páginas. | Importación y renderizado completan sin bloqueo ni cierre; la biblioteca sigue respondiendo mientras genera la miniatura. Registrar tiempos y cualquier degradación. | |
| P6 | Importar un archivo PDF truncado o dañado preparado para la prueba. | Suite-G rechaza el archivo con un aviso y no deja una miniatura defectuosa. | |
| P7 | Importar 50 partituras de prueba. Desplazarse rápido arriba/abajo en lista y cuadrícula, filtrar, cerrar y volver a abrir. | La miniatura corresponde al PDF correcto, no parpadea al volver a una fila, se carga desde disco al reabrir y el scroll sigue utilizable. | |
| P8 | Eliminar una partitura importada. Comprobar los archivos con `adb shell run-as com.suiteg.partituras ls files/miniaturas`. | Desaparece el PNG de esa partitura y no quedan archivos `.tmp` asociados. No inspeccionar ni copiar documentos privados del cliente. | |
| P9 | Dejar el lector abierto durante 2 horas y accionar el pedal cada pocos segundos. Registrar memoria al inicio y cada 30 minutos con `adb shell dumpsys meminfo com.suiteg.partituras`. | «Native Heap» y «Graphics» se estabilizan; no hay cierre, retraso creciente ni pérdida de respuesta. Adjuntar mediciones. | |
| P10 | Con el lector abierto, forzar cierre desde Android y volver a abrir Suite-G. | La app se recupera y vuelve a la misma partitura y página, o se registra claramente cualquier pérdida. | |
| P11 | Revisar las necesidades reales del músico: anotaciones, marcadores, giros, setlist, metrónomo/tempo, grabación, luz y volumen. Probar solo funciones que el cliente vaya a usar. | El cliente entiende las limitaciones de tempo y audio descritas en `docs/RIESGOS.md`; no hay una función esencial para el piloto que esté ausente o sin probar. | |

### Evidencia sugerida

- SHA del APK y versión de Android.
- Fotos o video de la tablet durante P1–P8 y, si se acepta, durante P10.
- Lecturas de memoria de P9 con hora y duración.
- PDF sintético para carga o corrupción. No guardar partituras protegidas del cliente como evidencia.
- Incidencias: pasos para reproducir, resultado esperado, resultado observado y severidad.

Guardar evidencia únicamente con autorización del cliente. Evitar capturar nombres de archivos, anotaciones o contenido de partituras si no hacen falta.

## Incidencias

| ID de prueba | Pasos para reproducir | Resultado esperado | Resultado observado | Severidad / seguimiento |
|---|---|---|---|---|
| | | | | |

## Decisión del piloto

- [ ] **Apto para continuar pruebas**: sin errores que impidan leer o pasar páginas; faltan pruebas no críticas registradas.
- [ ] **Apto para ensayo controlado**: P1–P8 y P10 aprobadas; P9 completada sin degradación; el cliente conoce las limitaciones.
- [ ] **No apto todavía**: existe cualquier error que pueda interrumpir la lectura, perder la posición o impedir el control con pedal.

**Observaciones del cliente:**  

**Pendientes con responsable y fecha objetivo:**  

**Nombre / aceptación del cliente:** ____________________  **Fecha:** __________

La firma o aceptación documenta el piloto y sus resultados; no equivale a garantía de funcionamiento con cualquier tablet, pedal o PDF.
