package com.vomlabs.dialect.gui;

import com.vomlabs.dialect.api.LanguageChangeEvent;
import com.vomlabs.dialect.config.ConfigManager;
import com.vomlabs.dialect.model.Language;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.service.effect.ParticleService;
import com.vomlabs.dialect.service.effect.SoundService;
import com.vomlabs.dialect.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LanguageGUI implements Listener {

    private static final int GUI_SIZE = 54;

    private final CacheService cacheService;
    private final ConfigManager configManager;
    private final SoundService soundService;
    private final ParticleService particleService;

    public LanguageGUI(
        CacheService cacheService,
        ConfigManager configManager,
        SoundService soundService,
        ParticleService particleService
    ) {
        this.cacheService = cacheService;
        this.configManager = configManager;
        this.soundService = soundService;
        this.particleService = particleService;
    }

    public void open(Player player) {
        Map<Integer, Language> languageSlots = new HashMap<>();
        Component title = ColorUtil.deserializeUncached(configManager.config().languageSelection().guiTitle());
        Inventory inv = Bukkit.createInventory(new LanguageHolder(languageSlots), GUI_SIZE, title);

        List<Language> languages = List.of(
            Language.ENGLISH, Language.SPANISH, Language.FRENCH, Language.GERMAN,
            Language.ITALIAN, Language.PORTUGUESE, Language.DUTCH, Language.POLISH,
            Language.SWEDISH, Language.DANISH, Language.FINNISH, Language.NORWEGIAN,
            Language.CZECH, Language.HUNGARIAN, Language.ROMANIAN, Language.UKRAINIAN,
            Language.GREEK, Language.RUSSIAN, Language.CHINESE, Language.JAPANESE,
            Language.KOREAN, Language.VIETNAMESE, Language.THAI, Language.HINDI,
            Language.INDONESIAN, Language.MALAY, Language.HEBREW, Language.ARABIC,
            Language.TURKISH
        );

        Material[] colors = {
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE
        };

        for (int i = 0; i < languages.size() && i < GUI_SIZE - 9; i++) {
            Language lang = languages.get(i);
            Material mat = colors[i % colors.length];
            languageSlots.put(i, lang);
            inv.setItem(i, createLanguageItem(mat, lang));
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);

        for (int i = languages.size(); i < GUI_SIZE - 9; i++) {
            inv.setItem(i, filler);
        }

        for (int i = GUI_SIZE - 9; i < GUI_SIZE - 2; i++) {
            inv.setItem(i, filler);
        }

        Optional<Language> current = cacheService.getUserLanguage(player.getUniqueId());
        ItemStack currentItem;
        if (current.isPresent()) {
            currentItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta curMeta = currentItem.getItemMeta();
            curMeta.displayName(ColorUtil.deserializeUncached(
                configManager.messages().languageSet().replace("{lang}", current.get().displayName())));
            currentItem.setItemMeta(curMeta);
        } else {
            currentItem = new ItemStack(Material.BOOK);
            ItemMeta curMeta = currentItem.getItemMeta();
            curMeta.displayName(ColorUtil.deserializeUncached(
                "<color:#C2C7D3>No language set</color>"));
            currentItem.setItemMeta(curMeta);
        }
        inv.setItem(49, currentItem);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(ColorUtil.deserializeUncached("<color:#D1988C>✕ Close</color>"));
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);

        player.openInventory(inv);
    }

    private ItemStack createLanguageItem(Material material, Language language) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.deserializeUncached(
            "<color:#478FC6>" + language.displayName() + "</color>"));
        meta.lore(List.of(
            ColorUtil.deserializeUncached("<color:#C2C7D3>" + language.code() + "</color>")
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LanguageHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) {
            return;
        }

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        Language selected = holder.languageSlots().get(slot);
        if (selected == null) {
            return;
        }

        Optional<Language> currentLanguage = cacheService.getUserLanguage(player.getUniqueId());
        LanguageChangeEvent changeEvent = new LanguageChangeEvent(
            player,
            currentLanguage.map(Language::code).orElse("unset"),
            selected.code()
        );
        Bukkit.getPluginManager().callEvent(changeEvent);

        if (changeEvent.isCancelled()) {
            return;
        }

        cacheService.setUserLanguage(player.getUniqueId(), selected);
        player.closeInventory();
        player.sendMessage(ColorUtil.deserializeUncached(
            configManager.messages().prefix()
            + configManager.messages().languageSet().replace("{lang}", selected.displayName())
        ));
        soundService.playLanguageSet(player);
        particleService.spawnLanguageParticles(player);
    }

    private record LanguageHolder(Map<Integer, Language> languageSlots) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
