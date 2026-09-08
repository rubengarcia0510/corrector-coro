package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Locale;

class ChromaWindowSimilarityRealAudioTest {

    private static final double WINDOW_SEC = 2.0;
    private static final double SEARCH_RADIUS_SEC = 3.0;
    private static final double STEP_SEC = 0.25;

    @Test
    void characterizeRealAudioWindowSimilarity() throws Exception {

        File referenceFile =
                new File("regina-ref-30s.wav");

        File performanceFile =
                new File("regina-flores-30s.wav");

        TarsosChromaExtractor extractor =
                new TarsosChromaExtractor();

        List<ChromaFrame> reference =
                extractor.extract(referenceFile);

        List<ChromaFrame> performance =
                extractor.extract(performanceFile);

        double frameStep =
                reference.get(2).timestampSec()
                        - reference.get(1).timestampSec();

        int windowFrames =
                Math.max(
                        1,
                        (int) Math.round(
                                WINDOW_SEC / frameStep
                        )
                );

        System.out.println(
                "---- CHROMA WINDOW SIMILARITY ----"
        );

        System.out.printf(
                Locale.US,
                "referenceFrames=%d performanceFrames=%d " +
                        "frameStep=%.5fs window=%.2fs%n",
                reference.size(),
                performance.size(),
                frameStep,
                WINDOW_SEC
        );

        for (double referenceTime = 2.0;
             referenceTime <= 28.0;
             referenceTime += 2.0) {

            int referenceCenter =
                    findNearestFrame(
                            reference,
                            referenceTime
                    );

            int referenceStart =
                    Math.max(
                            0,
                            referenceCenter
                                    - windowFrames / 2
                    );

            int referenceEnd =
                    Math.min(
                            reference.size(),
                            referenceStart
                                    + windowFrames
                    );

            double bestDistance =
                    Double.POSITIVE_INFINITY;

            double bestPerformanceTime =
                    0.0;

            for (double offset =
                         -SEARCH_RADIUS_SEC;
                 offset <= SEARCH_RADIUS_SEC;
                 offset += STEP_SEC) {

                double performanceTime =
                        referenceTime
                                + 0.700
                                + offset;

                int performanceCenter =
                        findNearestFrame(
                                performance,
                                performanceTime
                        );

                int performanceStart =
                        Math.max(
                                0,
                                performanceCenter
                                        - windowFrames / 2
                        );

                int performanceEnd =
                        Math.min(
                                performance.size(),
                                performanceStart
                                        + windowFrames
                        );

                double distance =
                        windowDistance(
                                reference,
                                referenceStart,
                                referenceEnd,
                                performance,
                                performanceStart,
                                performanceEnd
                        );

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestPerformanceTime =
                            performanceTime;
                }
            }

            double offset =
                    bestPerformanceTime
                            - referenceTime;

            System.out.printf(
                    Locale.US,
                    "ref=%5.2fs -> perf=%5.2fs " +
                            "offset=%+6.3fs distance=%.4f%n",
                    referenceTime,
                    bestPerformanceTime,
                    offset,
                    bestDistance
            );
        }
    }

    private double windowDistance(
            List<ChromaFrame> reference,
            int referenceStart,
            int referenceEnd,
            List<ChromaFrame> performance,
            int performanceStart,
            int performanceEnd) {

        int length =
                Math.min(
                        referenceEnd - referenceStart,
                        performanceEnd - performanceStart
                );

        if (length <= 0) {
            return 1.0;
        }

        double total = 0.0;

        for (int i = 0; i < length; i++) {

            total += cosineDistance(
                    reference
                            .get(referenceStart + i)
                            .chroma(),
                    performance
                            .get(performanceStart + i)
                            .chroma()
            );
        }

        return total / length;
    }

    private int findNearestFrame(
            List<ChromaFrame> frames,
            double timestamp) {

        int bestIndex = 0;
        double bestDistance =
                Double.POSITIVE_INFINITY;

        for (int i = 0; i < frames.size(); i++) {

            double distance =
                    Math.abs(
                            frames.get(i).timestampSec()
                                    - timestamp
                    );

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private double cosineDistance(
            double[] a,
            double[] b) {

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

        return 1.0 -
                dot / (
                        Math.sqrt(normA)
                                * Math.sqrt(normB)
                );
    }
}
