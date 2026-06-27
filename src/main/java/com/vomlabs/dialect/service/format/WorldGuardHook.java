package com.vomlabs.dialect.service.format;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.logging.Logger;

public class WorldGuardHook {

    private final Logger logger;
    private boolean worldGuardAvailable;
    private boolean worldEditAvailable;
    private Object worldGuard;
    private Object worldGuardPlatform;

    public WorldGuardHook(Logger logger) {
        this.logger = logger;
        detect();
    }

    private void detect() {
        try {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
                Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                worldGuard = wgClass.getMethod("getInstance").invoke(null);
                worldGuardPlatform = wgClass.getMethod("getPlatform").invoke(worldGuard);
                worldGuardAvailable = true;
                logger.info("WorldGuard hook: available");
            }
        } catch (Exception e) {
            worldGuardAvailable = false;
        }

        try {
            if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
                worldEditAvailable = true;
                logger.info("WorldEdit/FAWE hook: available");
            }
        } catch (Exception e) {
            worldEditAvailable = false;
        }
    }

    public boolean isWorldGuardAvailable() { return worldGuardAvailable; }
    public boolean isWorldEditAvailable() { return worldEditAvailable; }
    public boolean isAnyAvailable() { return worldGuardAvailable || worldEditAvailable; }

    public boolean isInRegion(Player player, String regionId) {
        if (!worldGuardAvailable) {
            return false;
        }
        try {
            Object regionContainer = worldGuardPlatform.getClass()
                .getMethod("getRegionContainer")
                .invoke(worldGuardPlatform);
            if (regionContainer == null) {
                return false;
            }

            Object query = regionContainer.getClass()
                .getMethod("createQuery")
                .invoke(regionContainer);
            if (query == null) {
                return false;
            }

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adapter = bukkitAdapterClass.getDeclaredConstructor().newInstance();
            Object wgLoc = bukkitAdapterClass.getMethod("adapt", org.bukkit.Location.class)
                .invoke(adapter, player.getLocation());
            Object wgWorld = bukkitAdapterClass.getMethod("adapt", org.bukkit.World.class)
                .invoke(adapter, player.getWorld());

            @SuppressWarnings("unchecked")
            Set<String> regions = (Set<String>) query.getClass()
                .getMethod("getApplicableRegidentsIdentifiers",
                    Class.forName("com.sk89q.worldedit.world.World"),
                    Class.forName("com.sk89q.worldedit.util.Location"))
                .invoke(query, wgWorld, wgLoc);

            return regions.contains(regionId);
        } catch (Exception e) {
            return false;
        }
    }
}
