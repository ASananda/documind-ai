package com.example.docbot.service;

import com.example.docbot.dto.ChatAskRequestDto;
import com.example.docbot.config.RagProperties;
import com.example.docbot.dto.ChatResponseDto;
import com.example.docbot.dto.ChatMetricsDto;
import com.example.docbot.dto.ConversationDetailDto;
import com.example.docbot.dto.ConversationMessageDto;
import com.example.docbot.dto.ConversationSummaryDto;
import com.example.docbot.dto.SourceDto;
import com.example.docbot.entity.AppUserEntity;
import com.example.docbot.entity.ChatMessageEntity;
import com.example.docbot.entity.ConversationEntity;
import com.example.docbot.repository.AppUserRepository;
import com.example.docbot.repository.ChatMessageRepository;
import com.example.docbot.repository.ConversationRepository;
import com.example.docbot.security.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final ChatMetricsService chatMetricsService;
    private final String chatModelName;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public ChatService(
            ChatModel chatModel,
            VectorStore vectorStore,
            RagProperties ragProperties,
            ChatMetricsService chatMetricsService,
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            AppUserRepository appUserRepository,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            @Value("${spring.ai.ollama.chat.options.model:unknown}") String chatModelName
    ) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
        this.chatMetricsService = chatMetricsService;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.appUserRepository = appUserRepository;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
        this.chatModelName = chatModelName;
    }

    @Transactional
    public ChatResponseDto ask(String conversationId, String message, String filename) {
        AuthUser currentUser = currentUserService.requireUser();
        ConversationEntity conversation = getOrCreateConversation(conversationId, message, filename);
        long startedAt = System.currentTimeMillis();
        String scope = getScopeLabel(filename);
        SearchRequest searchRequest = SearchRequest.query(message)
                .withTopK(ragProperties.getRetrieval().getTopK())
                .withSimilarityThreshold(ragProperties.getRetrieval().getSimilarityThreshold());

        String userFilter = "userId == '" + escapeFilterValue(currentUser.userId()) + "'";

        if (filename != null && !filename.isBlank()) {
            searchRequest = searchRequest.withFilterExpression(
                    userFilter + " && filename == '" + escapeFilterValue(filename.trim()) + "'"
            );
        } else {
            searchRequest = searchRequest.withFilterExpression(userFilter);
        }

        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        if (relevantDocs == null || relevantDocs.isEmpty()) {
            ChatResponseDto responseDto = new ChatResponseDto(
                    conversation.getId(),
                    message,
                    "I could not find relevant information in " + scope + ".",
                    List.of(),
                    scope,
                    elapsedMs(startedAt),
                    chatModelName
            );
            persistExchange(conversation, message, filename, responseDto);
            chatMetricsService.recordDuration(responseDto.durationMs());
            return responseDto;
        }

        String context = buildContext(relevantDocs);

        Prompt prompt = new Prompt(new UserMessage("""
                You are DocuMind AI, a document question-answering assistant.
                Use only the document context below.
                Do not add examples, code, or facts unless they are present in the context.
                If the answer is not clearly present, say the uploaded documents do not provide enough information.
                Keep the answer direct and concise.

                Document context:
                %s

                User question:
                %s
                """.formatted(context, message)));

        ChatResponse response = chatModel.call(prompt);

        if (response != null && response.getResult() != null) {
            String answer = response.getResult().getOutput().getContent();
            ChatResponseDto responseDto = new ChatResponseDto(
                    conversation.getId(),
                    message,
                    answer,
                    buildSources(relevantDocs),
                    scope,
                    elapsedMs(startedAt),
                    chatModelName
            );
            persistExchange(conversation, message, filename, responseDto);
            chatMetricsService.recordDuration(responseDto.durationMs());
            return responseDto;
        }

        ChatResponseDto responseDto = new ChatResponseDto(
                conversation.getId(),
                message,
                "No response from AI.",
                buildSources(relevantDocs),
                scope,
                elapsedMs(startedAt),
                chatModelName
        );
        persistExchange(conversation, message, filename, responseDto);
        chatMetricsService.recordDuration(responseDto.durationMs());
        return responseDto;
    }

    public ChatMetricsDto metrics() {
        return chatMetricsService.snapshot();
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> listConversations() {
        AuthUser currentUser = currentUserService.requireUser();

        return conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(currentUser.userId()).stream()
                .map(conversation -> new ConversationSummaryDto(
                        conversation.getId(),
                        conversation.getTitle(),
                        conversation.getActiveFilename(),
                        chatMessageRepository.countByConversationId(conversation.getId()),
                        conversation.getCreatedAt(),
                        conversation.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailDto getConversation(String conversationId) {
        AuthUser currentUser = currentUserService.requireUser();

        ConversationEntity conversation = conversationRepository.findByIdAndUserId(conversationId, currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        List<ConversationMessageDto> messages = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toConversationMessageDto)
                .toList();

        return new ConversationDetailDto(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getActiveFilename(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                messages
        );
    }

    private long elapsedMs(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private ConversationEntity getOrCreateConversation(String conversationId, String message, String filename) {
        AuthUser currentUser = currentUserService.requireUser();
        AppUserEntity user = appUserRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ConversationEntity conversation = conversationId == null || conversationId.isBlank()
                ? new ConversationEntity()
                : conversationRepository.findByIdAndUserId(conversationId, currentUser.userId())
                        .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(buildConversationTitle(message));
        }

        conversation.setUser(user);
        conversation.setActiveFilename(filename == null || filename.isBlank() ? null : filename.trim());
        return conversationRepository.save(conversation);
    }

    private String buildConversationTitle(String message) {
        String normalized = normalize(message);
        return normalized.length() <= 60
                ? normalized
                : normalized.substring(0, 57).trim() + "...";
    }

    private void persistExchange(
            ConversationEntity conversation,
            String question,
            String filename,
            ChatResponseDto responseDto
    ) {
        saveMessage(conversation, "user", question, getScopeLabel(filename), null, null, List.of());
        saveMessage(
                conversation,
                "assistant",
                responseDto.answer(),
                responseDto.scope(),
                responseDto.model(),
                responseDto.durationMs(),
                responseDto.sources()
        );
    }

    private void saveMessage(
            ConversationEntity conversation,
            String role,
            String content,
            String scope,
            String modelName,
            Long durationMs,
            List<SourceDto> sources
    ) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setScope(scope);
        message.setModelName(modelName);
        message.setDurationMs(durationMs);
        message.setSourcesJson(writeSources(sources));
        chatMessageRepository.save(message);
    }

    private String writeSources(List<SourceDto> sources) {
        try {
            return objectMapper.writeValueAsString(sources == null ? List.of() : sources);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to serialize sources", exception);
        }
    }

    private List<SourceDto> readSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(sourcesJson, new TypeReference<List<SourceDto>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to parse sources", exception);
        }
    }

    private ConversationMessageDto toConversationMessageDto(ChatMessageEntity message) {
        return new ConversationMessageDto(
                message.getRole(),
                message.getContent(),
                message.getScope(),
                message.getModelName(),
                message.getDurationMs(),
                readSources(message.getSourcesJson()),
                message.getCreatedAt()
        );
    }

    private String getScopeLabel(String filename) {
        return filename == null || filename.isBlank()
                ? "uploaded documents"
                : filename.trim();
    }

    private String escapeFilterValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();

        for (Document document : documents) {
            if (context.length() >= ragProperties.getRetrieval().getMaxTotalContextChars()) {
                break;
            }

            Map<String, Object> metadata = document.getMetadata();
            String filename = String.valueOf(metadata.getOrDefault("filename", "unknown.pdf"));
            String chunkIndex = String.valueOf(metadata.getOrDefault("chunkIndex", "unknown"));
            String content = truncate(
                    normalize(document.getContent()),
                    ragProperties.getRetrieval().getMaxChunkContextChars()
            );

            String chunk = """
                    Source: %s, chunk: %s
                    Content: %s
                    """.formatted(filename, chunkIndex, content);

            int remainingChars = ragProperties.getRetrieval().getMaxTotalContextChars() - context.length();

            if (chunk.length() > remainingChars) {
                context.append(truncate(chunk, remainingChars));
                break;
            }

            if (context.length() > 0) {
                context.append("\n---\n");
            }

            context.append(chunk);
        }

        return context.toString();
    }

    private List<SourceDto> buildSources(List<Document> documents) {
        return documents.stream()
                .map(document -> {
                    Map<String, Object> metadata = document.getMetadata();
                    String filename = String.valueOf(metadata.getOrDefault("filename", "unknown.pdf"));
                    String chunkIndex = String.valueOf(metadata.getOrDefault("chunkIndex", "unknown"));
                    String preview = truncate(normalize(document.getContent()), 220);

                    return new SourceDto(filename, chunkIndex, preview);
                })
                .distinct()
                .toList();
    }

    private String normalize(String content) {
        return content == null
                ? ""
                : content.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String content, int maxChars) {
        if (maxChars <= 0) {
            return "";
        }

        if (content.length() <= maxChars) {
            return content;
        }

        return content.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }
}
