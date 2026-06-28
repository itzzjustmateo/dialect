package com.vomlabs.dialect.service.format;

import org.bukkit.Bukkit;
import java.util.logging.Logger;

public class LPCHook {

    private static final String LPC_PLUGIN_NAME = "LightPluginChat";
    private static final String LPCX_PLUGIN_NAME = "LPCX";

    private final boolean lpcAvailable;
    private final boolean lpcxAvailable;
    private final Logger logger;

    public LPCHook(Logger logger) {
        this.logger = logger;
        this.lpcAvailable = Bukkit.getPluginManager().getPlugin(LPC_PLUGIN_NAME) != null;
        this.lpcxAvailable = Bukkit.getPluginManager().getPlugin(LPCX_PLUGIN_NAME) != null;

        if (lpcAvailable) {
            logger.info("LightPluginChat (LPC) detected.");
        }
        if (lpcxAvailable) {
            logger.info("LPCX detected.");
        }
    }

    public boolean isLpcAvailable() {
        return lpcAvailable;
    }

    public boolean isLpcxAvailable() {
        return lpcxAvailable;
    }

    public boolean isAnyAvailable() {
        return lpcAvailable || lpcxAvailable;
    }

    public boolean shouldUseLpc(boolean preferLpc, boolean preferLpcx) {
        if (preferLpcx && lpcxAvailable) {
            return false;
        }
        if (preferLpc && lpcAvailable) {
            return true;
        }
        if (lpcxAvailable) {
            return false;
        }
        return lpcAvailable;
    }

    public boolean shouldUseLpcx(boolean preferLpc, boolean preferLpcx) {
        if (preferLpcx && lpcxAvailable) {
            return true;
        }
        if (preferLpc && lpcAvailable) {
            return false;
        }
        return false;
    }
}
