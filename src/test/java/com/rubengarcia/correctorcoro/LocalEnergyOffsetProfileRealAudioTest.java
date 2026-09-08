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

class LocalEnergyOffsetProfileRealAudioTest {

    private static final int BUFFER_SIZE = 4096;
    private static final int OVERLAP = 3072;

    @Test
    void measureLocalEnergyOffsetProfileOnRealAudio() throws Exception {
        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        List<EnergyFrame> reference = extractEnergy(referenceFile);
        List<EnergyFrame> performance = extractEnergy(performanceFile);

        double frameStepSec =
                reference.get(2).timestampSec()
                        - reference.get(1).timestampSec();

        System.out.println("---- LOCAL ENERGY OFFSET PROFILE ----");

        double windowSec = 2.0;
        double searchRadiusSec = 1.5;
        int windowFrames =
                (int) Math.round(windowSec / frameStepSec);

        for (double centerTime = 2.0;
             centerTime <= 28.0;
             centerTime += 2.0) {

            int centerIndex =
                    (int) Math.round(centerTime / frameStepSec);

            double bestOffset = 0.0;
            double bestDistance = Double.POSITIVE_INFINITY;

            for (int offsetMs = -1500;
                 offsetMs <= 1500;
                 offsetMs += 25) {

                double offsetSec = offsetMs / 1000.0;

                double totalDistance = 0.0;
                int comparisons = 0;

                for (int delta = -windowFrames / 2;
                     delta <= windowFrames / 2;
                     delta++) {

                    int refIndex = centerIndex + delta;

                    if (refIndex < 0 || refIndex >= reference.size()) {
                        continue;
                    }

                    double targetTime =
                            reference.get(refIndex).timestampSec()
                                    + offsetSec;

                    int performanceIndex =
                            (int) Math.round(targetTime / frameStepSec);

                    if (performanceIndex < 0
                            || performanceIndex >= performance.size()) {
                        continue;
                    }

                    double refEnergy = reference.get(refIndex).rms();
                    double perfEnergy =
                            performance.get(performanceIndex).rms();

                    totalDistance += Math.abs(
                            Math.log1p(refEnergy)
                                    - Math.log1p(perfEnergy)
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
                    "ref=%5.2fs -> offset=%+6.3fs distance=%.5f%n",
                    centerTime,
                    bestOffset,
                    bestDistance
            );
        }

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
