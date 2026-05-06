package com.example.docbot.dto;

import java.time.Instant;
import java.util.List;

public record ConversationMessageDto(
        String role,
        String text,
        String scope,
        String model,
        Long durationMs,
        List<SourceDto> sources,
        Instant createdAt
) {
}
