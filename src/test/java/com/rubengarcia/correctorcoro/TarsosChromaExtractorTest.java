package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarsosChromaExtractorTest {

    private static final int SAMPLE_RATE = 44100;
    private static final double FREQUENCY = 440.0;
    private static final double DURATION_SECONDS = 1.0;

    @Test
    void shouldDetectA440AsA() throws Exception {
        File wav = createSineWave();

        try {
            TarsosChromaExtractor extractor = new TarsosChromaExtractor();

            var frames = extractor.extract(wav);

            assertTrue(frames.size() > 0);

            double[] average = new double[12];

            for (ChromaFrame frame : frames) {
                for (int i = 0; i < 12; i++) {
                    average[i] += frame.chroma()[i];
                }
            }

            int dominantBin = 0;

            for (int i = 1; i < 12; i++) {
                if (average[i] > average[dominantBin]) {
                    dominantBin = i;
                }
            }

            assertEquals(9, dominantBin);
            assertTrue(average[9] > average[0]);
        } finally {
            Files.deleteIfExists(wav.toPath());
        }
    }


    @Test
    void shouldDetectAllChromaticPitchClasses() throws Exception {
        double[] frequencies = {
                261.6256, // C4
                277.1826, // C#4
                293.6648, // D4
                311.1270, // D#4
                329.6276, // E4
                349.2282, // F4
                369.9944, // F#4
                391.9954, // G4
                415.3047, // G#4
                440.0000, // A4
                466.1638, // A#4
                493.8833  // B4
        };

        for (int expectedBin = 0; expectedBin < 12; expectedBin++) {
            File wav = createSineWave(frequencies[expectedBin]);

            try {
                TarsosChromaExtractor extractor = new TarsosChromaExtractor();
                var frames = extractor.extract(wav);

                assertTrue(frames.size() > 0);

                double[] average = new double[12];

                for (ChromaFrame frame : frames) {
                    for (int i = 0; i < 12; i++) {
                        average[i] += frame.chroma()[i];
                    }
                }

                int dominantBin = 0;

                for (int i = 1; i < 12; i++) {
                    if (average[i] > average[dominantBin]) {
                        dominantBin = i;
                    }
                }

                assertEquals(
                        expectedBin,
                        dominantBin,
                        "Wrong chroma bin for frequency " + frequencies[expectedBin]
                );
            } finally {
                Files.deleteIfExists(wav.toPath());
            }
        }
    }

    private File createSineWave() throws Exception {
        return createSineWave(FREQUENCY);
    }

    private File createSineWave(double frequency) throws Exception {
        int sampleCount = (int) (SAMPLE_RATE * DURATION_SECONDS);
        byte[] audio = new byte[sampleCount * 2];

        for (int i = 0; i < sampleCount; i++) {
            double time = (double) i / SAMPLE_RATE;
            double sample = Math.sin(2.0 * Math.PI * frequency * time);

            short value = (short) (sample * Short.MAX_VALUE);

            audio[i * 2] = (byte) (value & 0xff);
            audio[i * 2 + 1] = (byte) ((value >> 8) & 0xff);
        }

        AudioFormat format = new AudioFormat(
                SAMPLE_RATE,
                16,
                1,
                true,
                false
        );

        File file = Files.createTempFile("chroma-a440-", ".wav").toFile();

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(audio);
             AudioInputStream stream =
                     new AudioInputStream(
                             input,
                             format,
                             sampleCount
                     )) {

            AudioSystem.write(
                    stream,
                    javax.sound.sampled.AudioFileFormat.Type.WAVE,
                    file
            );
        }

        return file;
    }
}
