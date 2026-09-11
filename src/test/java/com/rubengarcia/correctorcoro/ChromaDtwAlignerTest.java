package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void temporalPenaltyLambdaShouldBeAccepted() {
        ChromaDtwAligner aligner = new ChromaDtwAligner(0.5, 0.5);

        List<ChromaFrame> reference = List.of(
                frame(0.0, 0),
                frame(0.1, 4),
                frame(0.2, 7)
        );

        List<ChromaFrame> performance = List.of(
                frame(0.0, 0),
                frame(0.2, 4),
                frame(0.4, 7)
        );

        List<ChromaDtwAligner.ChromaAlignment> path =
                aligner.align(reference, performance);

        assertTrue(path.size() >= reference.size());
    }

    @Test
    void negativeTemporalPenaltyLambdaShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChromaDtwAligner(0.5, -0.1)
        );
    }

    private ChromaFrame frame(double timestamp, int pitchClass) {
        double[] chroma = new double[12];
        chroma[pitchClass] = 1.0;
        return new ChromaFrame(timestamp, chroma);
    }
}
