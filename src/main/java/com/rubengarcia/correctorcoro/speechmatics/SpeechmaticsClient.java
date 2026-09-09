package com.rubengarcia.correctorcoro.speechmatics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpeechmaticsClient {

    private final WebClient speechmaticsWebClient;

    @Value("${speechmatics.api-key}")
    private String apiKey;

    public Mono<SpeechmaticsJobResponse> submitJob(
            File audioFile,
            String language
    ) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();

        body.part("data_file", new FileSystemResource(audioFile));

        body.part("config", """
                {
                  "type": "transcription",
                  "transcription_config": {
                    "language": "%s"
                  }
                }
                """.formatted(language))
                .contentType(MediaType.APPLICATION_JSON);

        return speechmaticsWebClient.post()
                .uri("/v2/jobs/")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body.build())
                .retrieve()
                .bodyToMono(SpeechmaticsJobResponse.class);
    }

    public Mono<SpeechmaticsJobStatus> getJobStatus(String jobId) {
        return speechmaticsWebClient.get()
                .uri("/v2/jobs/{jobId}", jobId)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(SpeechmaticsJobStatus.class);
    }

    public Mono<String> getTranscript(String jobId) {
        return speechmaticsWebClient.get()
                .uri("/v2/jobs/{jobId}/transcript?format=txt", jobId)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSubscribe(subscription ->
                        log.info("Speechmatics transcript request started: jobId={}", jobId))
                .doOnNext(transcript ->
                        log.info("Speechmatics transcript received: jobId={}, length={}",
                                jobId,
                                transcript.length()))
                .doOnSuccess(transcript ->
                        log.info("Speechmatics transcript request completed: jobId={}, empty={}",
                                jobId,
                                transcript == null))
                .doOnError(error ->
                        log.error("Speechmatics transcript failed: jobId={}",
                                jobId,
                                error));
    }
}
