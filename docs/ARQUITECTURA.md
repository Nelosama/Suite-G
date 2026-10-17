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

## Procesos
- principal: interfaz y datos;
- `:grabacion`: `MediaRecorder` en un servicio de primer plano;
- `:validador`: abre cada PDF al importarlo; si el motor nativo se cae, muere solo ese proceso.

## Datos
Room (documentos, giros, marcadores, tramos, config de tempo, setlists, trazos, grabaciones, marcas) con claves foráneas en cascada;
DataStore para ajustes, asignación del pedal y última sesión. Sin migraciones destructivas.
