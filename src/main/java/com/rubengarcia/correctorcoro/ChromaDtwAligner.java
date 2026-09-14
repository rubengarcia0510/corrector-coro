package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class ChromaDtwAligner {

    /*
     * Bounded DTW band.
     *
     * The full DTW matrix requires O(n * m) memory and caused OOM on Render.
     * A 2-second temporal band keeps memory bounded while allowing the
     * initial timing offset observed in real rehearsal recordings.
     */
    private static final double MAX_BAND_SECONDS = 2.0;
    private static final double MIN_FRAME_STEP_SECONDS = 1.0e-6;

    public List<ChromaAlignment> align(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance) {

        if (reference == null || performance == null) {
            throw new IllegalArgumentException("Chroma sequences cannot be null");
        }

        if (reference.isEmpty() || performance.isEmpty()) {
            return Collections.emptyList();
        }

        int n = reference.size();
        int m = performance.size();

        double frameStep = estimateFrameStep(reference, performance);

        int radius = Math.max(
                1,
                (int) Math.ceil(MAX_BAND_SECONDS / frameStep)
        );

        radius = Math.min(radius, Math.max(n, m));

        int bandWidth = 2 * radius + 1;

        double[][] costs = new double[n + 1][bandWidth];

        for (double[] row : costs) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }

        costs[0][radius] = 0.0;

        for (int i = 1; i <= n; i++) {
            int center = centerForRow(i, n, m);
            int start = Math.max(1, center - radius);
            int end = Math.min(m, center + radius);

            for (int j = start; j <= end; j++) {
                double distance = cosineDistance(
                        reference.get(i - 1).chroma(),
                        performance.get(j - 1).chroma()
                );

                double diagonal = costAt(
                        costs,
                        i - 1,
                        j - 1,
                        n,
                        m,
                        radius
                );

                double up = costAt(
                        costs,
                        i - 1,
                        j,
                        n,
                        m,
                        radius
                );

                double left = costAt(
                        costs,
                        i,
                        j - 1,
                        n,
                        m,
                        radius
                );

                double previous = Math.min(
                        diagonal,
                        Math.min(up, left)
                );

                if (Double.isFinite(previous)) {
                    setCost(
                            costs,
                            i,
                            j,
                            n,
                            m,
                            radius,
                            distance + previous
                    );
                }
            }
        }

        double finalCost = costAt(
                costs,
                n,
                m,
                n,
                m,
                radius
        );

        if (!Double.isFinite(finalCost)) {
            throw new IllegalStateException(
                    "DTW endpoint is outside the configured temporal band"
            );
        }

        List<ChromaAlignment> path = new ArrayList<>();

        int i = n;
        int j = m;

        while (i > 0 && j > 0) {
            ChromaFrame referenceFrame = reference.get(i - 1);
            ChromaFrame performanceFrame = performance.get(j - 1);

            double distance = cosineDistance(
                    referenceFrame.chroma(),
                    performanceFrame.chroma()
            );

            path.add(new ChromaAlignment(
                    referenceFrame.timestampSec(),
                    performanceFrame.timestampSec(),
                    distance
            ));

            double diagonal = costAt(
                    costs,
                    i - 1,
                    j - 1,
                    n,
                    m,
                    radius
            );

            double up = costAt(
                    costs,
                    i - 1,
                    j,
                    n,
                    m,
                    radius
            );

            double left = costAt(
                    costs,
                    i,
                    j - 1,
                    n,
                    m,
                    radius
            );

            if (diagonal <= up && diagonal <= left) {
                i--;
                j--;
            } else if (up <= left) {
                i--;
            } else {
                j--;
            }
        }

        if (i != 0 || j != 0) {
            throw new IllegalStateException(
                    "DTW backtracking could not reach the origin"
            );
        }

        Collections.reverse(path);
        return path;
    }

    private int centerForRow(int i, int n, int m) {
        return (int) Math.round(i * (m / (double) n));
    }

    private double costAt(
            double[][] costs,
            int i,
            int j,
            int n,
            int m,
            int radius
    ) {
        if (i < 0 || i > n || j < 0 || j > m) {
            return Double.POSITIVE_INFINITY;
        }

        int center = centerForRow(i, n, m);
        int offset = j - center;

        if (offset < -radius || offset > radius) {
            return Double.POSITIVE_INFINITY;
        }

        return costs[i][offset + radius];
    }

    private void setCost(
            double[][] costs,
            int i,
            int j,
            int n,
            int m,
            int radius,
            double value
    ) {
        int center = centerForRow(i, n, m);
        int offset = j - center;

        if (offset < -radius || offset > radius) {
            return;
        }

        costs[i][offset + radius] = value;
    }

    private double estimateFrameStep(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance
    ) {
        double referenceStep = estimateFrameStep(reference);
        double performanceStep = estimateFrameStep(performance);

        double step = Math.min(referenceStep, performanceStep);

        if (!Double.isFinite(step) || step <= MIN_FRAME_STEP_SECONDS) {
            return MAX_BAND_SECONDS;
        }

        return step;
    }

    private double estimateFrameStep(List<ChromaFrame> frames) {
        if (frames.size() < 2) {
            return MAX_BAND_SECONDS;
        }

        double step =
                frames.get(1).timestampSec()
                        - frames.get(0).timestampSec();

        if (!Double.isFinite(step) || step <= MIN_FRAME_STEP_SECONDS) {
            return MAX_BAND_SECONDS;
        }

        return step;
    }

    private double cosineDistance(double[] a, double[] b) {
        if (a.length != 12 || b.length != 12) {
            throw new IllegalArgumentException(
                    "Chroma vectors must have 12 bins"
            );
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < 12; i++) {
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

    public record ChromaAlignment(
            double referenceTimestampSec,
            double performanceTimestampSec,
            double distance
    ) {
    }
}
