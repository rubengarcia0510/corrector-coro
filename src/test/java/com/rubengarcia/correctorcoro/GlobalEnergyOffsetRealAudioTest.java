package com.rubengarcia.correctorcoro;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class GlobalEnergyOffsetRealAudioTest {

    private static final int BUFFER_SIZE = 4096;
    private static final int OVERLAP = 3072;

    @Test
    void measureGlobalEnergyOffsetOnRealAudio() throws Exception {
        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        List<EnergyFrame> reference = extractEnergy(referenceFile);
        List<EnergyFrame> performance = extractEnergy(performanceFile);

        double frameStepSec =
                reference.get(2).timestampSec()
                        - reference.get(1).timestampSec();

        double bestOffset = 0.0;
        double bestDistance = Double.POSITIVE_INFINITY;

        System.out.println("---- GLOBAL ENERGY OFFSET ----");

        for (int offsetMs = -2000; offsetMs <= 2000; offsetMs += 25) {
            double offsetSec = offsetMs / 1000.0;

            double totalDistance = 0.0;
            int comparisons = 0;

            for (EnergyFrame referenceFrame : reference) {
                double targetTime =
                        referenceFrame.timestampSec() + offsetSec;

                int performanceIndex =
                        (int) Math.round(targetTime / frameStepSec);

                if (performanceIndex < 0
                        || performanceIndex >= performance.size()) {
                    continue;
                }

                double refEnergy = referenceFrame.rms();
                double perfEnergy = performance.get(performanceIndex).rms();

                totalDistance += Math.abs(
                        Math.log1p(refEnergy) - Math.log1p(perfEnergy)
                );

                comparisons++;
            }

            if (comparisons == 0) {
                continue;
            }

            double averageDistance =
                    totalDistance / comparisons;

            if (averageDistance < bestDistance) {
                bestDistance = averageDistance;
                bestOffset = offsetSec;
            }
        }

        System.out.printf(
                Locale.US,
                "bestOffset=%+.3fs bestDistance=%.5f%n",
                bestOffset,
                bestDistance
        );

        System.out.printf(
                Locale.US,
                "frameStep=%.5fs referenceFrames=%d performanceFrames=%d%n",
                frameStepSec,
                reference.size(),
                performance.size()
        );
    }

    private List<EnergyFrame> extractEnergy(File file) throws Exception {
        List<EnergyFrame> frames = new ArrayList<>();

        AudioDispatcher dispatcher =
                AudioDispatcherFactory.fromFile(
                        file,
                        BUFFER_SIZE,
                        OVERLAP
                );

        dispatcher.addAudioProcessor(new AudioProcessor() {

            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] buffer = audioEvent.getFloatBuffer();

                double sum = 0.0;

                for (float sample : buffer) {
                    sum += sample * sample;
                }

                double rms =
                        Math.sqrt(sum / buffer.length);

                frames.add(
                        new EnergyFrame(
                                audioEvent.getTimeStamp(),
                                rms
                        )
                );

                return true;
            }

            @Override
            public void processingFinished() {
            }
        });

        dispatcher.run();

        return frames;
    }

    private record EnergyFrame(
            double timestampSec,
            double rms
    ) {
    }
}
