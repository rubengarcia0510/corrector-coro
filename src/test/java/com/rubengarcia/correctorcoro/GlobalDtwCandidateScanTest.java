package com.rubengarcia.correctorcoro;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
class GlobalDtwCandidateScanTest {

    private static final File REFERENCE_FILE =
            new File("regina-ref-30s.wav");

    private static final File PERFORMANCE_FILE =
            new File("regina-flores.wav");

    private static final double CANDIDATE_STEP_SEC = 5.0;
    private static final double TARGET_FRAME_STEP_SEC = 0.10;
    private static final double LAMBDA = 0.50;

    private static final int TOP_RESULTS = 10;

    @Test
    void scanGlobalCandidates() {

        TarsosChromaExtractor extractor =
                new TarsosChromaExtractor();

        List<ChromaFrame> reference =
                downsample(
                        extractor.extract(REFERENCE_FILE),
                        TARGET_FRAME_STEP_SEC
                );

        List<ChromaFrame> performance =
                extractor.extract(PERFORMANCE_FILE);

        double referenceDuration =
                duration(reference);

        log.info(
                "REFERENCE frames={} duration={}s",
                reference.size(),
                referenceDuration
        );

        log.info(
                "PERFORMANCE frames={} duration={}s",
                performance.size(),
                duration(performance)
        );

        List<Result> results = new ArrayList<>();

        double maxStart =
                duration(performance) - referenceDuration;

        for (double start = 0.0;
             start <= maxStart;
             start += CANDIDATE_STEP_SEC) {

            final double candidateStart = start;
            final double candidateEnd =
                    candidateStart + referenceDuration;

            List<ChromaFrame> candidate =
                    performance.stream()
                            .filter(frame ->
                                    frame.timestampSec() >= candidateStart
                                            && frame.timestampSec() <= candidateEnd)
                            .toList();

            List<ChromaFrame> candidateDownsampled =
                    downsample(
                            candidate,
                            TARGET_FRAME_STEP_SEC
                    );

            if (candidateDownsampled.size()
                    < reference.size() * 0.70) {
                continue;
            }

            Result result =
                    align(
                            reference,
                            candidateDownsampled,
                            candidateStart,
                            LAMBDA
                    );

            results.add(result);
        }

        results.sort(
                Comparator.comparingDouble(
                        Result::combinedScore
                )
        );

        log.info("---- GLOBAL TOP {} ----", TOP_RESULTS);

        for (int i = 0;
             i < Math.min(TOP_RESULTS, results.size());
             i++) {

            Result result = results.get(i);

            log.info(
                    "#{} start={} end={} path={} avgDistance={} avgTemporalDeviation={} combined={}",
                    i + 1,
                    result.start(),
                    result.end(),
                    result.pathSize(),
                    result.averageDistance(),
                    result.averageTemporalDeviation(),
                    result.combinedScore()
            );
        }
    }

    private Result align(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double performanceStart,
            double lambda) {

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

                double referenceRelativeTime =
                        reference.get(i - 1).timestampSec()
                                - reference.get(0).timestampSec();

                double performanceRelativeTime =
                        performance.get(j - 1).timestampSec()
                                - performanceStart;

                double temporalDeviation =
                        Math.abs(
                                performanceRelativeTime
                                        - referenceRelativeTime
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
                            distance
                                    + lambda * temporalDeviation
                                    + previous;
                }
            }
        }

        int i = n;
        int j = m;

        double totalDistance = 0.0;
        double totalTemporalDeviation = 0.0;
        int pathSize = 0;

        while (i > 0 && j > 0) {

            double distance =
                    cosineDistance(
                            reference.get(i - 1).chroma(),
                            performance.get(j - 1).chroma()
                    );

            double referenceRelativeTime =
                    reference.get(i - 1).timestampSec()
                            - reference.get(0).timestampSec();

            double performanceRelativeTime =
                    performance.get(j - 1).timestampSec()
                            - performanceStart;

            double temporalDeviation =
                    Math.abs(
                            performanceRelativeTime
                                    - referenceRelativeTime
                    );

            totalDistance += distance;
            totalTemporalDeviation += temporalDeviation;
            pathSize++;

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

        double averageDistance =
                totalDistance / pathSize;

        double averageTemporalDeviation =
                totalTemporalDeviation / pathSize;

        double combinedScore =
                averageDistance
                        + lambda * averageTemporalDeviation;

        return new Result(
                performanceStart,
                performanceStart
                        + duration(performance),
                pathSize,
                averageDistance,
                averageTemporalDeviation,
                combinedScore
        );
    }

    private List<ChromaFrame> downsample(
            List<ChromaFrame> frames,
            double targetStepSec) {

        if (frames.isEmpty()) {
            return frames;
        }

        List<ChromaFrame> result =
                new ArrayList<>();

        double nextTimestamp =
                frames.get(0).timestampSec();

        for (ChromaFrame frame : frames) {

            if (frame.timestampSec() >= nextTimestamp) {
                result.add(frame);
                nextTimestamp += targetStepSec;
            }
        }

        return result;
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

        return 1.0
                - dot / (
                        Math.sqrt(normA)
                                * Math.sqrt(normB)
                );
    }

    private double duration(
            List<ChromaFrame> frames) {

        if (frames.size() < 2) {
            return 0.0;
        }

        return frames.get(frames.size() - 1).timestampSec()
                - frames.get(0).timestampSec();
    }

    private record Result(
            double start,
            double end,
            int pathSize,
            double averageDistance,
            double averageTemporalDeviation,
            double combinedScore) {
    }
}
