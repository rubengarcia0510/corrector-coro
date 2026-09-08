package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class ChromaDtwAligner {

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

        double[][] cost = new double[n + 1][m + 1];

        for (double[] row : cost) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }

        cost[0][0] = 0.0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {

                double distance = cosineDistance(
                        reference.get(i - 1).chroma(),
                        performance.get(j - 1).chroma()
                );

                cost[i][j] = distance + Math.min(
                        cost[i - 1][j],
                        Math.min(
                                cost[i][j - 1],
                                cost[i - 1][j - 1]
                        )
                );
            }
        }

        List<ChromaAlignment> path = new ArrayList<>();

        int i = n;
        int j = m;

        while (i > 0 && j > 0) {

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

                path.add(new ChromaAlignment(
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

        if (a.length != 12 || b.length != 12) {
            throw new IllegalArgumentException("Chroma vectors must have 12 bins");
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
