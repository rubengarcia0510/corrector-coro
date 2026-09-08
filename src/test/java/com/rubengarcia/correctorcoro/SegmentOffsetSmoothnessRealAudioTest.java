package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Locale;

class SegmentOffsetSmoothnessRealAudioTest {

    private static final double GLOBAL_OFFSET_SEC = 0.700;

    @Test
    void testSegmentOffsetSmoothness() {

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
                "---- SEGMENT OFFSET SMOOTHNESS EXPERIMENT ----"
        );

        double[] maxChanges = {
                0.25,
                0.50,
                0.75
        };

        for (double maxChange : maxChanges) {
            runExperiment(
                    reference,
                    shiftedPerformance,
                    frameStepSec,
                    maxChange
            );
        }
    }

    private void runExperiment(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double frameStepSec,
            double maxChange) {

        double segmentSizeSec = 4.0;
        double searchRangeSec = 4.0;

        double previousOffset = 0.0;
        double totalDistance = 0.0;
        int totalMatches = 0;

        System.out.printf(
                Locale.US,
                "%n---- maxChange=%.2fs ----%n",
                maxChange
        );

        for (double start = 0.0;
             start < 28.0;
             start += segmentSizeSec) {

            double end =
                    Math.min(
                            start + segmentSizeSec,
                            28.0
                    );

            double bestOffset = 0.0;
            double bestDistance = Double.POSITIVE_INFINITY;
            double bestScore = Double.POSITIVE_INFINITY;
            int bestMatches = 0;

            for (double offset = -searchRangeSec;
                 offset <= searchRangeSec;
                 offset += frameStepSec) {

                double distanceSum = 0.0;
                int matches = 0;

                for (ChromaFrame refFrame : reference) {

                    double referenceTime =
                            refFrame.timestampSec();

                    if (referenceTime < start
                            || referenceTime >= end) {
                        continue;
                    }

                    double targetPerformanceTime =
                            referenceTime + offset;

                    ChromaFrame nearest =
                            findNearestFrame(
                                    performance,
                                    targetPerformanceTime
                            );

                    if (nearest == null) {
                        continue;
                    }

                    distanceSum += cosineDistance(
                            refFrame.chroma(),
                            nearest.chroma()
                    );

                    matches++;
                }

                if (matches == 0) {
                    continue;
                }

                double averageDistance =
                        distanceSum / matches;

                double excessChange =
                        Math.max(
                                0.0,
                                Math.abs(
                                        offset
                                                - previousOffset
                                )
                                        - maxChange
                        );

                double score =
                        averageDistance
                                + excessChange;

                if (score < bestScore) {
                    bestScore = score;
                    bestOffset = offset;
                    bestDistance = averageDistance;
                    bestMatches = matches;
                }
            }

            System.out.printf(
                    Locale.US,
                    "segment=%4.0f-%4.0fs "
                            + "offset=%+6.3fs "
                            + "change=%+.3fs "
                            + "distance=%.4f "
                            + "score=%.4f%n",
                    start,
                    end,
                    bestOffset,
                    bestOffset - previousOffset,
                    bestDistance,
                    bestScore
            );

            previousOffset = bestOffset;
            totalDistance += bestDistance * bestMatches;
            totalMatches += bestMatches;
        }

        System.out.printf(
                Locale.US,
                "SUMMARY maxChange=%.2fs "
                        + "avgDistance=%.4f%n",
                maxChange,
                totalDistance / totalMatches
        );
    }

    private ChromaFrame findNearestFrame(
            List<ChromaFrame> frames,
            double targetTime) {

        ChromaFrame best = null;
        double bestDifference = Double.POSITIVE_INFINITY;

        for (ChromaFrame frame : frames) {

            double difference =
                    Math.abs(
                            frame.timestampSec()
                                    - targetTime
                    );

            if (difference < bestDifference) {
                bestDifference = difference;
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

        int length = Math.min(a.length, b.length);

        for (int i = 0; i < length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 1.0;
        }

        return 1.0
                - dot
                / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    }
