package com.rubengarcia.correctorcoro.analysis;

public class AnalysisJobNotFoundException extends RuntimeException {

    public AnalysisJobNotFoundException(String jobId) {
        super("Analysis job not found: " + jobId);
    }
}
