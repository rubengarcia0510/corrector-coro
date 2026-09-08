package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemporalStrategyVsLinearRegressionTest {

    @Test
    void compareSecondOrderAgainstLinearRegression() {

        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        assertTrue(referenceFile.exists(),
                "Reference audio not found: " + referenceFile);

        assertTrue(performanceFile.exists(),
                "Performance audio not found: " + performanceFile);

        TarsosChromaExtractor extractor = new TarsosChromaExtractor();

        List<ChromaFrame> reference =
                extractor.extract(referenceFile);

        List<ChromaFrame> performance =
                extractor.extract(performanceFile);

        SecondOrderTemporalStrategy strategy =
                new SecondOrderTemporalStrategy();

        AlignmentResult result =
                strategy.align(reference, performance);

        assertNotNull(result);
        assertEquals(8, result.points().size());

        int n = result.points().size();

        double[] referenceTimes = new double[n];
        double[] performanceTimes = new double[n];

        for (int i = 0; i < n; i++) {
            AlignmentPoint point = result.points().get(i);

            referenceTimes[i] =
                    point.referenceTimestampSec();

            performanceTimes[i] =
                    point.performanceTimestampSec();
        }

        // ------------------------------------------------------------
        // Linear regression:
        //
        // performance = slope * reference + intercept
        // ------------------------------------------------------------

        double meanX = 0.0;
        double meanY = 0.0;

        for (int i = 0; i < n; i++) {
            meanX += referenceTimes[i];
            meanY += performanceTimes[i];
        }

        meanX /= n;
        meanY /= n;

        double numerator = 0.0;
        double denominator = 0.0;

        for (int i = 0; i < n; i++) {
            double dx = referenceTimes[i] - meanX;
            double dy = performanceTimes[i] - meanY;

            numerator += dx * dy;
            denominator += dx * dx;
        }

        double slope = numerator / denominator;
        double intercept = meanY - slope * meanX;

        // ------------------------------------------------------------
        // Regression residuals
        // ------------------------------------------------------------

        double linearSse = 0.0;
        double linearMaxResidual = 0.0;

        for (int i = 0; i < n; i++) {

            double predicted =
                    slope * referenceTimes[i] + intercept;

            double residual =
                    performanceTimes[i] - predicted;

            linearSse += residual * residual;

            linearMaxResidual =
                    Math.max(
                            linearMaxResidual,
                            Math.abs(residual)
                    );
        }

        double linearRmse =
                Math.sqrt(linearSse / n);

        // ------------------------------------------------------------
        // Offset statistics
        // ------------------------------------------------------------

        double firstOffset =
                performanceTimes[0] - referenceTimes[0];

        double lastOffset =
                performanceTimes[n - 1]
                        - referenceTimes[n - 1];

        double offsetVariation =
                lastOffset - firstOffset;

        // ------------------------------------------------------------
        // Print all points
        // ------------------------------------------------------------

        System.out.println();
        System.out.println(
                "========== SECOND ORDER VS LINEAR =========="
        );

        System.out.printf(
                "LINEAR slope=%.6f intercept=%+.3f%n",
                slope,
                intercept
        );

        System.out.printf(
                "LINEAR RMSE=%.3f s maxResidual=%.3f s%n",
                linearRmse,
                linearMaxResidual
        );

        System.out.printf(
                "OFFSET first=%+.3f s last=%+.3f s variation=%+.3f s%n",
                firstOffset,
                lastOffset,
                offsetVariation
        );

        System.out.println();
        System.out.println("POINTS:");

        for (int i = 0; i < n; i++) {

            double offset =
                    performanceTimes[i]
                            - referenceTimes[i];

            double predicted =
                    slope * referenceTimes[i]
                            + intercept;

            double residual =
                    performanceTimes[i]
                            - predicted;

            System.out.printf(
                    "%d  ref=%6.2f  perf=%6.2f  offset=%+6.3f  linearResidual=%+6.3f%n",
                    i,
                    referenceTimes[i],
                    performanceTimes[i],
                    offset,
                    residual
            );
        }

        System.out.println(
                "============================================="
        );

        // Exploratory test: no quality assertions yet.
    }
}
