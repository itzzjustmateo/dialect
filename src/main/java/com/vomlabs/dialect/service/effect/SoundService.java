package com.vomlabs.dialect.service.effect;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.logging.Logger;

public class SoundService {

    private static final Sound SOUND_TRANSLATE = Sound.BLOCK_AMETHYST_CLUSTER_STEP;
    private static final Sound SOUND_DENY = Sound.BLOCK_NOTE_BLOCK_BASS;
    private static final Sound SOUND_WARN = Sound.ENTITY_VILLAGER_NO;
    private static final Sound SOUND_ALLOW = Sound.BLOCK_NOTE_BLOCK_PLING;
    private static final Sound SOUND_DETECT = Sound.BLOCK_NOTE_BLOCK_HAT;
    private static final Sound SOUND_RELOAD = Sound.BLOCK_ANVIL_USE;
    private static final Sound SOUND_CACHE_CLEAR = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final Sound SOUND_LANGUAGE_SET = Sound.ENTITY_PLAYER_LEVELUP;
    private static final Sound SOUND_ACTION_FAIL = Sound.ENTITY_ENDERMAN_TELEPORT;
    private static final Sound SOUND_STAFF_NOTIFY = Sound.BLOCK_NOTE_BLOCK_BELL;

    private final boolean enabled;
    private final Logger logger;

    public SoundService(boolean enabled, Logger logger) {
        this.enabled = enabled;
        this.logger = logger;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void playTranslationComplete(Player player) {
        play(player, SOUND_TRANSLATE, 1.0f, 1.2f);
    }

    public void playMessageDenied(Player player) {
        play(player, SOUND_DENY, 0.8f, 0.5f);
    }

    public void playMessageWarned(Player player) {
        play(player, SOUND_WARN, 1.0f, 1.0f);
    }

    public void playMessageAllowed(Player player) {
        play(player, SOUND_ALLOW, 1.0f, 1.5f);
    }

    public void playDetectionComplete(Player player) {
        play(player, SOUND_DETECT, 1.0f, 1.0f);
    }

    public void playReloadComplete(Player player) {
        play(player, SOUND_RELOAD, 1.0f, 1.0f);
    }

    public void playCacheCleared(Player player) {
        play(player, SOUND_CACHE_CLEAR, 1.0f, 1.5f);
    }

    public void playLanguageSet(Player player) {
        play(player, SOUND_LANGUAGE_SET, 1.0f, 1.8f);
    }

    public void playActionFail(Player player) {
        play(player, SOUND_ACTION_FAIL, 1.0f, 0.8f);
    }

    public void playStaffNotify(Player player) {
        play(player, SOUND_STAFF_NOTIFY, 0.7f, 0.9f);
    }

    private void play(Player player, Sound sound, float volume, float pitch) {
        if (!enabled || player == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
