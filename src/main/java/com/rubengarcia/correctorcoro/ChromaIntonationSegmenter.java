package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChromaIntonationSegmenter {

    public List<ChromaIntonationSegment> segment(
            List<ChromaIntonationError> errors,
            int minErrorFrames) {

        if (errors == null || errors.isEmpty()) {
            return List.of();
        }

        if (minErrorFrames < 1) {
            throw new IllegalArgumentException("minErrorFrames must be >= 1");
        }

        List<ChromaIntonationSegment> segments = new ArrayList<>();

        List<ChromaIntonationError> current = new ArrayList<>();

        for (ChromaIntonationError error : errors) {

            if (error.severity() == ChromaIntonationError.Severity.OK) {
                addSegmentIfValid(segments, current, minErrorFrames);
                current.clear();
                continue;
            }

            if (!current.isEmpty()) {
                ChromaIntonationError previous = current.get(current.size() - 1);

                double gap = error.performanceTimestampSec()
                        - previous.performanceTimestampSec();

                if (gap > 0.10) {
                    addSegmentIfValid(segments, current, minErrorFrames);
                    current.clear();
                }
            }

            current.add(error);
        }

        addSegmentIfValid(segments, current, minErrorFrames);

        return segments;
    }

    private void addSegmentIfValid(
            List<ChromaIntonationSegment> segments,
            List<ChromaIntonationError> frames,
            int minErrorFrames) {

        if (frames.size() < minErrorFrames) {
            return;
        }

        double startReference = frames.get(0).referenceTimestampSec();
        double endReference = frames.get(frames.size() - 1).referenceTimestampSec();

        double startPerformance = frames.get(0).performanceTimestampSec();
        double endPerformance = frames.get(frames.size() - 1).performanceTimestampSec();

        double maxDeviation = 0.0;
        double sumDeviation = 0.0;

        ChromaIntonationError.Severity severity =
                ChromaIntonationError.Severity.WARNING;

        for (ChromaIntonationError frame : frames) {
            double absDeviation = Math.abs(frame.deviationCents());

            maxDeviation = Math.max(maxDeviation, absDeviation);
            sumDeviation += frame.deviationCents();

            if (frame.severity().ordinal() > severity.ordinal()) {
                severity = frame.severity();
            }
        }

        double meanDeviation = sumDeviation / frames.size();

        segments.add(new ChromaIntonationSegment(
                startReference,
                endReference,
                startPerformance,
                endPerformance,
                maxDeviation,
                meanDeviation,
                severity
        ));
    }
}
