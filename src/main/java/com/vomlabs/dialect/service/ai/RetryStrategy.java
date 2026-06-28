package com.vomlabs.dialect.service.ai;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class RetryStrategy {

    private final int maxRetries;
    private final long baseDelayMs;
    private final double factor;
    private final Logger logger;

    public RetryStrategy(int maxRetries, long baseDelayMs, double factor, Logger logger) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.factor = factor;
        this.logger = logger;
    }

    public RetryStrategy(int maxRetries, long baseDelayMs, Logger logger) {
        this(maxRetries, baseDelayMs, 2.0, logger);
    }

    public <T> CompletableFuture<T> executeWithRetry(Supplier<CompletableFuture<T>> operation) {
        return executeWithRetry(operation, 0);
    }

    private <T> CompletableFuture<T> executeWithRetry(Supplier<CompletableFuture<T>> operation, int attempt) {
        return operation.get().handle((result, throwable) -> {
            if (throwable == null) {
                return CompletableFuture.completedFuture(result);
            }

            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            String errorMessage = cause.getMessage() != null ? cause.getMessage() : "Unknown error";

            if (isRetryable(cause) && attempt < maxRetries) {
                long delayMs = calculateDelay(attempt);
                logger.warning("AI request failed (attempt " + (attempt + 1) + "/" + maxRetries + "): "
                    + errorMessage + ". Retrying in " + delayMs + "ms...");
                return delay(delayMs).thenCompose(v -> executeWithRetry(operation, attempt + 1));
            } else {
                if (attempt >= maxRetries) {
                    logger.severe("AI request failed after " + maxRetries + " retries: " + errorMessage);
                }
                return CompletableFuture.<T>failedFuture(new RuntimeException("AI request failed: " + errorMessage, cause));
            }
        }).thenCompose(f -> f);
    }

    private boolean isRetryable(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return true;
        }

        int httpCode = extractHttpCode(message);
        return httpCode == 0
            || httpCode == 429
            || httpCode == 502
            || httpCode == 503
            || httpCode == 504
            || httpCode == 408;
    }

    private int extractHttpCode(String message) {
        try {
            String[] parts = message.split(" ");
            for (String part : parts) {
                if (part.matches("\\d{3}")) {
                    return Integer.parseInt(part);
                }
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    private long calculateDelay(int attempt) {
        return (long) (baseDelayMs * Math.pow(factor, attempt));
    }

    private CompletableFuture<Void> delay(long millis) {
        if (millis <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
