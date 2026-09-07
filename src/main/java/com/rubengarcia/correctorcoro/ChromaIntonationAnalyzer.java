package com.rubengarcia.correctorcoro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChromaIntonationAnalyzer {

    private final ChromaExtractor chromaExtractor;
    private final ChromaDtwAligner chromaDtwAligner;
    private final SpectralPitchExtractor spectralPitchExtractor;
    private final PolyphonicIntonationAligner polyphonicIntonationAligner;
    private final ChromaIntonationSegmenter segmenter;

    private final double warningCents;
    private final double errorCents;
    private final double severeCents;
    private final int minErrorFrames;

    public ChromaIntonationAnalyzer(
            ChromaExtractor chromaExtractor,
            ChromaDtwAligner chromaDtwAligner,
            SpectralPitchExtractor spectralPitchExtractor,
            PolyphonicIntonationAligner polyphonicIntonationAligner,
            ChromaIntonationSegmenter segmenter,
            @Value("${chorus.analysis.intonation.warning-cents:10}") double warningCents,
            @Value("${chorus.analysis.intonation.error-cents:20}") double errorCents,
            @Value("${chorus.analysis.intonation.severe-cents:35}") double severeCents,
            @Value("${chorus.analysis.intonation.min-error-frames:3}") int minErrorFrames) {

        this.chromaExtractor = chromaExtractor;
        this.chromaDtwAligner = chromaDtwAligner;
        this.spectralPitchExtractor = spectralPitchExtractor;
        this.polyphonicIntonationAligner = polyphonicIntonationAligner;
        this.segmenter = segmenter;
        this.warningCents = warningCents;
        this.errorCents = errorCents;
        this.severeCents = severeCents;
        this.minErrorFrames = minErrorFrames;
    }

    public List<ChromaIntonationError> analyze(
            File referenceAudio,
            File performanceAudio) {

        List<ChromaFrame> referenceChroma =
                chromaExtractor.extract(referenceAudio);

        List<ChromaFrame> performanceChroma =
                chromaExtractor.extract(performanceAudio);

        List<ChromaDtwAligner.ChromaAlignment> chromaAlignments =
                chromaDtwAligner.align(referenceChroma, performanceChroma);

        List<SpectralFrame> referenceSpectral =
                spectralPitchExtractor.extract(referenceAudio);

        List<SpectralFrame> performanceSpectral =
                spectralPitchExtractor.extract(performanceAudio);

        List<PolyphonicIntonationFrame> polyphonicFrames =
                polyphonicIntonationAligner.align(
                        chromaAlignments,
                        referenceSpectral,
                        performanceSpectral);

        List<ChromaIntonationError> errors = new ArrayList<>();

        for (PolyphonicIntonationFrame frame : polyphonicFrames) {
            for (PolyphonicPitchMatch match : frame.matches()) {

                double deviation = match.deviationCents();
                double absoluteDeviation = Math.abs(deviation);

                ChromaIntonationError.Severity severity =
                        classify(absoluteDeviation);

                errors.add(new ChromaIntonationError(
                        frame.referenceTimestampSec(),
                        frame.performanceTimestampSec(),
                        match.referenceFrequencyHz(),
                        match.performanceFrequencyHz(),
                        deviation,
                        severity
                ));
            }
        }

        return errors;
    }

    public List<ChromaIntonationSegment> analyzeSegments(
            File referenceAudio,
            File performanceAudio) {

        List<ChromaIntonationError> errors =
                analyze(referenceAudio, performanceAudio);

        return segmenter.segment(errors, minErrorFrames);
    }

    private ChromaIntonationError.Severity classify(double absoluteDeviationCents) {

        if (absoluteDeviationCents >= severeCents) {
            return ChromaIntonationError.Severity.SEVERE;
        }

        if (absoluteDeviationCents >= errorCents) {
            return ChromaIntonationError.Severity.ERROR;
        }

        if (absoluteDeviationCents >= warningCents) {
            return ChromaIntonationError.Severity.WARNING;
        }

        return ChromaIntonationError.Severity.OK;
    }
}
