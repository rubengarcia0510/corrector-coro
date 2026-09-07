package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YinPolyphonyDiagnosticTest {

    @Test
    void diagnosticarYinSobreAcorde() throws Exception {

        File wav = crearAcorde(
                261.63,
                329.63,
                392.00
        );

        try {
            PitchExtractor extractor = new PitchExtractor();

            List<PitchPoint> points =
                    extractor.extraerPitch(wav);

            long validos = points.stream()
                    .filter(PitchPoint::esValido)
                    .count();

            System.out.println("======================================");
            System.out.println("YIN POLYPHONY DIAGNOSTIC");
            System.out.println("Total frames: " + points.size());
            System.out.println("Valid pitch: " + validos);

            points.stream()
                    .filter(PitchPoint::esValido)
                    .limit(20)
                    .forEach(point ->
                            System.out.println(
                                    "t=" + point.timestampSec()
                                            + " pitch="
                                            + point.pitchHz()
                                            + " Hz"
                            )
                    );

            System.out.println("======================================");

            assertFalse(points.isEmpty());

        } finally {
            wav.delete();
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
                File.createTempFile("yin-polyphony-", ".wav");

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
