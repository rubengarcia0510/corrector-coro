package com.rubengarcia.correctorcoro.speechmatics;

public record SpeechmaticsJobStatus(
        Job job
) {

    public record Job(
            String id,
            String status
    ) {}
}
