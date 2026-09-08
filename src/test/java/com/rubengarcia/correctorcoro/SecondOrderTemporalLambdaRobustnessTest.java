package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondOrderTemporalLambdaRobustnessTest {

    private static final double[] LAMBDAS = {
        0.00, 0.01, 0.02, 0.05, 0.10
    };

    @Test
    void lambda01RemainsCompetitiveUnderOffsetPerturbations() throws Exception {

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

        Field offsetField =
                null;

        System.out.println();
        System.out.println("========== LAMBDA ROBUSTNESS ==========");
        System.out.println("lambda | baseDistance | perturbedDistance");

        double distance01 = Double.NaN;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (double lambda : LAMBDAS) {

            List<?> path =
                    (List<?>) optimize.invoke(
                            strategy,
                            candidates,
                            lambda
                    );

            if (offsetField == null) {
                offsetField =
                        path.get(0)
                                .getClass()
                                .getDeclaredField("offsetSec");
                offsetField.setAccessible(true);
            }

            double baseDistance =
                    evaluateWarp(
                            reference,
                            performance,
                            path,
                            offsetField,
                            0.0
                    );

            double perturbedDistance =
                    evaluateWarp(
                            reference,
                            performance,
                            path,
                            offsetField,
                            0.05
                    );

            System.out.printf(
                    Locale.US,
                    "%.2f   | %.6f     | %.6f%n",
                    lambda,
                    baseDistance,
                    perturbedDistance
            );

            if (lambda == 0.01) {
                distance01 = perturbedDistance;
            }

            bestDistance =
                    Math.min(bestDistance, perturbedDistance);
        }

        System.out.println("======================================");

        assertTrue(
                distance01 <= bestDistance + 0.005,
                "lambda=0.01 should remain competitive under perturbation"
        );
    }

    private double evaluateWarp(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            List<?> path,
            Field offsetField,
            double perturbation
    ) throws Exception {

        Class<?> candidateClass =
                path.get(0).getClass();

        Field startField =
                candidateClass.getDeclaredField(
                        "segmentStartSec"
                );

        Field endField =
                candidateClass.getDeclaredField(
                        "segmentEndSec"
                );

        startField.setAccessible(true);
        endField.setAccessible(true);

        double totalDistance = 0.0;
        int matches = 0;

        for (ChromaFrame referenceFrame : reference) {

            double referenceTime =
                    referenceFrame.timestampSec();

            int segment =
                    findSegment(
                            referenceTime,
                            path,
                            startField,
                            endField
                    );

            if (segment < 0) {
                continue;
            }

            double start =
                    startField.getDouble(
                            path.get(segment)
                    );

            double end =
                    endField.getDouble(
                            path.get(segment)
                    );

            double offset =
                    offsetField.getDouble(
                            path.get(segment)
                    );

            double fraction =
                    (referenceTime - start)
                            / Math.max(
                                    end - start,
                                    0.000001
                            );

            double targetTime =
                    referenceTime
                            + offset
                            + perturbation * fraction;

            if (segment + 1 < path.size()) {

                double nextOffset =
                        offsetField.getDouble(
                                path.get(segment + 1)
                        );

                targetTime =
                        referenceTime
                                + offset
                                + fraction
                                * (
                                    nextOffset
                                    - offset
                                )
                                + perturbation;
            }

            ChromaFrame best =
                    findNearestFrame(
                            performance,
                            targetTime
                    );

            if (best != null) {

                totalDistance +=
                        cosineDistance(
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
            Field endField
    ) throws Exception {

        for (int i = 0; i < path.size(); i++) {

            double start =
                    startField.getDouble(
                            path.get(i)
                    );

            double end =
                    endField.getDouble(
                            path.get(i)
                    );

            if (time >= start && time < end) {
                return i;
            }
        }

        return path.size() - 1;
    }

    private ChromaFrame findNearestFrame(
            List<ChromaFrame> frames,
            double targetTime
    ) {

        ChromaFrame best = null;
        double bestDelta =
                Double.POSITIVE_INFINITY;

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
            double[] b
    ) {

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

        return 1.0
                - dot
                / (
                    Math.sqrt(normA)
                    * Math.sqrt(normB)
                );
    }
}
