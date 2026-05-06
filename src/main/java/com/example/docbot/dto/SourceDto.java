package com.example.docbot.dto;

public record SourceDto(
        String filename,
        String chunkIndex,
        String preview
) {
}
