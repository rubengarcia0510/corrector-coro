package com.rubengarcia.correctorcoro;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

class CombinedChromaEnergyDtwRealAudioTest {

    private static final int BUFFER_SIZE = 4096;
    private static final int OVERLAP = 3072;

    private static final double ENERGY_WEIGHT = 0.30;
    private static final double CHROMA_WEIGHT = 0.70;

    @Test
    void characterizeCombinedChromaEnergyDtw() throws Exception {

        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        TarsosChromaExtractor chromaExtractor =
                new TarsosChromaExtractor();

        List<ChromaFrame> referenceChroma =
                chromaExtractor.extract(referenceFile);

        List<ChromaFrame> performanceChroma =
                chromaExtractor.extract(performanceFile);

        List<Double> referenceEnergy =
                extractEnergy(referenceFile);

        List<Double> performanceEnergy =
                extractEnergy(performanceFile);

        int n = Math.min(
                referenceChroma.size(),
                referenceEnergy.size()
        );

        int m = Math.min(
                performanceChroma.size(),
                performanceEnergy.size()
        );

        double[] refEnergy =
                normalizeEnergy(referenceEnergy, n);

        double[] perfEnergy =
                normalizeEnergy(performanceEnergy, m);

        Result result =
                align(
                        referenceChroma,
                        performanceChroma,
                        refEnergy,
                        perfEnergy
                );

        double totalAbsOffset = 0.0;
        double maxAbsOffset = 0.0;
        int within60ms = 0;

        for (Alignment alignment : result.alignments()) {

            double offset =
                    alignment.performanceTime()
                            - alignment.referenceTime();

            double absOffset = Math.abs(offset);

            totalAbsOffset += absOffset;
            maxAbsOffset =
                    Math.max(maxAbsOffset, absOffset);

            if (absOffset <= 0.060) {
                within60ms++;
            }
        }

        double averageAbsOffset =
                result.alignments().isEmpty()
                        ? 0.0
                        : totalAbsOffset
                        / result.alignments().size();

        System.out.println(
                "---- COMBINED CHROMA + ENERGY DTW ----"
        );

        System.out.printf(
                Locale.US,
                "referenceFrames=%d performanceFrames=%d%n",
                n,
                m
        );

        System.out.printf(
                Locale.US,
                "weights chroma=%.2f energy=%.2f%n",
                CHROMA_WEIGHT,
                ENERGY_WEIGHT
        );

        System.out.printf(
                Locale.US,
                "alignments=%d within60ms=%d (%.1f%%) " +
                        "avgAbsOffset=%.3fs " +
                        "maxAbsOffset=%.3fs " +
                        "avgDistance=%.4f%n",
                result.alignments().size(),
                within60ms,
                result.alignments().isEmpty()
                        ? 0.0
                        : 100.0 * within60ms
                        / result.alignments().size(),
                averageAbsOffset,
                maxAbsOffset,
                result.averageDistance()
        );

        printOffsetProfile(result.alignments());
    }

    private List<Double> extractEnergy(
            File file) throws Exception {

        List<Double> energy =
                new ArrayList<>();

        AudioDispatcher dispatcher =
                AudioDispatcherFactory.fromFile(
                        file,
                        BUFFER_SIZE,
                        OVERLAP
                );

        dispatcher.addAudioProcessor(
                new be.tarsos.dsp.AudioProcessor() {

                    @Override
                    public boolean process(
                            AudioEvent audioEvent) {

                        float[] buffer =
                                audioEvent.getFloatBuffer();

                        double sum = 0.0;

                        for (float sample : buffer) {
                            sum += sample * sample;
                        }

                        double rms =
                                Math.sqrt(
                                        sum / buffer.length
                                );

                        energy.add(rms);

                        return true;
                    }

                    @Override
                    public void processingFinished() {
                    }
                }
        );

        dispatcher.run();

        return energy;
    }

    private double[] normalizeEnergy(
            List<Double> values,
            int size) {

        double[] result =
                new double[size];

        double mean = 0.0;

        for (int i = 0; i < size; i++) {
            mean += Math.log1p(values.get(i));
        }

        mean /= size;

        double variance = 0.0;

        for (int i = 0; i < size; i++) {
            double value =
                    Math.log1p(values.get(i));

            variance +=
                    (value - mean)
                            * (value - mean);
        }

        double std =
                Math.sqrt(variance / size);

        if (std == 0.0) {
            std = 1.0;
        }

        for (int i = 0; i < size; i++) {
            result[i] =
                    (Math.log1p(values.get(i))
                            - mean) / std;
        }

        return result;
    }

