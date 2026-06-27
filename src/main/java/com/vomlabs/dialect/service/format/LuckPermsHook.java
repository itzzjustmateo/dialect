package com.vomlabs.dialect.service.format;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class LuckPermsHook {

    private static final String PLUGIN_NAME = "LuckPerms";
    private final boolean available;
    private final Logger logger;
    private Method getUserMethod;
    private Method getMetaDataMethod;
    private Method getPrefixMethod;
    private Method getSuffixMethod;
    private Method getPrimaryGroupMethod;
    private Object userManager;

    public LuckPermsHook(Logger logger) {
        this.logger = logger;
        this.available = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null;
        if (available) {
            try {
                Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Method getMethod = luckPermsClass.getMethod("get");
                Object api = getMethod.invoke(null);

                Class<?> luckPermsApi = Class.forName("net.luckperms.api.LuckPerms");
                Method getUserManagerMethod = luckPermsApi.getMethod("getUserManager");
                userManager = getUserManagerMethod.invoke(api);

                Class<?> userManagerClass = Class.forName("net.luckperms.api.user.UserManager");
                getUserMethod = userManagerClass.getMethod("getUser", UUID.class);

                Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
                Method getCachedDataMethod = userClass.getMethod("getCachedData");
                Class<?> cachedDataClass = Class.forName("net.luckperms.api.cacheddata.UserCachedData");
                getMetaDataMethod = cachedDataClass.getMethod("getMetaData");

                Class<?> metaDataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
                getPrefixMethod = metaDataClass.getMethod("getPrefix");
                getSuffixMethod = metaDataClass.getMethod("getSuffix");
                getPrimaryGroupMethod = userClass.getMethod("getPrimaryGroup");

                logger.info("LuckPerms detected - prefix/suffix resolution enabled.");
            } catch (Exception e) {
                logger.warning("LuckPerms found but failed to load API: " + e.getMessage());
            }
        }
    }

    public Optional<String> getPrefix(Player player) {
        if (!available || player == null) return Optional.empty();
        try {
            Object user = getUserMethod.invoke(userManager, player.getUniqueId());
            if (user == null) return Optional.empty();
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = getMetaDataMethod.invoke(cachedData);
            String prefix = (String) getPrefixMethod.invoke(metaData);
            return Optional.ofNullable(prefix);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<String> getSuffix(Player player) {
        if (!available || player == null) return Optional.empty();
        try {
            Object user = getUserMethod.invoke(userManager, player.getUniqueId());
            if (user == null) return Optional.empty();
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = getMetaDataMethod.invoke(cachedData);
            String suffix = (String) getSuffixMethod.invoke(metaData);
            return Optional.ofNullable(suffix);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<String> getPrimaryGroup(Player player) {
        if (!available || player == null) return Optional.empty();
        try {
            Object user = getUserMethod.invoke(userManager, player.getUniqueId());
            if (user == null) return Optional.empty();
            String group = (String) getPrimaryGroupMethod.invoke(user);
            return Optional.ofNullable(group);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
