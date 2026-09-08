package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class SegmentOffsetGlobalOptimizationRealAudioTest {

    private static final double GLOBAL_OFFSET_SEC = 0.700;

    private static final double SEGMENT_SIZE_SEC = 4.0;
    private static final double SEARCH_RANGE_SEC = 4.0;
    private static final double OFFSET_STEP_SEC = 0.05;

    private static final double[] LAMBDAS = {
            0.00,
            0.05,
            0.10,
            0.20,
            0.50
    };

    @Test
    void testGlobalSegmentOffsetOptimization() {

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
                "---- GLOBAL SEGMENT OFFSET OPTIMIZATION EXPERIMENT ----"
        );

        List<List<OffsetCandidate>> allCandidates =
                generateCandidates(
                        reference,
                        shiftedPerformance
                );

        for (double lambda : LAMBDAS) {

            System.out.println();
            System.out.printf(
                    Locale.US,
                    "---- lambda=%.2f ----%n",
                    lambda
            );

            List<OffsetCandidate> path =
                    optimizeGlobally(
                            allCandidates,
                            lambda
                    );

            double totalDistance = 0.0;
            double totalSmoothness = 0.0;

            for (int i = 0; i < path.size(); i++) {

                OffsetCandidate candidate =
                        path.get(i);

                double change = 0.0;

                if (i > 0) {
                    change =
                            candidate.offsetSec
                                    - path.get(i - 1).offsetSec;

                    totalSmoothness +=
                            Math.abs(change);
                }

                totalDistance += candidate.distance;

                System.out.printf(
                        Locale.US,
                        "segment=%4.0f-%4.0fs "
                                + "offset=%+.3fs "
                                + "change=%+.3fs "
                                + "distance=%.4f%n",
                        candidate.segmentStartSec,
                        candidate.segmentEndSec,
                        candidate.offsetSec,
                        change,
                        candidate.distance
                );
            }

            double averageDistance =
                    totalDistance / path.size();

            System.out.printf(
                    Locale.US,
                    "SUMMARY lambda=%.2f "
                            + "avgDistance=%.4f "
                            + "totalSmoothness=%.3fs%n",
                    lambda,
                    averageDistance,
                    totalSmoothness
            );
        }
    }

    private List<OffsetCandidate> optimizeGlobally(
            List<List<OffsetCandidate>> candidates,
            double lambda) {

        int segmentCount =
                candidates.size();

        List<double[]> costs =
                new ArrayList<>();

        List<int[]> previous =
                new ArrayList<>();

        List<OffsetCandidate> first =
                candidates.get(0);

        double[] firstCosts =
                new double[first.size()];

        int[] firstPrevious =
                new int[first.size()];

        for (int j = 0; j < first.size(); j++) {
            firstCosts[j] =
                    first.get(j).distance;
            firstPrevious[j] = -1;
        }

        costs.add(firstCosts);
        previous.add(firstPrevious);

        for (int segment = 1;
             segment < segmentCount;
             segment++) {

            List<OffsetCandidate> current =
                    candidates.get(segment);

            List<OffsetCandidate> prior =
                    candidates.get(segment - 1);

            double[] currentCosts =
                    new double[current.size()];

            int[] currentPrevious =
                    new int[current.size()];

            for (int currentIndex = 0;
                 currentIndex < current.size();
                 currentIndex++) {

                OffsetCandidate currentCandidate =
                        current.get(currentIndex);

                double bestCost =
                        Double.POSITIVE_INFINITY;

                int bestPrevious = -1;

                for (int previousIndex = 0;
                     previousIndex < prior.size();
                     previousIndex++) {

                    OffsetCandidate previousCandidate =
                            prior.get(previousIndex);

                    double offsetChange =
                            Math.abs(
                                    currentCandidate.offsetSec
                                            - previousCandidate.offsetSec
                            );

                    double transitionCost =
                            lambda * offsetChange * offsetChange;

                    double candidateCost =
                            costs.get(segment - 1)[previousIndex]
                                    + currentCandidate.distance
                                    + transitionCost;

                    if (candidateCost < bestCost) {
                        bestCost = candidateCost;
                        bestPrevious = previousIndex;
                    }
                }

                currentCosts[currentIndex] = bestCost;
                currentPrevious[currentIndex] = bestPrevious;
            }

            costs.add(currentCosts);
            previous.add(currentPrevious);
        }

        int lastSegment =
                segmentCount - 1;

        double[] lastCosts =
                costs.get(lastSegment);

        int bestLastIndex = 0;

        for (int i = 1; i < lastCosts.length; i++) {
            if (lastCosts[i] < lastCosts[bestLastIndex]) {
                bestLastIndex = i;
            }
        }

        List<OffsetCandidate> path =
                new ArrayList<>();

        int currentIndex = bestLastIndex;

        for (int segment = lastSegment;
             segment >= 0;
             segment--) {

            path.add(
                    candidates
                            .get(segment)
                            .get(currentIndex)
            );

            currentIndex =
                    previous
                            .get(segment)[currentIndex];
        }

        java.util.Collections.reverse(path);

        return path;
    }

    private List<List<OffsetCandidate>> generateCandidates(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

        List<List<OffsetCandidate>> result =
                new ArrayList<>();

        double duration =
                reference.get(reference.size() - 1)
                        .timestampSec();

        for (double segmentStart = 0.0;
             segmentStart < duration;
             segmentStart += SEGMENT_SIZE_SEC) {

            double segmentEnd =
                    Math.min(
                            segmentStart + SEGMENT_SIZE_SEC,
                            duration
                    );

            List<OffsetCandidate> candidates =
                    new ArrayList<>();

            for (double offset = -SEARCH_RANGE_SEC;
                 offset <= SEARCH_RANGE_SEC + 0.0001;
                 offset += OFFSET_STEP_SEC) {

                double distance =
                        calculateSegmentDistance(
                                reference,
                                performance,
                                segmentStart,
                                segmentEnd,
                                offset
                        );

                if (Double.isFinite(distance)) {
                    candidates.add(
                            new OffsetCandidate(
                                    segmentStart,
                                    segmentEnd,
                                    offset,
                                    distance
                            )
                    );
                }
            }

            result.add(candidates);
        }

        return result;
    }

    private double calculateSegmentDistance(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double segmentStart,
            double segmentEnd,
            double offset) {

        double totalDistance = 0.0;
        int matches = 0;

        for (ChromaFrame referenceFrame : reference) {

            double referenceTime =
                    referenceFrame.timestampSec();

            if (referenceTime < segmentStart
                    || referenceTime >= segmentEnd) {
                continue;
            }

            double targetPerformanceTime =
                    referenceTime + offset;

            ChromaFrame best =
                    findNearestFrame(
                            performance,
                            targetPerformanceTime
                    );

            if (best == null) {
                continue;
            }

            double distance =
                    cosineDistance(
                            referenceFrame.chroma(),
                            best.chroma()
                    );

            totalDistance += distance;
            matches++;
        }

        if (matches == 0) {
            return Double.POSITIVE_INFINITY;
        }

        return totalDistance / matches;
    }

    private ChromaFrame findNearestFrame(
            List<ChromaFrame> frames,
            double targetTime) {

        ChromaFrame best = null;
        double bestDelta = Double.POSITIVE_INFINITY;

        for (ChromaFrame frame : frames) {

            double delta =
                    Math.abs(
                            frame.timestampSec()
                                    - targetTime
                    );

            if (delta < bestDelta) {
                bestDelta = delta;
                best = frame;
            }
        }

        return best;
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

        double similarity =
                dot / (Math.sqrt(normA) * Math.sqrt(normB));

        return 1.0 - similarity;
    }

    private static class OffsetCandidate {

        final double segmentStartSec;
        final double segmentEndSec;
        final double offsetSec;
        final double distance;

        OffsetCandidate(
                double segmentStartSec,
                double segmentEndSec,
                double offsetSec,
                double distance) {

            this.segmentStartSec = segmentStartSec;
            this.segmentEndSec = segmentEndSec;
            this.offsetSec = offsetSec;
            this.distance = distance;
        }
    }
}
