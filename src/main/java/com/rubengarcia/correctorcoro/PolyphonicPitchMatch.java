package com.rubengarcia.correctorcoro;

public record PolyphonicPitchMatch(
        double referenceFrequencyHz,
        double performanceFrequencyHz,
        double deviationCents
) {
}
