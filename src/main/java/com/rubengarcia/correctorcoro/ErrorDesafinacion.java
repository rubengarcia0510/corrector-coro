package com.rubengarcia.correctorcoro;

public record ErrorDesafinacion(
        double timestampEnsayoSec,
        float pitchReferenciaHz,
        float pitchEnsayoHz,
        double diferenciaCents
) {}
