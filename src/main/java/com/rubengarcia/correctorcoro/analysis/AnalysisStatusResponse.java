package com.rubengarcia.correctorcoro.analysis;

public record AnalysisStatusResponse(
        String jobId,
        AnalysisStatus status
) {
}
