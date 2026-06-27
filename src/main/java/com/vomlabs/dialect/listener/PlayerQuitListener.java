package com.vomlabs.dialect.listener;

import com.vomlabs.dialect.service.cache.CacheService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final CacheService cacheService;

    public PlayerQuitListener(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cacheService.removeUserLanguage(event.getPlayer().getUniqueId());
    }
}
