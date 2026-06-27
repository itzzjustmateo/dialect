package com.vomlabs.dialect.service.translation;

import com.vomlabs.dialect.config.DialectConfig;
import com.vomlabs.dialect.config.MessagesConfig;
import com.vomlabs.dialect.service.ai.AiProvider;
import com.vomlabs.dialect.service.ai.PromptBuilder;
import com.vomlabs.dialect.util.TextSanitizer;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class TranslationService {

    private final AiProvider aiProvider;
    private final DeepLClient deepLClient;
    private final DialectConfig.AIConfig aiConfig;
    private final DialectConfig.DeepLConfig deepLConfig;
    private final DialectConfig.LanguageConfig languageConfig;
    private final MessagesConfig messagesConfig;
    private final Logger logger;

    public TranslationService(
        AiProvider aiProvider,
        DeepLClient deepLClient,
        DialectConfig.AIConfig aiConfig,
        DialectConfig.DeepLConfig deepLConfig,
        DialectConfig.LanguageConfig languageConfig,
        MessagesConfig messagesConfig,
        Logger logger
    ) {
        this.aiProvider = aiProvider;
        this.deepLClient = deepLClient;
        this.aiConfig = aiConfig;
        this.deepLConfig = deepLConfig;
        this.languageConfig = languageConfig;
        this.messagesConfig = messagesConfig;
        this.logger = logger;
    }

    public CompletableFuture<String> translate(String text, String sourceLanguage) {
        String targetLanguage = languageConfig.serverDefault();
        return translate(text, sourceLanguage, targetLanguage);
    }

    public CompletableFuture<String> translate(String text, String sourceLanguage, String targetLanguage) {
        if (text == null || text.isBlank()) {
            return CompletableFuture.completedFuture(text);
        }
        if (sourceLanguage == null || sourceLanguage.equalsIgnoreCase(targetLanguage)) {
            return CompletableFuture.completedFuture(text);
        }

        String sanitized = TextSanitizer.sanitize(text);

        if (aiConfig.isConfigured()) {
            logger.fine("Translating via OpenRouter: " + sourceLanguage + " -> " + targetLanguage);
            return translateViaOpenRouter(sanitized, sourceLanguage, targetLanguage)
                .thenCompose(result -> {
                    if (!result.equals(sanitized)) {
                        return CompletableFuture.completedFuture(result);
                    }
                    return tryDeepLTranslate(sanitized, sourceLanguage, targetLanguage);
                });
        }

        return tryDeepLTranslate(sanitized, sourceLanguage, targetLanguage);
    }

    private CompletableFuture<String> translateViaOpenRouter(String text, String sourceLanguage, String targetLanguage) {
        return aiProvider.analyzeMessage(
            PromptBuilder.createTranslationPrompt(text, sourceLanguage, targetLanguage).build()
        ).thenApply(response -> {
            try {
                String translated = AiProvider.extractTextField(response, "translated_text");
                if (translated != null && !translated.isBlank()) {
                    return translated;
                }
                String content = response.toString();
                int quoteStart = content.indexOf('"');
                int quoteEnd = content.lastIndexOf('"');
                if (quoteStart != -1 && quoteEnd > quoteStart + 1) {
                    return content.substring(quoteStart + 1, quoteEnd);
                }
                return text;
            } catch (Exception e) {
                logger.warning("Failed to extract OpenRouter translation: " + e.getMessage());
                return text;
            }
        }).exceptionally(throwable -> {
            logger.warning("OpenRouter translation failed: " + throwable.getMessage() + " - falling back to DeepL");
            try {
                return deepLClient.translate(text, sourceLanguage, targetLanguage).get();
            } catch (Exception e2) {
                logger.warning("DeepL fallback also failed: " + e2.getMessage());
                return text;
            }
        });
    }

    private CompletableFuture<String> tryDeepLTranslate(String text, String sourceLanguage, String targetLanguage) {
        if (!deepLConfig.isConfigured()) {
            logger.warning("No translation provider configured (neither OpenRouter nor DeepL)");
            return CompletableFuture.completedFuture(text);
        }

        logger.fine("Translating via DeepL: " + sourceLanguage + " -> " + targetLanguage);
        return deepLClient.translate(text, sourceLanguage, targetLanguage)
            .exceptionally(throwable -> {
                logger.warning("DeepL translation failed: " + throwable.getMessage());
                return text;
            });
    }

    public CompletableFuture<String> translateWithFormatting(String text, String sourceLanguage) {
        FormattingGuard guard = new FormattingGuard();
        String tokenized = guard.tokenize(text);
        return translate(tokenized, sourceLanguage)
            .thenApply(guard::restore);
    }

    public String formatTranslation(String originalMessage, String translatedMessage, String sourceLanguage) {
        String format = messagesConfig.translationFormat();
        return format
            .replace("{from}", sourceLanguage != null ? sourceLanguage.toUpperCase() : "UNKNOWN")
            .replace("{message}", translatedMessage != null ? translatedMessage : originalMessage);
    }
}
