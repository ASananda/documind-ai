package com.example.docbot.dto;

public record AuthResponseDto(
        String token,
        String userId,
        String username
) {
}
