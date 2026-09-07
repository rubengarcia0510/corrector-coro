package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpectralPitchExtractorTest {

    @Test
    void debeEncontrarComponentesDeUnAcorde() throws Exception {

        File wav = crearAcorde(
                261.63,
                329.63,
                392.00
        );

        try {
            SpectralPitchExtractor extractor =
                    new SpectralPitchExtractor();

            List<SpectralFrame> frames =
                    extractor.extract(wav);

            assertFalse(frames.isEmpty());

            SpectralFrame frame = frames.get(frames.size() / 2);
            List<SpectralPitch> peaks = frame.peaks();

            System.out.printf("Middle frame timestamp: %.4f sec%n", frame.timestampSec());

            System.out.println("========== SPECTRAL PEAKS ==========");

            peaks.stream()
                    .limit(10)
                    .forEach(p ->
                            System.out.printf(
                                    "%.2f Hz -> %.4f%n",
                                    p.frequencyHz(),
                                    p.magnitude()
                            )
                    );

            System.out.printf(
                    "C4 error: %.2f cents%n",
                    cents(peaks, 261.63)
            );

            System.out.printf(
                    "E4 error: %.2f cents%n",
                    cents(peaks, 329.63)
            );

            System.out.printf(
                    "G4 error: %.2f cents%n",
                    cents(peaks, 392.00)
            );

            System.out.println("====================================");

            assertTrue(cercaFrecuencia(peaks, 261.63, 3.0));
            assertTrue(cercaFrecuencia(peaks, 329.63, 3.0));
            assertTrue(cercaFrecuencia(peaks, 392.00, 3.0));

        } finally {
            wav.delete();
        }
    }

    private double cents(
            List<SpectralPitch> peaks,
            double expected) {

        return peaks.stream()
                .min((a, b) ->
                        Double.compare(
                                Math.abs(a.frequencyHz() - expected),
                                Math.abs(b.frequencyHz() - expected)
                        ))
                .map(p ->
                        1200.0 *
                                Math.log(p.frequencyHz() / expected)
                                / Math.log(2.0)
                )
                .orElse(Double.NaN);
    }

    private boolean cercaFrecuencia(
            List<SpectralPitch> peaks,
            double expected,
            double tolerance) {

        return peaks.stream()
                .anyMatch(p ->
                        Math.abs(p.frequencyHz() - expected)
                                <= tolerance
                );
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
                File.createTempFile("spectral-chord-", ".wav");

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
