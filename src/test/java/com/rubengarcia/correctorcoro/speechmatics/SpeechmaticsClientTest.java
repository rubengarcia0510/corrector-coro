package com.rubengarcia.correctorcoro.speechmatics;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SpeechmaticsClientTest {

    private MockWebServer server;
    private SpeechmaticsClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build();

        client = new SpeechmaticsClient(webClient);

        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldSubmitJobWithAuthenticationAndMultipartData() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "job-123",
                          "status": "queued"
                        }
                        """));

        Path audio = Files.createTempFile("speechmatics-test-", ".wav");
        Files.write(audio, new byte[]{1, 2, 3, 4});

        StepVerifier.create(client.submitJob(audio.toFile(), "es"))
                .assertNext(response -> {
                    assertEquals("job-123", response.id());
                    assertEquals("queued", response.status());
                })
                .verifyComplete();

        RecordedRequest request = server.takeRequest();

        assertEquals("POST", request.getMethod());
        assertEquals("/v2/jobs/", request.getPath());
        assertEquals("Bearer test-api-key",
                request.getHeader("Authorization"));

        String body = request.getBody().readUtf8();

        assertTrue(body.contains("data_file"));
        assertTrue(body.contains("config"));
        assertTrue(body.contains("\"type\": \"transcription\""));
        assertTrue(body.contains("\"language\": \"es\""));

        Files.deleteIfExists(audio);
    }

    @Test
    void shouldGetJobStatus() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "job": {
                            "id": "job-123",
                            "status": "running"
                          }
                        }
                        """));

        StepVerifier.create(client.getJobStatus("job-123"))
                .assertNext(response -> {
                    assertEquals("job-123", response.job().id());
                    assertEquals("running", response.job().status());
                })
                .verifyComplete();

        RecordedRequest request = server.takeRequest();

        assertEquals("GET", request.getMethod());
        assertEquals("/v2/jobs/job-123", request.getPath());
        assertEquals("Bearer test-api-key",
                request.getHeader("Authorization"));
    }

    @Test
    void shouldGetTranscript() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("La corchea del compás 32 está atrasada"));

        StepVerifier.create(client.getTranscript("job-123"))
                .expectNext("La corchea del compás 32 está atrasada")
                .verifyComplete();

        RecordedRequest request = server.takeRequest();

        assertEquals("GET", request.getMethod());
        assertEquals("/v2/jobs/job-123/transcript?format=txt",
                request.getPath());
        assertEquals("Bearer test-api-key",
                request.getHeader("Authorization"));
    }
}
