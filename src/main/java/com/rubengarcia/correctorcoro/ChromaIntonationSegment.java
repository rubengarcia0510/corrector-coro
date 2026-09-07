package com.rubengarcia.correctorcoro;

public record ChromaIntonationSegment(
        double startReferenceTimestampSec,
        double endReferenceTimestampSec,
        double startPerformanceTimestampSec,
        double endPerformanceTimestampSec,
        double maxDeviationCents,
        double meanDeviationCents,
        ChromaIntonationError.Severity severity
) {
}
