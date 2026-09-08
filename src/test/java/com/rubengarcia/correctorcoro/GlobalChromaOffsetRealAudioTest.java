package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Locale;
import java.util.List;

class GlobalChromaOffsetRealAudioTest {

    @Test
    void measureGlobalChromaOffsetProfileOnRealAudio() {
        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        TarsosChromaExtractor extractor = new TarsosChromaExtractor();

        List<ChromaFrame> reference = extractor.extract(referenceFile);
        List<ChromaFrame> performance = extractor.extract(performanceFile);

        double frameStepSec =
                reference.get(2).timestampSec()
                        - reference.get(1).timestampSec();

        System.out.println("---- GLOBAL CHROMA OFFSET PROFILE ----");

        for (double referenceTime = 2.0;
             referenceTime <= 28.0;
             referenceTime += 2.0) {

            int referenceIndex =
                    (int) Math.round(referenceTime / frameStepSec);

            if (referenceIndex < 0 || referenceIndex >= reference.size()) {
                continue;
            }

            double bestOffset = 0.0;
            double bestDistance = Double.POSITIVE_INFINITY;

            for (int offsetMs = -1500; offsetMs <= 1500; offsetMs += 25) {
                double offsetSec = offsetMs / 1000.0;

                double totalDistance = 0.0;
                int comparisons = 0;

                int windowFrames =
                        (int) Math.round(0.75 / frameStepSec);

                for (int delta = -windowFrames;
                     delta <= windowFrames;
                     delta++) {

                    int refIndex = referenceIndex + delta;

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

                    totalDistance += cosineDistance(
                            reference.get(refIndex).chroma(),
                            performance.get(performanceIndex).chroma()
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
                    "ref=%5.2fs -> offset=%+6.3fs distance=%.4f%n",
                    referenceTime,
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

    private double cosineDistance(double[] a, double[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 1.0;
        }

        return 1.0 - dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
