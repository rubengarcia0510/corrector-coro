package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondOrderTemporalLambdaSweepTest {

    private static final double[] LAMBDAS = {
            0.00, 0.01, 0.02, 0.05, 0.10, 0.20, 0.50
    };

    @Test
    void compareLambdaValuesOnRealAudio() throws Exception {

        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        TarsosChromaExtractor extractor = new TarsosChromaExtractor();

        List<ChromaFrame> reference = extractor.extract(referenceFile);
        List<ChromaFrame> performance = extractor.extract(performanceFile);

        SecondOrderTemporalStrategy strategy =
                new SecondOrderTemporalStrategy();

        Method generateCandidates =
                SecondOrderTemporalStrategy.class
                        .getDeclaredMethod(
                                "generateCandidates",
                                List.class,
                                List.class
                        );
        generateCandidates.setAccessible(true);

        Method optimize =
                SecondOrderTemporalStrategy.class
                        .getDeclaredMethod(
                                "optimizeSecondOrder",
                                List.class,
                                double.class
                        );
        optimize.setAccessible(true);

        List<?> candidates =
                (List<?>) generateCandidates.invoke(
                        strategy,
                        reference,
                        performance
                );

        System.out.println();
        System.out.println("========== LAMBDA SWEEP ==========");
        System.out.println(
                "referenceFrames=" + reference.size()
                        + " performanceFrames=" + performance.size()
        );
        System.out.println(
                "lambda | distance | maxAccel | accelEnergy | firstOffset | lastOffset"
        );

        for (double lambda : LAMBDAS) {

            List<?> path =
                    (List<?>) optimize.invoke(
                            strategy,
                            candidates,
                            lambda
                    );

            double[] offsets = new double[path.size()];

            Field offsetField =
                    path.get(0).getClass()
                            .getDeclaredField("offsetSec");
            offsetField.setAccessible(true);

            for (int i = 0; i < path.size(); i++) {
                offsets[i] =
                        offsetField.getDouble(path.get(i));
            }

            double distance =
                    evaluateWarp(reference, performance, path);

            double maxAcceleration = 0.0;
            double accelerationEnergy = 0.0;

            for (int i = 2; i < offsets.length; i++) {
                double change1 =
                        offsets[i - 1] - offsets[i - 2];

                double change2 =
                        offsets[i] - offsets[i - 1];

                double acceleration =
                        change2 - change1;

                maxAcceleration =
                        Math.max(
                                maxAcceleration,
                                Math.abs(acceleration)
                        );

                accelerationEnergy +=
                        acceleration * acceleration;
            }

            System.out.printf(
                    java.util.Locale.US,
                    "%.2f   | %.6f | %.6f | %.6f | %+.3f      | %+.3f%n",
                    lambda,
                    distance,
                    maxAcceleration,
                    accelerationEnergy,
                    offsets[0],
                    offsets[offsets.length - 1]
            );
        }

        System.out.println("==================================");
        System.out.println();

        assertTrue(pathSize(candidates) >= 3);
    }

    private double evaluateWarp(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            List<?> path) throws Exception {

        Class<?> candidateClass =
                path.get(0).getClass();

        Field startField =
                candidateClass.getDeclaredField("segmentStartSec");
        Field endField =
                candidateClass.getDeclaredField("segmentEndSec");
        Field offsetField =
                candidateClass.getDeclaredField("offsetSec");

        startField.setAccessible(true);
        endField.setAccessible(true);
        offsetField.setAccessible(true);

        double totalDistance = 0.0;
        int matches = 0;

        for (ChromaFrame referenceFrame : reference) {

            double referenceTime =
                    referenceFrame.timestampSec();

            int segment = findSegment(
                    referenceTime,
                    path,
                    startField,
                    endField
            );

            if (segment < 0) {
                continue;
            }

            double start =
                    startField.getDouble(path.get(segment));

            double end =
                    endField.getDouble(path.get(segment));

            double offset =
                    offsetField.getDouble(path.get(segment));

            double fraction =
                    (referenceTime - start)
                            / Math.max(end - start, 0.000001);

            double targetTime =
                    referenceTime + offset;

            if (segment + 1 < path.size()) {
                double nextOffset =
                        offsetField.getDouble(
                                path.get(segment + 1)
                        );

                targetTime =
                        referenceTime
                                + offset
                                + fraction
                                * (nextOffset - offset);
            }

            ChromaFrame best =
                    findNearestFrame(
                            performance,
                            targetTime
                    );

            if (best != null) {
                totalDistance += cosineDistance(
                        referenceFrame.chroma(),
                        best.chroma()
                );
                matches++;
            }
        }

        return totalDistance / matches;
    }

    private int findSegment(
            double time,
            List<?> path,
            Field startField,
            Field endField) throws Exception {

        for (int i = 0; i < path.size(); i++) {

            double start =
                    startField.getDouble(path.get(i));

            double end =
                    endField.getDouble(path.get(i));

            if (time >= start && time < end) {
                return i;
            }
        }

        return path.size() - 1;
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

    private int pathSize(List<?> candidates) {
        return candidates.size();
    }
}
