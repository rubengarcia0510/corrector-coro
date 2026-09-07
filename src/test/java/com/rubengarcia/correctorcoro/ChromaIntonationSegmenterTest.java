package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChromaIntonationSegmenterTest {

    private final ChromaIntonationSegmenter segmenter =
            new ChromaIntonationSegmenter();

    @Test
    void debeIgnorarDesafinacionAislada() {
        List<ChromaIntonationError> errors = List.of(
                error(0.0, 0.0, 12.0, ChromaIntonationError.Severity.ERROR),
                error(0.1, 0.1, 0.0, ChromaIntonationError.Severity.OK),
                error(0.2, 0.2, 0.0, ChromaIntonationError.Severity.OK)
        );

        List<ChromaIntonationSegment> segments =
                segmenter.segment(errors, 3);

        assertTrue(segments.isEmpty());
    }

    @Test
    void debeCrearSegmentoConTresFramesConsecutivos() {
        List<ChromaIntonationError> errors = List.of(
                error(0.0, 0.0, 21.0, ChromaIntonationError.Severity.ERROR),
                error(0.1, 0.1, 23.0, ChromaIntonationError.Severity.ERROR),
                error(0.2, 0.2, 25.0, ChromaIntonationError.Severity.SEVERE),
                error(0.3, 0.3, 0.0, ChromaIntonationError.Severity.OK)
        );

        List<ChromaIntonationSegment> segments =
                segmenter.segment(errors, 3);

        assertEquals(1, segments.size());

        ChromaIntonationSegment segment = segments.get(0);

        assertEquals(0.0, segment.startPerformanceTimestampSec());
        assertEquals(0.2, segment.endPerformanceTimestampSec());
        assertEquals(25.0, segment.maxDeviationCents());
        assertEquals(23.0, segment.meanDeviationCents(), 0.001);
        assertEquals(
                ChromaIntonationError.Severity.SEVERE,
                segment.severity()
        );
    }

    @Test
    void debeSepararSegmentosCuandoHayUnHuecoTemporal() {
        List<ChromaIntonationError> errors = List.of(
                error(0.0, 0.0, 22.0, ChromaIntonationError.Severity.ERROR),
                error(0.1, 0.1, 23.0, ChromaIntonationError.Severity.ERROR),
                error(0.2, 0.2, 24.0, ChromaIntonationError.Severity.ERROR),

                error(0.5, 0.5, 21.0, ChromaIntonationError.Severity.ERROR),
                error(0.6, 0.6, 22.0, ChromaIntonationError.Severity.ERROR),
                error(0.7, 0.7, 23.0, ChromaIntonationError.Severity.ERROR)
        );

        List<ChromaIntonationSegment> segments =
                segmenter.segment(errors, 3);

        assertEquals(2, segments.size());
    }

    @Test
    void debeConservarSignoEnPromedio() {
        List<ChromaIntonationError> errors = List.of(
                error(0.0, 0.0, -20.0, ChromaIntonationError.Severity.ERROR),
                error(0.1, 0.1, -10.0, ChromaIntonationError.Severity.WARNING),
                error(0.2, 0.2, -30.0, ChromaIntonationError.Severity.ERROR)
        );

        List<ChromaIntonationSegment> segments =
                segmenter.segment(errors, 3);

        assertEquals(1, segments.size());

        ChromaIntonationSegment segment = segments.get(0);

        assertEquals(30.0, segment.maxDeviationCents());
        assertEquals(-20.0, segment.meanDeviationCents(), 0.001);
    }

    private ChromaIntonationError error(
            double referenceTime,
            double performanceTime,
            double cents,
            ChromaIntonationError.Severity severity) {

        return new ChromaIntonationError(
                referenceTime,
                performanceTime,
                440.0,
                440.0,
                cents,
                severity
        );
    }
}
