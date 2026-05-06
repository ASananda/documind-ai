package com.example.docbot.dto;

public record DocumentSummaryDto(
        String filename,
        int chunks
) {
}
