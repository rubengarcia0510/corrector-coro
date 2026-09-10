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
}
