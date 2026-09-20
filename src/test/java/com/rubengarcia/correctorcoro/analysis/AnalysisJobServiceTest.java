package com.rubengarcia.correctorcoro.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rubengarcia.correctorcoro.ChromaIntonationAnalyzer;
import com.rubengarcia.correctorcoro.ChromaIntonationSegment;

@ExtendWith(MockitoExtension.class)
class AnalysisJobServiceTest {

    @Mock
    private AnalysisJobRepository repository;

    @Mock
    private ChromaIntonationAnalyzer analyzer;

    @Mock
    private PerformanceAudioRepository performanceAudioRepository;

    @Mock
    private Executor executor;

    @InjectMocks
    private AnalysisJobService service;

    @Test
    void shouldDeletePerformanceAudioWhenAnalysisSucceeds() {
        File referenceAudio = new File("reference.wav");
        File performanceAudio = new File("performance.wav");
        List<ChromaIntonationSegment> segments = List.of();

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        when(analyzer.analyzeSegments(referenceAudio, performanceAudio))
                .thenReturn(segments);

        service.start(referenceAudio, performanceAudio);

        verify(performanceAudioRepository).delete(performanceAudio);
    }

    @Test
    void shouldDeletePerformanceAudioWhenAnalysisFails() {
        File referenceAudio = new File("reference.wav");
        File performanceAudio = new File("performance.wav");

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        when(analyzer.analyzeSegments(referenceAudio, performanceAudio))
                .thenThrow(new IllegalStateException("analysis failed"));

        service.start(referenceAudio, performanceAudio);

        verify(performanceAudioRepository).delete(performanceAudio);
    }
}
