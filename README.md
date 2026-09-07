# Spike: Detección de desafinación (CDM-3)

Proyecto Spring Boot standalone para validar el enfoque de CDC-1/CDM-3
antes de integrarlo al backend definitivo: extracción de pitch con
TarsosDSP + alineación con DTW para detectar desafinación entre una
referencia y un ensayo grabado.

## Requisitos

- Java 21
- Maven

## Correr el proyecto

```bash
mvn spring-boot:run
```

Levanta en `http://localhost:8080`.

## Preparar los audios de prueba

TarsosDSP (vía `AudioDispatcherFactory.fromFile`) espera **WAV/PCM sin
comprimir**. Si grabás con el celular en M4A/AAC, convertí con ffmpeg:

```bash
ffmpeg -i referencia.m4a -ar 44100 -ac 1 referencia.wav
ffmpeg -i ensayo.m4a -ar 44100 -ac 1 ensayo.wav
```

(mono, 44.1kHz — TarsosDSP no necesita más que eso, y simplifica el análisis)

Grabá dos versiones cortas (15-30 segundos alcanza para el spike) de la
misma frase:
1. `referencia.wav` — cantada afinada.
2. `ensayo.wav` — la misma frase pero con un error a propósito (medio
   tono arriba o abajo en algún punto).

## Validar

```bash
curl -F "referencia=@referencia.wav" -F "ensayo=@ensayo.wav" \
     http://localhost:8080/spike/comparar
```

Devuelve un JSON con los tramos donde la diferencia de pitch (en cents)
superó el umbral (50 cents = medio semitono) durante al menos 150ms:

```json
[
  {
    "timestampEnsayoSec": 4.2,
    "pitchReferenciaHz": 293.66,
    "pitchEnsayoHz": 311.13,
    "diferenciaCents": 97.5
  }
]
```

## Análisis sin referencia (afinación absoluta)

Si solo querés saber si una grabación está afinada o no — sin comparar
contra otro audio — usá:

```bash
curl -F "audio=@ensayo_real.wav" http://localhost:8080/spike/analizar
```

Compara cada nota contra la afinación estándar (A4 = 440Hz, temperamento
igual) y devuelve los tramos donde el coro canta sostenidamente
desviado (más de 50 cents) de la nota más cercana:

```json
[
  {
    "inicioSec": 12.3,
    "finSec": 14.1,
    "notaMasCercana": "F4",
    "diferenciaCentsPromedio": -63.2
  }
]
```

`diferenciaCentsPromedio` negativo = canta bajo (flat) respecto a esa
nota; positivo = canta alto (sharp). Sirve en particular para detectar
"flatting" — el coro se va quedando bajo de tono a medida que avanza
la canción, un problema muy común en la práctica real.

**Ojo con el criterio**: esto asume que la pieza está en afinación
estándar A440 y que las notas caen en el temperamento igual (12 notas
por octava). Si el coro canta acompañado de un instrumento afinado
distinto (ej. un piano viejo desafinado), este análisis va a marcar
"error" aunque el coro esté perfectamente afinado *respecto a su
acompañamiento* — en ese caso conviene el endpoint `/spike/comparar`
con una referencia real, no este.

## Criterio de éxito del spike

- El error metido a propósito aparece en el JSON, con un `timestampEnsayoSec`
  cercano al momento real donde ocurrió.
- Las partes bien cantadas NO generan entradas en la lista (falsos positivos).
- Repetir con 3-4 pares de audio distintos (diferentes voces, con y sin
  acompañamiento de piano de fondo) para confirmar consistencia.

Si hay muchos falsos positivos/negativos, los puntos de ajuste son:
- `UMBRAL_CENTS` y `DURACION_MINIMA_SEC` en `ComparadorDeCoro.java`
- Probar `PitchEstimationAlgorithm.MPM` en vez de `YIN` en `PitchExtractor.java`

## Nota sobre TarsosDSP

No está en Maven Central: se descarga del repositorio propio del autor
(`https://mvn.0110.be/releases`, declarado en el `pom.xml`). Se usan dos
módulos oficiales de JorenSix — `be.tarsos.dsp:core:2.5` (algoritmos de
pitch) y `be.tarsos.dsp:jvm:2.5` (`AudioDispatcherFactory` y el resto del
I/O de audio sobre JVM, que `core` no incluye) — no un fork de terceros.

## Próximo paso

Si el spike valida el enfoque, migrar esta lógica (`PitchExtractor`,
`DtwAligner`, `ComparadorDeCoro`) al backend definitivo (ver épica
CDM-1 en Jira), agregando:
- Decodificación de AAC/M4A/Opus directamente (sin paso manual por ffmpeg)
- Persistencia de resultados + subida a Cloudflare R2
- Procesamiento asincrónico (job queue en vez de esperar en el request)
