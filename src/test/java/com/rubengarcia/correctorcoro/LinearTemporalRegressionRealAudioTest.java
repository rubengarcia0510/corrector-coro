package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearTemporalRegressionRealAudioTest {

    @Test
    void characterizeLinearTemporalRelationship() {

        double[] referenceTime = {
                2.00, 4.00, 6.00, 8.00,
                10.00, 12.00, 14.00, 16.00,
                18.00, 20.00, 22.00, 24.00,
                26.00, 28.00
        };

        double[] performanceTime = {
                2.95, 4.95, 6.95, 8.70,
                10.70, 12.45, 14.70, 15.70,
                17.70, 19.45, 21.20, 22.95,
                24.70, 26.45
        };

        System.out.println("---- LINEAR TEMPORAL REGRESSION ----");

        RegressionResult allPoints =
                calculateRegression(referenceTime, performanceTime, -1);

        printResult("ALL POINTS", allPoints);

        RegressionResult withoutOutlier =
                calculateRegression(referenceTime, performanceTime, 6);

        printResult("WITHOUT 14s OUTLIER", withoutOutlier);

        System.out.printf(
                Locale.US,
                "RMSE improvement=%.3fs%n",
                allPoints.rmse - withoutOutlier.rmse
        );

        assertTrue(Double.isFinite(allPoints.slope),
                "La pendiente debe ser finita");

        assertTrue(Double.isFinite(allPoints.intercept),
                "El intercepto debe ser finito");

        assertTrue(Double.isFinite(withoutOutlier.slope),
                "La pendiente sin outlier debe ser finita");

        assertTrue(Double.isFinite(withoutOutlier.intercept),
                "El intercepto sin outlier debe ser finito");
    }

    private RegressionResult calculateRegression(
            double[] referenceTime,
            double[] performanceTime,
            int excludedIndex) {

        double meanX = 0.0;
        double meanY = 0.0;
        int count = 0;

        for (int i = 0; i < referenceTime.length; i++) {
            if (i == excludedIndex) {
                continue;
            }

            meanX += referenceTime[i];
            meanY += performanceTime[i];
            count++;
        }

        meanX /= count;
        meanY /= count;

        double numerator = 0.0;
        double denominator = 0.0;

        for (int i = 0; i < referenceTime.length; i++) {
            if (i == excludedIndex) {
                continue;
            }

            double dx = referenceTime[i] - meanX;
            double dy = performanceTime[i] - meanY;

            numerator += dx * dy;
            denominator += dx * dx;
        }

        double slope = numerator / denominator;
        double intercept = meanY - slope * meanX;

        double squaredError = 0.0;
        double maxError = 0.0;

        for (int i = 0; i < referenceTime.length; i++) {
            if (i == excludedIndex) {
                continue;
            }

            double predicted =
                    slope * referenceTime[i] + intercept;

            double error =
                    performanceTime[i] - predicted;

            double absError = Math.abs(error);

            squaredError += error * error;
            maxError = Math.max(maxError, absError);
        }

        double rmse = Math.sqrt(squaredError / count);

        return new RegressionResult(
                slope,
                intercept,
                rmse,
                maxError
        );
    }

    private void printResult(
            String label,
            RegressionResult result) {

        System.out.println();
        System.out.println("---- " + label + " ----");

        System.out.printf(
                Locale.US,
                "performanceTime = %.4f * referenceTime + %.4f%n",
                result.slope,
                result.intercept
        );

        System.out.printf(
                Locale.US,
                "rmse=%.3fs maxError=%.3fs%n",
                result.rmse,
                result.maxError
        );
    }

    private static class RegressionResult {

        final double slope;
        final double intercept;
        final double rmse;
        final double maxError;

        RegressionResult(
                double slope,
                double intercept,
                double rmse,
                double maxError) {

            this.slope = slope;
            this.intercept = intercept;
            this.rmse = rmse;
            this.maxError = maxError;
        }
    }
}
