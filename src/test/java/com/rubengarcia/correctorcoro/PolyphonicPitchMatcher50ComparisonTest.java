package com.rubengarcia.correctorcoro;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class PolyphonicPitchMatcher50ComparisonTest {

    private static final double THRESHOLD_CENTS = 50.0;
    private static final double PENALTY_CENTS = 50.0;

    private static final int[] FRAME_INDICES = {322, 644, 966, 1288};
    private static final double[] MAGNITUDE_THRESHOLDS = {1.0, 5.0, 10.0, 20.0};

    @Test
    void compareThreshold50AgainstGlobalPenalty50() throws Exception {

        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        assertNotNull(referenceFile);
        assertNotNull(performanceFile);

        SpectralPitchExtractor extractor = new SpectralPitchExtractor();

        List<SpectralFrame> referenceFrames = extractor.extract(referenceFile);
        List<SpectralFrame> performanceFrames = extractor.extract(performanceFile);

        log.info("========== THRESHOLD 50 vs PENALTY 50 ==========");
        log.info("referenceFrames={} performanceFrames={}",
                referenceFrames.size(), performanceFrames.size());

        for (double magnitudeThreshold : MAGNITUDE_THRESHOLDS) {

            log.info("");
            log.info("---- magnitude > {} ----", magnitudeThreshold);

            for (int frameIndex : FRAME_INDICES) {

                SpectralFrame refFrame = referenceFrames.get(frameIndex);
                SpectralFrame perfFrame = performanceFrames.get(frameIndex);

                List<SpectralPitch> reference = filter(refFrame.peaks(), magnitudeThreshold);
                List<SpectralPitch> performance = filter(perfFrame.peaks(), magnitudeThreshold);

                List<Match> thresholdMatches =
                        threshold50(reference, performance);

                List<Match> penaltyMatches =
                        penalty50(reference, performance);

                log.info(
                        "frame={} ref={} perf={} threshold50={} penalty50={} identical={}",
                        frameIndex,
                        reference.size(),
                        performance.size(),
                        thresholdMatches.size(),
                        penaltyMatches.size(),
                        sameAssignments(thresholdMatches, penaltyMatches)
                );

                logMatches("THRESHOLD50", thresholdMatches);
                logMatches("PENALTY50", penaltyMatches);

                if (!sameAssignments(thresholdMatches, penaltyMatches)) {
                    log.warn("DIFFERENCE DETECTED at frame={} magnitude>{}",
                            frameIndex, magnitudeThreshold);
                }
            }
        }
    }

    private List<SpectralPitch> filter(
            List<SpectralPitch> peaks,
            double magnitudeThreshold) {

        return peaks.stream()
                .filter(p -> p.magnitude() > magnitudeThreshold)
                .collect(Collectors.toList());
    }

    /**
     * Equivalent to the current matcher with MAX_MATCH_CENTS = 50.
     * Greedy implementation is intentional here because this is the
     * exact threshold-only baseline we want to compare against.
     */
    private List<Match> threshold50(
            List<SpectralPitch> reference,
            List<SpectralPitch> performance) {

        List<Match> matches = new ArrayList<>();
        boolean[] used = new boolean[performance.size()];

        for (SpectralPitch ref : reference) {

            int bestIndex = -1;
            double bestCents = Double.MAX_VALUE;

            for (int i = 0; i < performance.size(); i++) {

                if (used[i]) {
                    continue;
                }

                double cents = centsDifference(
                        ref.frequencyHz(),
                        performance.get(i).frequencyHz());

                if (cents <= THRESHOLD_CENTS && cents < bestCents) {
                    bestCents = cents;
                    bestIndex = i;
                }
            }

            if (bestIndex >= 0) {
                used[bestIndex] = true;
                matches.add(new Match(
                        ref.frequencyHz(),
                        performance.get(bestIndex).frequencyHz(),
                        bestCents));
            }
        }

        return matches;
    }

    /**
     * Global assignment minimizing:
     *
     *   sum(match cents) + 50 * unmatchedReferenceCount
     *
     * with 50 cents as the maximum useful association radius.
     */
    private List<Match> penalty50(
            List<SpectralPitch> reference,
            List<SpectralPitch> performance) {

        int n = reference.size();
        int m = performance.size();

        if (n == 0 || m == 0) {
            return List.of();
        }

        double[][] dp = new double[n + 1][m + 1];
        Decision[][] decisions = new Decision[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            Arrays.fill(dp[i], Double.POSITIVE_INFINITY);
        }

        dp[0][0] = 0.0;

        for (int i = 1; i <= n; i++) {
            dp[i][0] = dp[i - 1][0] + PENALTY_CENTS;
            decisions[i][0] = Decision.SKIP_REFERENCE;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {

                double skipReference =
                        dp[i - 1][j] + PENALTY_CENTS;

                double skipPerformance =
                        dp[i][j - 1];

                double best = skipReference;
                Decision decision = Decision.SKIP_REFERENCE;

                if (skipPerformance < best) {
                    best = skipPerformance;
                    decision = Decision.SKIP_PERFORMANCE;
                }

                double cents = centsDifference(
                        reference.get(i - 1).frequencyHz(),
                        performance.get(j - 1).frequencyHz());

                if (cents <= THRESHOLD_CENTS) {

                    double match =
                            dp[i - 1][j - 1] + cents;

                    if (match < best) {
                        best = match;
                        decision = Decision.MATCH;
                    }
                }

                dp[i][j] = best;
                decisions[i][j] = decision;
            }
        }

        List<Match> result = new ArrayList<>();

        int i = n;
        int j = m;

        while (i > 0 && j > 0) {

            Decision decision = decisions[i][j];

            if (decision == Decision.MATCH) {

                SpectralPitch ref = reference.get(i - 1);
                SpectralPitch perf = performance.get(j - 1);

                double cents = centsDifference(
                        ref.frequencyHz(),
                        perf.frequencyHz());

                result.add(new Match(
                        ref.frequencyHz(),
                        perf.frequencyHz(),
                        cents));

                i--;
                j--;

            } else if (decision == Decision.SKIP_REFERENCE) {
                i--;

            } else {
                j--;
            }
        }

        Collections.reverse(result);
        return result;
    }

    private boolean sameAssignments(
            List<Match> first,
            List<Match> second) {

        if (first.size() != second.size()) {
            return false;
        }

        for (int i = 0; i < first.size(); i++) {

            Match a = first.get(i);
            Match b = second.get(i);

            if (Math.abs(a.referenceHz - b.referenceHz) > 0.001) {
                return false;
            }

            if (Math.abs(a.performanceHz - b.performanceHz) > 0.001) {
                return false;
            }
        }

        return true;
    }

    private void logMatches(String strategy, List<Match> matches) {

        if (matches.isEmpty()) {
            log.info("{}: no matches", strategy);
            return;
        }

        double avg = matches.stream()
                .mapToDouble(Match::cents)
                .average()
                .orElse(0.0);

        double max = matches.stream()
                .mapToDouble(Match::cents)
                .max()
                .orElse(0.0);

        log.info(
                "{}: matches={} avgAbsCents={} maxAbsCents={}",
                strategy,
                matches.size(),
                String.format(Locale.US, "%.2f", avg),
                String.format(Locale.US, "%.2f", max)
        );
    }

    private double centsDifference(double referenceHz, double performanceHz) {
        return Math.abs(
                1200.0 * Math.log(performanceHz / referenceHz) / Math.log(2.0)
        );
    }

    private enum Decision {
        MATCH,
        SKIP_REFERENCE,
        SKIP_PERFORMANCE
    }

    private record Match(
            double referenceHz,
            double performanceHz,
            double cents) {
    }
}
