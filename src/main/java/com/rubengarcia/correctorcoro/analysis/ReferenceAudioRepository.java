package com.rubengarcia.correctorcoro.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Repository;

@Repository
public class ReferenceAudioRepository {

    private final Map<String, File> references = new ConcurrentHashMap<>();

    public void save(String coroId, FilePart audio) throws IOException {
        Path directory = Files.createTempDirectory("corrector-coro-references-");
        Path file = directory.resolve("reference-audio");

        audio.transferTo(file).block();

        references.put(coroId, file.toFile());
    }

    public File find(String coroId) {
        return references.get(coroId);
    }
}
