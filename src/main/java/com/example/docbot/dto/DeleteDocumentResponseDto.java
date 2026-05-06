package com.example.docbot.dto;

public record DeleteDocumentResponseDto(
        String filename,
        int chunksDeleted,
        String status
) {
}
