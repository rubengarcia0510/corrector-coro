package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ChromaDtwAligner {

    /*
     * DTW band width in seconds.
     *
     * A broad band keeps the implementation robust against moderate
     * tempo differences while avoiding the O(n*m) memory consumption
     * of a full DTW cost matrix.
     */
    private static final double BAND_SECONDS = 4.0;

    private static final byte DIAGONAL = 0;
    private static final byte UP = 1;
    private static final byte LEFT = 2;

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

        double referenceStep = estimateFrameStep(reference);
        double performanceStep = estimateFrameStep(performance);

        double bandSeconds = Math.max(
                BAND_SECONDS,
                Math.max(referenceStep, performanceStep) * 2.0
        );

        int radiusReference = Math.max(
                1,
                (int) Math.ceil(bandSeconds / referenceStep)
        );

        int radiusPerformance = Math.max(
                1,
                (int) Math.ceil(bandSeconds / performanceStep)
        );

        /*
         * The band is centered on a linear mapping between the two
         * sequence timelines. This allows different total durations
         * without requiring the full n*m matrix.
         */
        double referenceDuration = reference.get(n - 1).timestampSec()
                - reference.get(0).timestampSec();

        double performanceDuration = performance.get(m - 1).timestampSec()
                - performance.get(0).timestampSec();

        double referenceStart = reference.get(0).timestampSec();
        double performanceStart = performance.get(0).timestampSec();

        double durationScale = referenceDuration > 0.0
                ? performanceDuration / referenceDuration
                : 1.0;

        double[] previousCosts = new double[m + 1];
        double[] currentCosts = new double[m + 1];

        fillInfinity(previousCosts);
        previousCosts[0] = 0.0;

        /*
         * Backpointers are stored only inside the band.
         *
         * This is dramatically smaller than double[n+1][m+1].
         */
        byte[][] directions = new byte[n + 1][];

        int[][] bandStarts = new int[n + 1][];
        int[][] bandEnds = new int[n + 1][];

        for (int i = 1; i <= n; i++) {
            fillInfinity(currentCosts);

            double referenceTime = reference.get(i - 1).timestampSec();
            double elapsedReference = referenceTime - referenceStart;

            double predictedPerformanceTime =
                    performanceStart + elapsedReference * durationScale;

            int center = findClosestFrameIndex(
                    performance,
                    predictedPerformanceTime
            ) + 1;

            int start = Math.max(1, center - radiusPerformance);
            int end = Math.min(m, center + radiusPerformance);

            /*
             * Ensure the start/end cells remain reachable.
             */
            if (i == 1) {
                start = 1;
            }

            if (i == n) {
                end = m;
            }

            bandStarts[i] = new int[]{start};
            bandEnds[i] = new int[]{end};
            directions[i] = new byte[end - start + 1];

            for (int j = start; j <= end; j++) {
                double distance = cosineDistance(
                        reference.get(i - 1).chroma(),
                        performance.get(j - 1).chroma()
                );

                double diagonal = previousCosts[j - 1];
                double up = previousCosts[j];
                double left = currentCosts[j - 1];

                double best;
                byte direction;

                if (diagonal <= up && diagonal <= left) {
                    best = diagonal;
                    direction = DIAGONAL;
                } else if (up <= left) {
                    best = up;
                    direction = UP;
                } else {
                    best = left;
                    direction = LEFT;
                }

                currentCosts[j] = distance + best;
                directions[i][j - start] = direction;
            }

            double[] swap = previousCosts;
            previousCosts = currentCosts;
            currentCosts = swap;
        }

        if (Double.isInfinite(previousCosts[m])) {
            throw new IllegalStateException(
                    "DTW endpoint is outside the constrained band"
            );
        }

        List<ChromaAlignment> path = new ArrayList<>();

        int i = n;
        int j = m;

        while (i > 0 && j > 0) {
            int start = bandStarts[i][0];
            int end = bandEnds[i][0];

            if (j < start || j > end) {
                throw new IllegalStateException(
                        "DTW backtracking left the constrained band"
                );
            }

            byte direction = directions[i][j - start];

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

            if (direction == DIAGONAL) {
                i--;
                j--;
            } else if (direction == UP) {
                i--;
            } else if (direction == LEFT) {
                j--;
            } else {
                throw new IllegalStateException(
                        "Invalid DTW backpointer"
                );
            }
        }

        Collections.reverse(path);
        return path;
    }

    private double estimateFrameStep(List<ChromaFrame> frames) {
        if (frames.size() < 2) {
            return 1.0;
        }

        double total = 0.0;
        int count = 0;

        for (int i = 1; i < frames.size(); i++) {
            double delta =
                    frames.get(i).timestampSec()
                            - frames.get(i - 1).timestampSec();

            if (delta > 0.0) {
                total += delta;
                count++;
            }
        }

        return count == 0 ? 1.0 : total / count;
    }

    private int findClosestFrameIndex(
            List<ChromaFrame> frames,
            double timestampSec) {

        int low = 0;
        int high = frames.size() - 1;

        while (low < high) {
            int mid = (low + high) >>> 1;

            if (frames.get(mid).timestampSec() < timestampSec) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        if (low == 0) {
            return 0;
        }

        double currentDistance =
                Math.abs(frames.get(low).timestampSec() - timestampSec);

        double previousDistance =
                Math.abs(frames.get(low - 1).timestampSec() - timestampSec);

        return previousDistance <= currentDistance
                ? low - 1
                : low;
    }

    private void fillInfinity(double[] values) {
        java.util.Arrays.fill(values, Double.POSITIVE_INFINITY);
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
