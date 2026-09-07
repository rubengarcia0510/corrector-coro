package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpectralPolyphonicIntonationTest {

    @Test
    void debeDetectarDesafinacionDe40CentsEnLasTresVoces() throws Exception {

        double[] referencia = {
                261.63,
                329.63,
                392.00
        };

        double[] ensayo = {
                desplazarCents(261.63, 40),
                desplazarCents(329.63, 40),
                desplazarCents(392.00, 40)
        };

        File referenciaWav = crearAcorde(referencia);
        File ensayoWav = crearAcorde(ensayo);

        try {
            SpectralPitchExtractor extractor =
                    new SpectralPitchExtractor();

            List<SpectralFrame> refFrames =
                    extractor.extract(referenciaWav);

            List<SpectralFrame> ensayoFrames =
                    extractor.extract(ensayoWav);

            assertFalse(refFrames.isEmpty());
            assertFalse(ensayoFrames.isEmpty());

            List<SpectralPitch> refPeaks =
                    refFrames.get(refFrames.size() / 2).peaks();

            List<SpectralPitch> ensayoPeaks =
                    ensayoFrames.get(ensayoFrames.size() / 2).peaks();

            System.out.println(
                    "========== POLYPHONIC INTONATION =========="
            );

            for (double frecuenciaReferencia : referencia) {

                SpectralPitch ref =
                        buscarMasCercano(
                                refPeaks,
                                frecuenciaReferencia
                        );

                double frecuenciaEsperada =
                        desplazarCents(
                                frecuenciaReferencia,
                                40
                        );

                SpectralPitch perf =
                        buscarMasCercano(
                                ensayoPeaks,
                                frecuenciaEsperada
                        );

                double cents =
                        calcularCents(
                                ref.frequencyHz(),
                                perf.frequencyHz()
                        );

                System.out.printf(
                        "Ref %.2f Hz -> %.2f Hz | Ensayo %.2f Hz -> %.2f Hz | %.2f cents%n",
                        frecuenciaReferencia,
                        ref.frequencyHz(),
                        perf.frequencyHz(),
                        frecuenciaEsperada,
                        cents
                );

                assertEquals(
                        40.0,
                        cents,
                        8.0
                );
            }

            System.out.println(
                    "============================================"
            );

        } finally {
            referenciaWav.delete();
            ensayoWav.delete();
        }
    }

    private static double desplazarCents(
            double frequency,
            double cents) {

        return frequency *
                Math.pow(
                        2.0,
                        cents / 1200.0
                );
    }

    private static double calcularCents(
            double referencia,
            double ensayo) {

        return 1200.0 *
                Math.log(ensayo / referencia)
                / Math.log(2.0);
    }

    private static SpectralPitch buscarMasCercano(
            List<SpectralPitch> peaks,
            double expected) {

        return peaks.stream()
                .min((a, b) ->
                        Double.compare(
                                Math.abs(
                                        a.frequencyHz()
                                                - expected
                                ),
                                Math.abs(
                                        b.frequencyHz()
                                                - expected
                                )
                        )
                )
                .orElseThrow(
                        () -> new AssertionError(
                                "No se encontró componente espectral"
                        )
                );
    }

    private File crearAcorde(
            double... frequencies) throws Exception {

        float sampleRate = 44100f;
        int seconds = 2;
        int samples =
                (int) (sampleRate * seconds);

        byte[] data =
                new byte[samples * 2];

        for (int i = 0; i < samples; i++) {

            double value = 0.0;

            for (double frequency : frequencies) {

                value += Math.sin(
                        2.0 *
                                Math.PI *
                                frequency *
                                i /
                                sampleRate
                );
            }

            value /= frequencies.length;

            short sample =
                    (short) (value * 16000);

            data[i * 2] =
                    (byte) (sample & 0xff);

            data[i * 2 + 1] =
                    (byte) ((sample >> 8) & 0xff);
        }

        AudioFormat format =
                new AudioFormat(
                        sampleRate,
                        16,
                        1,
                        true,
                        false
                );

        File file =
                File.createTempFile(
                        "polyphonic-intonation-",
                        ".wav"
                );

        try (AudioInputStream stream =
                     new AudioInputStream(
                             new ByteArrayInputStream(data),
                             format,
                             samples
                     )) {

            AudioSystem.write(
                    stream,
                    AudioFileFormat.Type.WAVE,
                    file
            );
        }

        return file;
    }
}
