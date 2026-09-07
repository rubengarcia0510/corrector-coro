package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaDtwAlignerTest {

    @Test
    void identicalSequencesShouldHaveZeroDistance() {
        ChromaDtwAligner aligner = new ChromaDtwAligner();

        List<ChromaFrame> reference = List.of(
                frame(0.0, 0),
                frame(0.1, 4),
                frame(0.2, 7),
                frame(0.3, 0)
        );

        List<ChromaFrame> performance = List.of(
                frame(0.0, 0),
                frame(0.1, 4),
                frame(0.2, 7),
                frame(0.3, 0)
        );

        List<ChromaDtwAligner.ChromaAlignment> path =
                aligner.align(reference, performance);

        assertEquals(4, path.size());

        for (ChromaDtwAligner.ChromaAlignment alignment : path) {
            assertEquals(0.0, alignment.distance(), 1e-10);
        }
    }

    @Test
    void slowerPerformanceShouldStillAlignWithReference() {
        ChromaDtwAligner aligner = new ChromaDtwAligner();

        List<ChromaFrame> reference = List.of(
                frame(0.0, 0),
                frame(0.1, 0),
                frame(0.2, 4),
                frame(0.3, 4),
                frame(0.4, 7),
                frame(0.5, 7),
                frame(0.6, 0)
        );

        List<ChromaFrame> performance = List.of(
                frame(0.0, 0),
                frame(0.1, 0),
                frame(0.2, 0),
                frame(0.3, 4),
                frame(0.4, 4),
                frame(0.5, 4),
                frame(0.6, 7),
                frame(0.7, 7),
                frame(0.8, 7),
                frame(0.9, 0)
        );

        List<ChromaDtwAligner.ChromaAlignment> path =
                aligner.align(reference, performance);

        assertTrue(path.size() >= reference.size());

        double averageDistance = path.stream()
                .mapToDouble(ChromaDtwAligner.ChromaAlignment::distance)
                .average()
                .orElse(1.0);

        assertTrue(
                averageDistance < 0.05,
                "Expected good alignment, average distance was " + averageDistance
        );
    }

    private ChromaFrame frame(double timestamp, int pitchClass) {
        double[] chroma = new double[12];
        chroma[pitchClass] = 1.0;
        return new ChromaFrame(timestamp, chroma);
    }
}
