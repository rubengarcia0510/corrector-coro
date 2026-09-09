package com.rubengarcia.correctorcoro.speechmatics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SpeechmaticsService {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final SpeechmaticsClient client;

    public Mono<SpeechmaticsTranscriptionResponse> transcribe(
            File audioFile,
            String language
    ) {
        return client.submitJob(audioFile, language)
                .flatMap(job ->
                        pollUntilCompleted(job.id())
                                .map(transcript ->
                                        new SpeechmaticsTranscriptionResponse(
                                                job.id(),
                                                language,
                                                transcript
                                        )
                                )
                );
    }

    private Mono<String> pollUntilCompleted(String jobId) {
        return client.getJobStatus(jobId)
                .flatMap(status -> switch (status.job().status()) {
                    case "done" -> client.getTranscript(jobId);

                    case "rejected", "deleted", "expired" ->
                            Mono.error(new IllegalStateException(
                                    "Speechmatics job " + jobId
                                            + " finished with status: "
                                            + status.job().status()
                            ));

                    default ->
                            Mono.delay(POLL_INTERVAL)
                                    .then(pollUntilCompleted(jobId));
                });
    }
}
