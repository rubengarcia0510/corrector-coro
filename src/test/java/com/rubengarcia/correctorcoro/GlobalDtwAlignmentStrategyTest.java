package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalDtwAlignmentStrategyTest {

    @Test
    void shouldReturnAlignmentPointsFromGlobalDtw() {
        ChromaDtwAligner aligner = new ChromaDtwAligner();
        GlobalDtwAlignmentStrategy strategy =
                new GlobalDtwAlignmentStrategy(aligner);

        ChromaFrame reference = frame(1.0, 0);
        ChromaFrame performance = frame(1.2, 0);

        AlignmentResult result = strategy.align(
                List.of(reference),
                List.of(performance)
        );

        assertNotNull(result);
        assertEquals(1, result.points().size());

        AlignmentPoint point = result.points().get(0);

        assertEquals(1.0, point.referenceTimestampSec());
        assertEquals(1.2, point.performanceTimestampSec());
        assertEquals(0.0, point.distance(), 1e-9);
    }

    private ChromaFrame frame(double timestamp, int pitchClass) {
        double[] chroma = new double[12];
        chroma[pitchClass] = 1.0;
        return new ChromaFrame(timestamp, chroma);
    }
}
