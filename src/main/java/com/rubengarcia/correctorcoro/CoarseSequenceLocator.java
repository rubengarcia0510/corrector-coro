package com.rubengarcia.correctorcoro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CoarseSequenceLocator {

    private static final double HOP_SEC = 0.5;
    private static final double DTW_FRAME_STEP_SEC = 0.10;
    private static final double DTW_BAND_SEC = 0.5;
    private static final double GLOBAL_SCAN_HOP_SEC = 5.0;
    private static final double REFINEMENT_RADIUS_SEC = 5.0;
    private static final double REFINEMENT_STEP_SEC = 0.5;

    private static final double MIN_SCALE = 0.85;
    private static final double MAX_SCALE = 1.35;
    private static final double SCALE_STEP = 0.05;

    private static final double TEMPORAL_PENALTY_LAMBDA = 0.5;

    private static final int TOP_K = 5;
    private static final int MIN_REFERENCE_FRAMES = 8;

    private final ChromaDtwAligner dtwAligner;

    public CoarseSequenceLocator() {
        this.dtwAligner =
                new ChromaDtwAligner(
                        DTW_BAND_SEC,
                        TEMPORAL_PENALTY_LAMBDA
                );
    }

    public SequenceMatch locate(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

        validate(reference, performance);

        List<ChromaFrame> coarseReference =
                downsampleForDtw(reference);

        List<ChromaFrame> coarsePerformance =
                downsampleForDtw(performance);

        if (coarseReference.size() < MIN_REFERENCE_FRAMES) {
            throw new IllegalArgumentException(
                    "Reference sequence is too short for coarse localization"
            );
        }

        List<Candidate> globalCandidates =
                globalScan(
                        coarseReference,
                        coarsePerformance
                );

        if (globalCandidates.isEmpty()) {
            throw new IllegalStateException(
                    "No coarse performance sequence could be matched"
            );
        }

        return refineBestCandidate(
                coarseReference,
                coarsePerformance,
                globalCandidates
        );
    }

    private List<Candidate> globalScan(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

        List<Candidate> candidates =
                new ArrayList<>();

        double performanceStart =
                performance.get(0).timestampSec();

        double performanceEnd =
                performance.get(performance.size() - 1).timestampSec();

        double referenceDuration =
                duration(reference);

        /*
         * The validated global-DTW experiment searches fixed-size
         * candidate windows. Tempo variation is handled by DTW itself.
         *
         * We therefore do not multiply the candidate window by scale here.
         * Doing so was one of the reasons the previous nearest-frame
         * implementation selected false long-distance candidates.
         */
        for (double startTime = performanceStart;
             startTime + referenceDuration <= performanceEnd + HOP_SEC;
             startTime += GLOBAL_SCAN_HOP_SEC) {

            Candidate candidate =
                    scoreCandidate(
                            reference,
                            performance,
                            startTime
                    );

            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        candidates.sort(
                Comparator.comparingDouble(
                        Candidate::combinedScore
                )
        );

        if (candidates.size() <= TOP_K) {
            return candidates;
        }

        return new ArrayList<>(
                candidates.subList(0, TOP_K)
        );
    }

    private SequenceMatch refineBestCandidate(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            List<Candidate> globalCandidates) {

        Candidate best = null;

        double performanceStart =
                performance.get(0).timestampSec();

        double performanceEnd =
                performance.get(performance.size() - 1).timestampSec();

        double referenceDuration =
                duration(reference);

        for (Candidate coarseCandidate : globalCandidates) {

            double minStart =
                    Math.max(
                            performanceStart,
                            coarseCandidate.performanceStartSec()
                                    - REFINEMENT_RADIUS_SEC
                    );

            double maxStart =
                    Math.min(
                            performanceEnd - referenceDuration,
                            coarseCandidate.performanceStartSec()
                                    + REFINEMENT_RADIUS_SEC
                    );

            if (maxStart < minStart) {
                continue;
            }

            for (double startTime = minStart;
                 startTime <= maxStart + 1e-9;
                 startTime += REFINEMENT_STEP_SEC) {

                Candidate candidate =
                        scoreCandidate(
                                reference,
                                performance,
                                startTime
                        );

                if (candidate == null) {
                    continue;
                }

                if (best == null
                        || candidate.combinedScore()
                        < best.combinedScore()) {

                    best = candidate;
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException(
                    "No refined performance sequence could be matched"
            );
        }

        return new SequenceMatch(
                best.performanceStartSec(),
                best.performanceEndSec(),
                best.tempoScale(),
                best.similarity()
        );
    }

    private Candidate scoreCandidate(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double performanceStartSec) {

        double referenceDuration =
                duration(reference);

        if (referenceDuration <= 0.0) {
            return null;
        }

        double performanceEndSec =
                performanceStartSec
                        + referenceDuration;

        List<ChromaFrame> candidate =
                extractWindow(
                        performance,
                        performanceStartSec,
                        performanceEndSec
                );

        if (candidate.size() < MIN_REFERENCE_FRAMES) {
            return null;
        }

        List<ChromaFrame> coarseReference =
                downsampleForDtw(reference);

        List<ChromaFrame> coarseCandidate =
                downsampleForDtw(candidate);

        if (coarseCandidate.size()
                < coarseReference.size() * 0.70) {

            return null;
        }

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                dtwAligner.align(
                        coarseReference,
                        coarseCandidate
                );

        if (alignments.isEmpty()) {
            return null;
        }

        double referenceStartSec =
                coarseReference
                        .get(0)
                        .timestampSec();

        double candidateStartSec =
                coarseCandidate
                        .get(0)
                        .timestampSec();

        double totalDistance = 0.0;
        double totalTemporalDeviation = 0.0;

        for (ChromaDtwAligner.ChromaAlignment alignment
                : alignments) {

            totalDistance += alignment.distance();

            double referenceRelativeTime =
                    alignment.referenceTimestampSec()
                            - referenceStartSec;

            double performanceRelativeTime =
                    alignment.performanceTimestampSec()
                            - candidateStartSec;

            totalTemporalDeviation +=
                    Math.abs(
                            performanceRelativeTime
                                    - referenceRelativeTime
                    );
        }

        double averageDistance =
                totalDistance / alignments.size();

        double averageTemporalDeviation =
                totalTemporalDeviation
                        / alignments.size();

        double combinedScore =
                averageDistance
                        + TEMPORAL_PENALTY_LAMBDA
                        * averageTemporalDeviation;

        double similarity =
                1.0 - averageDistance;

        double tempoScale =
                estimateTempoScale(alignments);

        if (tempoScale < MIN_SCALE
                || tempoScale > MAX_SCALE) {

            return null;
        }

        return new Candidate(
                performanceStartSec,
                performanceEndSec,
                tempoScale,
                similarity,
                averageTemporalDeviation,
                combinedScore
        );
    }

    double scoreWindow(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double performanceStartSec,
            double scale) {

        validate(reference, performance);

        if (scale < MIN_SCALE
                || scale > MAX_SCALE) {

            return 0.0;
        }

        List<ChromaFrame> coarseReference =
                downsampleForDtw(reference);

        List<ChromaFrame> coarsePerformance =
                downsampleForDtw(performance);

        double referenceDuration =
                duration(coarseReference);

        if (referenceDuration <= 0.0) {
            return 0.0;
        }

        /*
         * Keep the public/test helper compatible with its existing
         * scale argument. The actual DTW candidate is still evaluated
         * against a window whose nominal duration follows that scale.
         */
        double targetDuration =
                referenceDuration * scale;

        List<ChromaFrame> candidate =
                extractWindow(
                        coarsePerformance,
                        performanceStartSec,
                        performanceStartSec
                                + targetDuration
                );

        if (candidate.size()
                < coarseReference.size() * 0.70) {

            return 0.0;
        }

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                dtwAligner.align(
                        coarseReference,
                        candidate
                );

        if (alignments.isEmpty()) {
            return 0.0;
        }

        double totalDistance = 0.0;

        for (ChromaDtwAligner.ChromaAlignment alignment
                : alignments) {

            totalDistance += alignment.distance();
        }

        double averageDistance =
                totalDistance / alignments.size();

        return 1.0 - averageDistance;
    }

    private List<ChromaFrame> extractWindow(
            List<ChromaFrame> frames,
            double startSec,
            double endSec) {

        if (frames.isEmpty()) {
            return List.of();
        }

        int start =
                findFrameAtOrAfter(
                        frames,
                        startSec
                );

        if (start >= frames.size()) {
            return List.of();
        }

        List<ChromaFrame> result =
                new ArrayList<>();

        for (int i = start;
             i < frames.size();
             i++) {

            ChromaFrame frame =
                    frames.get(i);

            if (frame.timestampSec()
                    > endSec) {

                break;
            }

            result.add(frame);
        }

        return result;
    }

    private double estimateTempoScale(
            List<ChromaDtwAligner.ChromaAlignment> alignments) {

        if (alignments.size() < 2) {
            return 1.0;
        }

        ChromaDtwAligner.ChromaAlignment first =
                alignments.get(0);

        ChromaDtwAligner.ChromaAlignment last =
                alignments.get(
                        alignments.size() - 1
                );

        double referenceDuration =
                last.referenceTimestampSec()
                        - first.referenceTimestampSec();

        double performanceDuration =
                last.performanceTimestampSec()
                        - first.performanceTimestampSec();

        if (referenceDuration <= 0.0) {
            return 1.0;
        }

        return performanceDuration
                / referenceDuration;
    }

    private List<ChromaFrame> downsample(
            List<ChromaFrame> frames) {

        if (frames.isEmpty()) {
            return List.of();
        }

        List<ChromaFrame> result =
                new ArrayList<>();

        double nextTimestamp =
                frames.get(0).timestampSec();

        for (ChromaFrame frame : frames) {

            if (frame.timestampSec() + 1e-9
                    >= nextTimestamp) {

                result.add(frame);
                nextTimestamp += HOP_SEC;
            }
        }

        return result;
    }

    private List<ChromaFrame> downsampleForDtw(
            List<ChromaFrame> frames) {

        if (frames.isEmpty()) {
            return List.of();
        }

        List<ChromaFrame> result =
                new ArrayList<>();

        double nextTimestamp =
                frames.get(0).timestampSec();

        for (ChromaFrame frame : frames) {
            if (frame.timestampSec() + 1e-9 >= nextTimestamp) {
                result.add(frame);
                nextTimestamp += DTW_FRAME_STEP_SEC;
            }
        }

        return result;
    }

    private int findFrameAtOrAfter(
            List<ChromaFrame> frames,
            double timestamp) {

        int low = 0;
        int high = frames.size() - 1;

        if (timestamp
                > frames.get(high).timestampSec()) {

            return frames.size();
        }

        while (low < high) {

            int mid =
                    (low + high) >>> 1;

            if (frames.get(mid).timestampSec()
                    < timestamp) {

                low = mid + 1;

            } else {

                high = mid;
            }
        }

        return low;
    }

    private double duration(
            List<ChromaFrame> frames) {

        if (frames.size() < 2) {
            return 0.0;
        }

        return frames.get(
                frames.size() - 1
        ).timestampSec()
                - frames.get(
                0
        ).timestampSec();
    }

    private void validate(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

        if (reference == null
                || performance == null) {

            throw new IllegalArgumentException(
                    "Chroma sequences cannot be null"
            );
        }

        if (reference.isEmpty()
                || performance.isEmpty()) {

            throw new IllegalArgumentException(
                    "Chroma sequences cannot be empty"
            );
        }
    }

    private record Candidate(
            double performanceStartSec,
            double performanceEndSec,
            double tempoScale,
            double similarity,
            double averageTemporalDeviation,
            double combinedScore) {
    }

    public record SequenceMatch(
            double performanceStartSec,
            double performanceEndSec,
            double tempoScale,
            double similarity) {
    }
}
