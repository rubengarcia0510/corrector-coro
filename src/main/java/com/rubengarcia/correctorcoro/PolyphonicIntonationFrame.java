package com.rubengarcia.correctorcoro;

import java.util.List;

public record PolyphonicIntonationFrame(
        double referenceTimestampSec,
        double performanceTimestampSec,
        List<PolyphonicPitchMatch> matches
) {
}
