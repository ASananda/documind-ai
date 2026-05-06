package com.example.docbot.dto;

public record ChatMetricsDto(
        String model,
        int samples,
        double averageDurationMs,
        long lastDurationMs,
        int topK,
        double similarityThreshold,
        int chunkSize,
        int chunkOverlap
) {
}
