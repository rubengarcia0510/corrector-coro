package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ChromaIntonationAnalyzerTest {

    private static final int SAMPLE_RATE = 44100;
    private static final double DURATION_SECONDS = 1.0;

    @Autowired
    private ChromaIntonationAnalyzer analyzer;

    @Test
    void shouldDetectA4PerformanceAsSeverelySharp(@TempDir Path tempDir)
            throws Exception {

        File reference = createSineWave(
                tempDir,
                "reference",
                440.0
        );

        File performance = createSineWave(
                tempDir,
                "performance",
                450.0
        );

        List<ChromaIntonationError> errors =
                analyzer.analyze(reference, performance);

        assertFalse(errors.isEmpty());

        boolean severeFound = errors.stream()
                .anyMatch(error ->
                        error.severity() ==
                                ChromaIntonationError.Severity.SEVERE
                                && error.deviationCents() > 35.0
                );

        assertTrue(
                severeFound,
                "Expected a severe positive deviation around +39 cents"
        );
    }

    private File createSineWave(
            Path directory,
            String name,
            double frequency) throws Exception {

        int sampleCount =
                (int) (SAMPLE_RATE * DURATION_SECONDS);

        byte[] audio = new byte[sampleCount * 2];

        for (int i = 0; i < sampleCount; i++) {

            double time =
                    (double) i / SAMPLE_RATE;

            double sample =
                    Math.sin(
                            2.0 *
                            Math.PI *
                            frequency *
                            time
                    );

            short value =
                    (short) (sample * Short.MAX_VALUE);

            audio[i * 2] =
                    (byte) (value & 0xff);

            audio[i * 2 + 1] =
                    (byte) ((value >> 8) & 0xff);
        }

        AudioFormat format =
                new AudioFormat(
                        SAMPLE_RATE,
                        16,
                        1,
                        true,
                        false
                );

        File file =
                directory
                        .resolve(name + ".wav")
                        .toFile();

        try (
                ByteArrayInputStream input =
                        new ByteArrayInputStream(audio);

                AudioInputStream stream =
                        new AudioInputStream(
                                input,
                                format,
                                sampleCount
                        )
        ) {
            AudioSystem.write(
                    stream,
                    javax.sound.sampled.AudioFileFormat.Type.WAVE,
                    file
            );
        }

        return file;
    }
}
