package com.rubengarcia.correctorcoro.speechmatics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(SpeechmaticsController.class)
class SpeechmaticsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SpeechmaticsService service;

    @Test
    void shouldTranscribeAudioAndReturnResponse() {

        when(service.transcribe(any(), eq("es")))
                .thenReturn(Mono.just(
                        new SpeechmaticsTranscriptionResponse(
                                "job-123",
                                "es",
                                "La corchea del compás 32 está atrasada"
                        )
                ));

        ByteArrayResource audio = new ByteArrayResource(
                "test-audio".getBytes()
        ) {
            @Override
            public String getFilename() {
                return "test.wav";
            }
        };

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/speechmatics/transcribe")
                        .queryParam("language", "es")
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(
                        org.springframework.web.reactive.function.BodyInserters
                                .fromMultipartData("file", audio)
                )
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jobId").isEqualTo("job-123")
                .jsonPath("$.language").isEqualTo("es")
                .jsonPath("$.transcript")
                .isEqualTo("La corchea del compás 32 está atrasada");
    }

}
