package com.rubengarcia.correctorcoro.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerformanceAudioRepositoryTest {

    @InjectMocks
    private PerformanceAudioRepository repository;

    @Test
    void shouldDeleteAudioFileAndTemporaryDirectory() throws IOException {
        Path directory = Files.createTempDirectory("corrector-coro-test-");
        Path audio = directory.resolve("performance-audio");
        Files.createFile(audio);

        File audioFile = audio.toFile();

        assertTrue(audioFile.exists());
        assertTrue(directory.toFile().exists());

        repository.delete(audioFile);

        assertFalse(audioFile.exists());
        assertFalse(directory.toFile().exists());
    }
}
