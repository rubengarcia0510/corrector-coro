package com.rubengarcia.correctorcoro;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

class QuizasMananaDtwCharacterizationTest {

    @Test
    void characterizeQuizasMananaTemporalChromaAndCents() {
        File referenceFile =
                new File(System.getProperty("user.home"),
                        "downloads/quizas-manana-ref.wav");

        File performanceFile =
                new File(System.getProperty("user.home"),
                        "downloads/aud-20260410-wa0001-trimmed.wav");

        TarsosChromaExtractor chromaExtractor = new TarsosChromaExtractor();
        SpectralPitchExtractor spectralExtractor = new SpectralPitchExtractor();
        PolyphonicPitchMatcher matcher100 = new PolyphonicPitchMatcher(100.0);
        PolyphonicPitchMatcher matcher75 = new PolyphonicPitchMatcher(75.0);
        PolyphonicPitchMatcher matcher50 = new PolyphonicPitchMatcher(50.0);

        List<ChromaFrame> referenceChroma =
                chromaExtractor.extract(referenceFile);

        List<ChromaFrame> performanceChroma =
                chromaExtractor.extract(performanceFile);

        List<SpectralFrame> referenceSpectral =
                spectralExtractor.extract(referenceFile);

        List<SpectralFrame> performanceSpectral =
                spectralExtractor.extract(performanceFile);

        List<ChromaDtwAligner.ChromaAlignment> alignments =
                new ChromaDtwAligner(0.5).align(
                        referenceChroma,
                        performanceChroma
                );

        double referenceDuration =
                referenceChroma.get(referenceChroma.size() - 1).timestampSec();

        double performanceDuration =
                performanceChroma.get(performanceChroma.size() - 1).timestampSec();

        double scale = performanceDuration / referenceDuration;

        System.out.println();
        System.out.println(
                "========== TEMPORAL + CHROMA + CENTS CHARACTERIZATION =========="
        );
        System.out.printf(
                "referenceDuration=%.3fs performanceDuration=%.3fs scale=%.6f%n",
                referenceDuration,
                performanceDuration,
                scale
        );

        System.out.println();
        System.out.println(
                "ref     perf    expected residual chroma  matches meanCents maxCents"
        );

        double sumAbsResidual = 0.0;
        double sumSquaredResidual = 0.0;
        double maxAbsResidual = 0.0;

        double sumChromaDistance = 0.0;
        double maxChromaDistance = 0.0;

        double sumMeanAbsCents = 0.0;
        double maxAbsCents = 0.0;

        int residualWithin500ms = 0;
        int samples = 0;
        int samplesWithPitchMatches = 0;

        for (double checkpoint = 10.0;
             checkpoint <= 170.0;
             checkpoint += 10.0) {

            ChromaDtwAligner.ChromaAlignment best = null;
            double bestReferenceDistance = Double.POSITIVE_INFINITY;

            for (ChromaDtwAligner.ChromaAlignment alignment : alignments) {
                double distance = Math.abs(
                        alignment.referenceTimestampSec() - checkpoint
                );

                if (distance < bestReferenceDistance) {
                    bestReferenceDistance = distance;
                    best = alignment;
                }
            }

            if (best == null) {
                continue;
            }

            SpectralFrame referenceFrame = findNearestSpectralFrame(
                    referenceSpectral,
                    best.referenceTimestampSec()
            );

            SpectralFrame performanceFrame = findNearestSpectralFrame(
                    performanceSpectral,
                    best.performanceTimestampSec()
            );

            if (referenceFrame == null || performanceFrame == null) {
                continue;
            }

            List<PolyphonicPitchMatch> matches100 =
                    matcher100.match(
                            referenceFrame.peaks(),
                            performanceFrame.peaks()
                    );

            List<PolyphonicPitchMatch> matches75 =
                    matcher75.match(
                            referenceFrame.peaks(),
                            performanceFrame.peaks()
                    );

            List<PolyphonicPitchMatch> matches50 =
                    matcher50.match(
                            referenceFrame.peaks(),
                            performanceFrame.peaks()
                    );

            List<PolyphonicPitchMatch> matches = matches100;

            double expectedPerformance =
                    checkpoint * scale;

            double residual =
                    best.performanceTimestampSec()
                            - expectedPerformance;

            double meanAbsCents = 0.0;
            double checkpointMaxAbsCents = 0.0;

            if (!matches.isEmpty()) {
                for (PolyphonicPitchMatch match : matches) {
                    double absCents =
                            Math.abs(match.deviationCents());

                    meanAbsCents += absCents;
                    checkpointMaxAbsCents =
                            Math.max(checkpointMaxAbsCents, absCents);
                }

                meanAbsCents /= matches.size();
                sumMeanAbsCents += meanAbsCents;
                maxAbsCents =
                        Math.max(maxAbsCents, checkpointMaxAbsCents);
                samplesWithPitchMatches++;
            }

            double absResidual = Math.abs(residual);

            sumAbsResidual += absResidual;
            sumSquaredResidual += residual * residual;
            maxAbsResidual = Math.max(maxAbsResidual, absResidual);

            sumChromaDistance += best.distance();
            maxChromaDistance =
                    Math.max(maxChromaDistance, best.distance());

            if (absResidual <= 0.5) {
                residualWithin500ms++;
            }

            samples++;

            System.out.printf(
                    "%6.2f  %6.2f  %6.2f  %+8.3f  %.4f  %7d  %8.2f  %8.2f%n",
                    best.referenceTimestampSec(),
                    best.performanceTimestampSec(),
                    expectedPerformance,
                    residual,
                    best.distance(),
                    matches.size(),
                    meanAbsCents,
                    checkpointMaxAbsCents
            );

            System.out.printf(
                    "    threshold 100: matches=%d meanCents=%.2f%n",
                    matches100.size(),
                    meanAbsCents(matches100)
            );

            System.out.printf(
                    "    threshold  75: matches=%d meanCents=%.2f%n",
                    matches75.size(),
                    meanAbsCents(matches75)
            );

            System.out.printf(
                    "    threshold  50: matches=%d meanCents=%.2f%n",
                    matches50.size(),
                    meanAbsCents(matches50)
            );

            for (PolyphonicPitchMatch match : matches100) {
                System.out.printf(
                        "      100: %.2f Hz -> %.2f Hz = %+7.2f cents%n",
                        match.referenceFrequencyHz(),
                        match.performanceFrequencyHz(),
                        match.deviationCents()
                );
            }
        }

        double rmseResidual =
                Math.sqrt(sumSquaredResidual / samples);

        System.out.println();
        System.out.println("---------- GLOBAL STATISTICS ----------");
        System.out.printf("samples=%d%n", samples);
        System.out.printf(
                "meanAbsResidual=%.3fs%n",
                sumAbsResidual / samples
        );
        System.out.printf(
                "maxAbsResidual=%.3fs%n",
                maxAbsResidual
        );
        System.out.printf(
                "rmseResidual=%.3fs%n",
                rmseResidual
        );
        System.out.printf(
                "meanChromaDistance=%.4f%n",
                sumChromaDistance / samples
        );
        System.out.printf(
                "maxChromaDistance=%.4f%n",
                maxChromaDistance
        );
        System.out.printf(
                "residualWithin500ms=%d/%d (%.1f%%)%n",
                residualWithin500ms,
                samples,
                100.0 * residualWithin500ms / samples
        );

        System.out.printf(
                "samplesWithPitchMatches=%d/%d (%.1f%%)%n",
                samplesWithPitchMatches,
                samples,
                100.0 * samplesWithPitchMatches / samples
        );

        if (samplesWithPitchMatches > 0) {
            System.out.printf(
                    "meanOfMeanAbsCents=%.2f%n",
                    sumMeanAbsCents / samplesWithPitchMatches
            );
            System.out.printf(
                    "maxAbsCents=%.2f%n",
                    maxAbsCents
            );
        }
    }