    private Result align(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double[] referenceEnergy,
            double[] performanceEnergy) {

        int n = Math.min(
                reference.size(),
                referenceEnergy.length
        );

        int m = Math.min(
                performance.size(),
                performanceEnergy.length
        );

        double[][] cost =
                new double[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            Arrays.fill(
                    cost[i],
                    Double.POSITIVE_INFINITY
            );
        }

        cost[0][0] = 0.0;

        for (int i = 1; i <= n; i++) {

            for (int j = 1; j <= m; j++) {

                double chromaDistance =
                        cosineDistance(
                                reference.get(i - 1).chroma(),
                                performance.get(j - 1).chroma()
                        );

                double energyDistance =
                        Math.min(
                                1.0,
                                Math.abs(
                                        referenceEnergy[i - 1]
                                                - performanceEnergy[j - 1]
                                ) / 4.0
                        );

                double distance =
                        CHROMA_WEIGHT
                                * chromaDistance
                                + ENERGY_WEIGHT
                                * energyDistance;

                double previous =
                        Math.min(
                                cost[i - 1][j - 1],
                                Math.min(
                                        cost[i - 1][j],
                                        cost[i][j - 1]
                                )
                        );

                if (Double.isFinite(previous)) {
                    cost[i][j] =
                            distance + previous;
                }
            }
        }

        List<Alignment> path =
                new ArrayList<>();

        int i = n;
        int j = m;

        while (i > 0 && j > 0) {

            double chromaDistance =
                    cosineDistance(
                            reference.get(i - 1).chroma(),
                            performance.get(j - 1).chroma()
                    );

            double energyDistance =
                    Math.min(
                            1.0,
                            Math.abs(
                                    referenceEnergy[i - 1]
                                            - performanceEnergy[j - 1]
                            ) / 4.0
                    );

            double distance =
                    CHROMA_WEIGHT
                            * chromaDistance
                            + ENERGY_WEIGHT
                            * energyDistance;

            path.add(
                    new Alignment(
                            reference.get(i - 1)
                                    .timestampSec(),
                            performance.get(j - 1)
                                    .timestampSec(),
                            distance
                    )
            );

            double diagonal =
                    cost[i - 1][j - 1];

            double up =
                    cost[i - 1][j];

            double left =
                    cost[i][j - 1];

            if (diagonal <= up
                    && diagonal <= left) {

                i--;
                j--;

            } else if (up <= left) {

                i--;

            } else {

                j--;
            }
        }

        Collections.reverse(path);

        double totalDistance = 0.0;

        for (Alignment alignment : path) {
            totalDistance +=
                    alignment.distance();
        }

        double averageDistance =
                path.isEmpty()
                        ? 0.0
                        : totalDistance / path.size();

        return new Result(
                path,
                averageDistance
        );
    }

    private void printOffsetProfile(
            List<Alignment> alignments) {

        System.out.println(
                "---- OFFSET PROFILE ----"
        );

        for (double targetTime = 2.0;
             targetTime <= 28.0;
             targetTime += 2.0) {

            Alignment nearest = null;

            double nearestDistance =
                    Double.POSITIVE_INFINITY;

            for (Alignment alignment : alignments) {

                double distance =
                        Math.abs(
                                alignment.referenceTime()
                                        - targetTime
                        );

                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = alignment;
                }
            }

            if (nearest != null) {

                double offset =
                        nearest.performanceTime()
                                - nearest.referenceTime();

                System.out.printf(
                        Locale.US,
                        "ref=%5.2fs -> perf=%5.2fs " +
                                "offset=%+6.3fs " +
                                "distance=%.4f%n",
                        nearest.referenceTime(),
                        nearest.performanceTime(),
                        offset,
                        nearest.distance()
                );
            }
        }
    }

    private double cosineDistance(
            double[] a,
            double[] b) {

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {

            dot += a[i] * b[i];

            normA +=
                    a[i] * a[i];

            normB +=
                    b[i] * b[i];
        }

        if (normA == 0.0
                || normB == 0.0) {

            return 1.0;
        }

        return 1.0 -
                dot / (
                        Math.sqrt(normA)
                                * Math.sqrt(normB)
                );
    }

    private record Alignment(
            double referenceTime,
            double performanceTime,
            double distance) {
    }

    private record Result(
            List<Alignment> alignments,
            double averageDistance) {
    }
}
