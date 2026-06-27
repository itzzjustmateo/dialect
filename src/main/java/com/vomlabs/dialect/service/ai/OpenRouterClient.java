package com.vomlabs.dialect.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vomlabs.dialect.config.DialectConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OpenRouterClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String OPENROUTER_REFERRER = "https://github.com/anomalyco/dialect";
    private static final String OPENROUTER_TITLE = "Dialect";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RetryStrategy retryStrategy;
    private final RateLimiter rateLimiter;
    private final Logger logger;
    private final DialectConfig.AIConfig config;
    private final ExecutorService executor;
    private volatile boolean shutdown;

    public OpenRouterClient(DialectConfig.AIConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.objectMapper = new ObjectMapper();
        this.retryStrategy = new RetryStrategy(config.maxRetries(), config.backoffBaseMs(), logger);
        this.rateLimiter = new RateLimiter(60, 40000);
        this.shutdown = false;

        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        this.httpClient = HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
            .build();
    }

    public CompletableFuture<JsonNode> analyzeMessage(List<PromptBuilder.Message> messages) {
        if (shutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("OpenRouterClient is shut down"));
        }
        if (config.openrouterKey() == null || config.openrouterKey().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("OpenRouter API key is not configured"));
        }
        if (config.openrouterKey().equals("your-key-here")) {
            return CompletableFuture.failedFuture(new IllegalStateException("OpenRouter API key is not configured (default placeholder)"));
        }
        if (!rateLimiter.tryAcquire()) {
            long resetTime = rateLimiter.getResetTimeMillis();
            return CompletableFuture.failedFuture(
                new RateLimitExceededException("Rate limit exceeded. Resets in " + (resetTime / 1000) + "s", resetTime)
            );
        }

        return retryStrategy.executeWithRetry(() -> executeRequest(messages));
    }

    private CompletableFuture<JsonNode> executeRequest(List<PromptBuilder.Message> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestBody = buildRequestBody(messages);
                HttpRequest request = buildHttpRequest(requestBody);
                logger.fine("Sending request to OpenRouter: " + config.model());

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return handleResponse(response);
            } catch (java.net.http.HttpTimeoutException e) {
                throw new RuntimeException("Request timed out after " + config.timeoutSeconds() + "s", e);
            } catch (java.net.ConnectException e) {
                throw new RuntimeException("Connection refused to OpenRouter API: " + e.getMessage(), e);
            } catch (java.io.IOException e) {
                throw new RuntimeException("IO error during OpenRouter API call: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }, executor);
    }

    private String buildRequestBody(List<PromptBuilder.Message> messages) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("temperature", config.temperature());
        root.put("max_tokens", 512);

        ArrayNode messagesArray = root.putArray("messages");
        for (PromptBuilder.Message msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
        }

        return root.toString();
    }

    private HttpRequest buildHttpRequest(String requestBody) {
        return HttpRequest.newBuilder()
            .uri(URI.create(config.endpoint()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + config.openrouterKey())
            .header("HTTP-Referer", OPENROUTER_REFERRER)
            .header("X-Title", OPENROUTER_TITLE)
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    }

    private JsonNode handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode == 200) {
            return parseSuccessResponse(body);
        }

        String errorMessage = extractErrorMessage(body);
        switch (statusCode) {
            case 400 -> throw new RuntimeException("Bad request (400): " + errorMessage);
            case 401 -> throw new RuntimeException("Invalid API key (401): " + errorMessage);
            case 402 -> throw new RuntimeException("Insufficient credits (402): " + errorMessage);
            case 429 -> throw new RuntimeException("Rate limited (429): " + errorMessage);
            case 502 -> throw new RuntimeException("Gateway error (502): " + errorMessage);
            case 503 -> throw new RuntimeException("Service unavailable (503): " + errorMessage);
            default -> throw new RuntimeException("OpenRouter API error (" + statusCode + "): " + errorMessage);
        }
    }

    private JsonNode parseSuccessResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("OpenRouter response missing 'choices' array");
            }
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message == null) {
                throw new RuntimeException("OpenRouter response missing 'message' in choice");
            }
            JsonNode content = message.get("content");
            if (content == null || !content.isTextual()) {
                throw new RuntimeException("OpenRouter response missing text 'content' in message");
            }
            String contentText = content.asText().trim();

            return parseContentAsJson(contentText);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenRouter response: " + e.getMessage(), e);
        }
    }

    private JsonNode parseContentAsJson(String content) {
        String json = content;

        int jsonStart = content.indexOf('{');
        int jsonEnd = content.lastIndexOf('}');
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            json = content.substring(jsonStart, jsonEnd + 1);
        }

        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("OpenRouter response was not valid JSON. Content: " + content.substring(0, Math.min(200, content.length())), e);
        }
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.get("error");
            if (error != null) {
                JsonNode message = error.get("message");
                if (message != null && message.isTextual()) {
                    return message.asText();
                }
            }
        } catch (Exception ignored) {}
        return "Unknown error (HTTP body: " + (body != null ? body.substring(0, Math.min(100, body.length())) : "empty") + ")";
    }

    public Optional<JsonNode> extractAnalysis(String content) {
        try {
            JsonNode json = objectMapper.readTree(content);
            return Optional.of(json);
        } catch (Exception e) {
            logger.warning("Failed to parse analysis result as JSON: " + e.getMessage());
            return Optional.empty();
        }
    }

    public String extractTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field != null && field.isTextual()) {
            return field.asText();
        }
        return null;
    }

    public double extractDoubleField(JsonNode node, String fieldName, double defaultValue) {
        JsonNode field = node.get(fieldName);
        if (field != null && field.isNumber()) {
            return field.asDouble();
        }
        return defaultValue;
    }

    public boolean extractBooleanField(JsonNode node, String fieldName, boolean defaultValue) {
        JsonNode field = node.get(fieldName);
        if (field != null && field.isBoolean()) {
            return field.asBoolean();
        }
        return defaultValue;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public void shutdown() {
        this.shutdown = true;
        executor.shutdown();
    }

    public static class RateLimitExceededException extends RuntimeException {
        private final long resetTimeMillis;

        public RateLimitExceededException(String message, long resetTimeMillis) {
            super(message);
            this.resetTimeMillis = resetTimeMillis;
        }

        public long getResetTimeMillis() {
            return resetTimeMillis;
        }
    }
}
