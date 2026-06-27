package com.vomlabs.dialect.service.format;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class PlaceholderAPIHook {

    private static final String PLUGIN_NAME = "PlaceholderAPI";
    private final boolean available;
    private final Logger logger;
    private Method setPlaceholdersMethod;

    public PlaceholderAPIHook(Logger logger) {
        this.logger = logger;
        this.available = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null;
        if (available) {
            try {
                Class<?> apiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholdersMethod = apiClass.getMethod("setPlaceholders", Player.class, String.class);
                logger.info("PlaceholderAPI detected - placeholder expansion enabled.");
            } catch (Exception e) {
                logger.warning("PlaceholderAPI found but failed to load API: " + e.getMessage());
            }
        }
    }

    public String setPlaceholders(Player player, String text) {
        if (!available || player == null || text == null || setPlaceholdersMethod == null) {
            return text;
        }
        try {
            Object result = setPlaceholdersMethod.invoke(null, player, text);
            return result != null ? (String) result : text;
        } catch (Exception e) {
            logger.warning("Failed to expand PlaceholderAPI placeholders: " + e.getMessage());
            return text;
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
