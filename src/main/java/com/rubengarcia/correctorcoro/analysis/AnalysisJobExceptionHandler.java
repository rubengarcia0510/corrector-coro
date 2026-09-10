package com.rubengarcia.correctorcoro.analysis;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AnalysisJobExceptionHandler {

    @ExceptionHandler(AnalysisJobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(
            AnalysisJobNotFoundException exception
    ) {
        return Map.of(
                "error", exception.getMessage()
        );
    }
}
