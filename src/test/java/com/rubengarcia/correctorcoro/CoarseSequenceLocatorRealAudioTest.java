package com.rubengarcia.correctorcoro;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class CoarseSequenceLocatorRealAudioTest {


    @Test
    void topKCandidatesWithTemporalTrajectory() {
        File referenceAudio = new File("../regina-ref.wav");
        File performanceAudio = new File("../regina-flores.wav");

        ChromaExtractor extractor = new TarsosChromaExtractor();
        List<ChromaFrame> reference = extractor.extract(referenceAudio);
        List<ChromaFrame> performance = extractor.extract(performanceAudio);

        ChromaDtwAligner aligner = new ChromaDtwAligner(0.5);

        final double SEGMENT_SEC = 4.0;
        final double REF_START_SEC = 2.0;
        final double REF_END_SEC = 102.0;

        final double PERF_START_SEC = 0.0;
        final double PERF_END_SEC = 120.0;
        final double PERF_STEP_SEC = 1.0;

        final double[] SCALES = {0.85, 0.90, 0.95};

        final int TOP_K = 10;
        final int FRAME_STRIDE = 4;

        final double EXPECTED_SCALE = 0.8961;
        final double EXPECTED_INTERCEPT = 1.4784;
        final double EXPECTED_DELTA = SEGMENT_SEC * EXPECTED_SCALE;

        final double TEMPORAL_LAMBDA = 0.10;

        record Candidate(
                double refStart,
                double perfStart,
                double scale,
                double similarity
        ) {}

        List<List<Candidate>> layers = new java.util.ArrayList<>();

        for (double refStart = REF_START_SEC;
             refStart < REF_END_SEC;
             refStart += SEGMENT_SEC) {

            final double currentRefStart = refStart;

            List<ChromaFrame> referenceSegment =
                    reference.stream()
                            .filter(f ->
                                    f.timestampSec() >= currentRefStart
                                            && f.timestampSec()
                                            < currentRefStart + SEGMENT_SEC)
                            .filter(f ->
                                    Math.round(
                                            f.timestampSec() * 1000
                                    ) % 93 != 0)
                            .toList();

            if (referenceSegment.isEmpty()) {
                log.warn("Empty reference segment at {}", refStart);
                continue;
            }

            List<Candidate> candidates = new java.util.ArrayList<>();

            for (double perfStart = PERF_START_SEC;
                 perfStart <= PERF_END_SEC;
                 perfStart += PERF_STEP_SEC) {

                for (double scale : SCALES) {
                    final double currentPerfStart = perfStart;
                    double targetDuration = SEGMENT_SEC * scale;

                    List<ChromaFrame> performanceSegment =
                            performance.stream()
                                    .filter(f ->
                                            f.timestampSec() >= currentPerfStart
                                                    && f.timestampSec()
                                                    < currentPerfStart + targetDuration)
                                    .toList();

                    if (performanceSegment.isEmpty()) {
                        continue;
                    }

                    List<ChromaFrame> reducedReference =
                            reduceFrames(referenceSegment, FRAME_STRIDE);

                    List<ChromaFrame> reducedPerformance =
                            reduceFrames(performanceSegment, FRAME_STRIDE);

                    List<ChromaDtwAligner.ChromaAlignment> alignments =
                            aligner.align(
                                    reducedReference,
                                    reducedPerformance
                            );

                    if (alignments.isEmpty()) {
                        continue;
                    }

                    double totalDistance = 0.0;

                    for (ChromaDtwAligner.ChromaAlignment alignment
                            : alignments) {
                        totalDistance += alignment.distance();
                    }

                    double similarity =
                            1.0
                                    - totalDistance / alignments.size();

                    candidates.add(
                            new Candidate(
                                    refStart,
                                    perfStart,
                                    scale,
                                    similarity
                            )
                    );
                }
            }

            candidates.sort(
                    java.util.Comparator.comparingDouble(
                            Candidate::similarity
                    ).reversed()
            );

            if (candidates.size() > TOP_K) {
                candidates = new java.util.ArrayList<>(
                        candidates.subList(0, TOP_K)
                );
            }

            layers.add(candidates);

            Candidate best =
                    candidates.isEmpty()
                            ? null
                            : candidates.get(0);

            log.info(
                    "LOCAL ref={}..{} candidates={} bestPerf={} bestScale={} bestSimilarity={}",
                    refStart,
                    refStart + SEGMENT_SEC,
                    candidates.size(),
                    best == null ? -1 : best.perfStart(),
                    best == null ? -1 : best.scale(),
                    best == null ? -1 : best.similarity()
            );
        }

        if (layers.isEmpty()) {
            log.warn("No candidate layers generated");
            return;
        }

        /*
         * Dynamic programming over the TOP-K candidates of each
         * reference segment.
         *
         * The temporal model is only a soft prior. It rewards a
         * coherent forward trajectory and does not require the
         * known regression to be selected exactly.
         */
        List<List<Double>> dp = new java.util.ArrayList<>();
        List<List<Integer>> previous = new java.util.ArrayList<>();

        List<Candidate> firstLayer = layers.get(0);

        List<Double> firstScores = new java.util.ArrayList<>();
        List<Integer> firstPrevious = new java.util.ArrayList<>();

        for (Candidate candidate : firstLayer) {
            firstScores.add(candidate.similarity());
            firstPrevious.add(-1);
        }

        dp.add(firstScores);
        previous.add(firstPrevious);

        for (int layerIndex = 1;
             layerIndex < layers.size();
             layerIndex++) {

            List<Candidate> current = layers.get(layerIndex);
            List<Candidate> prior = layers.get(layerIndex - 1);

            List<Double> currentScores = new java.util.ArrayList<>();
            List<Integer> currentPrevious = new java.util.ArrayList<>();

            for (Candidate currentCandidate : current) {
                double bestScore = Double.NEGATIVE_INFINITY;
                int bestPrevious = -1;

                for (int j = 0; j < prior.size(); j++) {
                    Candidate previousCandidate = prior.get(j);

                    double delta =
                            currentCandidate.perfStart()
                                    - previousCandidate.perfStart();

                    if (delta <= 0.0) {
                        continue;
                    }

                    double temporalPenalty =
                            Math.abs(delta - EXPECTED_DELTA)
                                    * TEMPORAL_LAMBDA;

                    double score =
                            dp.get(layerIndex - 1).get(j)
                                    + currentCandidate.similarity()
                                    - temporalPenalty;

                    if (score > bestScore) {
                        bestScore = score;
                        bestPrevious = j;
                    }
                }

                currentScores.add(bestScore);
                currentPrevious.add(bestPrevious);
            }

            dp.add(currentScores);
            previous.add(currentPrevious);
        }

        int lastLayerIndex = layers.size() - 1;

        List<Double> lastScores = dp.get(lastLayerIndex);

        int bestIndex = -1;
        double bestTrajectoryScore =
                Double.NEGATIVE_INFINITY;

        for (int i = 0; i < lastScores.size(); i++) {
            if (lastScores.get(i) > bestTrajectoryScore) {
                bestTrajectoryScore = lastScores.get(i);
                bestIndex = i;
            }
        }

        if (bestIndex < 0) {
            log.warn("No valid temporal trajectory found");
            return;
        }

        List<Candidate> trajectory =
                new java.util.ArrayList<>();

        int index = bestIndex;

        for (int layerIndex = lastLayerIndex;
             layerIndex >= 0;
             layerIndex--) {

            Candidate candidate =
                    layers.get(layerIndex).get(index);

            trajectory.add(candidate);

            index =
                    previous.get(layerIndex).get(index);

            if (layerIndex > 0 && index < 0) {
                log.warn(
                        "Trajectory disconnected at layer {}",
                        layerIndex
                );
                return;
            }
        }

        java.util.Collections.reverse(trajectory);

        log.info(
                "========== TOP-K TEMPORAL TRAJECTORY =========="
        );

        double totalAbsoluteError = 0.0;
        double maxAbsoluteError = 0.0;

        for (Candidate candidate : trajectory) {
            double expectedPerformance =
                    EXPECTED_SCALE * candidate.refStart()
                            + EXPECTED_INTERCEPT;

            double error =
                    candidate.perfStart()
                            - expectedPerformance;

            totalAbsoluteError += Math.abs(error);
            maxAbsoluteError =
                    Math.max(
                            maxAbsoluteError,
                            Math.abs(error)
                    );

            log.info(
                    "ref={} -> perf={} scale={} similarity={} expectedPerf={} error={}",
                    candidate.refStart(),
                    candidate.perfStart(),
                    candidate.scale(),
                    candidate.similarity(),
                    expectedPerformance,
                    error
            );
        }

        double meanAbsoluteError =
                totalAbsoluteError / trajectory.size();

        log.info(
                "========== TRAJECTORY SUMMARY =========="
        );
        log.info("points={}", trajectory.size());
        log.info(
                "trajectoryScore={}",
                bestTrajectoryScore
        );
        log.info(
                "meanAbsoluteError={}",
                meanAbsoluteError
        );
        log.info(
                "maxAbsoluteError={}",
                maxAbsoluteError
        );
    }

    private static List<ChromaFrame> reduceFrames(
            List<ChromaFrame> frames,
            int stride
    ) {
        List<ChromaFrame> result =
                new java.util.ArrayList<>();

        for (int i = 0; i < frames.size(); i += stride) {
            result.add(frames.get(i));
        }

        return result;
    }

    @Test
    void shouldLocateReferenceInsideLongPerformance() {
        File referenceAudio = new File("../regina-ref.wav");
        File performanceAudio = new File("../regina-flores.wav");

        ChromaExtractor extractor = new TarsosChromaExtractor(
        );

        List<ChromaFrame> reference =
                extractor.extract(referenceAudio);

        List<ChromaFrame> performance =
                extractor.extract(performanceAudio);

        CoarseSequenceLocator locator =
                new CoarseSequenceLocator();

        log.info("score 2s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 2.0, 0.90));

        log.info("score 10s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 10.0, 0.90));

        log.info("score 1.5s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 1.5, 0.90));

        log.info("score 2.5s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 2.5, 0.90));

        log.info("score 3.0s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 3.0, 0.90));
        log.info("score 3.3s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 3.3, 0.90));
        log.info("score 3.5s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 3.5, 0.90));
        log.info("score 4.0s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 4.0, 0.90));
        log.info("score 3.3s scale 0.85 = {}",
                locator.scoreWindow(reference, performance, 3.3, 0.85));
        log.info("score 3.3s scale 0.95 = {}",
                locator.scoreWindow(reference, performance, 3.3, 0.95));

        log.info("score 5.0s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 5.0, 0.90));

        log.info("score 10s scale 0.90 = {}",
                locator.scoreWindow(reference, performance, 10.0, 0.90));

        log.info("score 953s scale 0.95 = {}",
                locator.scoreWindow(reference, performance, 953.0, 0.95));

        CoarseSequenceLocator.SequenceMatch match =
                locator.locate(reference, performance);

        log.info(
                "COARSE MATCH: start={}s end={}s scale={} similarity={}",
                match.performanceStartSec(),
                match.performanceEndSec(),
                match.tempoScale(),
                match.similarity()
        );

        assertTrue(
                match.performanceStartSec() < 120.0,
                "Expected the reference to be located in the first performance block"
        );

        assertTrue(
                match.performanceEndSec() < 130.0,
                "Expected the matching sequence to end in the first performance block"
        );

        assertTrue(
                match.tempoScale() >= 0.85 &&
                        match.tempoScale() <= 1.35,
                "Tempo scale must remain inside the configured search range"
        );

        assertTrue(
                match.similarity() > 0.5,
                "Expected meaningful chroma similarity"
        );
    }
}
