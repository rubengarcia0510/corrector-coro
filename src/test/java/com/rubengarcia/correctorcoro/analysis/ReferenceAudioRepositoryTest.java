package com.rubengarcia.correctorcoro.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ReferenceAudioRepositoryTest {

    @InjectMocks
    private ReferenceAudioRepository repository;

    @Test
    void shouldDeletePreviousReferenceWhenReplacingIt() throws IOException {
                FilePart firstAudio = mock(FilePart.class);
        FilePart secondAudio = mock(FilePart.class);

        doAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            Files.writeString(target, "first");
            return Mono.empty();
        }).when(firstAudio).transferTo(any(Path.class));

        doAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            Files.writeString(target, "second");
            return Mono.empty();
        }).when(secondAudio).transferTo(any(Path.class));

        repository.save("coro-1", firstAudio);

        File firstReference = repository.find("coro-1");

        assertNotNull(firstReference);
        assertTrue(firstReference.exists());

        Path firstDirectory = firstReference.toPath().getParent();

        repository.save("coro-1", secondAudio);

        File secondReference = repository.find("coro-1");

        assertNotNull(secondReference);
        assertTrue(secondReference.exists());
        assertNotEquals(firstReference, secondReference);

        assertFalse(firstReference.exists());
        assertFalse(firstDirectory.toFile().exists());
    }
}
