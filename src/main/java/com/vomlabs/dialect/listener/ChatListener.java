package com.vomlabs.dialect.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import com.vomlabs.dialect.config.ConfigManager;
import com.vomlabs.dialect.config.MessagesConfig;
import com.vomlabs.dialect.model.Action;
import com.vomlabs.dialect.model.ChatMessage;
import com.vomlabs.dialect.model.Language;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.service.detection.DetectionService;
import com.vomlabs.dialect.service.effect.ParticleService;
import com.vomlabs.dialect.service.effect.SoundService;
import com.vomlabs.dialect.service.format.ChatFormatter;
import com.vomlabs.dialect.service.moderation.ModerationService;
import com.vomlabs.dialect.service.translation.TranslationService;
import com.vomlabs.dialect.util.ColorUtil;
import com.vomlabs.dialect.util.ComponentExtractor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ChatListener implements Listener {

    private final DetectionService detectionService;
    private final TranslationService translationService;
    private final ModerationService moderationService;
    private final CacheService cacheService;
    private final ConfigManager configManager;
    private final ChatFormatter chatFormatter;
    private final SoundService soundService;
    private final ParticleService particleService;
    private final Logger logger;
    private final Map<UUID, Long> cooldowns;

    public ChatListener(
        DetectionService detectionService,
        TranslationService translationService,
        ModerationService moderationService,
        CacheService cacheService,
        ConfigManager configManager,
        ChatFormatter chatFormatter,
        SoundService soundService,
        ParticleService particleService,
        Logger logger
    ) {
        this.detectionService = detectionService;
        this.translationService = translationService;
        this.moderationService = moderationService;
        this.cacheService = cacheService;
        this.configManager = configManager;
        this.chatFormatter = chatFormatter;
        this.soundService = soundService;
        this.particleService = particleService;
        this.logger = logger;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("lazydialect.bypass")) {
            applyChatFormat(player, event);
            return;
        }

        if (isOnCooldown(player)) {
            String rateMsg = configManager.messages().rateLimited();
            player.sendMessage(ColorUtil.deserializeUncached(rateMsg));
            player.sendActionBar(ColorUtil.deserializeUncached(rateMsg));
            soundService.playActionFail(player);
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        String plainText = ComponentExtractor.extractAndNormalize(event.message());
        if (plainText.isBlank()) {
            return;
        }

        ChatMessage initialMessage = ChatMessage.builder(player.getUniqueId(), player.getName(), plainText, event.message()).build();

        player.sendActionBar(ColorUtil.deserializeUncached(configManager.messages().actionbarProcessing()));
        processChatAsync(player, initialMessage, event);
    }

    private void processChatAsync(Player player, ChatMessage initialMessage, AsyncChatEvent event) {
        detectionService.detect(initialMessage).thenCompose(detected -> {
            ModerationService.ModerationResult result = moderationService.evaluate(detected);
            return handleModerationResult(player, detected, result, event, 0);
        }).exceptionally(throwable -> {
            logger.severe("Chat processing error for " + player.getName() + ": " + throwable.getMessage());
            handleFallback(player, initialMessage, event);
            return null;
        });
    }

    private CompletableFuture<Void> handleModerationResult(
        Player player, ChatMessage message, ModerationService.ModerationResult result,
        AsyncChatEvent event, int depth
    ) {
        if (depth > 3) {
            handleFallback(player, message, event);
            return CompletableFuture.completedFuture(null);
        }

        switch (result.action()) {
            case ALLOW -> {
                applyChatFormat(player, event, message);
                event.setCancelled(false);
                setCooldown(player);
                MessagesConfig msg = configManager.messages();
                player.sendActionBar(ColorUtil.deserializeUncached(msg.actionbarAllowed()));
                soundService.playMessageAllowed(player);
                particleService.spawnAllowParticles(player);
            }
            case DENY -> {
                MessagesConfig msg = configManager.messages();
                Component warning = ColorUtil.deserializeUncached(msg.prefix() + msg.violationWarning());
                player.sendMessage(warning);
                player.sendActionBar(ColorUtil.deserializeUncached(msg.actionbarBlocked()));
                soundService.playMessageDenied(player);
                particleService.spawnDenyParticles(player);
            }
            case WARN -> {
                MessagesConfig msg = configManager.messages();
                Component warning = ColorUtil.deserializeUncached(msg.prefix() + msg.violationWarning());
                player.sendMessage(warning);
                player.sendActionBar(ColorUtil.deserializeUncached(msg.actionbarWarned()));
                soundService.playMessageWarned(player);
                particleService.spawnWarnParticles(player);
                if (result.isViolation()) {
                    notifyStaff(player, message, result.reason());
                }
            }
            case TRANSLATE -> {
                String sourceLang = message.detectedLanguage().map(Language::code).orElse("unknown");
                return translationService.translateWithFormatting(message.content(), sourceLang)
                    .thenAccept(translated -> {
                        String formatted = translationService.formatTranslation(message.content(), translated, sourceLang);
                        MessagesConfig msg = configManager.messages();
                        Component translatedComponent = ColorUtil.deserializeUncached(msg.prefix() + formatted);

                        String provider = configManager.config().ai().isConfigured() ? "AI" : "DeepL";
                        Component icon = Component.text(" \u2139")
                            .color(TextColor.color(0x478FC6))
                            .hoverEvent(HoverEvent.showText(
                                ColorUtil.deserializeUncached("<color:#C2C7D3>Translated via</color> <color:#478FC6>" + provider + "</color>")
                            ));
                        translatedComponent = translatedComponent.append(icon);

                        event.message(translatedComponent);
                        applyChatFormat(player, event, message);
                        event.setCancelled(false);
                        setCooldown(player);

                        String actionbarMsg = msg.actionbarTranslated().replace("{lang}", sourceLang != null ? sourceLang.toUpperCase() : "UNKNOWN");
                        player.sendActionBar(ColorUtil.deserializeUncached(actionbarMsg));
                        soundService.playTranslationComplete(player);
                        particleService.spawnTranslationParticles(player);

                        if (result.isViolation()) {
                            notifyStaff(player, message, result.reason());
                        }
                    });
            }
            case SUGGEST_CORRECTION -> {
                String sourceLang = message.detectedLanguage().map(Language::code).orElse("unknown");
                return translationService.translate(message.content(), sourceLang)
                    .thenAccept(translated -> {
                        MessagesConfig msg = configManager.messages();
                        String correctionMsg = msg.correctionSuggested()
                            .replace("{correction}", translated != null ? translated : message.content());
                        player.sendMessage(ColorUtil.deserializeUncached(msg.prefix() + correctionMsg));
                        if (result.isViolation()) {
                            notifyStaff(player, message, result.reason());
                        }
                    });
            }
            case LOG_ONLY -> {
                logger.info("[LOG_ONLY] " + player.getName() + " [" + message.detectedLanguage().map(Language::code).orElse("?") + "]: " + message.content());
                applyChatFormat(player, event, message);
                event.setCancelled(false);
                setCooldown(player);
                soundService.playDetectionComplete(player);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private void applyChatFormat(Player player, AsyncChatEvent event) {
        Component formatted = chatFormatter.format(player, ComponentExtractor.extractPlainText(event.message()));
        event.message(formatted);
    }

    private void applyChatFormat(Player player, AsyncChatEvent event, ChatMessage message) {
        if (chatFormatter.shouldFormat()) {
            Component formatted = chatFormatter.format(player, ComponentExtractor.extractPlainText(event.message()));
            event.message(formatted);
        }
    }

    private void notifyStaff(Player player, ChatMessage message, String reason) {
        var modConfig = configManager.config().moderation();
        if (!modConfig.notifyStaff()) {
            return;
        }

        String langCode = message.detectedLanguage().map(Language::code).orElse("unknown");
        String staffMsg = "<color:#478FC6>◈</color> <color:#C2C7D3>" + player.getName() + "</color> <color:#C2C7D3>sent a message in</color> <color:#478FC6>" + langCode + "</color><color:#C2C7D3>:</color> "
            + "<hover:show_text:'<color:#D1988C>" + reason + "</color>'><color:#C2C7D3>" + message.content() + "</color></hover>";

        Component staffComponent = ColorUtil.deserializeUncached(staffMsg);
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("lazydialect.admin") && !online.equals(player)) {
                online.sendMessage(staffComponent);
            }
        }
    }

    private void handleFallback(Player player, ChatMessage message, AsyncChatEvent event) {
        logger.warning("Chat processing failed for " + player.getName() + ", using fallback behavior");
        var langConfig = configManager.config().languages();
        Action fallbackAction = langConfig.fallbackBehavior();

        switch (fallbackAction) {
            case ALLOW -> {
                applyChatFormat(player, event, message);
                event.setCancelled(false);
                setCooldown(player);
            }
            case WARN -> {
                MessagesConfig msg = configManager.messages();
                player.sendMessage(ColorUtil.deserializeUncached(msg.prefix() + msg.violationWarning()));
            }
            default -> {
                MessagesConfig msg = configManager.messages();
                player.sendMessage(ColorUtil.deserializeUncached(msg.prefix() + msg.aiUnavailable()));
            }
        }
    }

    private boolean isOnCooldown(Player player) {
        var modConfig = configManager.config().moderation();
        if (modConfig.cooldownSeconds() <= 0) {
            return false;
        }
        if (player.hasPermission("lazydialect.bypass")) {
            return false;
        }

        Long lastMessage = cooldowns.get(player.getUniqueId());
        if (lastMessage == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - lastMessage;
        return elapsed < modConfig.cooldownSeconds() * 1000L;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
}
