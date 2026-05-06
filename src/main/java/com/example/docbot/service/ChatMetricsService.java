package com.example.docbot.service;

import com.example.docbot.config.RagProperties;
import com.example.docbot.dto.ChatMetricsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;

@Service
public class ChatMetricsService {

    private final Deque<Long> recentDurations = new ArrayDeque<>();
    private final RagProperties ragProperties;
    private final String chatModelName;
    private long lastDurationMs;

    public ChatMetricsService(
            RagProperties ragProperties,
            @Value("${spring.ai.ollama.chat.options.model:unknown}") String chatModelName
    ) {
        this.ragProperties = ragProperties;
        this.chatModelName = chatModelName;
    }

    public synchronized void recordDuration(long durationMs) {
        recentDurations.addLast(durationMs);
        lastDurationMs = durationMs;

        while (recentDurations.size() > ragProperties.getMetrics().getRollingWindow()) {
            recentDurations.removeFirst();
        }
    }

    public synchronized ChatMetricsDto snapshot() {
        double average = recentDurations.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        return new ChatMetricsDto(
                chatModelName,
                recentDurations.size(),
                average,
                lastDurationMs,
                ragProperties.getRetrieval().getTopK(),
                ragProperties.getRetrieval().getSimilarityThreshold(),
                ragProperties.getIngestion().getChunkSize(),
                ragProperties.getIngestion().getChunkOverlap()
        );
    }
}
