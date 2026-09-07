package com.rubengarcia.correctorcoro;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.util.fft.FFT;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SpectralPitchExtractor {

    private static final int BUFFER_SIZE = 4096;
    private static final int OVERLAP = 3072;

    private static final double MIN_FREQUENCY = 55.0;
    private static final double MAX_FREQUENCY = 1760.0;

    public List<SpectralFrame> extract(File audioFile) {

        try {
            AudioDispatcher dispatcher =
                    AudioDispatcherFactory.fromFile(
                            audioFile,
                            BUFFER_SIZE,
                            OVERLAP
                    );

            FFT fft = new FFT(BUFFER_SIZE);

            List<SpectralFrame> frames =
                    new ArrayList<>();

            dispatcher.addAudioProcessor(
                    new AudioProcessor() {

                        @Override
                        public boolean process(
                                AudioEvent audioEvent) {

                            float[] buffer =
                                    audioEvent
                                            .getFloatBuffer()
                                            .clone();

                            // Ventana Hann.
                            for (int i = 0;
                                 i < buffer.length;
                                 i++) {

                                double window =
                                        0.5 *
                                                (1.0 -
                                                        Math.cos(
                                                                2.0 *
                                                                        Math.PI *
                                                                        i /
                                                                        (buffer.length - 1)
                                                        ));

                                buffer[i] *=
                                        (float) window;
                            }

                            fft.forwardTransform(buffer);

                            List<SpectralPitch> peaks =
                                    new ArrayList<>();

                            double binWidth =
                                    audioEvent.getSampleRate()
                                            / BUFFER_SIZE;

                            int minBin =
                                    Math.max(
                                            1,
                                            (int) Math.ceil(
                                                    MIN_FREQUENCY /
                                                            binWidth
                                            )
                                    );

                            int maxBin =
                                    Math.min(
                                            BUFFER_SIZE / 2 - 1,
                                            (int) Math.floor(
                                                    MAX_FREQUENCY /
                                                            binWidth
                                            )
                                    );

                            for (int bin = minBin;
                                 bin <= maxBin;
                                 bin++) {

                                double magnitudeLeft =
                                        fft.modulus(
                                                buffer,
                                                bin - 1
                                        );

                                double magnitude =
                                        fft.modulus(
                                                buffer,
                                                bin
                                        );

                                double magnitudeRight =
                                        fft.modulus(
                                                buffer,
                                                bin + 1
                                        );

                                // Solo máximos locales.
                                if (magnitude < magnitudeLeft ||
                                        magnitude < magnitudeRight) {
                                    continue;
                                }

                                double denominator =
                                        magnitudeLeft
                                                - 2.0 * magnitude
                                                + magnitudeRight;

                                double delta = 0.0;

                                if (Math.abs(denominator) > 1e-12) {
                                    delta =
                                            0.5 *
                                                    (magnitudeLeft
                                                            - magnitudeRight)
                                                    / denominator;
                                }

                                double frequency =
                                        (bin + delta) *
                                                binWidth;

                                if (frequency < MIN_FREQUENCY ||
                                        frequency > MAX_FREQUENCY) {
                                    continue;
                                }

                                peaks.add(
                                        new SpectralPitch(
                                                frequency,
                                                magnitude
                                        )
                                );
                            }

                            peaks.sort(
                                    Comparator.comparingDouble(
                                            SpectralPitch::magnitude
                                    ).reversed()
                            );

                            List<SpectralPitch> topPeaks =
                                    peaks.stream()
                                            .limit(20)
                                            .toList();

                            frames.add(
                                    new SpectralFrame(
                                            audioEvent.getTimeStamp(),
                                            topPeaks
                                    )
                            );

                            return true;
                        }

                        @Override
                        public void processingFinished() {
                            // Nada que hacer.
                        }
                    }
            );

            dispatcher.run();

            return frames;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo extraer el espectro del audio",
                    e
            );
        }
    }
}
