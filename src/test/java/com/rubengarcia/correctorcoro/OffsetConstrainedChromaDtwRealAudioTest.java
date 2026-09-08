package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

class OffsetConstrainedChromaDtwRealAudioTest {

    private static final double GLOBAL_OFFSET_SEC = 0.700;

    @Test
    void characterizeDtwAfterGlobalOffsetCompensation() {
        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        TarsosChromaExtractor extractor = new TarsosChromaExtractor();

        List<ChromaFrame> reference = extractor.extract(referenceFile);
        List<ChromaFrame> performance = extractor.extract(performanceFile);

        double frameStepSec =
                reference.get(2).timestampSec()
                        - reference.get(1).timestampSec();

        List<ChromaFrame> shiftedPerformance =
                performance.stream()
                        .filter(frame ->
                                frame.timestampSec()
                                        >= GLOBAL_OFFSET_SEC)
                        .map(frame ->
                                new ChromaFrame(
                                        frame.timestampSec()
                                                - GLOBAL_OFFSET_SEC,
                                        frame.chroma()
                                ))
                        .toList();

        System.out.printf(
                Locale.US,
                "referenceFrames=%d shiftedPerformanceFrames=%d offset=%.3fs frameStep=%.5fs%n",
                reference.size(),
                shiftedPerformance.size(),
                GLOBAL_OFFSET_SEC,
                frameStepSec
        );

        Result result =
                align(reference, shiftedPerformance);

        double totalAbsOffset = 0.0;
        double maxAbsOffset = 0.0;
        int within60ms = 0;

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

        System.out.println("---- DTW AFTER GLOBAL OFFSET COMPENSATION ----");

        System.out.printf(
                Locale.US,
                "alignments=%d within60ms=%d (%.1f%%) " +
                        "avgAbsOffset=%.3fs maxAbsOffset=%.3fs avgDistance=%.4f%n",
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

    private Result align(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

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

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {

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

        return new Result(path, averageDistance);
    }

    private void printOffsetProfile(
            List<Alignment> alignments) {

        System.out.println("---- OFFSET PROFILE ----");

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
                        "ref=%5.2fs -> perf=%5.2fs offset=%+6.3fs distance=%.4f%n",
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
