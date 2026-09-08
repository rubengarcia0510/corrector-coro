package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecondOrderTemporalStrategyRawRealAudioRegressionTest {

    private static final double EXPECTED_AVERAGE_DISTANCE = 0.3180;
    private static final double DISTANCE_TOLERANCE = 0.005;

    private static final double MAX_ALLOWED_ACCELERATION = 0.60;

    private static final double EXPECTED_FIRST_OFFSET = 0.850;
    private static final double EXPECTED_LAST_OFFSET = -1.750;

    private static final double OFFSET_TOLERANCE = 0.051;

    @Test
    void shouldPreserveRealCoralTemporalCalibration() {

        File referenceFile =
                new File("regina-ref-30s.wav");

        File performanceFile =
                new File("regina-flores-30s.wav");

        assertTrue(
                referenceFile.exists(),
                "Reference audio not found: " + referenceFile
        );

        assertTrue(
                performanceFile.exists(),
                "Performance audio not found: " + performanceFile
        );

        TarsosChromaExtractor extractor =
                new TarsosChromaExtractor();

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

        double totalDistance = 0.0;
        double maxAcceleration = 0.0;

        for (int i = 0; i < result.points().size(); i++) {

            AlignmentPoint point =
                    result.points().get(i);

            double offset =
                    point.performanceTimestampSec()
                            - point.referenceTimestampSec();

            assertTrue(
                    offset >= -4.0 && offset <= 4.0,
                    "Offset outside search range: " + offset
            );

            totalDistance += point.distance();

            if (i >= 2) {

                AlignmentPoint previous =
                        result.points().get(i - 1);

                AlignmentPoint previousPrevious =
                        result.points().get(i - 2);

                double previousOffset =
                        previous.performanceTimestampSec()
                                - previous.referenceTimestampSec();

                double previousPreviousOffset =
                        previousPrevious.performanceTimestampSec()
                                - previousPrevious.referenceTimestampSec();

                double currentChange =
                        offset - previousOffset;

                double previousChange =
                        previousOffset - previousPreviousOffset;

                double acceleration =
                        currentChange - previousChange;

                maxAcceleration =
                        Math.max(
                                maxAcceleration,
                                Math.abs(acceleration)
                        );
            }
        }

        double averageDistance =
                totalDistance / result.points().size();

        double firstOffset =
                result.points().get(0).performanceTimestampSec()
                        - result.points().get(0).referenceTimestampSec();

        AlignmentPoint last =
                result.points().get(
                        result.points().size() - 1
                );

        double lastOffset =
                last.performanceTimestampSec()
                        - last.referenceTimestampSec();

        System.out.printf(
                "REGRESSION averageDistance=%.4f "
                        + "firstOffset=%+.3f "
                        + "lastOffset=%+.3f "
                        + "maxAcceleration=%.3f%n",
                averageDistance,
                firstOffset,
                lastOffset,
                maxAcceleration
        );

        assertEquals(
                EXPECTED_AVERAGE_DISTANCE,
                averageDistance,
                DISTANCE_TOLERANCE
        );

        assertEquals(
                EXPECTED_FIRST_OFFSET,
                firstOffset,
                OFFSET_TOLERANCE
        );

        assertEquals(
                EXPECTED_LAST_OFFSET,
                lastOffset,
                OFFSET_TOLERANCE
        );

        assertTrue(
                maxAcceleration <= MAX_ALLOWED_ACCELERATION,
                "Temporal acceleration changed unexpectedly: "
                        + maxAcceleration
        );
    }
}
