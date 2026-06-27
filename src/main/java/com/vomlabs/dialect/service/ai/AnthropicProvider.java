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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class AnthropicProvider implements AiProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RetryStrategy retryStrategy;
    private final RateLimiter rateLimiter;
    private final Logger logger;
    private final DialectConfig.AIConfig config;
    private final ExecutorService executor;
    private volatile boolean shutdown;

    public AnthropicProvider(DialectConfig.AIConfig config, Logger logger) {
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
            return CompletableFuture.failedFuture(new IllegalStateException("Anthropic is not configured"));
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
                String requestBody = buildRequestBody(messages);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpoint()))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
                logger.fine("Sending request to Anthropic: " + config.model());
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return handleResponse(response);
            } catch (java.net.http.HttpTimeoutException e) {
                throw new RuntimeException("Request timed out after " + config.timeoutSeconds() + "s", e);
            } catch (java.net.ConnectException e) {
                throw new RuntimeException("Connection refused to Anthropic API: " + e.getMessage(), e);
            } catch (java.io.IOException e) {
                throw new RuntimeException("IO error during Anthropic API call: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }, executor);
    }

    private String buildRequestBody(List<PromptBuilder.Message> messages) {
        String systemPrompt = null;
        ArrayNode messagesArray = objectMapper.createArrayNode();
        for (PromptBuilder.Message msg : messages) {
            if ("system".equals(msg.role())) {
                systemPrompt = msg.content();
            } else {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "user");
                msgNode.put("content", msg.content());
            }
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("max_tokens", 1024);
        if (systemPrompt != null) {
            root.put("system", systemPrompt);
        }
        root.set("messages", messagesArray);
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
            case 429 -> throw new RuntimeException("Rate limited (429): " + errorMessage);
            case 500 -> throw new RuntimeException("Anthropic server error (500): " + errorMessage);
            default -> throw new RuntimeException("Anthropic API error (" + statusCode + "): " + errorMessage);
        }
    }

    private JsonNode parseSuccess(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.get("content");
            if (content == null || !content.isArray() || content.isEmpty()) {
                throw new RuntimeException("Response missing 'content' array");
            }
            JsonNode first = content.get(0);
            JsonNode text = first.get("text");
            if (text == null || !text.isTextual()) {
                throw new RuntimeException("Response missing text in content");
            }
            return OpenRouterClient.parseContentAsJson(text.asText().trim(), objectMapper);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Anthropic response: " + e.getMessage(), e);
        }
    }

    private String extractError(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.get("error");
            if (error != null) {
                JsonNode message = error.get("message");
                if (message != null && message.isTextual()) return message.asText();
            }
        } catch (Exception ignored) {}
        return "Unknown error (HTTP body: " + (body != null ? body.substring(0, Math.min(100, body.length())) : "empty") + ")";
    }
}
