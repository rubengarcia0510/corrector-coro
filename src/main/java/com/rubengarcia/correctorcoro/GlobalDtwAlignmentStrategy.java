package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GlobalDtwAlignmentStrategy implements AlignmentStrategy {

    private final ChromaDtwAligner chromaDtwAligner;

    public GlobalDtwAlignmentStrategy(ChromaDtwAligner chromaDtwAligner) {
        this.chromaDtwAligner = chromaDtwAligner;
    }

    @Override
    public AlignmentResult align(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                chromaDtwAligner.align(reference, performance);

        List<AlignmentPoint> points = alignments.stream()
                .map(alignment -> new AlignmentPoint(
                        alignment.referenceTimestampSec(),
                        alignment.performanceTimestampSec(),
                        alignment.distance()
                ))
                .toList();

        return new AlignmentResult(points);
    }
}
