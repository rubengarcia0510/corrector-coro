package com.rubengarcia.correctorcoro.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;

@Repository
public class PerformanceAudioRepository {

    public Mono<File> save(FilePart audio) throws IOException {
        Path directory = Files.createTempDirectory("corrector-coro-performance-");
        Path file = directory.resolve("performance-audio");

        return audio.transferTo(file)
                .thenReturn(file.toFile());
    }
}
