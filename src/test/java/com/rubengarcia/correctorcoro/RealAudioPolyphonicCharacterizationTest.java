package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;
import java.util.List;


import java.io.File;

class RealAudioPolyphonicCharacterizationTest {

    @Test
    void characterizeReginaFloresAudio() {
        File referenceFile = new File("regina-ref-30s.wav");
        File performanceFile = new File("regina-flores-30s.wav");

        SpectralPitchExtractor extractor = new SpectralPitchExtractor();

        var referenceFrames = extractor.extract(referenceFile);
        var performanceFrames = extractor.extract(performanceFile);

        System.out.println();
        System.out.println("========== REAL AUDIO CHARACTERIZATION ==========");
        System.out.println("Reference frames: " + referenceFrames.size());
        System.out.println("Performance frames: " + performanceFrames.size());

        printFrameSummary("REFERENCE", referenceFrames);
        printFrameSummary("PERFORMANCE", performanceFrames);

        printStatistics("REFERENCE", referenceFrames);
        printStatistics("PERFORMANCE", performanceFrames);

        printMagnitudeDistribution("REFERENCE", referenceFrames);
        printMagnitudeDistribution("PERFORMANCE", performanceFrames);

        printTemporalStability("REFERENCE", referenceFrames);
        printTemporalStability("PERFORMANCE", performanceFrames);

        printThreeFrameStability("REFERENCE", referenceFrames);
        printThreeFrameStability("PERFORMANCE", performanceFrames);
        printRealAudioMatches(referenceFrames, performanceFrames);
        printDtwTemporalCharacterization();
        printDtwOffsetProfile();
        printDtwFineOffsetProfile();
        printRealAudioDtwMatches(referenceFrames, performanceFrames);

        System.out.println("==================================================");
    }





    private void printRealAudioDtwMatches(
            List<SpectralFrame> referenceFrames,
            List<SpectralFrame> performanceFrames) {

        TarsosChromaExtractor chromaExtractor = new TarsosChromaExtractor();
        ChromaDtwAligner dtwAligner = new ChromaDtwAligner(0.5, 0.5);
        PolyphonicPitchMatcher matcher = new PolyphonicPitchMatcher();

        List<ChromaFrame> referenceChroma =
                chromaExtractor.extract(new File("regina-ref-30s.wav"));

        List<ChromaFrame> performanceChroma =
                chromaExtractor.extract(new File("regina-flores-30s.wav"));

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                dtwAligner.align(referenceChroma, performanceChroma);

        int[] indexes = {322, 644, 966, 1288};
        double[] thresholds = {1.0, 5.0, 10.0, 20.0};

        System.out.println("---- REAL AUDIO MATCHES AFTER CHROMA DTW ----");
        System.out.printf("DTW alignments=%d%n", alignments.size());

        for (int index : indexes) {

            if (index >= referenceChroma.size() || index >= performanceChroma.size()) {
                continue;
            }

            ChromaFrame refChroma = referenceChroma.get(index);

            ChromaDtwAligner.ChromaAlignment closest = alignments.stream()
                    .min((a, b) -> Double.compare(
                            Math.abs(a.referenceTimestampSec() - refChroma.timestampSec()),
                            Math.abs(b.referenceTimestampSec() - refChroma.timestampSec())))
                    .orElse(null);

            if (closest == null) {
                continue;
            }

            int refIndex = nearestFrameIndex(
                    referenceFrames,
                    closest.referenceTimestampSec());

            int perfIndex = nearestFrameIndex(
                    performanceFrames,
                    closest.performanceTimestampSec());

            System.out.printf(
                    "refFrame=%d refTime=%.3f -> perfFrame=%d perfTime=%.3f dtwDistance=%.4f%n",
                    refIndex,
                    closest.referenceTimestampSec(),
                    perfIndex,
                    closest.performanceTimestampSec(),
                    closest.distance());

            for (double threshold : thresholds) {

                List<SpectralPitch> refPeaks = referenceFrames.get(refIndex).peaks().stream()
                        .filter(p -> p.magnitude() > threshold)
                        .toList();

                List<SpectralPitch> perfPeaks = performanceFrames.get(perfIndex).peaks().stream()
                        .filter(p -> p.magnitude() > threshold)
                        .toList();

                List<PolyphonicPitchMatch> matches =
                        matcher.match(refPeaks, perfPeaks);

                long within35 = matches.stream()
                        .filter(m -> Math.abs(m.deviationCents()) <= 35.0)
                        .count();

                long within50 = matches.stream()
                        .filter(m -> Math.abs(m.deviationCents()) <= 50.0)
                        .count();

                double avgAbsCents = matches.isEmpty()
                        ? 0.0
                        : matches.stream()
                                .mapToDouble(m -> Math.abs(m.deviationCents()))
                                .average()
                                .orElse(0.0);

                System.out.printf(
                        "  >%.1f ref=%d perf=%d matches=%d avgAbsCents=%.2f <=35=%d <=50=%d%n",
                        threshold,
                        refPeaks.size(),
                        perfPeaks.size(),
                        matches.size(),
                        avgAbsCents,
                        within35,
                        within50);
            }
        }
    }

