package com.rubengarcia.correctorcoro.analysis;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class AnalysisJobRepository {

    private final Map<String, AnalysisJob> jobs = new ConcurrentHashMap<>();

    public void save(AnalysisJob job) {
        jobs.put(job.jobId(), job);
    }

    public Optional<AnalysisJob> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
