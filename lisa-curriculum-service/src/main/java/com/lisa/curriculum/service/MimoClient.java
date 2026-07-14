package com.lisa.curriculum.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lisa.curriculum.config.MimoProperties;
import com.lisa.curriculum.dto.AiSuggestionRequestDto;
import com.lisa.curriculum.dto.AiSuggestionResponseDto;
import com.lisa.curriculum.exception.AiProviderUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class MimoClient {
    private static final String SYSTEM_PROMPT = """
            You create short, safe speaking practice questions for a language learning LMS.
            Use only the curriculum context provided by the user. Match the language, level and topic.
            Do not create debates, academic topics, sensitive content, or questions that repeat the task.
            Return JSON only in this shape: {\"suggestions\":[{\"content\":\"...\",\"focus\":\"...\"}]}.
            Return no more than 5 concise suggestions.
            """;

    private final RestTemplateBuilder restTemplateBuilder;
    private final MimoProperties properties;
    private final ObjectMapper objectMapper;

    public List<AiSuggestionResponseDto.AiSuggestionDto> suggest(AiSuggestionRequestDto request) {
        validateConfiguration();
        int requestedCount = request.getCount() == null ? 3 : Math.min(5, request.getCount());
        RestTemplate client = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("language", request.getLanguage());
        context.put("stage", request.getStage());
        context.put("levelId", request.getLevelId());
        context.put("levelNumber", request.getLevelNumber());
        context.put("topic", request.getTopic());
        context.put("task", request.getTask());
        context.put("count", requestedCount);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("temperature", 0.7);
        payload.put("max_tokens", properties.getMaxTokens());
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", toJson(context))
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        try {
            ResponseEntity<String> response = client.exchange(
                    properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class);
            return parseSuggestions(response.getBody(), requestedCount);
        } catch (RuntimeException ex) {
            if (ex instanceof AiProviderUnavailableException unavailable) {
                throw unavailable;
            }
            throw new AiProviderUnavailableException("MiMo request failed", ex);
        }
    }

    private void validateConfiguration() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getModel() == null || properties.getModel().isBlank()) {
            throw new AiProviderUnavailableException("MiMo AI provider is not configured");
        }
    }

    private List<AiSuggestionResponseDto.AiSuggestionDto> parseSuggestions(String body, int requestedCount) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("MiMo response has no message content");
            }
            String json = content.replaceFirst("^\\s*```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```\\s*$", "").trim();
            JsonNode suggestions = objectMapper.readTree(json).path("suggestions");
            if (!suggestions.isArray()) throw new IllegalArgumentException("MiMo response has no suggestions array");

            List<AiSuggestionResponseDto.AiSuggestionDto> result = new ArrayList<>();
            for (JsonNode item : suggestions) {
                String suggestion = item.path("content").asText("").trim();
                String focus = item.path("focus").asText("speaking").trim();
                if (!suggestion.isBlank() && result.size() < Math.min(5, requestedCount)) {
                    result.add(AiSuggestionResponseDto.AiSuggestionDto.builder()
                            .content(suggestion).focus(focus.isBlank() ? "speaking" : focus).build());
                }
            }
            if (result.isEmpty()) throw new IllegalArgumentException("MiMo returned no valid suggestions");
            return result;
        } catch (Exception ex) {
            throw new AiProviderUnavailableException("MiMo response could not be parsed", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AiProviderUnavailableException("Could not build MiMo request", ex);
        }
    }
}
