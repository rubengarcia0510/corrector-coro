package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

class OffsetConstrainedChromaDtwRealAudioTest {

    private static final double GLOBAL_OFFSET_SEC = 0.700;

    private static final double REGRESSION_SLOPE = 0.8961;
    private static final double REGRESSION_INTERCEPT = 1.4784;
    private static final double REGRESSION_WINDOW_SEC = 0.750;

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
        System.out.println("---- CHROMA DISTANCE VS TEMPORAL ERROR ----");

        double[] thresholds = {0.05, 0.10, 0.20, 0.30, 0.50};

        for (double threshold : thresholds) {
            int matches = 0;
            int goodTemporal = 0;
            int badTemporal = 0;

            for (Alignment alignment : result.alignments()) {
                double offset =
                        alignment.performanceTime()
                                - alignment.referenceTime();

                double absOffset = Math.abs(offset);

                if (alignment.distance() <= threshold) {
                    matches++;

                    if (absOffset <= 0.500) {
                        goodTemporal++;
                    } else {
                        badTemporal++;
                    }
                }
            }

            double badPercentage =
                    matches == 0
                            ? 0.0
                            : 100.0 * badTemporal / matches;

            System.out.printf(
                    Locale.US,
                    "distance<=%.2f matches=%d good(<=0.500s)=%d bad(>0.500s)=%d bad%%=%.1f%%%n",
                    threshold,
                    matches,
                    goodTemporal,
                    badTemporal,
                    badPercentage
            );
        }

        System.out.println();
        System.out.println("---- LOW DISTANCE / LARGE TEMPORAL ERROR ----");

        int printed = 0;

        for (Alignment alignment : result.alignments()) {
            double offset =
                    alignment.performanceTime()
                            - alignment.referenceTime();

            double absOffset = Math.abs(offset);

            if (alignment.distance() <= 0.10
                    && absOffset > 0.500) {

                System.out.printf(
                        Locale.US,
                        "ref=%5.2fs perf=%5.2fs offset=%+6.3fs distance=%.4f%n",
                        alignment.referenceTime(),
                        alignment.performanceTime(),
                        offset,
                        alignment.distance()
                );

                printed++;

                if (printed >= 20) {
                    break;
                }
            }
        }


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

        printRegressionDeviationProfile(
                result.alignments()
        );

        runLocalDtwWindows(
                reference,
                performance
        );
        printSegmentOffsetTrajectory();

        List<ChromaFrame> regressionPerformance =
                performance.stream()
                        .map(frame -> {
                            double correctedTime =
                                    (frame.timestampSec()
                                            - REGRESSION_INTERCEPT)
                                            / REGRESSION_SLOPE;

                            return new ChromaFrame(
                                    correctedTime,
                                    frame.chroma()
                            );
                        })
                        .filter(frame ->
                                frame.timestampSec() >= 0.0)
                        .toList();

        System.out.println();
        System.out.println(
                "---- DTW SEGMENT TRAJECTORY WINDOWS ----"
        );

        double[] trajectoryWindows = {
                0.250,
                0.500,
                0.750
        };

        for (double window : trajectoryWindows) {

            Result trajectoryResult =
                    alignWithSegmentTrajectoryWindow(
                            reference,
                            performance,
                            window
                    );

            System.out.printf(
                    Locale.US,
                    "window=+/-%.3fs alignments=%d avgDistance=%.4f%n",
                    window,
                    trajectoryResult.alignments().size(),
                    trajectoryResult.averageDistance()
            );
        }

        System.out.println();
        System.out.println(
                "---- DTW REGRESSION WINDOWS ----"
        );

        double[] windows = {
                0.250,
                0.500,
                0.750,
                1.000
        };