    private int nearestFrameIndex(
            List<SpectralFrame> frames,
            double timestampSec) {

        int bestIndex = 0;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int i = 0; i < frames.size(); i++) {
            double distance =
                    Math.abs(frames.get(i).timestampSec() - timestampSec);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }



    private void printDtwOffsetProfile() {
        TarsosChromaExtractor chromaExtractor = new TarsosChromaExtractor();
        ChromaDtwAligner dtwAligner = new ChromaDtwAligner();

        List<ChromaFrame> reference = chromaExtractor.extract(new File("regina-ref-30s.wav"));
        List<ChromaFrame> performance = chromaExtractor.extract(new File("regina-flores-30s.wav"));

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                dtwAligner.align(reference, performance);

        double[] checkpoints = {0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 29.0};

        System.out.println("---- DTW OFFSET PROFILE ----");

        for (double checkpoint : checkpoints) {
            ChromaDtwAligner.ChromaAlignment best = null;
            double bestDistance = Double.POSITIVE_INFINITY;

            for (ChromaDtwAligner.ChromaAlignment alignment : alignments) {
                double distance = Math.abs(
                        alignment.referenceTimestampSec() - checkpoint
                );

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = alignment;
                }
            }

            if (best != null) {
                double offset =
                        best.performanceTimestampSec()
                                - best.referenceTimestampSec();

                System.out.printf(
                        "ref=%.3fs -> perf=%.3fs offset=%+.3fs dtwDistance=%.4f%n",
                        best.referenceTimestampSec(),
                        best.performanceTimestampSec(),
                        offset,
                        best.distance()
                );
            }
        }
    }


    private void printDtwFineOffsetProfile() {
        TarsosChromaExtractor chromaExtractor = new TarsosChromaExtractor();
        ChromaDtwAligner dtwAligner = new ChromaDtwAligner();

        List<ChromaFrame> reference =
                chromaExtractor.extract(new File("regina-ref-30s.wav"));
        List<ChromaFrame> performance =
                chromaExtractor.extract(new File("regina-flores-30s.wav"));

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                dtwAligner.align(reference, performance);

        System.out.println("---- DTW FINE OFFSET PROFILE ----");

        for (int second = 0; second <= 29; second++) {
            ChromaDtwAligner.ChromaAlignment best = null;
            double bestDistance = Double.POSITIVE_INFINITY;

            for (ChromaDtwAligner.ChromaAlignment alignment : alignments) {
                double distance = Math.abs(
                        alignment.referenceTimestampSec() - second
                );

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = alignment;
                }
            }

            if (best != null) {
                double offset =
                        best.performanceTimestampSec()
                                - best.referenceTimestampSec();

                System.out.printf(
                        "ref=%5.2fs -> perf=%5.2fs offset=%+6.3fs dtwDistance=%.4f%n",
                        best.referenceTimestampSec(),
                        best.performanceTimestampSec(),
                        offset,
                        best.distance()
                );
            }
        }
    }

    private void printDtwTemporalCharacterization() {

        TarsosChromaExtractor chromaExtractor = new TarsosChromaExtractor();
        ChromaDtwAligner dtwAligner = new ChromaDtwAligner(0.5, 0.5);

        List<ChromaFrame> reference =
                chromaExtractor.extract(new File("regina-ref-30s.wav"));

        List<ChromaFrame> performance =
                chromaExtractor.extract(new File("regina-flores-30s.wav"));

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                dtwAligner.align(reference, performance);

        int repeatedReference = 0;
        int repeatedPerformance = 0;
        int within60ms = 0;

        double totalAbsTimeDifference = 0.0;
        double maxAbsTimeDifference = 0.0;

        double previousRef = Double.NaN;
        double previousPerf = Double.NaN;

        for (ChromaDtwAligner.ChromaAlignment alignment : alignments) {

            double refTime = alignment.referenceTimestampSec();
            double perfTime = alignment.performanceTimestampSec();

            double delta = Math.abs(perfTime - refTime);

            totalAbsTimeDifference += delta;
            maxAbsTimeDifference = Math.max(maxAbsTimeDifference, delta);

            if (delta <= 0.060) {
                within60ms++;
            }

            if (!Double.isNaN(previousRef)
                    && Math.abs(refTime - previousRef) < 1e-9) {
                repeatedReference++;
            }

            if (!Double.isNaN(previousPerf)
                    && Math.abs(perfTime - previousPerf) < 1e-9) {
                repeatedPerformance++;
            }

            previousRef = refTime;
            previousPerf = perfTime;
        }

        double avgAbsTimeDifference =
                alignments.isEmpty()
                        ? 0.0
                        : totalAbsTimeDifference / alignments.size();

        System.out.println("---- DTW TEMPORAL CHARACTERIZATION ----");
        System.out.printf("referenceFrames=%d performanceFrames=%d%n",
                reference.size(), performance.size());
        System.out.printf("alignments=%d%n", alignments.size());
        System.out.printf("repeatedReference=%d%n", repeatedReference);
        System.out.printf("repeatedPerformance=%d%n", repeatedPerformance);
        System.out.printf("within60ms=%d (%.1f%%)%n",
                within60ms,
                alignments.isEmpty()
                        ? 0.0
                        : 100.0 * within60ms / alignments.size());
        System.out.printf("avgAbsTimeDifference=%.3fs%n", avgAbsTimeDifference);
        System.out.printf("maxAbsTimeDifference=%.3fs%n", maxAbsTimeDifference);
    }

    private void printRealAudioMatches(
            List<SpectralFrame> referenceFrames,
            List<SpectralFrame> performanceFrames) {

        PolyphonicPitchMatcher matcher = new PolyphonicPitchMatcher();

        int[] indexes = {322, 644, 966, 1288};
        double[] thresholds = {1.0, 5.0, 10.0, 20.0};

        System.out.println("---- REAL AUDIO MATCHES BY MAGNITUDE THRESHOLD ----");

        for (double threshold : thresholds) {
            System.out.printf("==== magnitude > %.1f ====%n", threshold);

            for (int index : indexes) {
                if (index >= referenceFrames.size() || index >= performanceFrames.size()) {
                    continue;
                }

                SpectralFrame reference = referenceFrames.get(index);
                SpectralFrame performance = performanceFrames.get(index);

                List<SpectralPitch> referencePeaks = reference.peaks().stream()
                        .filter(p -> p.magnitude() > threshold)
                        .toList();

                List<SpectralPitch> performancePeaks = performance.peaks().stream()
                        .filter(p -> p.magnitude() > threshold)
                        .toList();

                List<PolyphonicPitchMatch> matches =
                        matcher.match(referencePeaks, performancePeaks);

                System.out.printf(
                        "frame=%d ref=%d perf=%d matches=%d",
                        index,
                        referencePeaks.size(),
                        performancePeaks.size(),
                        matches.size());

                if (!matches.isEmpty()) {
                    double avgAbsCents = matches.stream()
                            .mapToDouble(m -> Math.abs(m.deviationCents()))
                            .average()
                            .orElse(0.0);

                    long within25 = matches.stream()
                            .filter(m -> Math.abs(m.deviationCents()) <= 25.0)
                            .count();

                    long within35 = matches.stream()
                            .filter(m -> Math.abs(m.deviationCents()) <= 35.0)
                            .count();

                    long within50 = matches.stream()
                            .filter(m -> Math.abs(m.deviationCents()) <= 50.0)
                            .count();

                    long within75 = matches.stream()
                            .filter(m -> Math.abs(m.deviationCents()) <= 75.0)
                            .count();

                    System.out.printf(
                            " avgAbsCents=%.2f <=25=%d <=35=%d <=50=%d <=75=%d",
                            avgAbsCents,
                            within25,
                            within35,
                            within50,
                            within75);
                }

                System.out.println();
            }

            System.out.println();
        }
    }

    private void printThreeFrameStability(
            String label,
            java.util.List<SpectralFrame> frames) {

        int totalStrongPeaks = 0;
        int stableOneFrame = 0;
        int stableThreeFrames = 0;

        for (int i = 0; i < frames.size() - 3; i++) {
            var current = frames.get(i).peaks().stream()
                    .filter(peak -> peak.magnitude() > 10.0)
                    .toList();

            for (var peak : current) {
                totalStrongPeaks++;

                boolean nextFrame = frames.get(i + 1).peaks().stream()
                        .filter(candidate -> candidate.magnitude() > 10.0)
                        .anyMatch(candidate ->
                                Math.abs(calculateCents(
                                        peak.frequencyHz(),
                                        candidate.frequencyHz()
                                )) <= 50.0
                        );

                boolean nextThreeFrames = true;

                for (int offset = 1; offset <= 3; offset++) {
                    boolean found = frames.get(i + offset).peaks().stream()
                            .filter(candidate -> candidate.magnitude() > 10.0)
                            .anyMatch(candidate ->
                                    Math.abs(calculateCents(
                                            peak.frequencyHz(),
                                            candidate.frequencyHz()
                                    )) <= 50.0
                            );

                    if (!found) {
                        nextThreeFrames = false;
                        break;
                    }
                }

                if (nextFrame) {
                    stableOneFrame++;
                }

                if (nextThreeFrames) {
                    stableThreeFrames++;
                }
            }
        }

        double oneFramePercentage = totalStrongPeaks == 0
                ? 0.0
                : 100.0 * stableOneFrame / totalStrongPeaks;

        double threeFramePercentage = totalStrongPeaks == 0
                ? 0.0
                : 100.0 * stableThreeFrames / totalStrongPeaks;

        System.out.println();
        System.out.println("---- " + label + " THREE-FRAME STABILITY ----");
        System.out.printf("strong peaks > 10: %d%n", totalStrongPeaks);
        System.out.printf(
                "stable next frame: %d (%.1f%%)%n",
                stableOneFrame,
                oneFramePercentage
        );
        System.out.printf(
                "stable across next 3 frames: %d (%.1f%%)%n",
                stableThreeFrames,
                threeFramePercentage
        );
    }

    private void printTemporalStability(
            String label,
            java.util.List<SpectralFrame> frames) {

        int totalStrongPeaks = 0;
        int stableStrongPeaks = 0;

        for (int i = 0; i < frames.size() - 1; i++) {
            var current = frames.get(i).peaks().stream()
                    .filter(peak -> peak.magnitude() > 10.0)
                    .toList();

            var next = frames.get(i + 1).peaks().stream()
                    .filter(peak -> peak.magnitude() > 10.0)
                    .toList();

            for (var peak : current) {
                totalStrongPeaks++;

                boolean stable = next.stream()
                        .anyMatch(candidate ->
                                Math.abs(calculateCents(
                                        peak.frequencyHz(),
                                        candidate.frequencyHz()
                                )) <= 50.0
                        );

                if (stable) {
                    stableStrongPeaks++;
                }
            }
        }

        double percentage = totalStrongPeaks == 0
                ? 0.0
                : 100.0 * stableStrongPeaks / totalStrongPeaks;

        System.out.println();
        System.out.println("---- " + label + " TEMPORAL STABILITY ----");
        System.out.printf("strong peaks > 10: %d%n", totalStrongPeaks);
        System.out.printf(
                "stable in next frame (<= 50 cents): %d (%.1f%%)%n",
                stableStrongPeaks,
                percentage
        );
    }

    private double calculateCents(double referenceHz, double performanceHz) {
        return 1200.0 * Math.log(performanceHz / referenceHz) / Math.log(2.0);
    }

    private void printMagnitudeDistribution(String label, java.util.List<SpectralFrame> frames) {
        double[] thresholds = {0.1, 1.0, 5.0, 10.0, 20.0};

        long totalPeaks = frames.stream()
                .mapToLong(frame -> frame.peaks().size())
                .sum();

        System.out.println();
        System.out.println("---- " + label + " MAGNITUDE DISTRIBUTION ----");
        System.out.println("total peaks=" + totalPeaks);

        for (double threshold : thresholds) {
            long count = frames.stream()
                    .flatMap(frame -> frame.peaks().stream())
                    .filter(peak -> peak.magnitude() > threshold)
                    .count();

            double averagePerFrame = (double) count / frames.size();

            System.out.printf(
                    "magnitude > %.1f: %d peaks | avg/frame=%.2f%n",
                    threshold,
                    count,
                    averagePerFrame
            );
        }
    }

    private void printStatistics(String label, java.util.List<SpectralFrame> frames) {
        long framesWithPeaks = frames.stream()
                .filter(frame -> !frame.peaks().isEmpty())
                .count();

        long framesWithUsefulPeaks = frames.stream()
                .filter(frame -> frame.peaks().stream()
                        .anyMatch(peak -> peak.magnitude() > 1.0))
                .count();

        int minPeaks = frames.stream()
                .mapToInt(frame -> frame.peaks().size())
                .min()
                .orElse(0);

        int maxPeaks = frames.stream()
                .mapToInt(frame -> frame.peaks().size())
                .max()
                .orElse(0);

        double avgPeaks = frames.stream()
                .mapToInt(frame -> frame.peaks().size())
                .average()
                .orElse(0.0);

        double avgUsefulPeaks = frames.stream()
                .mapToLong(frame -> frame.peaks().stream()
                        .filter(peak -> peak.magnitude() > 1.0)
                        .count())
                .average()
                .orElse(0.0);

        System.out.println();
        System.out.println("---- " + label + " STATISTICS ----");
        System.out.printf("frames=%d%n", frames.size());
        System.out.printf("frames with peaks=%d (%.1f%%)%n",
                framesWithPeaks,
                100.0 * framesWithPeaks / frames.size());
        System.out.printf("frames with magnitude > 1=%d (%.1f%%)%n",
                framesWithUsefulPeaks,
                100.0 * framesWithUsefulPeaks / frames.size());
        System.out.printf("peaks/frame: min=%d avg=%.2f max=%d%n",
                minPeaks,
                avgPeaks,
                maxPeaks);
        System.out.printf("useful peaks/frame (magnitude > 1): avg=%.2f%n",
                avgUsefulPeaks);
    }

    private void printFrameSummary(String label, java.util.List<SpectralFrame> frames) {
        System.out.println();
        System.out.println("---- " + label + " ----");

        int[] indexes = {
                0,
                frames.size() / 4,
                frames.size() / 2,
                (frames.size() * 3) / 4,
                frames.size() - 1
        };

        for (int index : indexes) {
            if (index < 0 || index >= frames.size()) {
                continue;
            }

            SpectralFrame frame = frames.get(index);

            System.out.printf(
                    "frame=%d timestamp=%.3fs peaks=%d%n",
                    index,
                    frame.timestampSec(),
                    frame.peaks().size()
            );

            frame.peaks().stream()
                    .limit(10)
                    .forEach(peak ->
                            System.out.printf(
                                    "  %.2f Hz | magnitude=%.2f%n",
                                    peak.frequencyHz(),
                                    peak.magnitude()
                            )
                    );
        }
    }
}
