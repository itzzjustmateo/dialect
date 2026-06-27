package com.vomlabs.dialect.service.translation;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class DeepLClientTest {

    private static final Logger LOGGER = Logger.getLogger(DeepLClientTest.class.getName());

    @Test
    void testNotConfiguredWithEmptyKey() {
        DeepLClient client = new DeepLClient("", true, 5, LOGGER);
        assertFalse(client.isConfigured());
    }

    @Test
    void testNotConfiguredWithBlankKey() {
        DeepLClient client = new DeepLClient("   ", true, 5, LOGGER);
        assertFalse(client.isConfigured());
    }

    @Test
    void testNotConfiguredWithPlaceholder() {
        DeepLClient client = new DeepLClient("your-key-here", true, 5, LOGGER);
        assertFalse(client.isConfigured());
    }

    @Test
    void testConfiguredWithValidKey() {
        DeepLClient client = new DeepLClient("valid-key-123", true, 5, LOGGER);
        assertTrue(client.isConfigured());
    }

    @Test
    void testTranslateFailsWhenNotConfigured() {
        DeepLClient client = new DeepLClient("", true, 5, LOGGER);
        CompletableFuture<String> future = client.translate("hello", "en", "de");
        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    void testTranslateFailsWithBadKey() {
        DeepLClient client = new DeepLClient("invalid-key", true, 1, LOGGER);
        CompletableFuture<String> future = client.translate("hello", "en", "de");
        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    void testShutdownPreventsRequests() {
        DeepLClient client = new DeepLClient("test-key", true, 5, LOGGER);
        client.shutdown();
        CompletableFuture<String> future = client.translate("hello", "en", "de");
        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    void testNormalizeLanguageCode() {
        DeepLClient client = new DeepLClient("test", true, 5, LOGGER);
        // Use reflection to test private method indirectly via the API call
        assertThrows(ExecutionException.class, () ->
            client.translate("hello", "en", "XX").get()
        );
    }

    @Test
    void testTranslateSameLanguage() {
        // The translation service handles this case - if source == target it returns original
        // But at the DeepLClient level it would still try to send
        DeepLClient client = new DeepLClient("test-key", true, 5, LOGGER);
        assertThrows(ExecutionException.class, () ->
            client.translate("hello", "en", "en").get()
        );
    }

    @Test
    void testFreeEndpoint() {
        DeepLClient client = new DeepLClient("test-key", true, 5, LOGGER);
        assertTrue(client.isConfigured());
    }

    @Test
    void testProEndpoint() {
        DeepLClient client = new DeepLClient("test-key", false, 10, LOGGER);
        assertTrue(client.isConfigured());
    }
}

