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

        File newReference = file.toFile();
        File previousReference = references.put(coroId, newReference);

        delete(previousReference);
    }

    public File find(String coroId) {
        return references.get(coroId);
    }

    private void delete(File file) {
        if (file == null) {
            return;
        }

        try {
            Files.deleteIfExists(file.toPath());
            Path directory = file.toPath().getParent();
            if (directory != null) {
                Files.deleteIfExists(directory);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup. Keep the active reference available.
        }
    }
}
