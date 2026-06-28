package com.vomlabs.dialect.command;

import com.vomlabs.dialect.config.ConfigManager;
import com.vomlabs.dialect.gui.LanguageGUI;
import com.vomlabs.dialect.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

public class LanguageCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final LanguageGUI languageGUI;
    @SuppressWarnings("unused")
    private final Logger logger;

    public LanguageCommand(
        ConfigManager configManager,
        LanguageGUI languageGUI,
        Logger logger
    ) {
        this.configManager = configManager;
        this.languageGUI = languageGUI;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender, @NotNull Command command,
        @NotNull String label, @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix()
                + "<color:#D1988C>✕ <color:#C2C7D3>This command can only be used by players.</color>"));
            return true;
        }

        if (!player.hasPermission("lazydialect.command.language")) {
            player.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix()
                + configManager.messages().noPermission()));
            return true;
        }

        if (!configManager.config().languageSelection().enabled()) {
            player.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix()
                + configManager.messages().languageDisabled()));
            return true;
        }

        languageGUI.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender, @NotNull Command command,
        @NotNull String alias, @NotNull String[] args
    ) {
        return List.of();
    }
}
