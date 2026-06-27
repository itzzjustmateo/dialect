package com.vomlabs.dialect.service.moderation;

import com.vomlabs.dialect.model.Action;
import com.vomlabs.dialect.model.ChatMessage;
import com.vomlabs.dialect.config.DialectConfig;
import com.vomlabs.dialect.service.ai.AiProvider;
import com.vomlabs.dialect.service.ai.PromptBuilder;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.util.TextSanitizer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class ModerationService {

    private final DialectConfig.ModerationConfig moderationConfig;
    private final DialectConfig.LanguageConfig languageConfig;
    private final AiProvider aiProvider;
    private final CacheService cacheService;
    private final Logger logger;

    public ModerationService(
        DialectConfig.ModerationConfig moderationConfig,
        DialectConfig.LanguageConfig languageConfig,
        AiProvider aiProvider,
        CacheService cacheService,
        Logger logger
    ) {
        this.moderationConfig = moderationConfig;
        this.languageConfig = languageConfig;
        this.aiProvider = aiProvider;
        this.cacheService = cacheService;
        this.logger = logger;
    }

    public ModerationResult evaluate(ChatMessage message) {
        String detectedLanguage = message.detectedLanguage().map(lang -> lang.code()).orElse(null);

        if (message.detectedLanguage().isEmpty()) {
            return new ModerationResult(languageConfig.fallbackBehavior(), "Language could not be detected", false);
        }

        if (!message.meetsConfidenceThreshold(languageConfig.confidenceThreshold())) {
            return new ModerationResult(languageConfig.fallbackBehavior(), "Low confidence detection: " + String.format("%.2f", message.confidence()), false);
        }

        boolean allowed = isLanguageAllowed(detectedLanguage);

        if (allowed) {
            if (moderationConfig.enableSlangValidation() && message.containsSlang() && !message.isValidSlangInContext()) {
                return new ModerationResult(Action.WARN, "Inappropriate slang usage detected", true);
            }
            return new ModerationResult(Action.ALLOW, null, false);
        }

        return new ModerationResult(moderationConfig.onViolation(), "Language not allowed: " + (detectedLanguage != null ? detectedLanguage : "unknown"), true);
    }

    public CompletableFuture<AiModerationResult> evaluateWithAI(ChatMessage message) {
        String sanitized = TextSanitizer.sanitize(message.content());
        String langCode = message.detectedLanguage().map(l -> l.code()).orElse("unknown");

        String cacheKey = "mod:" + sanitized.toLowerCase().hashCode();

        return aiProvider.analyzeMessage(
            PromptBuilder.createModerationPrompt(sanitized, langCode, buildRulesString()).build()
        ).thenApply(response -> {
            try {
                boolean isAllowed = AiProvider.extractBooleanField(response, "is_allowed", true);
                String reason = AiProvider.extractTextField(response, "reason");
                String severity = AiProvider.extractTextField(response, "severity");

                AiModerationResult result = new AiModerationResult(isAllowed, reason, severity);

                cacheService.cacheAnalysis(cacheKey, new CacheService.AnalysisResult(
                    langCode, isAllowed ? 1.0 : 0.0, false, false, null
                ));

                return result;
            } catch (Exception e) {
                logger.warning("Failed to parse AI moderation response: " + e.getMessage());
                return new AiModerationResult(true, null, "low");
            }
        }).exceptionally(throwable -> {
            logger.warning("AI moderation failed: " + throwable.getMessage());
            return new AiModerationResult(true, null, "low");
        });
    }

    private String buildRulesString() {
        StringBuilder rules = new StringBuilder();
        rules.append("Server language mode: ").append(languageConfig.mode()).append(". ");
        rules.append("Allowed languages: ").append(String.join(", ", languageConfig.allowed())).append(". ");
        if (moderationConfig.enableSlangValidation()) {
            rules.append("Slang validation is enabled. ");
        }
        return rules.toString();
    }

    private boolean isLanguageAllowed(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        String normalized = languageCode.toLowerCase().trim();
        boolean inList = languageConfig.allowedSet().contains(normalized);
        return languageConfig.isWhitelist() == inList;
    }

    public record ModerationResult(Action action, String reason, boolean isViolation) {}

    public record AiModerationResult(boolean isAllowed, String reason, String severity) {}
}
