package com.rubengarcia.correctorcoro.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;

@Repository
public class ReferenceAudioRepository {

    private final Map<String, File> references = new ConcurrentHashMap<>();

    public Mono<Void> save(String coroId, FilePart audio) throws IOException {
        Path directory = Files.createTempDirectory("corrector-coro-references-");
        Path file = directory.resolve("reference-audio");

        return audio.transferTo(file)
                .doOnSuccess(ignored -> references.put(coroId, file.toFile()));
    }

    public File find(String coroId) {
        return references.get(coroId);
    }
}
