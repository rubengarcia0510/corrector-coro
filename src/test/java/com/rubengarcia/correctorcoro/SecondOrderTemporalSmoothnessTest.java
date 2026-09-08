package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecondOrderTemporalSmoothnessTest {

    @Test
    void shouldProduceTemporallySmoothOffsetTrajectory() {

        SecondOrderTemporalStrategy strategy =
                new SecondOrderTemporalStrategy();

        List<ChromaFrame> reference =
                new ArrayList<>();

        List<ChromaFrame> performance =
                new ArrayList<>();

        /*
         * Dense temporal sampling:
         * 100 ms between frames.
         *
         * Performance is shifted by +0.5 s.
         */
        for (int i = 0; i < 120; i++) {

            double referenceTime =
                    i * 0.1;

            double performanceTime =
                    referenceTime + 0.5;

            int pitchClass =
                    i % 12;

            reference.add(
                    frame(
                            referenceTime,
                            pitchClass
                    )
            );

            performance.add(
                    frame(
                            performanceTime,
                            pitchClass
                    )
            );
        }

        AlignmentResult result =
                strategy.align(
                        reference,
                        performance
                );

        assertNotNull(result);
        assertFalse(result.points().isEmpty());

        double previousOffset = Double.NaN;
        double previousChange = Double.NaN;

        for (AlignmentPoint point :
                result.points()) {

            double offset =
                    point.performanceTimestampSec()
                            - point.referenceTimestampSec();

            assertTrue(
                    offset >= -4.0
                            && offset <= 4.0,
                    "Offset fuera de rango: " + offset
            );

            if (Double.isFinite(previousOffset)) {

                double change =
                        offset - previousOffset;

                if (Double.isFinite(previousChange)) {

                    double acceleration =
                            change - previousChange;

                    assertTrue(
                            Math.abs(acceleration) <= 0.11,
                            "Aceleración temporal demasiado grande: "
                                    + acceleration
                    );
                }

                previousChange = change;
            }

            previousOffset = offset;
        }
    }

    private ChromaFrame frame(
            double timestamp,
            int pitchClass) {

        double[] chroma =
                new double[12];

        chroma[pitchClass] = 1.0;

        return new ChromaFrame(
                timestamp,
                chroma
        );
    }
}
