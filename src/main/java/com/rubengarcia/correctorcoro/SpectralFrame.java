package com.rubengarcia.correctorcoro;

import java.util.List;

public record SpectralFrame(
        double timestampSec,
        List<SpectralPitch> peaks
) {
}
