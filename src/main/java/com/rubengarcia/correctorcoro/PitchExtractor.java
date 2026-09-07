package com.rubengarcia.correctorcoro;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchProcessor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrae la curva de pitch (tiempo -> frecuencia) de un archivo de audio
 * usando el algoritmo YIN de TarsosDSP.
 *
 * IMPORTANTE (spike, CDM-3): TarsosDSP con AudioDispatcherFactory.fromFile
 * espera WAV/PCM sin comprimir. Para el spike, convertir a WAV antes de
 * pasarlo (ver README). La conversion de AAC/M4A/Opus en el backend real
 * se resuelve en CDM-2 con una libreria de decodificacion (ej. FFmpeg).
 */
@Component
public class PitchExtractor {

    private static final int TAMANO_BUFFER = 1024;
    private static final int SOLAPAMIENTO = 0;

    public List<PitchPoint> extraerPitch(File archivoWav) throws Exception {
        List<PitchPoint> puntos = new ArrayList<>();

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromFile(archivoWav, TAMANO_BUFFER, SOLAPAMIENTO);

        PitchDetectionHandler handler = (resultado, evento) -> {
            float pitchHz = resultado.getPitch();
            puntos.add(new PitchPoint(evento.getTimeStamp(), pitchHz));
        };

        dispatcher.addAudioProcessor(new PitchProcessor(
                PitchProcessor.PitchEstimationAlgorithm.YIN,
                dispatcher.getFormat().getSampleRate(),
                TAMANO_BUFFER,
                handler
        ));

        dispatcher.run();

        return puntos;
    }
}
