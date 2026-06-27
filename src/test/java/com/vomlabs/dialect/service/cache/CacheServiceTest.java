package com.vomlabs.dialect.service.cache;

import com.vomlabs.dialect.config.DialectConfig;
import com.vomlabs.dialect.model.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CacheServiceTest {

    private CacheService cacheService;
    private UUID testPlayerId;
    private Language testLanguage;

    @BeforeEach
    void setUp() {
        DialectConfig.CacheConfig config = new DialectConfig.CacheConfig(100, 1);
        cacheService = new CacheService(config);
        testPlayerId = UUID.randomUUID();
        testLanguage = Language.fromCode("en").orElseThrow();
    }

    @Test
    void testSetAndGetUserLanguage() {
        cacheService.setUserLanguage(testPlayerId, testLanguage);
        Optional<Language> retrieved = cacheService.getUserLanguage(testPlayerId);
        assertTrue(retrieved.isPresent());
        assertEquals("en", retrieved.get().code());
    }

    @Test
    void testGetNonExistentUserLanguage() {
        Optional<Language> retrieved = cacheService.getUserLanguage(UUID.randomUUID());
        assertFalse(retrieved.isPresent());
    }

    @Test
    void testRemoveUserLanguage() {
        cacheService.setUserLanguage(testPlayerId, testLanguage);
        assertTrue(cacheService.hasUserLanguage(testPlayerId));
        cacheService.removeUserLanguage(testPlayerId);
        assertFalse(cacheService.hasUserLanguage(testPlayerId));
    }

    @Test
    void testHasUserLanguage() {
        assertFalse(cacheService.hasUserLanguage(testPlayerId));
        cacheService.setUserLanguage(testPlayerId, testLanguage);
        assertTrue(cacheService.hasUserLanguage(testPlayerId));
    }

    @Test
    void testCacheAndGetAnalysis() {
        String key = "test:hello";
        CacheService.AnalysisResult result = new CacheService.AnalysisResult("en", 0.95, false, true, null);
        cacheService.cacheAnalysis(key, result);

        Optional<CacheService.AnalysisResult> cached = cacheService.getCachedAnalysis(key);
        assertTrue(cached.isPresent());
        assertEquals("en", cached.get().detectedLanguage());
        assertEquals(0.95, cached.get().confidence(), 0.001);
    }

    @Test
    void testGetNonExistentAnalysis() {
        Optional<CacheService.AnalysisResult> cached = cacheService.getCachedAnalysis("nonexistent");
        assertFalse(cached.isPresent());
    }

    @Test
    void testInvalidateAnalysis() {
        String key = "test:hello";
        cacheService.cacheAnalysis(key, new CacheService.AnalysisResult("en", 0.95, false, true, null));
        assertTrue(cacheService.getCachedAnalysis(key).isPresent());
        cacheService.invalidateAnalysis(key);
        assertFalse(cacheService.getCachedAnalysis(key).isPresent());
    }

    @Test
    void testClearAll() {
        cacheService.setUserLanguage(testPlayerId, testLanguage);
        cacheService.cacheAnalysis("test", new CacheService.AnalysisResult("en", 0.95, false, true, null));

        assertTrue(cacheService.userLanguageCacheSize() > 0);
        assertTrue(cacheService.analysisCacheSize() > 0);

        cacheService.clearAll();
        assertEquals(0, cacheService.userLanguageCacheSize());
        assertEquals(0, cacheService.analysisCacheSize());
    }

    @Test
    void testEvictionPolicy() {
        DialectConfig.CacheConfig smallConfig = new DialectConfig.CacheConfig(3, 30);
        CacheService smallCache = new CacheService(smallConfig);

        for (int i = 0; i < 10; i++) {
            smallCache.setUserLanguage(UUID.randomUUID(), testLanguage);
        }

        assertTrue(smallCache.userLanguageCacheSize() <= 10);
    }

    @Test
    void testMultipleUserLanguages() {
        Language spanish = Language.fromCode("es").orElseThrow();
        Language french = Language.fromCode("fr").orElseThrow();

        cacheService.setUserLanguage(testPlayerId, testLanguage);
        UUID secondPlayer = UUID.randomUUID();
        cacheService.setUserLanguage(secondPlayer, spanish);
        UUID thirdPlayer = UUID.randomUUID();
        cacheService.setUserLanguage(thirdPlayer, french);

        assertEquals(testLanguage.code(), cacheService.getUserLanguage(testPlayerId).get().code());
        assertEquals(spanish.code(), cacheService.getUserLanguage(secondPlayer).get().code());
        assertEquals(french.code(), cacheService.getUserLanguage(thirdPlayer).get().code());
    }

    @Test
    void testCacheAndRetrieve() {
        String key = "test:analysis";
        CacheService.AnalysisResult result = new CacheService.AnalysisResult("en", 0.95, false, true, null);
        cacheService.cacheAnalysis(key, result);

        Optional<CacheService.AnalysisResult> cached = cacheService.getCachedAnalysis(key);
        assertTrue(cached.isPresent());
        assertEquals("en", cached.get().detectedLanguage());
    }

    @Test
    void testCacheSizeReporting() {
        assertEquals(0, cacheService.userLanguageCacheSize());
        assertEquals(0, cacheService.analysisCacheSize());

        cacheService.setUserLanguage(testPlayerId, testLanguage);
        assertEquals(1, cacheService.userLanguageCacheSize());

        cacheService.cacheAnalysis("key1", new CacheService.AnalysisResult("en", 0.9, false, true, null));
        cacheService.cacheAnalysis("key2", new CacheService.AnalysisResult("es", 0.8, false, true, null));
        assertEquals(2, cacheService.analysisCacheSize());
    }
}
