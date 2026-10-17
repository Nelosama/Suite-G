# Decisiones técnicas

## Motor de PDF: PdfRenderer (detrás de `MotorPdf`)
| | PdfRenderer | PDFium propio |
|---|---|---|
| Licencia | parte del sistema | BSD-3/Apache-2, con atribución |
| Motor | PDFium del sistema | versión propia |
| APK | +0 MB | +4–8 MB por ABI |
| Mantenimiento | Google | JNI propio o envoltorios abandonados |
| Páginas abiertas | una a la vez | varias |
Con render secuencial en un hilo la limitación no importa. Si en pruebas con escaneos pesados una página tarda más de ~300 ms
en gama media, se cambia a PDFium implementando `MotorPdf`.

## UI: Compose + Views
Compose para pantallas y diálogos. La superficie de lectura (`VistaPartitura`, `CapaTinta`, `SuperficieBloqueo`) son **Views** por el control
determinista de `MotionEvent` (tipo de herramienta, eventos históricos, palma). El teclado **no** pasa por Compose: se intercepta en
`Activity.dispatchKeyEvent` para que perder el foco nunca deje al pedal mudo.

## Persistencia: Room + DataStore
Room: transaccional, migraciones probables, consultas reactivas. Trazos como un BLOB por trazo, no una fila por punto. DataStore para ajustes.

## Copia interna de los PDF
Se pide permiso SAF persistente, pero se lee una copia propia: PdfRenderer necesita acceso aleatorio y el permiso o la SD pueden desaparecer.

## Modo concierto: overlay propio, `startLockTask` opcional
| | Overlay propio | `startLockTask` |
|---|---|---|
| Bloquea toques en la partitura | sí | no por sí solo |
| Bloquea Inicio/Recientes | no (inmersivo con doble gesto) | sí |
| Sin configuración | funciona siempre | aviso del sistema, depende de ajustes/fabricante |
Por defecto overlay + inmersivo + «Atrás» consumido, porque funciona en cualquier tablet. «Bloqueo máximo» activa además `startLockTask`.

## targetSdk 35
A partir de Android 16 (targetSdk 36) las apps ignoran el bloqueo de orientación en pantallas grandes. Se mantiene 35 mientras se instale por APK.

## Inyección manual en lugar de Hilt
Un único `Contenedor`: sin generación de código ni reflexión, menos puntos de fallo al arrancar.

## Inversión de colores
`TODO` = negativo. `TONALIDAD` = negativo + rotación de matiz 180° (cada color conserva su matiz). `FONDO` = lo anterior comprimido a 30..200
(fondo carbón, tinta gris claro: **bajo contraste**). Interpretación de «solo invertir el fondo»; es fácil de cambiar en `MatricesColor`.

## Pedal de una sola tecla
No se implementa «pulsación larga = retroceder»: obliga a esperar al `ACTION_UP` y retrasa el avance. Se recomiendan dos pedales o dos
botones; cada acción se asigna a la tecla que se quiera.
