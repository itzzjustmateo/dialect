package com.vomlabs.dialect.config;

import com.vomlabs.dialect.model.Action;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public record DialectConfig(
    AIConfig ai,
    DeepLConfig deepl,
    LanguageConfig languages,
    CacheConfig cache,
    ModerationConfig moderation,
    RedisConfig redis,
    ChatFormatConfig chatFormat,
    EffectsConfig effects
) {
    public DialectConfig {
        if (ai == null) {
            ai = new AIConfig(true, "openrouter", "", "", "", 0.1, 5, 3, 500);
        }
        if (deepl == null) {
            deepl = new DeepLConfig("", true, 5);
        }
        if (languages == null) {
            languages = new LanguageConfig(
                "WHITELIST", List.of("en", "de"), "en", Action.WARN, 0.75);
        }
        if (cache == null) {
            cache = new CacheConfig(10000, 30);
        }
        if (moderation == null) {
            moderation = new ModerationConfig(Action.TRANSLATE, true, 2, true);
        }
        if (redis == null) {
            redis = new RedisConfig(false, "redis://localhost:6379", "", 2, false);
        }
        if (chatFormat == null) {
            chatFormat = new ChatFormatConfig(
                true, "<%luckperms_prefix%><player_name><gray>:</gray> %message%", true, true);
        }
        if (effects == null) {
            effects = new EffectsConfig(true, true);
        }
    }

    public record AIConfig(
        boolean enabled,
        String provider,
        String apiKey,
        String endpoint,
        String model,
        double temperature,
        int timeoutSeconds,
        int maxRetries,
        int backoffBaseMs
    ) {
        public boolean isConfigured() {
            return enabled && apiKey != null && !apiKey.isBlank()
                && !apiKey.equals("your-key-here");
        }
    }

    public record DeepLConfig(
        String apiKey,
        boolean useFreePlan,
        int timeoutSeconds
    ) {
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank() && !apiKey.equals("your-key-here");
        }
    }

    public record LanguageConfig(
        String mode,
        List<String> allowed,
        String serverDefault,
        Action fallbackBehavior,
        double confidenceThreshold
    ) {
        public Set<String> allowedSet() {
            return allowed == null ? Set.of() : new HashSet<>(allowed);
        }
        public boolean isWhitelist() { return "WHITELIST".equalsIgnoreCase(mode); }
        public boolean isBlacklist() { return "BLACKLIST".equalsIgnoreCase(mode); }
    }

    public record CacheConfig(int maximumSize, int expireAfterAccessMinutes) {}

    public record ModerationConfig(
        Action onViolation,
        boolean enableSlangValidation,
        int cooldownSeconds,
        boolean notifyStaff
    ) {}

    public record RedisConfig(
        boolean enabled, String uri, String password, int timeoutSeconds, boolean useSsl
    ) {}

    public record ChatFormatConfig(
        boolean enabled, String template, boolean preferLpc, boolean preferLpcx
    ) {}

    public record EffectsConfig(
        boolean sounds, boolean particles
    ) {}
}
