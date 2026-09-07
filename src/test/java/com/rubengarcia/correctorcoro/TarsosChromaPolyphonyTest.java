package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.*;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TarsosChromaPolyphonyTest {

    private final TarsosChromaExtractor extractor =
            new TarsosChromaExtractor();

    @Test
    void debeDetectarTresClasesDeNotaEnUnAcorde() throws Exception {

        File wav = crearAcorde(
                261.63, // C4
                329.63, // E4
                392.00  // G4
        );

        try {
            List<ChromaFrame> frames = extractor.extract(wav);

            assertFalse(frames.isEmpty());

            double[] chroma = frames.stream()
                    .map(ChromaFrame::chroma)
                    .max((a, b) -> Double.compare(energia(a), energia(b)))
                    .orElseThrow();

            assertTrue(chroma[0] > 0.10, "Debe detectar C");
            assertTrue(chroma[4] > 0.10, "Debe detectar E");
            assertTrue(chroma[7] > 0.10, "Debe detectar G");

        } finally {
            wav.delete();
        }
    }

    private double energia(double[] chroma) {
        double suma = 0.0;

        for (double value : chroma) {
            suma += value * value;
        }

        return suma;
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

        File file = File.createTempFile("chroma-polyphony-", ".wav");

        try (AudioInputStream stream =
                     new AudioInputStream(
                             new java.io.ByteArrayInputStream(data),
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
