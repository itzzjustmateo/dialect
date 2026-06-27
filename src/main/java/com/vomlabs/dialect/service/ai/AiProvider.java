package com.vomlabs.dialect.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface AiProvider {

    CompletableFuture<JsonNode> analyzeMessage(List<PromptBuilder.Message> messages);

    boolean isConfigured();

    void shutdown();

    RateLimiter getRateLimiter();

    static String extractTextField(JsonNode node, String fieldName) {
        JsonNode field = node != null ? node.get(fieldName) : null;
        return field != null && field.isTextual() ? field.asText() : null;
    }

    static double extractDoubleField(JsonNode node, String fieldName, double defaultValue) {
        JsonNode field = node != null ? node.get(fieldName) : null;
        return field != null && field.isNumber() ? field.asDouble() : defaultValue;
    }

    static boolean extractBooleanField(JsonNode node, String fieldName, boolean defaultValue) {
        JsonNode field = node != null ? node.get(fieldName) : null;
        return field != null && field.isBoolean() ? field.asBoolean() : defaultValue;
    }

    static Optional<JsonNode> extractAnalysis(String json) {
        try {
            return Optional.of(new ObjectMapper().readTree(json));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