        for (double window : windows) {

            Result constrainedResult =
                    alignWithRegressionWindow(
                            reference,
                            performance,
                            window
                    );

            System.out.printf(
                    Locale.US,
                    "window=+/-%.3fs alignments=%d avgDistance=%.4f%n",
                    window,
                    constrainedResult.alignments().size(),
                    constrainedResult.averageDistance()
            );

            if (!constrainedResult.alignments().isEmpty()) {
                printOffsetProfile(
                        constrainedResult.alignments()
                );
            }
        }
    }


    private Result alignWithSegmentTrajectoryWindow(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double windowSec) {

        double[][] anchors = {
                {3.0,  0.464},
                {7.0,  0.789},
                {11.0, 0.534},
                {15.0, 0.302},
                {19.0, -0.441},
                {23.0, -0.998},
                {27.0, -1.370}
        };

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

            double referenceTime =
                    reference.get(i - 1).timestampSec();

            double expectedOffset =
                    interpolateOffset(
                            referenceTime,
                            anchors
                    );

            double expectedPerformanceTime =
                    referenceTime + expectedOffset;

            for (int j = 1; j <= m; j++) {

                double performanceTime =
                        performance.get(j - 1).timestampSec();

                if (Math.abs(
                        performanceTime
                                - expectedPerformanceTime)
                        > windowSec) {
                    continue;
                }

                double distance =
                        cosineDistance(
                                reference.get(i - 1).chroma(),
                                performance.get(j - 1).chroma()
                        );

                cost[i][j] =
                        distance
                                + Math.min(
                                cost[i - 1][j],
                                Math.min(
                                        cost[i][j - 1],
                                        cost[i - 1][j - 1]
                                )
                        );
            }
        }

        if (Double.isInfinite(cost[n][m])) {
            return new Result(
                    List.of(),
                    Double.POSITIVE_INFINITY
            );
        }

        List<Alignment> path =
                new java.util.ArrayList<>();

        int i = n;
        int j = m;
        double totalDistance = 0.0;

        while (i > 0 && j > 0) {

            double distance =
                    cosineDistance(
                            reference.get(i - 1).chroma(),
                            performance.get(j - 1).chroma()
                    );

            path.add(
                    new Alignment(
                            reference.get(i - 1).timestampSec(),
                            performance.get(j - 1).timestampSec(),
                            distance
                    )
            );

            totalDistance += distance;

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

        java.util.Collections.reverse(path);

        return new Result(
                path,
                path.isEmpty()
                        ? Double.POSITIVE_INFINITY
                        : totalDistance / path.size()
        );
    }

    private Result alignWithRegressionWindow(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double windowSec) {

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

            double referenceTime =
                    reference.get(i - 1).timestampSec();

            double expectedPerformanceTime =
                    REGRESSION_SLOPE * referenceTime
                            + REGRESSION_INTERCEPT;

            for (int j = 1; j <= m; j++) {

                double performanceTime =
                        performance.get(j - 1).timestampSec();

                if (Math.abs(
                        performanceTime - expectedPerformanceTime)
                        > windowSec) {
                    continue;
                }

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
                    cost[i][j] = distance + previous;
                }
            }
        }

        if (!Double.isFinite(cost[n][m])) {
            return new Result(
                    Collections.emptyList(),
                    Double.POSITIVE_INFINITY
            );
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

        return new Result(
                path,
                path.isEmpty()
                        ? 0.0
                        : totalDistance / path.size()
        );
    }

    private void printDominantOffset(
            List<Alignment> alignments,
            double start,
            double end) {

        List<Double> offsets =
                alignments.stream()
                        .filter(a ->
                                a.referenceTime() >= start
                                        && a.referenceTime() <= end)
                        .map(a ->
                                a.performanceTime()
                                        - a.referenceTime())
                        .sorted()
                        .toList();

        if (offsets.isEmpty()) {
            System.out.printf(
                    Locale.US,
                    "dominantOffset window=%4.0f-%4.0fs unavailable%n",
                    start,
                    end
            );
            return;
        }

        double median;

        int size = offsets.size();

        if (size % 2 == 0) {
            median =
                    (offsets.get(size / 2 - 1)
                            + offsets.get(size / 2)) / 2.0;
        } else {
            median =
                    offsets.get(size / 2);
        }

        double q1 =
                offsets.get(
                        Math.max(0, size / 4)
                );

        double q3 =
                offsets.get(
                        Math.min(
                                size - 1,
                                (3 * size) / 4
                        )
                );

        System.out.printf(
                Locale.US,
                "dominantOffset window=%4.0f-%4.0fs " +
                        "samples=%d median=%+.3fs Q1=%+.3fs Q3=%+.3fs%n",
                start,
                end,
                size,
                median,
                q1,
                q3
        );
    }

    private void printSegmentOffsetTrajectory() {
        double[][] anchors = {
                {3.0,  0.464},
                {7.0,  0.789},
                {11.0, 0.534},
                {15.0, 0.302},
                {19.0, -0.441},
                {23.0, -0.998},
                {27.0, -1.370}
        };

        System.out.println();
        System.out.println("---- SEGMENT OFFSET TRAJECTORY ----");

        for (double referenceTime = 3.0;
             referenceTime <= 27.0;
             referenceTime += 2.0) {

            double offset =
                    interpolateOffset(referenceTime, anchors);

            double expectedPerformanceTime =
                    referenceTime + offset;

            System.out.printf(
                    Locale.US,
                    "ref=%5.2fs offset=%+.3fs expectedPerf=%5.2fs%n",
                    referenceTime,
                    offset,
                    expectedPerformanceTime
            );
        }
    }

    private double interpolateOffset(
            double referenceTime,
            double[][] anchors) {

        if (referenceTime <= anchors[0][0]) {
            return anchors[0][1];
        }

        for (int i = 1; i < anchors.length; i++) {

            double leftTime = anchors[i - 1][0];
            double leftOffset = anchors[i - 1][1];

            double rightTime = anchors[i][0];
            double rightOffset = anchors[i][1];

            if (referenceTime <= rightTime) {

                double ratio =
                        (referenceTime - leftTime)
                                / (rightTime - leftTime);

                return leftOffset
                        + ratio * (rightOffset - leftOffset);
            }
        }

        return anchors[anchors.length - 1][1];
    }

    private void runLocalDtwWindows(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

        System.out.println();
        System.out.println("---- LOCAL DTW WINDOWS ----");

        double[][] windows = {
                {0.0, 6.0},
                {4.0, 10.0},
                {8.0, 14.0},
                {12.0, 18.0},
                {16.0, 22.0},
                {20.0, 26.0},
                {24.0, 30.0}
        };

        for (double[] window : windows) {

            double start = window[0];
            double end = window[1];

            List<ChromaFrame> referenceWindow =
                    reference.stream()
                            .filter(frame ->
                                    frame.timestampSec() >= start
                                            && frame.timestampSec() <= end)
                            .toList();

            List<ChromaFrame> performanceWindow =
                    performance.stream()
                            .filter(frame ->
                                    frame.timestampSec() >= start
                                            && frame.timestampSec() <= end)
                            .toList();

            Result result =
                    align(
                            referenceWindow,
                            performanceWindow
                    );

            System.out.printf(
                    Locale.US,
                    "window=%4.0f-%4.0fs refFrames=%d perfFrames=%d " +
                            "alignments=%d avgDistance=%.4f%n",
                    start,
                    end,
                    referenceWindow.size(),
                    performanceWindow.size(),
                    result.alignments().size(),
                    result.averageDistance()
            );

            if (!result.alignments().isEmpty()) {
                printDominantOffset(
                        result.alignments(),
                        start,
                        end
                );
            }
        }
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

    private void printRegressionDeviationProfile(
            List<Alignment> alignments) {

        System.out.println("---- DTW VS REGRESSION PROFILE ----");

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

                double actualPerformanceTime =
                        nearest.performanceTime();

                double expectedPerformanceTime =
                        REGRESSION_SLOPE
                                * nearest.referenceTime()
                                + REGRESSION_INTERCEPT;

                double actualOffset =
                        actualPerformanceTime
                                - nearest.referenceTime();

                double expectedOffset =
                        expectedPerformanceTime
                                - nearest.referenceTime();

                double deviation =
                        actualPerformanceTime
                                - expectedPerformanceTime;

                System.out.printf(
                        Locale.US,
                        "ref=%5.2fs actualPerf=%5.2fs " +
                                "expectedPerf=%5.2fs " +
                                "actualOffset=%+6.3fs " +
                                "expectedOffset=%+6.3fs " +
                                "deviation=%+6.3fs distance=%.4f%n",
                        nearest.referenceTime(),
                        actualPerformanceTime,
                        expectedPerformanceTime,
                        actualOffset,
                        expectedOffset,
                        deviation,
                        nearest.distance()
                );
            }
        }
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
