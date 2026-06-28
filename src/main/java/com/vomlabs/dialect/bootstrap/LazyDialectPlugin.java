package com.vomlabs.dialect.bootstrap;

import com.vomlabs.dialect.api.LazyDialectAPI;
import com.vomlabs.dialect.model.Language;
import com.vomlabs.dialect.model.ChatMessage;
import com.vomlabs.dialect.util.ComponentExtractor;
import com.vomlabs.dialect.util.ColorUtil;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class LazyDialectPlugin extends JavaPlugin implements LazyDialectAPI {

    private DIOrchestrator dependencyInjector;
    private boolean ready;
    private Logger logger;

    @Override
    public void onEnable() {
        this.logger = getLogger();
        this.ready = false;

        this.dependencyInjector = new DIOrchestrator(this);
        this.dependencyInjector.initialize();

        this.ready = true;
        logger.info("LazyDialect v" + getPluginMeta().getVersion() + " enabled successfully.");
        logger.info("AI configured: "
            + dependencyInjector.getConfigManager().config().ai().isConfigured());
    }

    @Override
    public void onDisable() {
        this.ready = false;
        if (dependencyInjector != null) {
            dependencyInjector.shutdown();
        }
        logger.info("LazyDialect disabled.");
    }

    public DIOrchestrator getDependencyInjector() {
        return dependencyInjector;
    }

    public Logger logger() {
        return logger != null ? logger : getLogger();
    }

    // ─── LazyDialectAPI Implementation ───

    @Override
    public CompletableFuture<ChatMessage> analyzeMessage(Player player, String message) {
        if (!ready || player == null || message == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Plugin is not ready"));
        }
        ChatMessage msg = ChatMessage.builder(
            player.getUniqueId(), player.getName(), message,
            ColorUtil.deserializeUncached(message)
        ).build();

        return dependencyInjector.getDetectionService().detect(msg)
            .thenCompose(detected -> {
                var modResult = dependencyInjector.getModerationService().evaluate(detected);
                return CompletableFuture.completedFuture(detected);
            });
    }

    @Override
    public CompletableFuture<ChatMessage> detectLanguage(Player player, String message) {
        if (!ready || player == null || message == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Plugin is not ready"));
        }
        ChatMessage msg = ChatMessage.builder(
            player.getUniqueId(), player.getName(), message,
            ColorUtil.deserializeUncached(message)
        ).build();
        return dependencyInjector.getDetectionService().detect(msg);
    }

    @Override
    public CompletableFuture<String> translate(
        String text, String sourceLanguage, String targetLanguage
    ) {
        if (!ready) {
            return CompletableFuture.failedFuture(new IllegalStateException("Plugin is not ready"));
        }
        return dependencyInjector.getTranslationService()
            .translate(text, sourceLanguage, targetLanguage);
    }

    @Override
    public Optional<Language> getPlayerLanguage(UUID playerId) {
        if (!ready || playerId == null) {
            return Optional.empty();
        }
        return dependencyInjector.getCacheService().getUserLanguage(playerId);
    }

    @Override
    public void setPlayerLanguage(UUID playerId, Language language) {
        if (ready && playerId != null && language != null) {
            dependencyInjector.getCacheService().setUserLanguage(playerId, language);
        }
    }

    @Override
    public void clearPlayerLanguage(UUID playerId) {
        if (ready && playerId != null) {
            dependencyInjector.getCacheService().removeUserLanguage(playerId);
        }
    }

    @Override
    public boolean isLanguageAllowed(String languageCode) {
        if (!ready || languageCode == null) {
            return true;
        }
        return dependencyInjector.getDetectionService().isLanguageAllowed(languageCode);
    }

    @Override
    public boolean isPlayerBypassing(Player player) {
        return player != null && player.hasPermission("lazydialect.bypass");
    }

    @Override
    public boolean isPluginReady() {
        return ready;
    }

    @Override
    public void reloadPlugin() {
        dependencyInjector.getConfigManager().reload();
        dependencyInjector.reinitializeServices();
    }
}
