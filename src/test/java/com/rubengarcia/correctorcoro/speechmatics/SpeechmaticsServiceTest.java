package com.rubengarcia.correctorcoro.speechmatics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.time.Duration;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SpeechmaticsServiceTest {

    @Mock
    private SpeechmaticsClient client;

    @InjectMocks
    private SpeechmaticsService service;

    @Test
    void shouldPollUntilJobIsDoneAndReturnTranscript() {
        File audioFile = new File("test.wav");

        when(client.submitJob(audioFile, "es"))
                .thenReturn(Mono.just(
                        new SpeechmaticsJobResponse("job-123", null)
                ));

        when(client.getJobStatus("job-123"))
                .thenReturn(
                        Mono.just(new SpeechmaticsJobStatus(
                                new SpeechmaticsJobStatus.Job("job-123", "running")
                        )),
                        Mono.just(new SpeechmaticsJobStatus(
                                new SpeechmaticsJobStatus.Job("job-123", "done")
                        ))
                );

        when(client.getTranscript("job-123"))
                .thenReturn(Mono.just(
                        "La corchea del compás 32 está atrasada"
                ));

        StepVerifier.withVirtualTime(
                        () -> service.transcribe(audioFile, "es")
                )
                .thenAwait(Duration.ofSeconds(2))
                .assertNext(response -> {
                    assertEquals("job-123", response.jobId());
                    assertEquals("es", response.language());
                    assertEquals(
                            "La corchea del compás 32 está atrasada",
                            response.transcript()
                    );
                })
                .verifyComplete();

        verify(client).submitJob(audioFile, "es");
        verify(client, times(2)).getJobStatus("job-123");
        verify(client).getTranscript("job-123");
    }

    @Test
    void shouldFailWhenJobIsDeleted() {
        File audioFile = new File("test.wav");

        when(client.submitJob(audioFile, "es"))
                .thenReturn(Mono.just(
                        new SpeechmaticsJobResponse("job-789", null)
                ));

        when(client.getJobStatus("job-789"))
                .thenReturn(Mono.just(
                        new SpeechmaticsJobStatus(
                                new SpeechmaticsJobStatus.Job("job-789", "deleted")
                        )
                ));

        StepVerifier.create(service.transcribe(audioFile, "es"))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException
                                && error.getMessage().contains("deleted")
                )
                .verify();

        verify(client).getJobStatus("job-789");
        verify(client, never()).getTranscript(anyString());
    }

    @Test
    void shouldFailWhenJobIsExpired() {
        File audioFile = new File("test.wav");

        when(client.submitJob(audioFile, "es"))
                .thenReturn(Mono.just(
                        new SpeechmaticsJobResponse("job-999", null)
                ));

        when(client.getJobStatus("job-999"))
                .thenReturn(Mono.just(
                        new SpeechmaticsJobStatus(
                                new SpeechmaticsJobStatus.Job("job-999", "expired")
                        )
                ));

        StepVerifier.create(service.transcribe(audioFile, "es"))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException
                                && error.getMessage().contains("expired")
                )
                .verify();

        verify(client).getJobStatus("job-999");
        verify(client, never()).getTranscript(anyString());
    }

    @Test
    void shouldFailWhenJobIsRejected() {
        File audioFile = new File("test.wav");

        when(client.submitJob(audioFile, "es"))
                .thenReturn(Mono.just(
                        new SpeechmaticsJobResponse("job-456", null)
                ));

        when(client.getJobStatus("job-456"))
                .thenReturn(Mono.just(
                        new SpeechmaticsJobStatus(
                                new SpeechmaticsJobStatus.Job("job-456", "rejected")
                        )
                ));

        StepVerifier.create(service.transcribe(audioFile, "es"))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException
                                && error.getMessage().contains("rejected")
                )
                .verify();

        verify(client).submitJob(audioFile, "es");
        verify(client).getJobStatus("job-456");
        verify(client, never()).getTranscript(anyString());
    }
}
