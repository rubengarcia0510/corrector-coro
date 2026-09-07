package com.rubengarcia.correctorcoro;

public record ChromaIntonationError(
        double referenceTimestampSec,
        double performanceTimestampSec,
        double referencePitchHz,
        double performancePitchHz,
        double deviationCents,
        Severity severity
) {

    public enum Severity {
        OK,
        WARNING,
        ERROR,
        SEVERE
    }
}
