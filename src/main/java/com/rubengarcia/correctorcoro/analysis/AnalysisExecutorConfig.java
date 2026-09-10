package com.rubengarcia.correctorcoro.analysis;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalysisExecutorConfig {

    @Bean
    public Executor analysisExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
