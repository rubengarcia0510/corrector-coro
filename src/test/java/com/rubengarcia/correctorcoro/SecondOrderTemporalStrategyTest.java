package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecondOrderTemporalStrategyTest {

    @Test
    void shouldReturnRealReferenceAndPerformanceTimestamps() {

        SecondOrderTemporalStrategy strategy =
                new SecondOrderTemporalStrategy();

        List<ChromaFrame> reference = List.of(
                frame(0.0, 0),
                frame(1.0, 1),
                frame(2.0, 2),
                frame(3.0, 3),
                frame(4.0, 4),
                frame(5.0, 5),
                frame(6.0, 6),
                frame(7.0, 7),
                frame(8.0, 8),
                frame(9.0, 9),
                frame(10.0, 10),
                frame(11.0, 11)
        );

        List<ChromaFrame> performance = List.of(
                frame(0.5, 0),
                frame(1.5, 1),
                frame(2.5, 2),
                frame(3.5, 3),
                frame(4.5, 4),
                frame(5.5, 5),
                frame(6.5, 6),
                frame(7.5, 7),
                frame(8.5, 8),
                frame(9.5, 9),
                frame(10.5, 10),
                frame(11.5, 11)
        );

        AlignmentResult result =
                strategy.align(reference, performance);

        assertNotNull(result);
        assertFalse(result.points().isEmpty());

        for (AlignmentPoint point : result.points()) {

            double offset =
                    point.performanceTimestampSec()
                            - point.referenceTimestampSec();

            assertTrue(
                    offset >= -4.0
                            && offset <= 4.0,
                    "Offset fuera del rango permitido: " + offset
            );

            assertTrue(
                    point.performanceTimestampSec() >= 0.0,
                    "Timestamp de performance inválido"
            );

            assertTrue(
                    point.referenceTimestampSec() >= 0.0,
                    "Timestamp de referencia inválido"
            );
        }
    }

    @Test
    void shouldReturnEmptyResultForEmptyInput() {

        SecondOrderTemporalStrategy strategy =
                new SecondOrderTemporalStrategy();

        AlignmentResult result =
                strategy.align(
                        List.of(),
                        List.of()
                );

        assertNotNull(result);
        assertTrue(result.points().isEmpty());
    }

    private ChromaFrame frame(
            double timestamp,
            int pitchClass) {

        double[] chroma = new double[12];
        chroma[pitchClass] = 1.0;

        return new ChromaFrame(
                timestamp,
                chroma
        );
    }
}
