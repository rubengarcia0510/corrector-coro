package com.rubengarcia.correctorcoro;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Slf4j
class PolyphonicPitchMatcherPenaltyComparisonTest {

    private static final int[] FRAME_INDEXES = {
            322, 644, 966, 1288
    };

    private static final double[] MAGNITUDE_THRESHOLDS = {
            1.0, 5.0, 10.0, 20.0
    };

    private static final double[] UNMATCHED_PENALTIES = {
            35.0, 50.0, 75.0, 100.0
    };

    @Test
    void compareGlobalUnmatchedPenaltyStrategies() {

        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        SpectralPitchExtractor extractor =
                new SpectralPitchExtractor();

        List<SpectralFrame> referenceFrames =
                extractor.extract(referenceFile);

        List<SpectralFrame> performanceFrames =
                extractor.extract(performanceFile);

        assertFalse(referenceFrames.isEmpty());
        assertFalse(performanceFrames.isEmpty());

        log.info("");
        log.info("========== GLOBAL UNMATCHED PENALTY COMPARISON ==========");

        for (double magnitudeThreshold : MAGNITUDE_THRESHOLDS) {

            log.info("");
            log.info(
                    "========== MAGNITUDE > {} ==========",
                    magnitudeThreshold
            );

            for (int frameIndex : FRAME_INDEXES) {

                if (frameIndex >= referenceFrames.size()
                        || frameIndex >= performanceFrames.size()) {
                    continue;
                }

                List<SpectralPitch> referencePeaks =
                        filterByMagnitude(
                                referenceFrames.get(frameIndex).peaks(),
                                magnitudeThreshold
                        );

                List<SpectralPitch> performancePeaks =
                        filterByMagnitude(
                                performanceFrames.get(frameIndex).peaks(),
                                magnitudeThreshold
                        );

                log.info("");
                log.info(
                        "FRAME {} ref={} perf={}",
                        frameIndex,
                        referencePeaks.size(),
                        performancePeaks.size()
                );

                for (double penalty : UNMATCHED_PENALTIES) {

                    List<PolyphonicPitchMatch> matches =
                            optimize(
                                    referencePeaks,
                                    performancePeaks,
                                    penalty
                            );

                    logResult(
                            penalty,
                            matches,
                            referencePeaks.size()
                    );
                }
            }
        }

        log.info("");
        log.info(
                "========================================================"
        );
    }

    private List<SpectralPitch> filterByMagnitude(
            List<SpectralPitch> peaks,
            double threshold) {

        return peaks.stream()
                .filter(p -> p.magnitude() > threshold)
                .toList();
    }

    /**
     * Minimiza:
     *
     *   sum(abs(cents)) para matches
     *   +
     *   penalty por cada reference peak sin match.
     *
     * Por lo tanto, un match de más de "penalty" cents
     * nunca es preferible a dejar ese peak sin match.
     *
     * La búsqueda sigue siendo GLOBAL: no es greedy.
     */
    private List<PolyphonicPitchMatch> optimize(
            List<SpectralPitch> reference,
            List<SpectralPitch> performance,
            double penalty) {

        SearchResult result =
                search(
                        reference,
                        performance,
                        0,
                        new boolean[performance.size()],
                        penalty
                );

        return result.matches();
    }

    private SearchResult search(
            List<SpectralPitch> reference,
            List<SpectralPitch> performance,
            int referenceIndex,
            boolean[] usedPerformance,
            double penalty) {

        if (referenceIndex >= reference.size()) {
            return new SearchResult(
                    List.of(),
                    0.0
            );
        }

        SpectralPitch referencePitch =
                reference.get(referenceIndex);

        /*
         * Opción 1:
         * dejar el reference peak sin match.
         */
        SearchResult best =
                search(
                        reference,
                        performance,
                        referenceIndex + 1,
                        usedPerformance,
                        penalty
                );

        best = new SearchResult(
                best.matches(),
                best.cost() + penalty
        );

        /*
         * Opción 2:
         * intentar match con cada performance peak disponible.
         */
        for (int performanceIndex = 0;
             performanceIndex < performance.size();
             performanceIndex++) {

            if (usedPerformance[performanceIndex]) {
                continue;
            }

            SpectralPitch performancePitch =
                    performance.get(performanceIndex);

            double cents =
                    Math.abs(
                            centsDifference(
                                    referencePitch.frequencyHz(),
                                    performancePitch.frequencyHz()
                            )
                    );

            /*
             * Si cuesta más que dejarlo sin match,
             * esta rama nunca puede mejorar.
             */
            if (cents > penalty) {
                continue;
            }

            usedPerformance[performanceIndex] = true;

            SearchResult remaining =
                    search(
                            reference,
                            performance,
                            referenceIndex + 1,
                            usedPerformance,
                            penalty
                    );

            usedPerformance[performanceIndex] = false;

            List<PolyphonicPitchMatch> candidateMatches =
                    new ArrayList<>();

            candidateMatches.add(
                    new PolyphonicPitchMatch(
                            referencePitch.frequencyHz(),
                            performancePitch.frequencyHz(),
                            cents
                    )
            );

            candidateMatches.addAll(
                    remaining.matches()
            );

            /*
             * remaining.cost() ya contiene el coste
             * de los siguientes reference peaks.
             *
             * Como este reference peak sí tiene match,
             * no agregamos el unmatched penalty.
             */
            double candidateCost =
                    remaining.cost() + cents;

            SearchResult candidate =
                    new SearchResult(
                            candidateMatches,
                            candidateCost
                    );

            if (candidate.cost() < best.cost()) {
                best = candidate;
            }
        }

        return best;
    }

    private double centsDifference(
            double referenceHz,
            double performanceHz) {

        return 1200.0
                * Math.log(performanceHz / referenceHz)
                / Math.log(2.0);
    }

    private void logResult(
            double penalty,
            List<PolyphonicPitchMatch> matches,
            int referenceCount) {

        double avgAbsCents =
                matches.stream()
                        .mapToDouble(
                                m -> Math.abs(m.deviationCents())
                        )
                        .average()
                        .orElse(0.0);

        double maxAbsCents =
                matches.stream()
                        .mapToDouble(
                                m -> Math.abs(m.deviationCents())
                        )
                        .max()
                        .orElse(0.0);

        long over35 =
                matches.stream()
                        .filter(
                                m -> Math.abs(m.deviationCents()) > 35.0
                        )
                        .count();

        long over50 =
                matches.stream()
                        .filter(
                                m -> Math.abs(m.deviationCents()) > 50.0
                        )
                        .count();

        long over75 =
                matches.stream()
                        .filter(
                                m -> Math.abs(m.deviationCents()) > 75.0
                        )
                        .count();

        int unmatched =
                referenceCount - matches.size();

        log.info(
                "PENALTY {}: matches={} unmatched={} avgAbs={}¢ maxAbs={}¢ >35={} >50={} >75={}",
                String.format(Locale.US, "%.0f", penalty),
                matches.size(),
                unmatched,
                String.format(Locale.US, "%.2f", avgAbsCents),
                String.format(Locale.US, "%.2f", maxAbsCents),
                over35,
                over50,
                over75
        );
    }

    private record SearchResult(
            List<PolyphonicPitchMatch> matches,
            double cost) {
    }
}
