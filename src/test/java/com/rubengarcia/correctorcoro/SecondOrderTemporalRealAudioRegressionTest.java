package com.rubengarcia.correctorcoro;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class SecondOrderTemporalRealAudioRegressionTest {

    private static final double EXPECTED_FIRST_OFFSET = 0.850;
    private static final double EXPECTED_LAST_OFFSET = -1.750;
    private static final double OFFSET_TOLERANCE = 0.051;
    private static final double MAX_CHROMA_DISTANCE = 0.36;

    @Test
    void secondOrderTemporalAlignmentShouldRemainStableOnRealAudio()
            throws IOException {

        File referenceFile = findAudio("regina-ref-30s.wav");
        File performanceFile = findAudio("regina-flores-30s.wav");

        TarsosChromaExtractor extractor = new TarsosChromaExtractor();

        List<ChromaFrame> reference = extractor.extract(referenceFile);
        List<ChromaFrame> performance = extractor.extract(performanceFile);

        AlignmentResult result =
                new SecondOrderTemporalStrategy().align(
                        reference,
                        performance
                );

        List<AlignmentPoint> points = result.points();

        assertEquals(
                8,
                points.size(),
                "Unexpected number of temporal control points"
        );

        double firstOffset =
                points.get(0).performanceTimestampSec()
                        - points.get(0).referenceTimestampSec();

        AlignmentPoint lastPoint =
                points.get(points.size() - 1);

        double lastOffset =
                lastPoint.performanceTimestampSec()
                        - lastPoint.referenceTimestampSec();

        double secondOrderDistance =
                evaluateSecondOrder(reference, performance, points);

        log.info("========== SECOND ORDER REAL AUDIO REGRESSION ==========");
        log.info(
                "referenceFrames={} performanceFrames={} controlPoints={}",
                reference.size(),
                performance.size(),
                points.size()
        );
        log.info(
                "firstOffset={} lastOffset={}",
                String.format("%+.3f", firstOffset),
                String.format("%+.3f", lastOffset)
        );
        log.info(
                "secondOrderDistance={}",
                String.format("%.6f", secondOrderDistance)
        );
        log.info("========================================================");

        assertEquals(
                EXPECTED_FIRST_OFFSET,
                firstOffset,
                OFFSET_TOLERANCE,
                "Unexpected initial temporal offset"
        );

        assertEquals(
                EXPECTED_LAST_OFFSET,
                lastOffset,
                OFFSET_TOLERANCE,
                "Unexpected final temporal offset"
        );

        assertTrue(
                secondOrderDistance < MAX_CHROMA_DISTANCE,
                "Second-order Chroma distance regressed: "
                        + secondOrderDistance
        );
    }

    private double evaluateSecondOrder(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            List<AlignmentPoint> points) {

        double totalDistance = 0.0;
        int count = 0;

        for (ChromaFrame referenceFrame : reference) {

            double performanceTime =
                    interpolatePiecewise(
                            referenceFrame.timestampSec(),
                            points
                    );

            if (performanceTime < 0.0) {
                continue;
            }

            ChromaFrame nearest =
                    nearestFrame(performance, performanceTime);

            totalDistance += cosineDistance(
                    referenceFrame.chroma(),
                    nearest.chroma()
            );

            count++;
        }

        return totalDistance / count;
    }

    private double interpolatePiecewise(
            double time,
            List<AlignmentPoint> points) {

        if (time <= points.get(0).referenceTimestampSec()) {
            return points.get(0).performanceTimestampSec();
        }

        for (int i = 1; i < points.size(); i++) {

            AlignmentPoint previous = points.get(i - 1);
            AlignmentPoint current = points.get(i);

            if (time <= current.referenceTimestampSec()) {

                double x1 = previous.referenceTimestampSec();
                double x2 = current.referenceTimestampSec();
                double y1 = previous.performanceTimestampSec();
                double y2 = current.performanceTimestampSec();

                double ratio = (time - x1) / (x2 - x1);

                return y1 + ratio * (y2 - y1);
            }
        }

        AlignmentPoint last = points.get(points.size() - 1);
        AlignmentPoint previous = points.get(points.size() - 2);

        double slope =
                (last.performanceTimestampSec()
                        - previous.performanceTimestampSec())
                        /
                        (last.referenceTimestampSec()
                                - previous.referenceTimestampSec());

        return last.performanceTimestampSec()
                + slope
                * (time - last.referenceTimestampSec());
    }

    private ChromaFrame nearestFrame(
            List<ChromaFrame> frames,
            double targetTime) {

        ChromaFrame best = frames.get(0);

        double bestDifference =
                Math.abs(best.timestampSec() - targetTime);

        for (int i = 1; i < frames.size(); i++) {

            ChromaFrame frame = frames.get(i);

            double difference =
                    Math.abs(frame.timestampSec() - targetTime);

            if (difference < bestDifference) {
                best = frame;
                bestDifference = difference;
            }
        }

        return best;
    }

    private double cosineDistance(double[] a, double[] b) {

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 1.0;
        }

        return 1.0 -
                dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private File findAudio(String fileName) throws IOException {

        Path root = Path.of(".")
                .toAbsolutePath()
                .normalize();

        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path ->
                        path.getFileName()
                                .toString()
                                .equals(fileName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Audio file not found: " + fileName))
                .toFile();
    }
}
