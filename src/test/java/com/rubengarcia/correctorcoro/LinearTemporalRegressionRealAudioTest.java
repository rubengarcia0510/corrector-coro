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

        int n = referenceTime.length;

        double meanX = 0.0;
        double meanY = 0.0;

        for (int i = 0; i < n; i++) {
            meanX += referenceTime[i];
            meanY += performanceTime[i];
        }

        meanX /= n;
        meanY /= n;

        double numerator = 0.0;
        double denominator = 0.0;

        for (int i = 0; i < n; i++) {

            double dx =
                    referenceTime[i] - meanX;

            double dy =
                    performanceTime[i] - meanY;

            numerator += dx * dy;
            denominator += dx * dx;
        }

        double slope =
                numerator / denominator;

        double intercept =
                meanY - slope * meanX;

        double squaredError = 0.0;
        double maxError = 0.0;

        System.out.println(
                "---- LINEAR TEMPORAL REGRESSION ----"
        );

        System.out.printf(
                Locale.US,
                "performanceTime = %.4f * referenceTime + %.4f%n",
                slope,
                intercept
        );

        for (int i = 0; i < n; i++) {

            double predicted =
                    slope * referenceTime[i]
                            + intercept;

            double error =
                    performanceTime[i]
                            - predicted;

            double absError =
                    Math.abs(error);

            squaredError += error * error;

            maxError =
                    Math.max(maxError, absError);

            System.out.printf(
                    Locale.US,
                    "ref=%5.2fs actual=%5.2fs predicted=%5.2fs error=%+6.3fs%n",
                    referenceTime[i],
                    performanceTime[i],
                    predicted,
                    error
            );
        }

        double rmse =
                Math.sqrt(
                        squaredError / n
                );

        System.out.printf(
                Locale.US,
                "rmse=%.3fs maxError=%.3fs%n",
                rmse,
                maxError
        );

        assertTrue(
                Double.isFinite(slope),
                "La pendiente debe ser finita"
        );

        assertTrue(
                Double.isFinite(intercept),
                "El intercepto debe ser finito"
        );
    }
}
