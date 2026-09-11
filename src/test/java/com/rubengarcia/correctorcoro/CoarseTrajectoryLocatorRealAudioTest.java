package com.rubengarcia.correctorcoro;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class CoarseTrajectoryLocatorRealAudioTest {

    private static final double SEGMENT_SIZE_SEC = 8.0;
    private static final double SEGMENT_HOP_SEC = 4.0;
    private static final double PERFORMANCE_HOP_SEC = 0.5;

    private static final double MIN_SCALE = 0.85;
    private static final double MAX_SCALE = 0.95;
    private static final double SCALE_STEP = 0.025;

    private static final int TOP_K = 100;

    @Test
    void shouldFindCoherentTemporalTrajectory() {
        File referenceAudio = new File("../regina-ref.wav");
        File performanceAudio = new File("../regina-flores.wav");

        ChromaExtractor extractor = new TarsosChromaExtractor();

        List<ChromaFrame> reference = extractor.extract(referenceAudio);
        List<ChromaFrame> performance = extractor.extract(performanceAudio);

        CoarseSequenceLocator locator = new CoarseSequenceLocator();

        List<PathState> states = null;

        log.info("---- COARSE TEMPORAL TRAJECTORY TOP-K ----");

        for (double segmentStart = 2.0;
             segmentStart < 98.0;
             segmentStart += SEGMENT_HOP_SEC) {

            double currentSegmentStart = segmentStart;
            double segmentEnd = currentSegmentStart + SEGMENT_SIZE_SEC;

            List<ChromaFrame> segment = reference.stream()
                    .filter(frame ->
                            frame.timestampSec() >= currentSegmentStart &&
                            frame.timestampSec() < segmentEnd)
                    .toList();

            if (segment.size() < 4) {
                continue;
            }

            List<Candidate> candidates =
                    findCandidates(locator, segment, performance);

            List<PathState> nextStates = new ArrayList<>();

            if (states == null) {
                for (Candidate candidate : candidates) {
                    nextStates.add(new PathState(
                            candidate,
                            candidate.similarity(),
                            null
                    ));
                }
            } else {
                for (Candidate candidate : candidates) {
                    PathState bestPrevious = null;
                    double bestScore = Double.NEGATIVE_INFINITY;

                    for (PathState previousState : states) {
                        double deltaReference =
                                currentSegmentStart
                                        - previousState.candidate().referenceStartSec();

                        double deltaPerformance =
                                candidate.performanceStartSec()
                                        - previousState.candidate().performanceStartSec();

                        if (deltaPerformance <= 0.0) {
                            continue;
                        }

                        double minDelta = deltaReference * MIN_SCALE;
                        double maxDelta = deltaReference * MAX_SCALE;

                        if (deltaPerformance < minDelta ||
                                deltaPerformance > maxDelta) {
                            continue;
                        }

                        double localScale =
                                deltaPerformance / deltaReference;

                        double smoothPenalty =
                                Math.abs(
                                        localScale
                                                - previousState.candidate().tempoScale()
                                );

                        double continuityReward =
                                1.0 - smoothPenalty;

                        double score =
                                previousState.score()
                                        + candidate.similarity()
                                        + continuityReward;

                        if (score > bestScore) {
                            bestScore = score;
                            bestPrevious = previousState;
                        }
                    }

                    if (bestPrevious != null) {
                        nextStates.add(new PathState(
                                candidate,
                                bestScore,
                                bestPrevious
                        ));
                    }
                }
            }

            nextStates.sort(
                    Comparator.comparingDouble(PathState::score).reversed()
            );

            if (nextStates.isEmpty()) {
                log.info(
                        "ref={}..{} -> no coherent continuation",
                        currentSegmentStart,
                        segmentEnd
                );
                break;
            }

            states = new ArrayList<>(
                    nextStates.subList(
                            0,
                            Math.min(TOP_K, nextStates.size())
                    )
            );

            PathState best = states.get(0);

            log.info(
                    "ref={}..{} -> bestPerf={} scale={} similarity={} pathScore={} alternatives={}",
                    currentSegmentStart,
                    segmentEnd,
                    best.candidate().performanceStartSec(),
                    best.candidate().tempoScale(),
                    best.candidate().similarity(),
                    best.score(),
                    states.size()
            );
        }

        assertFalse(states == null || states.isEmpty());

        PathState finalState = states.get(0);
        List<PathState> path = reconstructPath(finalState);

        double firstPerformance =
                path.get(0).candidate().performanceStartSec();

        double lastPerformance =
                path.get(path.size() - 1).candidate().performanceStartSec();

        log.info(
                "---- BEST TRAJECTORY: {} points, perf={}..{}s ----",
                path.size(),
                firstPerformance,
                lastPerformance
        );

        double totalTemporalModelError = 0.0;
        double maxTemporalModelError = 0.0;

        for (PathState state : path) {
            double referenceTime =
                    state.candidate().referenceStartSec();

            double performanceTime =
                    state.candidate().performanceStartSec();

            double expectedPerformance =
                    0.8961 * referenceTime
                            + 1.4784;

            double temporalModelError =
                    Math.abs(
                            performanceTime
                                    - expectedPerformance
                    );

            totalTemporalModelError += temporalModelError;
            maxTemporalModelError =
                    Math.max(
                            maxTemporalModelError,
                            temporalModelError
                    );

            log.info(
                    "trajectory ref={} -> perf={} expected={} modelError={} scale={} similarity={}",
                    referenceTime,
                    performanceTime,
                    expectedPerformance,
                    temporalModelError,
                    state.candidate().tempoScale(),
                    state.candidate().similarity()
            );
        }

        log.info(
                "---- TEMPORAL MODEL ERROR: avg={}s max={}s ----",
                totalTemporalModelError / path.size(),
                maxTemporalModelError
        );

        double sumReference = 0.0;
        double sumPerformance = 0.0;
        double sumReferenceSquared = 0.0;
        double sumReferencePerformance = 0.0;
        int n = path.size();

        for (PathState state : path) {
            double x = state.candidate().referenceStartSec();
            double y = state.candidate().performanceStartSec();

            sumReference += x;
            sumPerformance += y;
            sumReferenceSquared += x * x;
            sumReferencePerformance += x * y;
        }

        double trajectorySlope =
                (n * sumReferencePerformance
                        - sumReference * sumPerformance)
                        / (n * sumReferenceSquared
                        - sumReference * sumReference);

        double trajectoryIntercept =
                (sumPerformance
                        - trajectorySlope * sumReference)
                        / n;

        log.info(
                "---- TRAJECTORY REGRESSION: performance={} * reference + {} ----",
                trajectorySlope,
                trajectoryIntercept
        );

        assertTrue(
                path.size() >= 15,
                "Trajectory should contain most reference segments"
        );
    }

    private List<Candidate> findCandidates(
            CoarseSequenceLocator locator,
            List<ChromaFrame> referenceSegment,
            List<ChromaFrame> performance) {

        List<Candidate> candidates = new ArrayList<>();

        double referenceStart =
                referenceSegment.get(0).timestampSec();

        for (double scale = MIN_SCALE;
             scale <= MAX_SCALE + 1e-9;
             scale += SCALE_STEP) {

            for (double performanceStart = 0.0;
                 performanceStart < 970.0;
                 performanceStart += PERFORMANCE_HOP_SEC) {

                double expectedPerformance =
                        0.8961 * referenceStart
                                + 1.4784;

                if (Math.abs(
                        performanceStart - expectedPerformance
                ) > 1.0) {
                    continue;
                }

                double similarity = locator.scoreWindow(
                        referenceSegment,
                        performance,
                        performanceStart,
                        scale
                );

                if (similarity <= 0.0) {
                    continue;
                }

                candidates.add(new Candidate(
                        referenceStart,
                        performanceStart,
                        scale,
                        similarity
                ));
            }
        }

        candidates.sort(
                Comparator.comparingDouble(Candidate::similarity).reversed()
        );

        return candidates.subList(
                0,
                Math.min(TOP_K, candidates.size())
        );
    }

    private List<PathState> reconstructPath(PathState state) {
        List<PathState> path = new ArrayList<>();

        while (state != null) {
            path.add(state);
            state = state.previous();
        }

        java.util.Collections.reverse(path);
        return path;
    }

    private record Candidate(
            double referenceStartSec,
            double performanceStartSec,
            double tempoScale,
            double similarity
    ) {
    }

    private record PathState(
            Candidate candidate,
            double score,
            PathState previous
    ) {
    }
}
