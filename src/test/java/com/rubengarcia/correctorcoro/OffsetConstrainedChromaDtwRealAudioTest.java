package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

class OffsetConstrainedChromaDtwRealAudioTest {

    private static final double INITIAL_OFFSET_SEC = 0.700;

    @Test
    void characterizeOffsetConstrainedDtwOnRealAudio() {
        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        TarsosChromaExtractor extractor = new TarsosChromaExtractor();

        List<ChromaFrame> reference = extractor.extract(referenceFile);
        List<ChromaFrame> performance = extractor.extract(performanceFile);

        double frameStepSec =
                reference.get(2).timestampSec()
                        - reference.get(1).timestampSec();

        System.out.println("---- OFFSET-CONSTRAINED CHROMA DTW ----");

        for (double bandSec : new double[]{0.100, 0.250, 0.500}) {
            Result result = align(
                    reference,
                    performance,
                    frameStepSec,
                    INITIAL_OFFSET_SEC,
                    bandSec
            );

            int within60ms = 0;
            double totalAbsOffset = 0.0;
            double maxAbsOffset = 0.0;

            for (Alignment alignment : result.alignments()) {
                double offset =
                        alignment.performanceTime()
                                - alignment.referenceTime();

                double absOffset = Math.abs(offset);

                totalAbsOffset += absOffset;
                maxAbsOffset = Math.max(maxAbsOffset, absOffset);

                if (absOffset <= 0.060) {
                    within60ms++;
                }
            }

            double averageAbsOffset =
                    result.alignments().isEmpty()
                            ? 0.0
                            : totalAbsOffset / result.alignments().size();

            System.out.printf(
                    Locale.US,
                    "band=%.3fs alignments=%d within60ms=%d (%.1f%%) " +
                            "avgAbsOffset=%.3fs maxAbsOffset=%.3fs avgDistance=%.4f%n",
                    bandSec,
                    result.alignments().size(),
                    within60ms,
                    result.alignments().isEmpty()
                            ? 0.0
                            : 100.0 * within60ms / result.alignments().size(),
                    averageAbsOffset,
                    maxAbsOffset,
                    result.averageDistance()
            );

            printOffsetProfile(result.alignments());
        }
    }

    private Result align(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double frameStepSec,
            double initialOffsetSec,
            double bandSec) {

        int n = reference.size();
        int m = performance.size();

        double[][] cost =
                new double[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            java.util.Arrays.fill(
                    cost[i],
                    Double.POSITIVE_INFINITY
            );
        }

        cost[0][0] = 0.0;

        double referenceDuration =
                reference.get(n - 1).timestampSec();

        double performanceDuration =
                performance.get(m - 1).timestampSec();

        for (int i = 1; i <= n; i++) {
            double referenceTime =
                    reference.get(i - 1).timestampSec();

            /*
             * Expected path:
             *
             *   start near +700 ms
             *   finish at the natural end of both recordings.
             *
             * This prevents the +700 ms offset from being
             * incorrectly required at the final frame.
             */
            double progress =
                    referenceDuration == 0.0
                            ? 0.0
                            : referenceTime / referenceDuration;

            double expectedPerformanceTime =
                    referenceTime
                            + INITIAL_OFFSET_SEC * (1.0 - progress);

            int expectedJ =
                    (int) Math.round(
                            expectedPerformanceTime / frameStepSec
                    );

            int radius =
                    (int) Math.ceil(
                            bandSec / frameStepSec
                    );

            int minJ =
                    Math.max(1, expectedJ - radius);

            int maxJ =
                    Math.min(m, expectedJ + radius);

            for (int j = minJ; j <= maxJ; j++) {

                double distance =
                        cosineDistance(
                                reference.get(i - 1).chroma(),
                                performance.get(j - 1).chroma()
                        );

                double previous =
                        Math.min(
                                cost[i - 1][j - 1],
                                Math.min(
                                        cost[i - 1][j],
                                        cost[i][j - 1]
                                )
                        );

                if (Double.isFinite(previous)) {
                    cost[i][j] =
                            distance + previous;
                }
            }
        }

        List<Alignment> path =
                new ArrayList<>();

        int i = n;
        int j = m;

        if (!Double.isFinite(cost[i][j])) {
            return new Result(
                    Collections.emptyList(),
                    0.0
            );
        }

        while (i > 0 && j > 0) {

            path.add(
                    new Alignment(
                            reference.get(i - 1).timestampSec(),
                            performance.get(j - 1).timestampSec(),
                            cosineDistance(
                                    reference.get(i - 1).chroma(),
                                    performance.get(j - 1).chroma()
                            )
                    )
            );

            double diagonal = cost[i - 1][j - 1];
            double up = cost[i - 1][j];
            double left = cost[i][j - 1];

            if (diagonal <= up && diagonal <= left) {
                i--;
                j--;
            } else if (up <= left) {
                i--;
            } else {
                j--;
            }
        }

        Collections.reverse(path);

        double totalDistance = 0.0;

        for (Alignment alignment : path) {
            totalDistance += alignment.distance();
        }

        double averageDistance =
                path.isEmpty()
                        ? 0.0
                        : totalDistance / path.size();

        return new Result(
                path,
                averageDistance
        );
    }

    private void printOffsetProfile(
            List<Alignment> alignments) {

        if (alignments.isEmpty()) {
            return;
        }

        System.out.println("  offset profile:");

        for (double targetTime = 2.0;
             targetTime <= 28.0;
             targetTime += 2.0) {

            Alignment nearest = null;
            double nearestDistance =
                    Double.POSITIVE_INFINITY;

            for (Alignment alignment : alignments) {

                double distance =
                        Math.abs(
                                alignment.referenceTime()
                                        - targetTime
                        );

                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = alignment;
                }
            }

            if (nearest != null) {

                double offset =
                        nearest.performanceTime()
                                - nearest.referenceTime();

                System.out.printf(
                        Locale.US,
                        "    ref=%5.2fs -> perf=%5.2fs offset=%+6.3fs distance=%.4f%n",
                        nearest.referenceTime(),
                        nearest.performanceTime(),
                        offset,
                        nearest.distance()
                );
            }
        }
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

    private record Alignment(
            double referenceTime,
            double performanceTime,
            double distance) {
    }

    private record Result(
            List<Alignment> alignments,
            double averageDistance) {
    }
}
