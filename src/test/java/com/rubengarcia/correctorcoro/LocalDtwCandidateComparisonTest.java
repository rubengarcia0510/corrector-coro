package com.rubengarcia.correctorcoro;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
class LocalDtwCandidateComparisonTest {

    private static final File REFERENCE_FILE =
            new File("regina-ref-30s.wav");

    private static final File PERFORMANCE_FILE =
            new File("regina-flores.wav");

    private static final double FRAME_STEP_SEC = 0.023219954648526077;

    private static final double[] CANDIDATE_STARTS = {
            0.0,
            494.0,
            955.0
    };

    private static final double[] LAMBDAS = {
            0.0,
            0.20,
            0.50
    };

    @Test
    void compareLocalDtwCandidates() {

        TarsosChromaExtractor extractor =
                new TarsosChromaExtractor();

        List<ChromaFrame> reference =
                extractor.extract(REFERENCE_FILE);

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

        for (double candidateStart : CANDIDATE_STARTS) {

            double candidateEnd =
                    candidateStart + referenceDuration;

            List<ChromaFrame> candidate =
                    performance.stream()
                            .filter(frame ->
                                    frame.timestampSec() >= candidateStart
                                            && frame.timestampSec() <= candidateEnd)
                            .toList();

            log.info(
                    "---- CANDIDATE start={} end={} frames={} ----",
                    candidateStart,
                    candidateEnd,
                    candidate.size()
            );

            if (candidate.size() < reference.size() / 2) {
                log.warn(
                        "Candidate too short: start={} frames={}",
                        candidateStart,
                        candidate.size()
                );
                continue;
            }

            for (double lambda : LAMBDAS) {

                Result result =
                        alignWithTemporalPenalty(
                                reference,
                                candidate,
                                candidateStart,
                                lambda
                        );

                log.info(
                        "candidateStart={} lambda={} path={} avgDistance={} avgTemporalDeviation={} combined={}",
                        candidateStart,
                        lambda,
                        result.alignments().size(),
                        result.averageDistance(),
                        result.averageTemporalDeviation(),
                        result.combinedScore()
                );
            }
        }
    }

    private Result alignWithTemporalPenalty(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double performanceStart,
            double lambda) {

        int n = reference.size();
        int m = performance.size();

        double[][] cost =
                new double[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            Arrays.fill(
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

                /*
                 * Temporal deviation is measured relative to
                 * the candidate window, not absolute timestamps.
                 */
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

                double temporalPenalty =
                        lambda * temporalDeviation;

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
                                    + temporalPenalty
                                    + previous;
                }
            }
        }

        List<Alignment> path =
                new ArrayList<>();

        int i = n;
        int j = m;

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

            path.add(
                    new Alignment(
                            reference.get(i - 1).timestampSec(),
                            performance.get(j - 1).timestampSec(),
                            distance,
                            temporalDeviation
                    )
            );

            double current =
                    cost[i][j];

            double diagonal =
                    i > 0 && j > 0
                            ? cost[i - 1][j - 1]
                            : Double.POSITIVE_INFINITY;

            double up =
                    i > 0
                            ? cost[i - 1][j]
                            : Double.POSITIVE_INFINITY;

            double left =
                    j > 0
                            ? cost[i][j - 1]
                            : Double.POSITIVE_INFINITY;

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

        double totalDistance = 0.0;
        double totalTemporalDeviation = 0.0;

        for (Alignment alignment : path) {
            totalDistance += alignment.distance();
            totalTemporalDeviation +=
                    alignment.temporalDeviation();
        }

        double averageDistance =
                path.isEmpty()
                        ? Double.POSITIVE_INFINITY
                        : totalDistance / path.size();

        double averageTemporalDeviation =
                path.isEmpty()
                        ? Double.POSITIVE_INFINITY
                        : totalTemporalDeviation / path.size();

        double combinedScore =
                averageDistance
                        + lambda * averageTemporalDeviation;

        return new Result(
                path,
                averageDistance,
                averageTemporalDeviation,
                combinedScore
        );
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

    private record Alignment(
            double referenceTime,
            double performanceTime,
            double distance,
            double temporalDeviation) {
    }

    private record Result(
            List<Alignment> alignments,
            double averageDistance,
            double averageTemporalDeviation,
            double combinedScore) {
    }
}
