package com.lisa.curriculum.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lisa.curriculum.config.MimoProperties;
import com.lisa.curriculum.dto.AiSuggestionRequestDto;
import com.lisa.curriculum.dto.AiSuggestionResponseDto;
import com.lisa.curriculum.dto.SpeakingAssessmentResponseDto;
import com.lisa.curriculum.dto.SupportAiResponseDto;
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

    public SpeakingAssessmentResponseDto assessSpeaking(
            String task, String topic, int levelNumber, String transcript) {
        validateConfiguration();
        RestTemplate client = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();

        String system = """
                You assess an English learner's transcribed spoken answer.
                Judge only the transcript against the supplied speaking task and topic. Do not assess pronunciation.
                Give integer scores from 0 to 100 for relevance, grammar and vocabulary. Overall should reflect those scores.
                Keep feedback encouraging, specific and concise. Provide one improved example answer.
                Return JSON only in this exact shape:
                {"overallScore":0,"relevanceScore":0,"grammarScore":0,"vocabularyScore":0,
                "feedback":"...","suggestedAnswer":"..."}.
                """;
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("language", "ENGLISH");
        context.put("levelNumber", levelNumber);
        context.put("topic", topic);
        context.put("task", task);
        context.put("transcript", transcript);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("temperature", 0.2);
        payload.put("max_tokens", properties.getMaxTokens());
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", toJson(context))
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        try {
            String body = client.exchange(
                    properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class).getBody();
            return parseSpeakingAssessment(body);
        } catch (RuntimeException ex) {
            if (ex instanceof AiProviderUnavailableException unavailable) throw unavailable;
            throw new AiProviderUnavailableException("MiMo speaking assessment failed", ex);
        }
    }

    public SupportAiResponseDto answerSupport(String question, String locale, String knowledge) {
        validateConfiguration();
        RestTemplate client = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs())).build();
        String system = """
                You are LUCY product support. Answer only from the approved knowledge below.
                Never infer account data, balances, transactions, identity or features not in the knowledge.
                If the answer is absent, say that the information is not available and direct the user to support.
                Answer concisely in the requested locale. Return JSON only:
                {"answer":"...","suggestedLinks":[{"label":"...","path":"/..."}]}.
                Use at most 3 links and only paths listed in the knowledge.

                APPROVED KNOWLEDGE:
                """ + knowledge;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("temperature", 0.2);
        payload.put("max_tokens", properties.getMaxTokens());
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", toJson(Map.of("locale", locale == null ? "vi" : locale, "question", question)))
        ));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        try {
            String body = client.exchange(properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions",
                    HttpMethod.POST, new HttpEntity<>(payload, headers), String.class).getBody();
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText("")
                    .replaceFirst("^\\s*```(?:json)?\\s*", "").replaceFirst("\\s*```\\s*$", "").trim();
            JsonNode response = objectMapper.readTree(content);
            String answer = response.path("answer").asText("").trim();
            if (answer.isBlank()) throw new IllegalArgumentException("MiMo returned no support answer");
            List<SupportAiResponseDto.SupportLinkDto> links = new ArrayList<>();
            for (JsonNode link : response.path("suggestedLinks")) {
                String path = link.path("path").asText("").trim();
                String label = link.path("label").asText("").trim();
                if (path.matches("^/[a-zA-Z0-9/_-]*$") && !label.isBlank() && links.size() < 3) {
                    links.add(SupportAiResponseDto.SupportLinkDto.builder().label(label).path(path).build());
                }
            }
            return SupportAiResponseDto.builder().answer(answer).suggestedLinks(links).build();
        } catch (Exception ex) {
            throw new AiProviderUnavailableException("MiMo support request failed", ex);
        }
    }

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

    private SpeakingAssessmentResponseDto parseSpeakingAssessment(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText("")
                    .replaceFirst("^\\s*```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```\\s*$", "").trim();
            JsonNode result = objectMapper.readTree(content);
            int overall = requiredScore(result, "overallScore");
            int relevance = requiredScore(result, "relevanceScore");
            int grammar = requiredScore(result, "grammarScore");
            int vocabulary = requiredScore(result, "vocabularyScore");
            String feedback = requiredText(result, "feedback");
            String suggestedAnswer = requiredText(result, "suggestedAnswer");
            return SpeakingAssessmentResponseDto.builder()
                    .overallScore(overall)
                    .relevanceScore(relevance)
                    .grammarScore(grammar)
                    .vocabularyScore(vocabulary)
                    .feedback(feedback)
                    .suggestedAnswer(suggestedAnswer)
                    .build();
        } catch (Exception ex) {
            throw new AiProviderUnavailableException("MiMo speaking assessment response could not be parsed", ex);
        }
    }

    private int requiredScore(JsonNode result, String field) {
        if (!result.has(field) || !result.get(field).canConvertToInt()) {
            throw new IllegalArgumentException("Missing integer score: " + field);
        }
        int score = result.get(field).asInt();
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Score out of range: " + field);
        }
        return score;
    }

    private String requiredText(JsonNode result, String field) {
        String value = result.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException("Missing text: " + field);
        return value;
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
