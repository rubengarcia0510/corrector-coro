package com.rubengarcia.correctorcoro;

/**
 * Un punto de la curva de pitch: instante de tiempo (segundos) y
 * frecuencia detectada en Hz. pitchHz puede ser -1 cuando TarsosDSP
 * no logro detectar un pitch confiable en ese frame (silencio, ruido).
 */
public record PitchPoint(double timestampSec, float pitchHz) {

    public boolean esValido() {
        return pitchHz > 0;
    }
}
