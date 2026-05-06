package com.example.docbot.dto;

public record ChatAskRequestDto(
        String conversationId,
        String message,
        String filename
) {
}