    private double meanAbsCents(List<PolyphonicPitchMatch> matches) {
        if (matches.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;

        for (PolyphonicPitchMatch match : matches) {
            total += Math.abs(match.deviationCents());
        }

        return total / matches.size();
    }

    private SpectralFrame findNearestSpectralFrame(
            List<SpectralFrame> frames,
            double timestampSec) {

        SpectralFrame best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (SpectralFrame frame : frames) {
            double distance =
                    Math.abs(frame.timestampSec() - timestampSec);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = frame;
            }
        }

        return bestDistance <= 0.060 ? best : null;
    }

    private void characterizeBand(
            double band,
            List<ChromaFrame> reference,
            List<ChromaFrame> performance,
            double referenceDuration,
            double durationScale) {

        System.out.println();
        System.out.printf("---- BAND %.1fs ----%n", band);

        List<ChromaDtwAligner.ChromaAlignment> alignments;

        try {
            alignments = new ChromaDtwAligner(band).align(reference, performance);
        } catch (Exception e) {
            System.out.printf(
                    "ERROR: %s%n",
                    e.getMessage()
            );
            return;
        }

        double sumAbsTemporalError = 0.0;
        double maxAbsTemporalError = 0.0;
        int samples = 0;

        for (double checkpoint = 10.0;
             checkpoint <= referenceDuration;
             checkpoint += 10.0) {

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
                double expectedPerformance =
                        checkpoint * durationScale;

                double temporalError =
                        best.performanceTimestampSec()
                                - expectedPerformance;

                double absError = Math.abs(temporalError);

                sumAbsTemporalError += absError;
                maxAbsTemporalError = Math.max(
                        maxAbsTemporalError,
                        absError
                );
                samples++;

                System.out.printf(
                        "ref=%6.2fs -> perf=%6.2fs expected=%6.2fs error=%+7.3fs distance=%.4f%n",
                        best.referenceTimestampSec(),
                        best.performanceTimestampSec(),
                        expectedPerformance,
                        temporalError,
                        best.distance()
                );
            }
        }

        ChromaDtwAligner.ChromaAlignment last =
                alignments.get(alignments.size() - 1);

        double meanAbsTemporalError =
                samples > 0
                        ? sumAbsTemporalError / samples
                        : Double.NaN;

        System.out.printf(
                "SUMMARY alignments=%d meanAbsTemporalError=%.3fs "
                        + "maxAbsTemporalError=%.3fs endpointPerf=%.3fs "
                        + "endpointOffset=%+.3fs endpointDistance=%.4f%n",
                alignments.size(),
                meanAbsTemporalError,
                maxAbsTemporalError,
                last.performanceTimestampSec(),
                last.performanceTimestampSec()
                        - last.referenceTimestampSec(),
                last.distance()
        );
    }
}
