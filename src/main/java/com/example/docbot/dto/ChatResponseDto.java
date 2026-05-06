package com.example.docbot.dto;

import java.util.List;

public record ChatResponseDto(
        String conversationId,
        String question,
        String answer,
        List<SourceDto> sources,
        String scope,
        long durationMs,
        String model
) {
}
