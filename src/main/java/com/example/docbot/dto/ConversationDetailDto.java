package com.example.docbot.dto;

import java.time.Instant;
import java.util.List;

public record ConversationDetailDto(
        String conversationId,
        String title,
        String activeFilename,
        Instant createdAt,
        Instant updatedAt,
        List<ConversationMessageDto> messages
) {
}
