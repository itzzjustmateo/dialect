package com.vomlabs.dialect.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class OpenAiProvider implements AiProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RetryStrategy retryStrategy;
    private final RateLimiter rateLimiter;
    private final Logger logger;
    private final DialectConfig.AIConfig config;
    private final ExecutorService executor;
    private volatile boolean shutdown;

    public OpenAiProvider(DialectConfig.AIConfig config, Logger logger) {
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
            return CompletableFuture.failedFuture(new IllegalStateException("OpenAI is not configured"));
        }
        if (!rateLimiter.tryAcquire()) {
            long resetTime = rateLimiter.getResetTimeMillis();
            return CompletableFuture.failedFuture(
                new RateLimitExceededException("Rate limit exceeded. Resets in " + (resetTime / 1000) + "s", resetTime)
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
                String requestBody = OpenRouterClient.buildOpenAiRequestBody(messages, config.model(), config.temperature(), objectMapper);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
                logger.fine("Sending request to OpenAI: " + config.model());
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return OpenRouterClient.handleOpenAiResponse(response, objectMapper);
            } catch (java.net.http.HttpTimeoutException e) {
                throw new RuntimeException("Request timed out after " + config.timeoutSeconds() + "s", e);
            } catch (java.net.ConnectException e) {
                throw new RuntimeException("Connection refused to OpenAI API: " + e.getMessage(), e);
            } catch (java.io.IOException e) {
                throw new RuntimeException("IO error during OpenAI API call: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }, executor);
    }

    public static class RateLimitExceededException extends RuntimeException {
        private final long resetTimeMillis;
        public RateLimitExceededException(String message, long resetTimeMillis) {
            super(message);
            this.resetTimeMillis = resetTimeMillis;
        }
        public long getResetTimeMillis() { return resetTimeMillis; }
    }
}
