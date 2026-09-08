package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

class ConstrainedChromaDtwRealAudioTest {

    @Test
    void characterizeConstrainedDtwOnRealAudio() {
        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        TarsosChromaExtractor extractor = new TarsosChromaExtractor();

        List<ChromaFrame> reference = extractor.extract(referenceFile);
        List<ChromaFrame> performance = extractor.extract(performanceFile);

        double frameStepSec = reference.size() > 1
                ? reference.get(1).timestampSec() - reference.get(0).timestampSec()
                : 0.0;

        System.out.printf(
                Locale.US,
                "frames reference=%d performance=%d frameStep=%.5fs%n",
                reference.size(),
                performance.size(),
                frameStepSec
        );

        double[] bandsSec = {0.25, 0.50, 1.00, 1.50};

        System.out.println("---- CONSTRAINED DTW REAL AUDIO ----");

        for (double bandSec : bandsSec) {
            int radius = Math.max(
                    1,
                    (int) Math.round(bandSec / frameStepSec)
            );

            List<ChromaDtwAligner.ChromaAlignment> alignments =
                    alignWithBand(reference, performance, radius);

            int within60ms = 0;
            double totalAbsOffset = 0.0;
            double maxAbsOffset = 0.0;
            double totalDistance = 0.0;

            for (ChromaDtwAligner.ChromaAlignment alignment : alignments) {
                double offset = Math.abs(
                        alignment.performanceTimestampSec()
                                - alignment.referenceTimestampSec()
                );

                totalAbsOffset += offset;
                maxAbsOffset = Math.max(maxAbsOffset, offset);
                totalDistance += alignment.distance();

                if (offset <= 0.060) {
                    within60ms++;
                }
            }

            double avgAbsOffset = alignments.isEmpty()
                    ? 0.0
                    : totalAbsOffset / alignments.size();

            double avgDistance = alignments.isEmpty()
                    ? 0.0
                    : totalDistance / alignments.size();

            double within60Percent = alignments.isEmpty()
                    ? 0.0
                    : 100.0 * within60ms / alignments.size();

            System.out.printf(
                    Locale.US,
                    "band=%.2fs radius=%d alignments=%d "
                            + "within60ms=%d (%.1f%%) "
                            + "avgAbsOffset=%.3fs "
                            + "maxAbsOffset=%.3fs "
                            + "avgDistance=%.4f%n",
                    bandSec,
                    radius,
                    alignments.size(),
                    within60ms,
                    within60Percent,
                    avgAbsOffset,
                    maxAbsOffset,
                    avgDistance
            );
        }
    }

    private List<ChromaDtwAligner.ChromaAlignment> alignWithBand(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            int radius
    ) {
        int n = reference.size();
        int m = performance.size();
        double infinity = Double.POSITIVE_INFINITY;

        double[][] cost = new double[n + 1][m + 1];

        for (double[] row : cost) {
            Arrays.fill(row, infinity);
        }

        cost[0][0] = 0.0;

        for (int i = 1; i <= n; i++) {
            int jStart = Math.max(1, i - radius);
            int jEnd = Math.min(m, i + radius);

            for (int j = jStart; j <= jEnd; j++) {
                double distance = cosineDistance(
                        reference.get(i - 1).chroma(),
                        performance.get(j - 1).chroma()
                );

                cost[i][j] = distance + Math.min(
                        cost[i - 1][j - 1],
                        Math.min(
                                cost[i - 1][j],
                                cost[i][j - 1]
                        )
                );
            }
        }

        List<ChromaDtwAligner.ChromaAlignment> path =
                new ArrayList<>();

        int i = n;
        int j = m;

        while (i > 0 || j > 0) {
            if (i == 0) {
                j--;
                continue;
            }

            if (j == 0) {
                i--;
                continue;
            }

            double diagonal = cost[i - 1][j - 1];
            double up = cost[i - 1][j];
            double left = cost[i][j - 1];

            if (diagonal <= up && diagonal <= left) {
                ChromaFrame referenceFrame = reference.get(i - 1);
                ChromaFrame performanceFrame = performance.get(j - 1);

                double distance = cosineDistance(
                        referenceFrame.chroma(),
                        performanceFrame.chroma()
                );

                path.add(new ChromaDtwAligner.ChromaAlignment(
                        referenceFrame.timestampSec(),
                        performanceFrame.timestampSec(),
                        distance
                ));

                i--;
                j--;
            } else if (up <= left) {
                i--;
            } else {
                j--;
            }
        }

        Collections.reverse(path);
        return path;
    }

    private double cosineDistance(double[] a, double[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        int length = Math.min(a.length, b.length);

        for (int i = 0; i < length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 1.0;
        }

        return 1.0 - dot / (
                Math.sqrt(normA) * Math.sqrt(normB)
        );
    }
}
