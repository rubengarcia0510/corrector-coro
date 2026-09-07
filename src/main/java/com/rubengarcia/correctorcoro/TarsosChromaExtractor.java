package com.rubengarcia.correctorcoro;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.util.fft.FFT;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class TarsosChromaExtractor implements ChromaExtractor {

    private static final int BUFFER_SIZE = 4096;
    private static final int OVERLAP = 3072;
    private static final int CHROMA_BINS = 12;

    private static final double MIN_FREQUENCY = 55.0;
    private static final double MAX_FREQUENCY = 1760.0;

    @Override
    public List<ChromaFrame> extract(File audioFile) {
        List<ChromaFrame> frames = new ArrayList<>();

        try {
            AudioDispatcher dispatcher =
                    AudioDispatcherFactory.fromFile(audioFile, BUFFER_SIZE, OVERLAP);

            FFT fft = new FFT(BUFFER_SIZE);

            dispatcher.addAudioProcessor(new AudioProcessor() {

                @Override
                public boolean process(AudioEvent audioEvent) {
                    float[] buffer = audioEvent.getFloatBuffer().clone();

                    fft.forwardTransform(buffer);

                    double[] chroma = new double[CHROMA_BINS];

                    for (int bin = 1; bin < BUFFER_SIZE / 2; bin++) {
                        double frequency =
                                bin * audioEvent.getSampleRate() / BUFFER_SIZE;

                        if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) {
                            continue;
                        }

                        float magnitude = fft.modulus(buffer, bin);

                        if (magnitude <= 0) {
                            continue;
                        }

                        double midi =
                                69.0 + 12.0 * Math.log(frequency / 440.0) / Math.log(2.0);

                        int pitchClass = ((int) Math.round(midi) % 12 + 12) % 12;

                        chroma[pitchClass] += magnitude * magnitude;
                    }

                    normalize(chroma);

                    frames.add(new ChromaFrame(
                            audioEvent.getTimeStamp(),
                            chroma
                    ));

                    return true;
                }

                @Override
                public void processingFinished() {
                    // Nothing to release.
                }
            });

            dispatcher.run();

            return frames;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to extract chroma from " + audioFile.getName(), e);
        }
    }

    private void normalize(double[] chroma) {
        double sum = 0.0;

        for (double value : chroma) {
            sum += value;
        }

        if (sum == 0.0) {
            return;
        }

        for (int i = 0; i < chroma.length; i++) {
            chroma[i] /= sum;
        }
    }
}
