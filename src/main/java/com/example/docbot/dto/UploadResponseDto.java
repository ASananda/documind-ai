package com.example.docbot.dto;

public record UploadResponseDto(
        String filename,
        String status,
        int chunksCreated
) {
}
