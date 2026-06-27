package com.vomlabs.dialect.service.format;

import com.vomlabs.dialect.config.DialectConfig;
import com.vomlabs.dialect.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.logging.Logger;

public class ChatFormatter {

    private final PlaceholderAPIHook placeholderAPIHook;
    private final LuckPermsHook luckPermsHook;
    private final LPCHook lpcHook;
    private final DialectConfig.ChatFormatConfig config;
    private final Logger logger;

    public ChatFormatter(
        PlaceholderAPIHook placeholderAPIHook,
        LuckPermsHook luckPermsHook,
        LPCHook lpcHook,
        DialectConfig.ChatFormatConfig config,
        Logger logger
    ) {
        this.placeholderAPIHook = placeholderAPIHook;
        this.luckPermsHook = luckPermsHook;
        this.lpcHook = lpcHook;
        this.config = config;
        this.logger = logger;
    }

    public boolean shouldFormat() {
        if (!config.enabled()) return false;
        if (config.preferLpcx() && lpcHook.isLpcxAvailable()) return false;
        if (config.preferLpc() && lpcHook.isLpcAvailable()) return false;
        return true;
    }

    public Component format(Player player, String messageContent) {
        if (!config.enabled()) {
            return ColorUtil.deserializeUncached(messageContent);
        }

        if (config.preferLpcx() && lpcHook.isLpcxAvailable()) {
            return deserializeMessage(messageContent);
        }

        if (config.preferLpc() && lpcHook.isLpcAvailable()) {
            return deserializeMessage(messageContent);
        }

        return formatWithTemplate(player, messageContent);
    }

    private Component formatWithTemplate(Player player, String messageContent) {
        String template = config.template();
        String playerName = player.getName();
        String displayName = player.getDisplayName();
        String world = player.getWorld().getName();

        template = template.replace("%player_name%", playerName);
        template = template.replace("%player%", playerName);
        template = template.replace("%displayname%", displayName);
        template = template.replace("%world%", world);
        template = template.replace("%message%", messageContent);

        String prefix = luckPermsHook.getPrefix(player).orElse("");
        String suffix = luckPermsHook.getSuffix(player).orElse("");
        String primaryGroup = luckPermsHook.getPrimaryGroup(player).orElse("");

        template = template.replace("%luckperms_prefix%", prefix);
        template = template.replace("%luckperms_suffix%", suffix);
        template = template.replace("%luckperms_primary_group%", primaryGroup);
        template = template.replace("%prefix%", prefix);
        template = template.replace("%suffix%", suffix);
        template = template.replace("%primary_group%", primaryGroup);

        template = placeholderAPIHook.setPlaceholders(player, template);

        return ColorUtil.deserializeUncached(template);
    }

    private Component deserializeMessage(String messageContent) {
        return ColorUtil.deserializeUncached(messageContent);
    }
}
