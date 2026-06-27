package com.vomlabs.dialect.command;

import com.vomlabs.dialect.api.LanguageChangeEvent;
import com.vomlabs.dialect.config.ConfigManager;
import com.vomlabs.dialect.model.Language;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LanguageCommand implements CommandExecutor, TabCompleter {

    private final CacheService cacheService;
    private final ConfigManager configManager;

    public LanguageCommand(CacheService cacheService, ConfigManager configManager) {
        this.cacheService = cacheService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.deserializeUncached(configManager.messages().prefix() + "<red>This command can only be used by players.</red>"));
            return true;
        }

        if (!player.hasPermission("dialect.command.language")) {
            player.sendMessage(ColorUtil.deserializeUncached(configManager.messages().prefix() + configManager.messages().noPermission()));
            return true;
        }

        if (args.length == 0) {
            Optional<Language> current = cacheService.getUserLanguage(player.getUniqueId());
            if (current.isPresent()) {
                player.sendMessage(ColorUtil.deserializeUncached(
                    configManager.messages().prefix() + "<gray>Your current language: <lang:" + current.get().code() + ">" + current.get().displayName() + "</lang></gray>"
                ));
            } else {
                player.sendMessage(ColorUtil.deserializeUncached(
                    configManager.messages().prefix() + "<gray>You have not set a preferred language.</gray>"
                ));
            }
            return true;
        }

        String langCode = args[0].toLowerCase().trim();
        Optional<Language> language = Language.fromCode(langCode);

        if (language.isEmpty()) {
            player.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<red>Invalid language code: " + langCode + ". Use ISO 639-1 codes (e.g., en, es, fr).</red>"
            ));
            return true;
        }

        Optional<Language> currentLanguage = cacheService.getUserLanguage(player.getUniqueId());
        LanguageChangeEvent event = new LanguageChangeEvent(
            player,
            currentLanguage.map(Language::code).orElse("unset"),
            language.get().code()
        );

        org.bukkit.Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return true;
        }

        cacheService.setUserLanguage(player.getUniqueId(), language.get());
        player.sendMessage(ColorUtil.deserializeUncached(
            configManager.messages().prefix() + "<green>Language set to <lang:" + language.get().code() + ">" + language.get().displayName() + "</lang>.</green>"
        ));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            List.of("en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ko", "ar", "nl", "pl", "tr", "vi", "th", "sv", "da", "fi", "no", "cs", "hu", "ro", "uk", "el", "he", "hi", "id", "ms")
                .forEach(lang -> {
                    if (lang.startsWith(input)) completions.add(lang);
                });
            return completions;
        }
        return List.of();
    }
}
