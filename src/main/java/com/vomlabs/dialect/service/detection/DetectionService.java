package com.vomlabs.dialect.service.detection;

import com.fasterxml.jackson.databind.JsonNode;
import com.vomlabs.dialect.config.DialectConfig;
import com.vomlabs.dialect.model.ChatMessage;
import com.vomlabs.dialect.model.Language;
import com.vomlabs.dialect.service.ai.AiProvider;
import com.vomlabs.dialect.service.ai.PromptBuilder;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.util.TextSanitizer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class DetectionService {

    private final AiProvider aiProvider;
    private final CacheService cacheService;
    private final DialectConfig.LanguageConfig languageConfig;
    private final DialectConfig.AIConfig aiConfig;
    private final Logger logger;

    public DetectionService(
        AiProvider aiProvider,
        CacheService cacheService,
        DialectConfig.LanguageConfig languageConfig,
        DialectConfig.AIConfig aiConfig,
        Logger logger
    ) {
        this.aiProvider = aiProvider;
        this.cacheService = cacheService;
        this.languageConfig = languageConfig;
        this.aiConfig = aiConfig;
        this.logger = logger;
    }

    public CompletableFuture<ChatMessage> detect(ChatMessage message) {
        String sanitized = TextSanitizer.sanitize(message.content());
        if (sanitized.isBlank()) {
            return CompletableFuture.completedFuture(message);
        }

        if (!aiConfig.isConfigured()) {
            logger.fine("AI disabled, skipping detection for: " + sanitized);
            return CompletableFuture.completedFuture(message);
        }

        String cacheKey = "detect:" + sanitized.toLowerCase();

        Optional<CacheService.AnalysisResult> cached = cacheService.getCachedAnalysis(cacheKey);
        if (cached.isPresent()) {
            CacheService.AnalysisResult result = cached.get();
            Optional<Language> lang = Language.fromCode(result.detectedLanguage());
            return CompletableFuture.completedFuture(
                new ChatMessage(
                    message.playerId(), message.playerName(), message.content(), message.originalComponent(),
                    lang, Optional.ofNullable(result.normalizedTranslation()),
                    result.confidence(), result.containsSlang(), result.isValidSlangInContext()
                )
            );
        }

        return aiProvider.analyzeMessage(
            PromptBuilder.createDetectionPrompt(sanitized).build()
        ).thenApply(response -> parseDetectionResponse(message, response, cacheKey));
    }

    private ChatMessage parseDetectionResponse(ChatMessage original, JsonNode response, String cacheKey) {
        try {
            String detectedCode = AiProvider.extractTextField(response, "detected_language");
            double confidence = AiProvider.extractDoubleField(response, "confidence", 0.0);
            boolean containsSlang = AiProvider.extractBooleanField(response, "contains_slang", false);
            boolean isValidSlang = AiProvider.extractBooleanField(response, "is_valid_slang_in_context", true);
            String normalizedTranslation = AiProvider.extractTextField(response, "normalized_translation");

            Optional<Language> detectedLanguage = Language.fromCode(detectedCode != null ? detectedCode : "");

            ChatMessage result = new ChatMessage(
                original.playerId(), original.playerName(), original.content(), original.originalComponent(),
                detectedLanguage, Optional.ofNullable(normalizedTranslation),
                confidence, containsSlang, isValidSlang
            );

            if (detectedCode != null) {
                cacheService.cacheAnalysis(cacheKey, new CacheService.AnalysisResult(
                    detectedCode, confidence, containsSlang, isValidSlang, normalizedTranslation
                ));
            } else {
                logger.warning("Detection response missing detected_language field for message: " + original.content());
            }

            return result;
        } catch (Exception e) {
            logger.warning("Failed to parse detection response: " + e.getMessage());
            return original;
        }
    }

    public boolean isLanguageAllowed(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return !languageConfig.isWhitelist();
        }

        String normalized = languageCode.toLowerCase().trim();
        boolean inList = languageConfig.allowedSet().contains(normalized);

        if (languageConfig.isWhitelist()) {
            return inList;
        } else {
            return !inList;
        }
    }

    public boolean meetsConfidenceThreshold(double confidence) {
        return confidence >= languageConfig.confidenceThreshold();
    }

    public boolean shouldValidateSlang() {
        return languageConfig.confidenceThreshold() > 0;
    }
}
