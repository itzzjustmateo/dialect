package com.vomlabs.dialect.service.format;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Logger;

public class GeyserHook {

    private final Logger logger;
    private boolean geyserAvailable;
    private boolean floodgateAvailable;
    private Object floodgateApi;

    public GeyserHook(Logger logger) {
        this.logger = logger;
        detect();
    }

    private void detect() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
                geyserAvailable = true;
                logger.info("Geyser hook: available");
            }
        } catch (Exception e) {
            geyserAvailable = false;
        }

        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                floodgateAvailable = true;
                logger.info("Floodgate hook: available");
            }
        } catch (Exception e) {
            floodgateAvailable = false;
        }
    }

    public boolean isGeyserAvailable() { return geyserAvailable; }
    public boolean isFloodgateAvailable() { return floodgateAvailable; }

    public boolean isBedrockPlayer(Player player) {
        if (!floodgateAvailable || floodgateApi == null) {
            return false;
        }
        try {
            return (boolean) floodgateApi.getClass()
                .getMethod("isFloodgatePlayer", UUID.class)
                .invoke(floodgateApi, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (!floodgateAvailable || floodgateApi == null) {
            return false;
        }
        try {
            return (boolean) floodgateApi.getClass()
                .getMethod("isFloodgatePlayer", UUID.class)
                .invoke(floodgateApi, uuid);
        } catch (Exception e) {
            return false;
        }
    }
}
