package com.example.docbot.controller;

import com.example.docbot.dto.ChatAskRequestDto;
import com.example.docbot.dto.ChatResponseDto;
import com.example.docbot.dto.ChatMetricsDto;
import com.example.docbot.dto.ConversationDetailDto;
import com.example.docbot.dto.ConversationSummaryDto;
import com.example.docbot.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
@CrossOrigin
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/ask")
    public ChatResponseDto ask(
            @RequestParam String message,
            @RequestParam(required = false) String filename
    ) {
        return chatService.ask(null, message, filename);
    }

    @PostMapping("/conversations/ask")
    public ChatResponseDto askInConversation(@RequestBody ChatAskRequestDto request) {
        return chatService.ask(
                request.conversationId(),
                request.message(),
                request.filename()
        );
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryDto> conversations() {
        return chatService.listConversations();
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationDetailDto conversation(@PathVariable String conversationId) {
        return chatService.getConversation(conversationId);
    }

    @GetMapping("/metrics")
    public ChatMetricsDto metrics() {
        return chatService.metrics();
    }
}
