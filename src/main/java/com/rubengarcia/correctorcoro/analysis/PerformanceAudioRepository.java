package com.rubengarcia.correctorcoro.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Repository;
import org.springframework.http.codec.multipart.FilePart;

@Repository
public class PerformanceAudioRepository {

    public File save(FilePart audio) throws IOException {
        Path directory = Files.createTempDirectory("corrector-coro-performance-");
        Path file = directory.resolve("performance-audio");

        audio.transferTo(file).block();

        return file.toFile();
    }

    public void delete(File file) {
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
            // Best-effort cleanup. Analysis result must not be lost because
            // temporary-file deletion failed.
        }
    }
}
