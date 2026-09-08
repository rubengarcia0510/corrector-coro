package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

        SearchResult best = search(
                referencePeaks,
                performancePeaks,
                0,
                new boolean[performancePeaks.size()],
                new ArrayList<>(),
                0.0
        );

        return best.matches;
    }

    private SearchResult search(
            List<SpectralPitch> references,
            List<SpectralPitch> performances,
            int referenceIndex,
            boolean[] used,
            List<PolyphonicPitchMatch> currentMatches,
            double currentCost) {

        if (referenceIndex >= references.size()) {
            return new SearchResult(
                    new ArrayList<>(currentMatches),
                    currentCost
            );
        }

        SpectralPitch reference =
                references.get(referenceIndex);

        SearchResult best = null;

        /*
         * Opción 1: no asignar ninguna performance a esta referencia.
         */
        SearchResult skipped = search(
                references,
                performances,
                referenceIndex + 1,
                used,
                currentMatches,
                currentCost
        );

        best = skipped;

        /*
         * Opción 2: probar cada performance disponible
         * dentro del límite musical permitido.
         */
        for (int i = 0; i < performances.size(); i++) {

            if (used[i]) {
                continue;
            }

            SpectralPitch performance =
                    performances.get(i);

            double cents =
                    calculateCents(
                            reference.frequencyHz(),
                            performance.frequencyHz()
                    );

            double absCents = Math.abs(cents);

            if (absCents > MAX_MATCH_CENTS) {
                continue;
            }

            used[i] = true;

            currentMatches.add(
                    new PolyphonicPitchMatch(
                            reference.frequencyHz(),
                            performance.frequencyHz(),
                            cents
                    )
            );

            SearchResult candidate = search(
                    references,
                    performances,
                    referenceIndex + 1,
                    used,
                    currentMatches,
                    currentCost + absCents
            );

            currentMatches.remove(currentMatches.size() - 1);
            used[i] = false;

            if (isBetter(candidate, best)) {
                best = candidate;
            }
        }

        return best;
    }

    private boolean isBetter(
            SearchResult candidate,
            SearchResult currentBest) {

        if (candidate == null) {
            return false;
        }

        if (currentBest == null) {
            return true;
        }

        /*
         * Primero maximizamos la cantidad de matches.
         * En caso de empate, minimizamos la distancia
         * total en cents.
         */
        if (candidate.matches.size() !=
                currentBest.matches.size()) {

            return candidate.matches.size() >
                    currentBest.matches.size();
        }

        return candidate.cost < currentBest.cost;
    }

    private double calculateCents(
            double referenceFrequency,
            double performanceFrequency) {

        return 1200.0 *
                Math.log(
                        performanceFrequency /
                                referenceFrequency
                ) /
                Math.log(2.0);
    }

    private static class SearchResult {

        private final List<PolyphonicPitchMatch> matches;
        private final double cost;

        private SearchResult(
                List<PolyphonicPitchMatch> matches,
                double cost) {

            this.matches = matches;
            this.cost = cost;
        }
    }
}
