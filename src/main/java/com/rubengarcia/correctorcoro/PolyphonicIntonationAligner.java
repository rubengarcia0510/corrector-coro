package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolyphonicIntonationAligner {

    private static final double MAX_TIMESTAMP_DISTANCE_SEC = 0.060;

    private final PolyphonicPitchMatcher pitchMatcher;

    public PolyphonicIntonationAligner(
            PolyphonicPitchMatcher pitchMatcher) {

        this.pitchMatcher = pitchMatcher;
    }

    public List<PolyphonicIntonationFrame> align(
            List<ChromaDtwAligner.ChromaAlignment> chromaAlignments,
            List<SpectralFrame> referenceFrames,
            List<SpectralFrame> performanceFrames) {

        if (chromaAlignments == null ||
                referenceFrames == null ||
                performanceFrames == null ||
                chromaAlignments.isEmpty() ||
                referenceFrames.isEmpty() ||
                performanceFrames.isEmpty()) {

            return List.of();
        }

        List<PolyphonicIntonationFrame> result =
                new ArrayList<>();

        for (ChromaDtwAligner.ChromaAlignment alignment
                : chromaAlignments) {

            SpectralFrame referenceFrame =
                    findNearest(
                            referenceFrames,
                            alignment.referenceTimestampSec()
                    );

            SpectralFrame performanceFrame =
                    findNearest(
                            performanceFrames,
                            alignment.performanceTimestampSec()
                    );

            if (referenceFrame == null ||
                    performanceFrame == null) {
                continue;
            }

            List<PolyphonicPitchMatch> matches =
                    pitchMatcher.match(
                            referenceFrame.peaks(),
                            performanceFrame.peaks()
                    );

            if (matches.isEmpty()) {
                continue;
            }

            result.add(
                    new PolyphonicIntonationFrame(
                            alignment.referenceTimestampSec(),
                            alignment.performanceTimestampSec(),
                            matches
                    )
            );
        }

        return result;
    }

    private SpectralFrame findNearest(
            List<SpectralFrame> frames,
            double timestampSec) {

        SpectralFrame best = null;
        double bestDistance = Double.MAX_VALUE;

        for (SpectralFrame frame : frames) {

            double distance =
                    Math.abs(
                            frame.timestampSec()
                                    - timestampSec
                    );

            if (distance < bestDistance) {
                bestDistance = distance;
                best = frame;
            }
        }

        if (bestDistance >
                MAX_TIMESTAMP_DISTANCE_SEC) {

            return null;
        }

        return best;
    }
}
