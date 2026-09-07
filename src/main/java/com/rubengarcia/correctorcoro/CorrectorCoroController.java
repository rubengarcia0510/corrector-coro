package com.rubengarcia.correctorcoro;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Endpoint del spike (CDM-3): recibe un WAV de referencia y un WAV de
 * ensayo, y devuelve los tramos donde se detecto desafinacion.
 *
 * Uso (ver README.md):
 * curl -F "referencia=@referencia.wav" -F "ensayo=@ensayo.wav" \
 *      http://localhost:8080/spike/comparar
 */
@RestController
public class CorrectorCoroController {

    private final ComparadorDeCoro comparadorDeCoro;
    private final PitchExtractor pitchExtractor;
    private final AnalizadorAfinacionAbsoluta analizadorAfinacionAbsoluta;

    public CorrectorCoroController(
            ComparadorDeCoro comparadorDeCoro,
            PitchExtractor pitchExtractor,
            AnalizadorAfinacionAbsoluta analizadorAfinacionAbsoluta
    ) {
        this.comparadorDeCoro = comparadorDeCoro;
        this.pitchExtractor = pitchExtractor;
        this.analizadorAfinacionAbsoluta = analizadorAfinacionAbsoluta;
    }

    /**
     * Analiza un solo audio contra la afinacion estandar (A440), sin
     * necesidad de un audio de referencia. Responde "esta afinado o no".
     *
     * curl -F "audio=@ensayo_real.wav" http://localhost:8080/spike/analizar
     */
    /**
     * DIAGNOSTICO: devuelve la curva de pitch cruda (sin filtrar, sin
     * agrupar en tramos) para poder ver que esta detectando TarsosDSP
     * realmente. Usar cuando /spike/analizar o /spike/comparar dan
     * resultados vacios sospechosos.
     *
     * curl -F "audio=@nota_desafinada.wav" http://localhost:8080/spike/debug-pitch
     */
    @PostMapping("/spike/debug-pitch")
    public ResponseEntity<Object> debugPitch(@RequestParam("audio") MultipartFile audio) throws Exception {
        File archivoAudio = aArchivoTemporal(audio, "audio");
        try {
            List<PitchPoint> pitchCurva = pitchExtractor.extraerPitch(archivoAudio);
            long totalPuntos = pitchCurva.size();
            long puntosValidos = pitchCurva.stream().filter(PitchPoint::esValido).count();
            List<PitchPoint> muestra = pitchCurva.stream().limit(30).toList();

            return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{
                put("totalPuntos", totalPuntos);
                put("puntosValidos", puntosValidos);
                put("primeros30Puntos", muestra);
            }});
        } finally {
            archivoAudio.delete();
        }
    }

    @PostMapping("/spike/analizar")
    public ResponseEntity<List<AnalizadorAfinacionAbsoluta.TramoDesafinado>> analizar(
            @RequestParam("audio") MultipartFile audio
    ) throws Exception {
        File archivoAudio = aArchivoTemporal(audio, "audio");
        try {
            List<PitchPoint> pitchCurva = pitchExtractor.extraerPitch(archivoAudio);
            List<AnalizadorAfinacionAbsoluta.PuntoAfinacion> puntos = analizadorAfinacionAbsoluta.analizarPuntos(pitchCurva);
            List<AnalizadorAfinacionAbsoluta.TramoDesafinado> tramos = analizadorAfinacionAbsoluta.detectarTramosDesafinados(puntos);
            return ResponseEntity.ok(tramos);
        } finally {
            archivoAudio.delete();
        }
    }

    @PostMapping("/spike/comparar")
    public ResponseEntity<List<ErrorDesafinacion>> comparar(
            @RequestParam("referencia") MultipartFile referencia,
            @RequestParam("ensayo") MultipartFile ensayo
    ) throws Exception {
        File archivoReferencia = aArchivoTemporal(referencia, "referencia");
        File archivoEnsayo = aArchivoTemporal(ensayo, "ensayo");

        try {
            List<ErrorDesafinacion> errores = comparadorDeCoro.compararArchivos(archivoReferencia, archivoEnsayo);
            return ResponseEntity.ok(errores);
        } finally {
            archivoReferencia.delete();
            archivoEnsayo.delete();
        }
    }

    private File aArchivoTemporal(MultipartFile multipartFile, String prefijo) throws Exception {
        File temp = File.createTempFile(prefijo, ".wav");
        multipartFile.transferTo(temp);
        return temp;
    }
}
