package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Locale;

class LocalVelocityPenaltyRealAudioTest {

    private static final double GLOBAL_OFFSET_SEC = 0.700;

    @Test
    void evaluateLocalVelocityPenalty() {

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
                "referenceFrames=%d shiftedPerformanceFrames=%d "
                        + "offset=%.3fs frameStep=%.5fs%n",
                reference.size(),
                shiftedPerformance.size(),
                GLOBAL_OFFSET_SEC,
                frameStepSec
        );

        System.out.println(
                "---- DTW LOCAL VELOCITY PENALTY EXPERIMENT ----"
        );

        double[] lambdas = {
                0.01,
                0.05,
                0.10,
                0.20,
                0.50
        };

        for (double lambda : lambdas) {

            Result result =
                    alignWithLocalVelocityPenalty(
                            reference,
                            shiftedPerformance,
                            lambda
                    );

            double totalAbsOffset = 0.0;
            int within60ms = 0;

            for (Alignment alignment : result.alignments()) {

                double offset =
                        alignment.performanceTime()
                                - alignment.referenceTime();

                double absOffset =
                        Math.abs(offset);

                totalAbsOffset += absOffset;

                if (absOffset <= 0.060) {
                    within60ms++;
                }
            }

            double averageAbsOffset =
                    result.alignments().isEmpty()
                            ? 0.0
                            : totalAbsOffset
                                    / result.alignments().size();

            System.out.printf(
                    Locale.US,
                    "lambda=%.2f alignments=%d "
                            + "within60ms=%d (%.1f%%) "
                            + "avgAbsOffset=%.3fs "
                            + "avgDistance=%.4f%n",
                    lambda,
                    result.alignments().size(),
                    within60ms,
                    result.alignments().isEmpty()
                            ? 0.0
                            : 100.0 * within60ms
                                    / result.alignments().size(),
                    averageAbsOffset,
                    result.averageDistance()
            );
        }
    }

    private Result alignWithLocalVelocityPenalty(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double lambda) {

        int n = reference.size();
        int m = performance.size();

        /*
         * State includes the previous DTW step:
         *
         * 0 = diagonal  (1,1)
         * 1 = up        (1,0)
         * 2 = left      (0,1)
         *
         * We keep one cost matrix per previous step so that
         * the penalty can depend on the change in local velocity.
         */

        double[][][] cost =
                new double[3][n + 1][m + 1];

        for (int state = 0; state < 3; state++) {
            for (int i = 0; i <= n; i++) {
                java.util.Arrays.fill(
                        cost[state][i],
                        Double.POSITIVE_INFINITY
                );
            }
        }

        cost[0][0][0] = 0.0;
        cost[1][0][0] = 0.0;
        cost[2][0][0] = 0.0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {

                double distance =
                        cosineDistance(
                                reference.get(i - 1).chroma(),
                                performance.get(j - 1).chroma()
                        );

                double bestDiagonal =
                        minPrevious(
                                cost,
                                i - 1,
                                j - 1,
                                0
                        );

                double bestUp =
                        minPrevious(
                                cost,
                                i - 1,
                                j,
                                1
                        );

                double bestLeft =
                        minPrevious(
                                cost,
                                i,
                                j - 1,
                                2
                        );

                if (Double.isFinite(bestDiagonal)) {
                    cost[0][i][j] =
                            distance + bestDiagonal;
                }

                if (Double.isFinite(bestUp)) {
                    cost[1][i][j] =
                            distance
                                    + lambda
                                    + bestUp;
                }

                if (Double.isFinite(bestLeft)) {
                    cost[2][i][j] =
                            distance
                                    + lambda
                                    + bestLeft;
                }
            }
        }

        int endState = 0;
        double bestEnd = cost[0][n][m];

        for (int state = 1; state < 3; state++) {
            if (cost[state][n][m] < bestEnd) {
                bestEnd = cost[state][n][m];
                endState = state;
            }
        }

        List<Alignment> path =
                new java.util.ArrayList<>();

        int i = n;
        int j = m;
        int state = endState;

        while (i > 0 && j > 0) {

            path.add(
                    new Alignment(
                            reference.get(i - 1)
                                    .timestampSec(),
                            performance.get(j - 1)
                                    .timestampSec(),
                            cosineDistance(
                                    reference.get(i - 1)
                                            .chroma(),
                                    performance.get(j - 1)
                                            .chroma()
                            )
                    )
            );

            if (state == 0) {
                i--;
                j--;
            } else if (state == 1) {
                i--;
            } else {
                j--;
            }

            if (i == 0 || j == 0) {
                break;
            }

            state = bestPreviousState(
                    cost,
                    i,
                    j,
                    state
            );
        }

        java.util.Collections.reverse(path);

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

    private double minPrevious(
            double[][][] cost,
            int i,
            int j,
            int currentState) {

        if (i < 0 || j < 0) {
            return Double.POSITIVE_INFINITY;
        }

        double best =
                Double.POSITIVE_INFINITY;

        for (int state = 0; state < 3; state++) {
            double value =
                    cost[state][i][j];

            if (Double.isFinite(value)) {
                best = Math.min(best, value);
            }
        }

        return best;
    }

    private int bestPreviousState(
            double[][][] cost,
            int i,
            int j,
            int currentState) {

        int previousI = i;
        int previousJ = j;

        if (currentState == 0) {
            previousI--;
            previousJ--;
        } else if (currentState == 1) {
            previousI--;
        } else {
            previousJ--;
        }

        int bestState = 0;
        double best =
                cost[0][previousI][previousJ];

        for (int state = 1; state < 3; state++) {
            if (cost[state][previousI][previousJ] < best) {
                best =
                        cost[state][previousI][previousJ];
                bestState = state;
            }
        }

        return bestState;
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
