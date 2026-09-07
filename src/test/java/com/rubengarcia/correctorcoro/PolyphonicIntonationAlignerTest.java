package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolyphonicIntonationAlignerTest {

    @Test
    void debeCombinarAlineacionChromaConEspectro() {

        List<SpectralFrame> referenceFrames =
                List.of(
                        new SpectralFrame(
                                1.00,
                                List.of(
                                        new SpectralPitch(261.04, 150),
                                        new SpectralPitch(330.15, 140),
                                        new SpectralPitch(391.57, 130)
                                )
                        )
                );

        List<SpectralFrame> performanceFrames =
                List.of(
                        new SpectralFrame(
                                1.02,
                                List.of(
                                        new SpectralPitch(268.08, 150),
                                        new SpectralPitch(336.78, 140),
                                        new SpectralPitch(400.61, 130)
                                )
                        )
                );

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                List.of(
                        new ChromaDtwAligner.ChromaAlignment(
                                1.00,
                                1.02,
                                0.01
                        )
                );

        PolyphonicIntonationAligner aligner =
                new PolyphonicIntonationAligner(
                        new PolyphonicPitchMatcher()
                );

        List<PolyphonicIntonationFrame> result =
                aligner.align(
                        alignments,
                        referenceFrames,
                        performanceFrames
                );

        assertEquals(1, result.size());

        PolyphonicIntonationFrame frame =
                result.get(0);

        assertEquals(
                3,
                frame.matches().size()
        );

        System.out.println(
                "========== ALIGNED POLYPHONIC FRAME =========="
        );

        frame.matches().forEach(match ->
                System.out.printf(
                        "%.2f Hz -> %.2f Hz = %.2f cents%n",
                        match.referenceFrequencyHz(),
                        match.performanceFrequencyHz(),
                        match.deviationCents()
                )
        );

        System.out.println(
                "==============================================="
        );

        for (PolyphonicPitchMatch match :
                frame.matches()) {

            assertEquals(
                    40.0,
                    match.deviationCents(),
                    8.0
            );
        }
    }

    @Test
    void debeIgnorarFramesDemasiadoLejanos() {

        List<SpectralFrame> referenceFrames =
                List.of(
                        new SpectralFrame(
                                1.00,
                                List.of(
                                        new SpectralPitch(
                                                261.04,
                                                100
                                        )
                                )
                        )
                );

        List<SpectralFrame> performanceFrames =
                List.of(
                        new SpectralFrame(
                                1.00,
                                List.of(
                                        new SpectralPitch(
                                                268.08,
                                                100
                                        )
                                )
                        )
                );

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                List.of(
                        new ChromaDtwAligner.ChromaAlignment(
                                5.00,
                                5.00,
                                0.01
                        )
                );

        PolyphonicIntonationAligner aligner =
                new PolyphonicIntonationAligner(
                        new PolyphonicPitchMatcher()
                );

        List<PolyphonicIntonationFrame> result =
                aligner.align(
                        alignments,
                        referenceFrames,
                        performanceFrames
                );

        assertTrue(result.isEmpty());
    }
}
