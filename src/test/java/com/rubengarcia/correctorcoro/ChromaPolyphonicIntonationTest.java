package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChromaPolyphonicIntonationTest {

    private final ChromaIntonationAnalyzer analyzer;

    ChromaPolyphonicIntonationTest() {
        TarsosChromaExtractor chromaExtractor =
                new TarsosChromaExtractor();

        ChromaDtwAligner aligner =
                new ChromaDtwAligner();

        SpectralPitchExtractor spectralPitchExtractor =
                new SpectralPitchExtractor();

        PolyphonicPitchMatcher pitchMatcher =
                new PolyphonicPitchMatcher();

        PolyphonicIntonationAligner polyphonicAligner =
                new PolyphonicIntonationAligner(pitchMatcher);

        ChromaIntonationSegmenter segmenter =
                new ChromaIntonationSegmenter();

        analyzer = new ChromaIntonationAnalyzer(
                chromaExtractor,
                aligner,
                spectralPitchExtractor,
                polyphonicAligner,
                segmenter,
                10.0,
                20.0,
                35.0,
                3
        );
    }

    @Test
    void debeDetectarDesafinacionEnAudioPolifonico() throws Exception {

        double[] reference = {
                261.63, // C4
                329.63, // E4
                392.00  // G4
        };

        double ratio = Math.pow(2.0, 40.0 / 1200.0);

        double[] performance = {
                reference[0] * ratio,
                reference[1] * ratio,
                reference[2] * ratio
        };

        File referenceFile = crearAcorde(reference);
        File performanceFile = crearAcorde(performance);

        try {
            List<ChromaIntonationError> errors =
                    analyzer.analyze(referenceFile, performanceFile);

            assertFalse(
                    errors.isEmpty(),
                    "El análisis polifónico no debería quedar vacío"
            );

            boolean foundSevere = errors.stream()
                    .anyMatch(error ->
                            error.severity()
                                    == ChromaIntonationError.Severity.SEVERE
                                    && Math.abs(error.deviationCents()) > 35.0
                    );

            assertTrue(
                    foundSevere,
                    "Debe detectar una desviación severa en el acorde"
            );

        } finally {
            referenceFile.delete();
            performanceFile.delete();
        }
    }

    private File crearAcorde(double... frequencies) throws Exception {

        float sampleRate = 44100f;
        int seconds = 2;
        int samples = (int) (sampleRate * seconds);

        byte[] data = new byte[samples * 2];

        for (int i = 0; i < samples; i++) {

            double value = 0.0;

            for (double frequency : frequencies) {
                value += Math.sin(
                        2.0 * Math.PI * frequency * i / sampleRate
                );
            }

            value /= frequencies.length;

            short sample = (short) (value * 16000);

            data[i * 2] = (byte) (sample & 0xff);
            data[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }

        AudioFormat format =
                new AudioFormat(sampleRate, 16, 1, true, false);

        File file =
                File.createTempFile("chroma-polyphonic-", ".wav");

        try (AudioInputStream stream =
                     new AudioInputStream(
                             new ByteArrayInputStream(data),
                             format,
                             samples)) {

            AudioSystem.write(
                    stream,
                    AudioFileFormat.Type.WAVE,
                    file
            );
        }

        return file;
    }
}
