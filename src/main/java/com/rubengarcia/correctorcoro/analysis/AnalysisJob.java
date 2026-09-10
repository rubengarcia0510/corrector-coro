package com.rubengarcia.correctorcoro.analysis;

import java.util.List;

import com.rubengarcia.correctorcoro.ChromaIntonationSegment;

public record AnalysisJob(
        String jobId,
        AnalysisStatus status,
        List<ChromaIntonationSegment> segments,
        String errorMessage
) {
}
