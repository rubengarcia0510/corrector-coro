package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Locale;

class SegmentedTemporalAlignmentRealAudioTest {

    private static final double GLOBAL_OFFSET_SEC = 0.700;

    @Test
    void evaluateSegmentedTemporalAlignment() {

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
                "---- SEGMENTED TEMPORAL ALIGNMENT EXPERIMENT ----"
        );

        double[] segmentStarts = {
                0.0, 4.0, 8.0, 12.0,
                16.0, 20.0, 24.0
        };

        double segmentDurationSec = 4.0;
        double searchRangeSec = 4.0;
        double searchStepSec = frameStepSec;

        for (double segmentStart : segmentStarts) {

            double segmentEnd =
                    Math.min(
                            segmentStart + segmentDurationSec,
                            reference.get(reference.size() - 1)
                                    .timestampSec()
                    );

            double bestOffset = 0.0;
            double bestDistance = Double.POSITIVE_INFINITY;
            int bestMatches = 0;

            for (
                    double offset = -searchRangeSec;
                    offset <= searchRangeSec;
                    offset += searchStepSec
            ) {

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

                    ChromaFrame closest =
                            findClosestFrame(
                                    shiftedPerformance,
                                    targetPerformanceTime
                            );

                    if (closest == null) {
                        continue;
                    }

                    double distance =
                            cosineDistance(
                                    referenceFrame.chroma(),
                                    closest.chroma()
                            );

                    totalDistance += distance;
                    matches++;
                }

                if (matches == 0) {
                    continue;
                }

                double averageDistance =
                        totalDistance / matches;

                if (averageDistance < bestDistance) {
                    bestDistance = averageDistance;
                    bestOffset = offset;
                    bestMatches = matches;
                }
            }

            System.out.printf(
                    Locale.US,
                    "segment=%4.0f-%4.0fs bestOffset=%+6.3fs "
                            + "matches=%d avgDistance=%.4f%n",
                    segmentStart,
                    segmentEnd,
                    bestOffset,
                    bestMatches,
                    bestDistance
            );
        }
    }

    private ChromaFrame findClosestFrame(
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
    }
