package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolyphonicPitchMatcherTest {

    @Test
    void debeEmparejarLasTresVocesDesafinadas() {

        List<SpectralPitch> referencia =
                List.of(
                        new SpectralPitch(261.04, 157.3),
                        new SpectralPitch(330.15, 151.4),
                        new SpectralPitch(391.57, 149.3)
                );

        List<SpectralPitch> ensayo =
                List.of(
                        new SpectralPitch(268.08, 157.3),
                        new SpectralPitch(336.78, 151.4),
                        new SpectralPitch(400.61, 149.3)
                );

        PolyphonicPitchMatcher matcher =
                new PolyphonicPitchMatcher();

        List<PolyphonicPitchMatch> matches =
                matcher.match(
                        referencia,
                        ensayo
                );

        assertEquals(
                3,
                matches.size()
        );

        System.out.println(
                "========== MATCHES =========="
        );

        matches.forEach(match ->
                System.out.printf(
                        "%.2f Hz -> %.2f Hz = %.2f cents%n",
                        match.referenceFrequencyHz(),
                        match.performanceFrequencyHz(),
                        match.deviationCents()
                )
        );

        System.out.println(
                "============================="
        );

        for (PolyphonicPitchMatch match : matches) {

            assertEquals(
                    40.0,
                    match.deviationCents(),
                    8.0
            );
        }
    }

    @Test
    void noDebeEmparejarUnaNotaFueraDeRango() {

        List<SpectralPitch> referencia =
                List.of(
                        new SpectralPitch(261.04, 100.0)
                );

        List<SpectralPitch> ensayo =
                List.of(
                        new SpectralPitch(300.0, 100.0)
                );

        PolyphonicPitchMatcher matcher =
                new PolyphonicPitchMatcher();

        List<PolyphonicPitchMatch> matches =
                matcher.match(
                        referencia,
                        ensayo
                );

        assertTrue(matches.isEmpty());
    }
}
