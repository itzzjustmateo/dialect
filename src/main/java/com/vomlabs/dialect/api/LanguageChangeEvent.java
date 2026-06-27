package com.vomlabs.dialect.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class LanguageChangeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String fromLanguage;
    private final String toLanguage;
    private boolean cancelled;

    public LanguageChangeEvent(@NotNull Player player, String fromLanguage, String toLanguage) {
        super(player);
        this.fromLanguage = fromLanguage;
        this.toLanguage = toLanguage;
        this.cancelled = false;
    }

    public String getFromLanguage() {
        return fromLanguage;
    }

    public String getToLanguage() {
        return toLanguage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
