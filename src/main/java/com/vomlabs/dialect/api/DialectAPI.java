package com.vomlabs.dialect.api;

import com.vomlabs.dialect.model.Language;
import com.vomlabs.dialect.model.ChatMessage;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DialectAPI {

    CompletableFuture<ChatMessage> analyzeMessage(Player player, String message);

    CompletableFuture<ChatMessage> detectLanguage(Player player, String message);

    CompletableFuture<String> translate(String text, String sourceLanguage, String targetLanguage);

    Optional<Language> getPlayerLanguage(UUID playerId);

    void setPlayerLanguage(UUID playerId, Language language);

    void clearPlayerLanguage(UUID playerId);

    boolean isLanguageAllowed(String languageCode);

    boolean isPlayerBypassing(Player player);

    boolean isPluginReady();

    void reloadPlugin();
}
