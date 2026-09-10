package com.rubengarcia.correctorcoro.analysis;

import java.util.List;

import com.rubengarcia.correctorcoro.ChromaIntonationSegment;

public record AnalysisResultResponse(
        String jobId,
        AnalysisStatus status,
        List<ChromaIntonationSegment> segments
) {
}
