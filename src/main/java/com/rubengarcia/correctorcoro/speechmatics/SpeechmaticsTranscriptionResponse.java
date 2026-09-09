package com.rubengarcia.correctorcoro.speechmatics;

public record SpeechmaticsTranscriptionResponse(
        String jobId,
        String language,
        String transcript
) {
}
