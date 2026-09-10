package com.rubengarcia.correctorcoro.analysis;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Service;

import com.rubengarcia.correctorcoro.ChromaIntonationAnalyzer;
import com.rubengarcia.correctorcoro.ChromaIntonationSegment;

@Service
public class AnalysisJobService {

    private final AnalysisJobRepository repository;
    private final ChromaIntonationAnalyzer analyzer;
    private final Executor executor;

    public AnalysisJobService(
            AnalysisJobRepository repository,
            ChromaIntonationAnalyzer analyzer,
            Executor executor
    ) {
        this.repository = repository;
        this.analyzer = analyzer;
        this.executor = executor;
    }

    public String start(File referenceAudio, File performanceAudio) {
        String jobId = UUID.randomUUID().toString();

        repository.save(new AnalysisJob(
                jobId,
                AnalysisStatus.PENDIENTE,
                List.of(),
                null
        ));

        executor.execute(() -> analyze(jobId, referenceAudio, performanceAudio));

        return jobId;
    }

    public AnalysisJob find(String jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new AnalysisJobNotFoundException(jobId));
    }

    private void analyze(
            String jobId,
            File referenceAudio,
            File performanceAudio
    ) {
        repository.save(new AnalysisJob(
                jobId,
                AnalysisStatus.PROCESANDO,
                List.of(),
                null
        ));

        try {
            List<ChromaIntonationSegment> segments =
                    analyzer.analyzeSegments(referenceAudio, performanceAudio);

            repository.save(new AnalysisJob(
                    jobId,
                    AnalysisStatus.LISTO,
                    segments,
                    null
            ));
        } catch (Exception e) {
            repository.save(new AnalysisJob(
                    jobId,
                    AnalysisStatus.ERROR,
                    List.of(),
                    e.getMessage()
            ));
        }
    }
}
