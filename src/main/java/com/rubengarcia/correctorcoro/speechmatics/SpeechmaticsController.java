package com.rubengarcia.correctorcoro.speechmatics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/speechmatics")
@RequiredArgsConstructor
public class SpeechmaticsController {

    private final SpeechmaticsService service;

    @PostMapping(
            value = "/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<SpeechmaticsTranscriptionResponse>> transcribe(
            @RequestPart("file") FilePart file,
            @RequestParam(defaultValue = "es") String language
    ) {
        return Mono.usingWhen(
                Mono.fromCallable(() ->
                                Files.createTempFile(
                                        "speechmatics-",
                                        "-" + file.filename()
                                ))
                        .subscribeOn(Schedulers.boundedElastic()),
                tempFile -> file.transferTo(tempFile)
                        .then(service.transcribe(tempFile.toFile(), language))
                        .map(ResponseEntity::ok),
                this::deleteTempFile,
                (tempFile, error) -> deleteTempFile(tempFile),
                this::deleteTempFile
        );
    }

    private Mono<Void> deleteTempFile(Path tempFile) {
        return Mono.fromRunnable(() -> {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
                // La limpieza del temporal no debe alterar la respuesta.
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
