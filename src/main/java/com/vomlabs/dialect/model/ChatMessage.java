package com.vomlabs.dialect.model;

import net.kyori.adventure.text.Component;
import java.util.Optional;
import java.util.UUID;

public record ChatMessage(
    UUID playerId,
    String playerName,
    String content,
    Component originalComponent,
    Optional<Language> detectedLanguage,
    Optional<String> normalizedTranslation,
    double confidence,
    boolean containsSlang,
    boolean isValidSlangInContext
) {
    public ChatMessage {
        if (playerId == null) throw new IllegalArgumentException("playerId must not be null");
        if (playerName == null || playerName.isBlank()) throw new IllegalArgumentException("playerName must not be null or blank");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content must not be null or blank");
        if (originalComponent == null) throw new IllegalArgumentException("originalComponent must not be null");
        if (detectedLanguage == null) detectedLanguage = Optional.empty();
        if (normalizedTranslation == null) normalizedTranslation = Optional.empty();
    }

    public static Builder builder(UUID playerId, String playerName, String content, Component originalComponent) {
        return new Builder(playerId, playerName, content, originalComponent);
    }

    public ChatMessage withTranslation(String translation) {
        return new ChatMessage(
            playerId, playerName, content, originalComponent,
            detectedLanguage, Optional.ofNullable(translation),
            confidence, containsSlang, isValidSlangInContext
        );
    }

    public ChatMessage withDetectedLanguage(Language language, double confidence) {
        return new ChatMessage(
            playerId, playerName, content, originalComponent,
            Optional.ofNullable(language), normalizedTranslation,
            confidence, containsSlang, isValidSlangInContext
        );
    }

    public boolean meetsConfidenceThreshold(double threshold) {
        return confidence >= threshold;
    }

    public static final class Builder {
        private final UUID playerId;
        private final String playerName;
        private final String content;
        private final Component originalComponent;
        private Optional<Language> detectedLanguage = Optional.empty();
        private Optional<String> normalizedTranslation = Optional.empty();
        private double confidence = 0.0;
        private boolean containsSlang = false;
        private boolean isValidSlangInContext = false;

        private Builder(UUID playerId, String playerName, String content, Component originalComponent) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.content = content;
            this.originalComponent = originalComponent;
        }

        public Builder detectedLanguage(Language language) {
            this.detectedLanguage = Optional.ofNullable(language);
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder normalizedTranslation(String translation) {
            this.normalizedTranslation = Optional.ofNullable(translation);
            return this;
        }

        public Builder containsSlang(boolean containsSlang) {
            this.containsSlang = containsSlang;
            return this;
        }

        public Builder isValidSlangInContext(boolean isValidSlangInContext) {
            this.isValidSlangInContext = isValidSlangInContext;
            return this;
        }

        public ChatMessage build() {
            return new ChatMessage(
                playerId, playerName, content, originalComponent,
                detectedLanguage, normalizedTranslation,
                confidence, containsSlang, isValidSlangInContext
            );
        }
    }
}
