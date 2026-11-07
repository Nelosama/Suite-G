# Arquitectura

```
          MainActivity.dispatchKeyEvent  (ÚNICA puerta del teclado)
                       │ KeyEvent → EventoTecla
                ┌──────▼───────┐
                │ProcesadorPedal│  mapa (descriptor,tecla)→Accion · solo DOWN · dedup · consume
                └──────┬───────┘
                       │ Accion
                ┌──────▼───────┐        ┌──────────────┐
                │SesionLectura │◀──────▶│  MotorTempo  │ (adaptador: emite Girar/Aviso/Fin)
                └──┬───┬───┬───┘        └──────────────┘
   ComandoNavegacion│   │   │ImagenVista
        ┌───────────▼┐  │  ┌▼───────────┐
        │PageNavigator│  │  │ MotorPdf   │ hilo único · CacheBitmaps (LRU por bytes)
        │ (FSM)       │  │  └────────────┘
        └─────────────┘  └─▶ Room / DataStore (repositorios)
```

## Reglas de dependencia
- `core:*` y `engine:tempo` no usan Android: se prueban en la JVM con un `Reloj` inyectado.
- El **pedal es un adaptador**: produce `Accion`; `SesionLectura` la traduce a `ComandoNavegacion`. El `PageNavigator` no sabe de teclas.
- El **avance por tempo es otro adaptador**: entra por el mismo `PageNavigator.enviar(..., OrigenComando.TEMPO)`.
- El motor de PDF está detrás de `MotorPdf`: cambiar a PDFium no toca nada más.

## PageNavigator
Fases `SIN_DOCUMENTO`, `LEYENDO`, `ERROR`. Un solo punto de entrada `enviar(comando, origen, idEvento)`:
1. deduplica por `idEvento`;
2. cooldown mínimo configurable (no se aplica al origen `TEMPO` ni lo reinicia);
3. recorre la `SecuenciaDeVistas` (puntos de giro) o, en `ERROR`, solo permite saltar de canción: **un PDF dañado nunca bloquea el setlist**;
4. al final de la canción muestra «Siguiente: X»; una pisada más abre la siguiente.

## Posición y anotaciones
Todo se guarda en **coordenadas de página normalizadas** (`Posicion(pagina, y∈0..1)`, trazos con x,y∈0..1 y grosor relativo al ancho).
`TransformacionVista` convierte a pantalla; rotar o hacer zoom solo cambia la transformación. Los trazos se guardan como BLOB
binario compacto (`CodecTrazos`) indexados por el **SHA-256** del archivo.

## Render
`PdfRenderer` solo permite una página abierta: un único hilo (`pdf-render`) abre, rasteriza y cierra. Cada imagen es del tamaño
de la ventana (página + márgenes) con `transform` = escala + traslación. Se cachean 3 imágenes (actual, siguiente, anterior) con
presupuesto en bytes; nunca se llama a `recycle()` sobre una imagen expulsada (podría estar en pantalla).

## Miniaturas de la biblioteca
`pdf/Miniaturas` rasteriza la **primera página** de cada partitura con el mismo `PdfRendererMotor` que el lector (no hay motor
aparte), una sola a la vez (`Mutex`), y la guarda como PNG en `filesDir/miniaturas/<sha256>.png`:
- tamaño calculado en `core:modelo` (`CalculoMiniatura`: 240 px de ancho, alto proporcional y como mucho 3× el ancho);
- caché en memoria LRU de 8 MB; la biblioteca toma su valor inicial de esa caché de forma síncrona para que el icono no parpadee al hacer scroll;
- cualquier error (PDF dañado, error nativo, `OutOfMemoryError`) devuelve null y se muestra el icono genérico; esa partitura no se reintenta hasta el siguiente arranque;
- se escribe a un `.tmp` y se renombra; al eliminar una partitura se borra su miniatura, y al abrir la biblioteca se borran temporales y miniaturas sin partitura (`CalculoMiniatura.huerfanos`).

## Eventos del pedal
`MonitorPedal` escucha `InputManager.InputDeviceListener` y calcula `estado` (`SinConfigurar`, `Desconectado`, `Conectado`) a partir de
los descriptores con teclas asignadas. Cada cambio pasa por la función pura `eventoTransicion(anterior, nuevo)`: solo el paso
conectado ↔ desconectado produce un `EventoPedal` (`Reconectado` / `Desconectado`), publicado en el `SharedFlow` `eventos`
(`tryEmit`, nunca bloquea). `PantallaLector` lo recoge con `collectLatest`, lo traduce con `avisoDe` (texto y duración: 3 s
reconexión, 5 s desconexión) y muestra un aviso en la esquina superior derecha sin manejadores de toque: no tapa la barra de
giro, no pide interacción y no afecta al pedal, que sigue entrando por `dispatchKeyEvent`.

```
InputManager ─▶ MonitorPedal.recalcular ─▶ estado (StateFlow) ─▶ indicador ●/○ de la cabecera
                                     └──▶ eventoTransicion ─▶ eventos (SharedFlow) ─▶ avisoDe ─▶ aviso temporal en el lector
```

## Procesos
- principal: interfaz y datos;
- `:grabacion`: `MediaRecorder` en un servicio de primer plano;
- `:validador`: abre cada PDF al importarlo; si el motor nativo se cae, muere solo ese proceso.

## Datos
Room (documentos, giros, marcadores, tramos, config de tempo, setlists, trazos, grabaciones, marcas) con claves foráneas en cascada;
DataStore para ajustes, asignación del pedal y última sesión. Sin migraciones destructivas.
