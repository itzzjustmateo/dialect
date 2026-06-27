package com.vomlabs.dialect.service.format;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;
import java.util.logging.Logger;

public class VaultHook {

    private final Logger logger;
    private boolean vaultAvailable;
    private Object economy;
    private Object permission;

    public VaultHook(Logger logger) {
        this.logger = logger;
        detectVault();
    }

    private void detectVault() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                RegisteredServiceProvider<?> econProvider = Bukkit.getServicesManager()
                    .getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"));
                if (econProvider != null) {
                    economy = econProvider.getProvider();
                }

                RegisteredServiceProvider<?> permProvider = Bukkit.getServicesManager()
                    .getRegistration(Class.forName("net.milkbowl.vault.permission.Permission"));
                if (permProvider != null) {
                    permission = permProvider.getProvider();
                }

                vaultAvailable = (economy != null || permission != null);
                if (vaultAvailable) {
                    logger.info("Vault hook: economy=" + (economy != null) + " permission=" + (permission != null));
                }
            }
        } catch (Exception e) {
            vaultAvailable = false;
        }
    }

    public boolean isAvailable() { return vaultAvailable; }

    public Optional<Double> getBalance(Player player) {
        if (!vaultAvailable || economy == null) {
            return Optional.empty();
        }
        try {
            var result = economy.getClass().getMethod("getBalance", Player.class).invoke(economy, player);
            return Optional.of((Double) result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean hasPermission(Player player, String permissionNode) {
        if (!vaultAvailable || permission == null) {
            return false;
        }
        try {
            return (boolean) permission.getClass()
                .getMethod("playerHas", String.class, Player.class, String.class)
                .invoke(permission, null, player, permissionNode);
        } catch (Exception e) {
            return false;
        }
    }
}
