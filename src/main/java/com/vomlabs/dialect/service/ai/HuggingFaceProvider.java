package com.vomlabs.dialect.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vomlabs.dialect.config.DialectConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class HuggingFaceProvider implements AiProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RetryStrategy retryStrategy;
    private final RateLimiter rateLimiter;
    private final Logger logger;
    private final DialectConfig.AIConfig config;
    private final ExecutorService executor;
    private volatile boolean shutdown;

    public HuggingFaceProvider(DialectConfig.AIConfig config, Logger logger) {
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

    @Override
    public CompletableFuture<JsonNode> analyzeMessage(List<PromptBuilder.Message> messages) {
        if (shutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("AiProvider is shut down"));
        }
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new IllegalStateException("HuggingFace is not configured"));
        }
        if (!rateLimiter.tryAcquire()) {
            long resetTime = rateLimiter.getResetTimeMillis();
            return CompletableFuture.failedFuture(
                new OpenRouterClient.RateLimitExceededException("Rate limit exceeded. Resets in " + (resetTime / 1000) + "s", resetTime)
            );
        }
        return retryStrategy.executeWithRetry(() -> executeRequest(messages));
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public void shutdown() {
        this.shutdown = true;
        executor.shutdown();
    }

    @Override
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    private CompletableFuture<JsonNode> executeRequest(List<PromptBuilder.Message> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = formatPrompt(messages);
                String modelEndpoint = config.endpoint();
                if (!modelEndpoint.endsWith("/" + config.model())) {
                    modelEndpoint = modelEndpoint.endsWith("/") ? modelEndpoint + config.model() : modelEndpoint + "/" + config.model();
                }
                String requestBody = buildRequestBody(prompt);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelEndpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
                logger.fine("Sending request to HuggingFace: " + config.model());
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return handleResponse(response);
            } catch (java.net.http.HttpTimeoutException e) {
                throw new RuntimeException("Request timed out after " + config.timeoutSeconds() + "s", e);
            } catch (java.net.ConnectException e) {
                throw new RuntimeException("Connection refused to HuggingFace API: " + e.getMessage(), e);
            } catch (java.io.IOException e) {
                throw new RuntimeException("IO error during HuggingFace API call: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }, executor);
    }

    private String formatPrompt(List<PromptBuilder.Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (PromptBuilder.Message msg : messages) {
            String role = msg.role();
            if ("system".equals(role)) {
                sb.append("System: ").append(msg.content()).append("\n\n");
            } else if ("user".equals(role)) {
                sb.append("User: ").append(msg.content()).append("\n\n");
            } else if ("assistant".equals(role)) {
                sb.append("Assistant: ").append(msg.content()).append("\n\n");
            }
        }
        sb.append("Assistant:");
        return sb.toString();
    }

    private String buildRequestBody(String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("inputs", prompt);
        return root.toString();
    }

    private JsonNode handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();
        if (statusCode == 200) {
            return parseSuccess(body);
        }
        String errorMessage = extractError(body);
        switch (statusCode) {
            case 400 -> throw new RuntimeException("Bad request (400): " + errorMessage);
            case 401 -> throw new RuntimeException("Invalid API key (401): " + errorMessage);
            case 403 -> throw new RuntimeException("Forbidden (403): " + errorMessage);
            case 429 -> throw new RuntimeException("Rate limited (429): " + errorMessage);
            case 503 -> throw new RuntimeException("Model loading (503): " + errorMessage);
            default -> throw new RuntimeException("HuggingFace API error (" + statusCode + "): " + errorMessage);
        }
    }

    private JsonNode parseSuccess(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isArray()) {
                JsonNode first = root.get(0);
                JsonNode text = first.get("generated_text");
                if (text != null && text.isTextual()) {
                    return OpenRouterClient.parseContentAsJson(extractAssistantResponse(text.asText().trim()), objectMapper);
                }
                throw new RuntimeException("Response missing 'generated_text' field");
            }
            return OpenRouterClient.parseContentAsJson(body, objectMapper);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse HuggingFace response: " + e.getMessage(), e);
        }
    }

    private String extractAssistantResponse(String text) {
        int idx = text.lastIndexOf("Assistant:");
        if (idx != -1) {
            return text.substring(idx + "Assistant:".length()).trim();
        }
        return text;
    }

    private String extractError(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.get("error");
            if (error != null && error.isTextual()) return error.asText();
        } catch (Exception ignored) {}
        return "Unknown error (HTTP body: " + (body != null ? body.substring(0, Math.min(100, body.length())) : "empty") + ")";
    }
}
