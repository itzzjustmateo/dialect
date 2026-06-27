package com.vomlabs.dialect.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vomlabs.dialect.config.DialectConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterClientTest {

    private static final Logger LOGGER = Logger.getLogger(OpenRouterClientTest.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DialectConfig.AIConfig config;
    private OpenRouterClient client;

    @BeforeEach
    void setUp() {
        config = new DialectConfig.AIConfig(
            true, "openrouter", "test-key",
            "https://openrouter.ai/api/v1/chat/completions",
            "meta-llama/llama-3-8b-instruct:free",
            0.1, 5, 3, 500
        );
        client = new OpenRouterClient(config, LOGGER);
    }

    @Test
    void testEmptyApiKeyFails() {
        DialectConfig.AIConfig noKey = new DialectConfig.AIConfig(
            true, "openrouter", "", "https://openrouter.ai/api/v1/chat/completions",
            "model", 0.1, 5, 3, 500
        );
        OpenRouterClient badClient = new OpenRouterClient(noKey, LOGGER);

        CompletableFuture<JsonNode> future = badClient.analyzeMessage(
            PromptBuilder.createDetectionPrompt("hello").build()
        );

        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    void testDefaultKeyFails() {
        DialectConfig.AIConfig defaultKey = new DialectConfig.AIConfig(
            true, "openrouter", "your-key-here", "https://openrouter.ai/api/v1/chat/completions",
            "model", 0.1, 5, 3, 500
        );
        OpenRouterClient badClient = new OpenRouterClient(defaultKey, LOGGER);

        CompletableFuture<JsonNode> future = badClient.analyzeMessage(
            PromptBuilder.createDetectionPrompt("hello").build()
        );

        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    void testRateLimiterBlocksAfterExhaustion() {
        RateLimiter limiter = new RateLimiter(1, 10000);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void testRateLimiterResets() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, 10000);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void testExtractTextField() {
        JsonNode node = MAPPER.createObjectNode().put("language", "en");
        String result = AiProvider.extractTextField(node, "language");
        assertEquals("en", result);
    }

    @Test
    void testExtractTextFieldMissing() {
        JsonNode node = MAPPER.createObjectNode();
        String result = AiProvider.extractTextField(node, "nonexistent");
        assertNull(result);
    }

    @Test
    void testExtractDoubleField() {
        JsonNode node = MAPPER.createObjectNode().put("confidence", 0.95);
        double result = AiProvider.extractDoubleField(node, "confidence", 0.0);
        assertEquals(0.95, result, 0.001);
    }

    @Test
    void testExtractDoubleFieldDefault() {
        JsonNode node = MAPPER.createObjectNode();
        double result = AiProvider.extractDoubleField(node, "missing", 0.5);
        assertEquals(0.5, result, 0.001);
    }

    @Test
    void testExtractBooleanField() {
        JsonNode node = MAPPER.createObjectNode().put("slang", true);
        boolean result = AiProvider.extractBooleanField(node, "slang", false);
        assertTrue(result);
    }

    @Test
    void testExtractBooleanFieldDefault() {
        JsonNode node = MAPPER.createObjectNode();
        boolean result = AiProvider.extractBooleanField(node, "missing", true);
        assertTrue(result);
    }

    @Test
    void testExtractAnalysis() {
        String json = "{\"detected_language\": \"en\", \"confidence\": 0.95}";
        var result = AiProvider.extractAnalysis(json);
        assertTrue(result.isPresent());
        assertEquals("en", result.get().get("detected_language").asText());
    }

    @Test
    void testExtractAnalysisInvalid() {
        var result = AiProvider.extractAnalysis("not json");
        assertFalse(result.isPresent());
    }

    @Test
    void testPromptBuilderDetection() {
        List<PromptBuilder.Message> messages = PromptBuilder.createDetectionPrompt("Hello world").build();
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
        assertTrue(messages.get(1).content().contains("Hello world"));
    }

    @Test
    void testPromptBuilderTranslation() {
        List<PromptBuilder.Message> messages = PromptBuilder.createTranslationPrompt("Bonjour", "fr", "en").build();
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).content().contains("Bonjour"));
    }

    @Test
    void testPromptBuilderModeration() {
        List<PromptBuilder.Message> messages = PromptBuilder.createModerationPrompt("bad message", "en", "no swearing").build();
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).content().contains("bad message"));
    }

    @Test
    void testRetryStrategyConstruction() {
        RetryStrategy retry = new RetryStrategy(3, 100, LOGGER);
        assertNotNull(retry);
    }

    @Test
    void testRetryStrategyNoRetries() {
        RetryStrategy retry = new RetryStrategy(0, 100, LOGGER);
        assertNotNull(retry);
    }

    @Test
    void testShutdown() {
        client.shutdown();
        CompletableFuture<JsonNode> future = client.analyzeMessage(
            PromptBuilder.createDetectionPrompt("test").build()
        );
        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    void testRateLimiterGetRemaining() {
        RateLimiter limiter = new RateLimiter(10, 10000);
        assertEquals(10, limiter.getRemainingRequests());
        assertTrue(limiter.tryAcquire());
        assertEquals(9, limiter.getRemainingRequests());
    }

    @Test
    void testRateLimiterIsLimited() {
        RateLimiter limiter = new RateLimiter(1, 10000);
        assertFalse(limiter.isLimited());
        limiter.tryAcquire();
        assertTrue(limiter.isLimited());
    }
}
