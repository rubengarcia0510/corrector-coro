package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SecondOrderTemporalStrategy implements AlignmentStrategy {

    private static final double SEGMENT_SIZE_SEC = 4.0;
    private static final double SEARCH_RANGE_SEC = 4.0;
    private static final double OFFSET_STEP_SEC = 0.05;
    private static final double LAMBDA = 0.01;

    @Override
    public AlignmentResult align(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

        if (reference == null || performance == null) {
            throw new IllegalArgumentException(
                    "Chroma sequences cannot be null"
            );
        }

        if (reference.isEmpty() || performance.isEmpty()) {
            return new AlignmentResult(Collections.emptyList());
        }

        List<List<OffsetCandidate>> candidates =
                generateCandidates(reference, performance);

        List<OffsetCandidate> path =
                optimizeSecondOrder(candidates, LAMBDA);

        List<AlignmentPoint> points =
                path.stream()
                        .map(candidate -> new AlignmentPoint(
                                candidate.segmentStartSec,
                                candidate.segmentStartSec
                                        + candidate.offsetSec,
                                candidate.distance
                        ))
                        .toList();

        return new AlignmentResult(points);
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

            totalDistance += cosineDistance(
                    referenceFrame.chroma(),
                    best.chroma()
            );

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

        return 1.0 -
                dot / (
                        Math.sqrt(normA)
                                * Math.sqrt(normB)
                );
    }

    private List<OffsetCandidate> optimizeSecondOrder(
            List<List<OffsetCandidate>> candidates,
            double lambda) {

        if (candidates.size() <= 2) {
            throw new IllegalArgumentException(
                    "At least 3 segments are required"
            );
        }

        int firstSize = candidates.get(0).size();
        int secondSize = candidates.get(1).size();

        double[][] costs =
                new double[firstSize][secondSize];

        for (int i = 0; i < firstSize; i++) {
            for (int j = 0; j < secondSize; j++) {
                costs[i][j] =
                        candidates.get(0).get(i).distance
                                + candidates.get(1).get(j).distance;
            }
        }

        List<int[][]> previousStates =
                new ArrayList<>();

        for (int segment = 2;
             segment < candidates.size();
             segment++) {

            List<OffsetCandidate> previous =
                    candidates.get(segment - 1);

            List<OffsetCandidate> current =
                    candidates.get(segment);

            double[][] nextCosts =
                    new double[previous.size()][current.size()];

            int[][] nextPrevious =
                    new int[previous.size()][current.size()];

            for (int previousIndex = 0;
                 previousIndex < previous.size();
                 previousIndex++) {

                OffsetCandidate previousCandidate =
                        previous.get(previousIndex);

                for (int currentIndex = 0;
                     currentIndex < current.size();
                     currentIndex++) {

                    OffsetCandidate currentCandidate =
                            current.get(currentIndex);

                    double currentChange =
                            currentCandidate.offsetSec
                                    - previousCandidate.offsetSec;

                    double bestCost =
                            Double.POSITIVE_INFINITY;

                    int bestPreviousIndex = -1;

                    for (int previousPreviousIndex = 0;
                         previousPreviousIndex
                                 < candidates.get(segment - 2).size();
                         previousPreviousIndex++) {

                        double previousCost =
                                costs[
                                        previousPreviousIndex
                                ][
                                        previousIndex
                                ];

                        if (!Double.isFinite(previousCost)) {
                            continue;
                        }

                        OffsetCandidate previousPrevious =
                                candidates
                                        .get(segment - 2)
                                        .get(previousPreviousIndex);

                        double previousChange =
                                previousCandidate.offsetSec
                                        - previousPrevious.offsetSec;

                        double acceleration =
                                currentChange
                                        - previousChange;

                        double transitionPenalty =
                                lambda
                                        * acceleration
                                        * acceleration;

                        double candidateCost =
                                previousCost
                                        + currentCandidate.distance
                                        + transitionPenalty;

                        if (candidateCost < bestCost) {
                            bestCost = candidateCost;
                            bestPreviousIndex =
                                    previousPreviousIndex;
                        }
                    }

                    nextCosts[
                            previousIndex
                    ][
                            currentIndex
                    ] = bestCost;

                    nextPrevious[
                            previousIndex
                    ][
                            currentIndex
                    ] = bestPreviousIndex;
                }
            }

            costs = nextCosts;
            previousStates.add(nextPrevious);
        }

        int lastSegment = candidates.size() - 1;

        double bestCost = Double.POSITIVE_INFINITY;
        int bestPreviousIndex = -1;
        int bestCurrentIndex = -1;

        for (int previousIndex = 0;
             previousIndex < costs.length;
             previousIndex++) {

            for (int currentIndex = 0;
                 currentIndex < costs[previousIndex].length;
                 currentIndex++) {

                if (costs[previousIndex][currentIndex]
                        < bestCost) {

                    bestCost =
                            costs[previousIndex][currentIndex];

                    bestPreviousIndex = previousIndex;
                    bestCurrentIndex = currentIndex;
                }
            }
        }

        List<OffsetCandidate> path =
                new ArrayList<>();

        path.add(
                candidates
                        .get(lastSegment)
                        .get(bestCurrentIndex)
        );

        path.add(
                candidates
                        .get(lastSegment - 1)
                        .get(bestPreviousIndex)
        );

        int currentIndex = bestPreviousIndex;

        for (int historyIndex =
                     previousStates.size() - 1;
             historyIndex >= 0;
             historyIndex--) {

            int[][] state =
                    previousStates.get(historyIndex);

            int previousIndex =
                    state[
                            currentIndex
                    ][
                            bestCurrentIndex
                    ];

            if (previousIndex < 0) {
                break;
            }

            path.add(
                    candidates
                            .get(historyIndex)
                            .get(previousIndex)
            );

            bestCurrentIndex = currentIndex;
            currentIndex = previousIndex;
        }

        Collections.reverse(path);

        return path;
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
