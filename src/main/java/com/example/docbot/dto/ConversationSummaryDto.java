package com.example.docbot.dto;

import java.time.Instant;

public record ConversationSummaryDto(
        String conversationId,
        String title,
        String activeFilename,
        int messageCount,
        Instant createdAt,
        Instant updatedAt
) {
}
