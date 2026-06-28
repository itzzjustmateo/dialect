package com.vomlabs.dialect.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeepLClient {

    private static final String FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate";
    private static final String PRO_ENDPOINT = "https://api.deepl.com/v2/translate";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String endpoint;
    private final boolean useFreePlan;
    private final int timeoutSeconds;
    private final Logger logger;
    private final ExecutorService executor;
    private volatile boolean shutdown;

    public DeepLClient(String apiKey, boolean useFreePlan, int timeoutSeconds, Logger logger) {
        this.apiKey = apiKey;
        this.useFreePlan = useFreePlan;
        this.endpoint = useFreePlan ? FREE_ENDPOINT : PRO_ENDPOINT;
        this.timeoutSeconds = timeoutSeconds;
        this.logger = logger;
        this.objectMapper = new ObjectMapper();
        this.shutdown = false;

        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        this.httpClient = HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("your-key-here");
    }

    public CompletableFuture<String> translate(String text, String sourceLanguage, String targetLanguage) {
        if (shutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("DeepLClient is shut down"));
        }
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new IllegalStateException("DeepL API key is not configured"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestBody = buildRequestBody(text, sourceLanguage, targetLanguage);
                HttpRequest request = buildHttpRequest(requestBody);

                logger.fine("Sending translation request to DeepL (" + (useFreePlan ? "free" : "pro") + ")");

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return handleResponse(response);
            } catch (java.net.http.HttpTimeoutException e) {
                throw new RuntimeException("DeepL request timed out after " + timeoutSeconds + "s", e);
            } catch (java.net.ConnectException e) {
                throw new RuntimeException("Connection refused to DeepL API: " + e.getMessage(), e);
            } catch (java.io.IOException e) {
                throw new RuntimeException("IO error during DeepL API call: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("DeepL request interrupted", e);
            }
        }, executor);
    }

    private String buildRequestBody(String text, String sourceLanguage, String targetLanguage) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode textArray = root.putArray("text");
        textArray.add(text);

        root.put("target_lang", normalizeLanguageCode(targetLanguage));

        String src = normalizeLanguageCode(sourceLanguage);
        if (src != null && !src.isBlank() && !"auto".equalsIgnoreCase(src)) {
            root.put("source_lang", src);
        }

        return root.toString();
    }

    private HttpRequest buildHttpRequest(String requestBody) {
        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "DeepL-Auth-Key " + apiKey)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    }

    private String handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode == 200) {
            return parseTranslationResponse(body);
        }

        String errorMessage = extractErrorMessage(body);
        switch (statusCode) {
            case 400 -> throw new RuntimeException("DeepL bad request (400): " + errorMessage);
            case 403 -> throw new RuntimeException("DeepL authorization failed (403): " + errorMessage);
            case 413 -> throw new RuntimeException("DeepL text too large (413): " + errorMessage);
            case 429 -> throw new RuntimeException("DeepL rate limited (429): " + errorMessage);
            case 456 -> throw new RuntimeException("DeepL quota exceeded (456): " + errorMessage);
            default -> throw new RuntimeException("DeepL API error (" + statusCode + "): " + errorMessage);
        }
    }

    private String parseTranslationResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode translations = root.get("translations");
            if (translations == null || !translations.isArray() || translations.isEmpty()) {
                throw new RuntimeException("DeepL response missing 'translations' array");
            }
            JsonNode first = translations.get(0);
            JsonNode text = first.get("text");
            if (text == null || !text.isTextual()) {
                throw new RuntimeException("DeepL response missing 'text' in translation entry");
            }
            return text.asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DeepL response: " + e.getMessage(), e);
        }
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.get("message");
            if (message != null && message.isTextual()) {
                return message.asText();
            }
        } catch (Exception ignored) {}
        return "Unknown error";
    }

    private String normalizeLanguageCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String upper = code.toUpperCase().trim();
        return switch (upper) {
            case "EN" -> "EN";
            case "DE" -> "DE";
            case "FR" -> "FR";
            case "ES" -> "ES";
            case "IT" -> "IT";
            case "PT" -> "PT";
            case "PT-BR", "PT_BR" -> "PT-BR";
            case "PT-PT", "PT_PT" -> "PT-PT";
            case "NL" -> "NL";
            case "PL" -> "PL";
            case "RU" -> "RU";
            case "ZH", "ZH-CN", "ZH_CN" -> "ZH";
            case "JA" -> "JA";
            case "KO" -> "KO";
            case "AR" -> "AR";
            case "TR" -> "TR";
            case "CS" -> "CS";
            case "DA" -> "DA";
            case "EL" -> "EL";
            case "FI" -> "FI";
            case "HU" -> "HU";
            case "ID" -> "ID";
            case "LV" -> "LV";
            case "LT" -> "LT";
            case "NB", "NO" -> "NB";
            case "RO" -> "RO";
            case "SK" -> "SK";
            case "SL" -> "SL";
            case "SV" -> "SV";
            case "UK" -> "UK";
            case "VI" -> "VI";
            default -> {
                if (code.length() == 2) {
                    yield upper;
                }
                yield null;
            }
        };
    }

    public void shutdown() {
        this.shutdown = true;
        executor.shutdown();
    }
}
