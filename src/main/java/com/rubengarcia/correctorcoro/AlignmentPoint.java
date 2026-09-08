package com.rubengarcia.correctorcoro;

public record AlignmentPoint(
        double referenceTimestampSec,
        double performanceTimestampSec,
        double distance
) {
}
