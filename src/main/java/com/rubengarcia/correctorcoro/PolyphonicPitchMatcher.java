package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class PolyphonicPitchMatcher {

    /*
     * Máxima diferencia musical permitida para considerar
     * que dos componentes representan la misma nota.
     */
    private static final double MAX_MATCH_CENTS = 100.0;

    public List<PolyphonicPitchMatch> match(
            List<SpectralPitch> referencePeaks,
            List<SpectralPitch> performancePeaks) {

        if (referencePeaks == null ||
                performancePeaks == null ||
                referencePeaks.isEmpty() ||
                performancePeaks.isEmpty()) {

            return List.of();
        }

        List<PolyphonicPitchMatch> matches =
                new ArrayList<>();

        Set<Integer> usedPerformancePeaks =
                new HashSet<>();

        /*
         * Procesamos los picos de referencia de mayor magnitud
         * primero. Esto ayuda a priorizar componentes fuertes.
         */
        List<SpectralPitch> references =
                referencePeaks.stream()
                        .sorted(
                                Comparator.comparingDouble(
                                        SpectralPitch::magnitude
                                ).reversed()
                        )
                        .toList();

        for (SpectralPitch reference : references) {

            SpectralPitch best =
                    null;

            double bestAbsCents =
                    Double.MAX_VALUE;

            int bestIndex =
                    -1;

            for (int i = 0;
                 i < performancePeaks.size();
                 i++) {

                if (usedPerformancePeaks.contains(i)) {
                    continue;
                }

                SpectralPitch performance =
                        performancePeaks.get(i);

                double cents =
                        calculateCents(
                                reference.frequencyHz(),
                                performance.frequencyHz()
                        );

                double absCents =
                        Math.abs(cents);

                if (absCents <= MAX_MATCH_CENTS &&
                        absCents < bestAbsCents) {

                    best = performance;
                    bestAbsCents = absCents;
                    bestIndex = i;
                }
            }

            if (best != null) {

                usedPerformancePeaks.add(bestIndex);

                matches.add(
                        new PolyphonicPitchMatch(
                                reference.frequencyHz(),
                                best.frequencyHz(),
                                calculateCents(
                                        reference.frequencyHz(),
                                        best.frequencyHz()
                                )
                        )
                );
            }
        }

        return matches;
    }

    private double calculateCents(
            double referenceFrequency,
            double performanceFrequency) {

        return 1200.0 *
                Math.log(
                        performanceFrequency /
                                referenceFrequency
                )
                / Math.log(2.0);
    }
}
