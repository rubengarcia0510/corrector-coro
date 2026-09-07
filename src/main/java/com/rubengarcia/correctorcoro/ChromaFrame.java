package com.rubengarcia.correctorcoro;

public record ChromaFrame(
        double timestampSec,
        double[] chroma
) {
    public ChromaFrame {
        if (chroma == null || chroma.length != 12) {
            throw new IllegalArgumentException("Chroma must have 12 bins");
        }
    }
}
