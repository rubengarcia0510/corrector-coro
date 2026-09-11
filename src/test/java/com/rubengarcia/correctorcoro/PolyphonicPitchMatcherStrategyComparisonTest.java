package com.rubengarcia.correctorcoro;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
class PolyphonicPitchMatcherStrategyComparisonTest {

    private static final int[] FRAME_INDEXES = {322, 644, 966, 1288};
    private static final double[] MAGNITUDE_THRESHOLDS = {1.0, 5.0, 10.0, 20.0};

    @Test
    void compareMatchingStrategies() {
        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        SpectralPitchExtractor extractor = new SpectralPitchExtractor();

        var referenceFrames = extractor.extract(referenceFile);
        var performanceFrames = extractor.extract(performanceFile);

        log.info("========== MATCH STRATEGY COMPARISON ==========");

        for (double magnitudeThreshold : MAGNITUDE_THRESHOLDS) {
            log.info("==== magnitude > {} ====", magnitudeThreshold);

            for (int frameIndex : FRAME_INDEXES) {
                var referencePeaks = referenceFrames.get(frameIndex).peaks().stream()
                        .filter(p -> p.magnitude() > magnitudeThreshold)
                        .toList();

                var performancePeaks = performanceFrames.get(frameIndex).peaks().stream()
                        .filter(p -> p.magnitude() > magnitudeThreshold)
                        .toList();

                log.info(
                        "frame={} ref={} perf={}",
                        frameIndex,
                        referencePeaks.size(),
                        performancePeaks.size()
                );

                compare("CURRENT", referencePeaks, performancePeaks, 100.0);
                compare("THRESHOLD_75", referencePeaks, performancePeaks, 75.0);
                compare("THRESHOLD_50", referencePeaks, performancePeaks, 50.0);
                logResult("GREEDY", greedy(referencePeaks, performancePeaks, 100.0));
            }
        }
    }

    private void compare(
            String name,
            List<SpectralPitch> reference,
            List<SpectralPitch> performance,
            double maxCents
    ) {
        List<PolyphonicPitchMatch> matches =
                new PolyphonicPitchMatcher(maxCents).match(reference, performance);

        logResult(name, matches);
    }

    private void compare(
            String name,
            List<SpectralPitch> reference,
            List<SpectralPitch> performance,
            List<PolyphonicPitchMatch> matches
    ) {
        logResult(name, matches);
    }

    private void logResult(String name, List<PolyphonicPitchMatch> matches) {
        double totalCents = matches.stream()
                .mapToDouble(m -> Math.abs(m.deviationCents()))
                .sum();

        double avgCents = matches.isEmpty()
                ? 0.0
                : totalCents / matches.size();

        double maxCents = matches.stream()
                .mapToDouble(m -> Math.abs(m.deviationCents()))
                .max()
                .orElse(0.0);

        long over35 = matches.stream()
                .filter(m -> Math.abs(m.deviationCents()) > 35.0)
                .count();

        long over50 = matches.stream()
                .filter(m -> Math.abs(m.deviationCents()) > 50.0)
                .count();

        long over75 = matches.stream()
                .filter(m -> Math.abs(m.deviationCents()) > 75.0)
                .count();

        log.info(
                "  {} matches={} avgCents={} maxCents={} >35={} >50={} >75={}",
                name,
                matches.size(),
                String.format("%.2f", avgCents),
                String.format("%.2f", maxCents),
                over35,
                over50,
                over75
        );

        matches.stream()
                .sorted(Comparator.comparingDouble(m -> Math.abs(m.deviationCents())))
                .forEach(m -> log.info(
                        "      {} Hz -> {} Hz = {} cents",
                        String.format("%.2f", m.referenceFrequencyHz()),
                        String.format("%.2f", m.performanceFrequencyHz()),
                        String.format("%.2f", m.deviationCents())
                ));
    }

    private List<PolyphonicPitchMatch> greedy(
            List<SpectralPitch> reference,
            List<SpectralPitch> performance,
            double maxCents
    ) {
        List<Candidate> candidates = new ArrayList<>();

        for (int r = 0; r < reference.size(); r++) {
            for (int p = 0; p < performance.size(); p++) {
                double cents = centsDifference(
                        reference.get(r).frequencyHz(),
                        performance.get(p).frequencyHz()
                );

                if (Math.abs(cents) <= maxCents) {
                    candidates.add(new Candidate(r, p, cents));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(c -> Math.abs(c.cents)));

        Set<Integer> usedReference = new HashSet<>();
        Set<Integer> usedPerformance = new HashSet<>();

        List<PolyphonicPitchMatch> result = new ArrayList<>();

        for (Candidate candidate : candidates) {
            if (usedReference.contains(candidate.referenceIndex)
                    || usedPerformance.contains(candidate.performanceIndex)) {
                continue;
            }

            usedReference.add(candidate.referenceIndex);
            usedPerformance.add(candidate.performanceIndex);

            SpectralPitch ref = reference.get(candidate.referenceIndex);
            SpectralPitch perf = performance.get(candidate.performanceIndex);

            result.add(new PolyphonicPitchMatch(
                    ref.frequencyHz(),
                    perf.frequencyHz(),
                    candidate.cents
            ));
        }

        return result;
    }

    private double centsDifference(double referenceHz, double performanceHz) {
        return 1200.0 * Math.log(performanceHz / referenceHz) / Math.log(2.0);
    }

    private record Candidate(
            int referenceIndex,
            int performanceIndex,
            double cents
    ) {
    }
}
