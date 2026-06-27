package com.vomlabs.dialect.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vomlabs.dialect.config.DialectConfig;
import com.vomlabs.dialect.model.Language;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CacheService {

    private final Cache<UUID, Language> userLanguageCache;
    private final Cache<String, AnalysisResult> analysisCache;
    private final DialectConfig.CacheConfig config;
    private RedisService redisService;

    public CacheService(DialectConfig.CacheConfig config) {
        this.config = config;

        this.userLanguageCache = Caffeine.newBuilder()
            .maximumSize(config.maximumSize())
            .expireAfterAccess(config.expireAfterAccessMinutes(), TimeUnit.MINUTES)
            .build();

        this.analysisCache = Caffeine.newBuilder()
            .maximumSize(config.maximumSize())
            .expireAfterAccess(config.expireAfterAccessMinutes(), TimeUnit.MINUTES)
            .build();
    }

    public void setRedisService(RedisService redisService) {
        this.redisService = redisService;
    }

    public void setUserLanguage(UUID playerId, Language language) {
        userLanguageCache.put(playerId, language);
        if (redisService != null && redisService.isConnected()) {
            String key = "dialect:lang:" + playerId.toString();
            redisService.set(key, language.code(), (int) TimeUnit.MINUTES.toSeconds(config.expireAfterAccessMinutes()));
        }
    }

    public Optional<Language> getUserLanguage(UUID playerId) {
        Language cached = userLanguageCache.getIfPresent(playerId);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (redisService != null && redisService.isConnected()) {
            String key = "dialect:lang:" + playerId.toString();
            try {
                return redisService.get(key).thenApply(opt -> opt.flatMap(code -> {
                    Language lang = Language.fromCode(code).orElse(null);
                    if (lang != null) {
                        userLanguageCache.put(playerId, lang);
                    }
                    return Optional.ofNullable(lang);
                })).get();
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public void removeUserLanguage(UUID playerId) {
        userLanguageCache.invalidate(playerId);
        if (redisService != null && redisService.isConnected()) {
            String key = "dialect:lang:" + playerId.toString();
            redisService.delete(key);
        }
    }

    public boolean hasUserLanguage(UUID playerId) {
        return userLanguageCache.getIfPresent(playerId) != null;
    }

    public void cacheAnalysis(String key, AnalysisResult result) {
        analysisCache.put(key, result);
    }

    public Optional<AnalysisResult> getCachedAnalysis(String key) {
        return Optional.ofNullable(analysisCache.getIfPresent(key));
    }

    public void invalidateAnalysis(String key) {
        analysisCache.invalidate(key);
    }

    public void clearAll() {
        userLanguageCache.invalidateAll();
        analysisCache.invalidateAll();
    }

    public long userLanguageCacheSize() {
        return userLanguageCache.estimatedSize();
    }

    public long analysisCacheSize() {
        return analysisCache.estimatedSize();
    }

    public record AnalysisResult(
        String detectedLanguage,
        double confidence,
        boolean containsSlang,
        boolean isValidSlangInContext,
        String normalizedTranslation
    ) {}
}
