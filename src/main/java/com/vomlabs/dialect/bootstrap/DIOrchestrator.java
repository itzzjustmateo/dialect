package com.vomlabs.dialect.bootstrap;

import com.vomlabs.dialect.command.LazyDialectCommand;
import com.vomlabs.dialect.command.LanguageCommand;
import com.vomlabs.dialect.config.ConfigManager;
import com.vomlabs.dialect.listener.ChatListener;
import com.vomlabs.dialect.listener.PlayerQuitListener;
import com.vomlabs.dialect.service.ai.AiProvider;
import com.vomlabs.dialect.service.ai.AiProviderFactory;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.service.cache.RedisService;
import com.vomlabs.dialect.service.detection.DetectionService;
import com.vomlabs.dialect.service.effect.ParticleService;
import com.vomlabs.dialect.service.effect.SoundService;
import com.vomlabs.dialect.service.format.ChatFormatter;
import com.vomlabs.dialect.service.format.GeyserHook;
import com.vomlabs.dialect.service.format.LPCHook;
import com.vomlabs.dialect.service.format.LuckPermsHook;
import com.vomlabs.dialect.service.format.PlaceholderAPIHook;
import com.vomlabs.dialect.service.format.VaultHook;
import com.vomlabs.dialect.service.format.WorldGuardHook;
import com.vomlabs.dialect.service.moderation.ModerationService;
import com.vomlabs.dialect.service.translation.DeepLClient;
import com.vomlabs.dialect.service.translation.TranslationService;
import com.vomlabs.dialect.service.update.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;

import java.util.logging.Logger;

public class DIOrchestrator {

    private final LazyDialectPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;

    private AiProvider aiProvider;
    private DeepLClient deepLClient;
    private CacheService cacheService;
    private RedisService redisService;
    private PlaceholderAPIHook placeholderAPIHook;
    private LuckPermsHook luckPermsHook;
    private LPCHook lpcHook;
    private VaultHook vaultHook;
    private WorldGuardHook worldGuardHook;
    private GeyserHook geyserHook;
    private ChatFormatter chatFormatter;
    private UpdateChecker updateChecker;
    private DetectionService detectionService;
    private TranslationService translationService;
    private ModerationService moderationService;
    private SoundService soundService;
    private ParticleService particleService;
    private ChatListener chatListener;
    private PlayerQuitListener playerQuitListener;
    private LazyDialectCommand lazyDialectCommand;
    private LanguageCommand languageCommand;

    public DIOrchestrator(LazyDialectPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.logger();
        this.configManager = new ConfigManager(plugin);
    }

    public void initialize() {
        logger.info("Initializing LazyDialect services...");

        configManager.load();

        cacheService = new CacheService(configManager.config().cache());
        redisService = new RedisService(configManager.config().redis(), logger);
        redisService.initialize();
        cacheService.setRedisService(redisService);

        aiProvider = AiProviderFactory.create(configManager.config().ai(), logger);
        deepLClient = new DeepLClient(
            configManager.config().deepl().apiKey(),
            configManager.config().deepl().useFreePlan(),
            configManager.config().deepl().timeoutSeconds(),
            logger
        );

        placeholderAPIHook = new PlaceholderAPIHook(logger);
        luckPermsHook = new LuckPermsHook(logger);
        lpcHook = new LPCHook(logger);
        vaultHook = new VaultHook(logger);
        worldGuardHook = new WorldGuardHook(logger);
        geyserHook = new GeyserHook(logger);

        chatFormatter = new ChatFormatter(
            placeholderAPIHook, luckPermsHook, lpcHook,
            configManager.config().chatFormat(), logger
        );

        soundService = new SoundService(configManager.config().effects().sounds(), logger);
        particleService = new ParticleService(configManager.config().effects().particles(), logger);

        updateChecker = new UpdateChecker(plugin, plugin.getPluginMeta().getVersion(), logger);
        updateChecker.checkAsync();

        detectionService = new DetectionService(
            aiProvider, cacheService, configManager.config().languages(),
            configManager.config().ai(), logger
        );

        translationService = new TranslationService(
            aiProvider, deepLClient,
            configManager.config().ai(), configManager.config().deepl(),
            configManager.config().languages(),
            configManager.messages(), logger
        );

        moderationService = new ModerationService(
            configManager.config().moderation(), configManager.config().languages(),
            aiProvider, cacheService, logger
        );

        chatListener = new ChatListener(
            detectionService, translationService, moderationService,
            cacheService, configManager, chatFormatter, soundService, particleService, logger
        );

        playerQuitListener = new PlayerQuitListener(cacheService);

        lazyDialectCommand = new LazyDialectCommand(
            plugin, configManager, detectionService, translationService,
            cacheService, aiProvider, soundService, particleService, logger
        );

        languageCommand = new LanguageCommand(
            cacheService, configManager, soundService, particleService, logger
        );

        logProviderStatus();
        registerListeners();
        registerCommands();
    }

    public void reinitializeServices() {
        aiProvider.shutdown();
        deepLClient.shutdown();
        redisService.shutdown();

        cacheService = new CacheService(configManager.config().cache());
        redisService = new RedisService(configManager.config().redis(), logger);
        redisService.initialize();
        cacheService.setRedisService(redisService);

        aiProvider = AiProviderFactory.create(configManager.config().ai(), logger);
        deepLClient = new DeepLClient(
            configManager.config().deepl().apiKey(),
            configManager.config().deepl().useFreePlan(),
            configManager.config().deepl().timeoutSeconds(),
            logger
        );

        detectionService = new DetectionService(
            aiProvider, cacheService, configManager.config().languages(),
            configManager.config().ai(), logger
        );

        translationService = new TranslationService(
            aiProvider, deepLClient,
            configManager.config().ai(), configManager.config().deepl(),
            configManager.config().languages(),
            configManager.messages(), logger
        );

        moderationService = new ModerationService(
            configManager.config().moderation(), configManager.config().languages(),
            aiProvider, cacheService, logger
        );

        soundService = new SoundService(configManager.config().effects().sounds(), logger);
        particleService = new ParticleService(configManager.config().effects().particles(), logger);

        chatListener = new ChatListener(
            detectionService, translationService, moderationService,
            cacheService, configManager, chatFormatter, soundService, particleService, logger
        );

        chatFormatter = new ChatFormatter(
            placeholderAPIHook, luckPermsHook, lpcHook,
            configManager.config().chatFormat(), logger
        );

        logger.info("Services reinitialized after reload.");
    }

    public void shutdown() {
        logger.info("Shutting down LazyDialect services...");
        if (aiProvider != null) {
            aiProvider.shutdown();
        }
        if (deepLClient != null) {
            deepLClient.shutdown();
        }
        if (redisService != null) {
            redisService.shutdown();
        }
        if (cacheService != null) {
            cacheService.clearAll();
        }
    }

    private void logProviderStatus() {
        boolean aiEnabled = configManager.config().ai().enabled();
        boolean hasOpenRouter = configManager.config().ai().isConfigured();
        boolean hasDeepL = configManager.config().deepl().isConfigured();
        boolean hasRedis = configManager.config().redis().enabled();

        if (!aiEnabled) {
            logger.info("AI features disabled via config.");
        }

        if (hasOpenRouter) {
            String provider = configManager.config().ai().provider();
            logger.info("AI provider: " + provider + " ("
                + configManager.config().ai().model() + ")");
        } else if (hasDeepL) {
            logger.info("Translation provider: DeepL (fallback mode)");
        } else if (aiEnabled) {
            logger.warning("No AI provider configured. Set 'ai.api_key'"
                + " or 'deepl.api_key' in config.yml");
        }

        if (hasRedis) {
            logger.info("Redis caching: "
                + (redisService.isConnected() ? "Connected" : "Disconnected"));
        }

        if (vaultHook.isAvailable()) {
            logger.info("Vault hook: available");
        }
        if (worldGuardHook.isAnyAvailable()) {
            logger.info("WorldGuard/WE hook: WG="
                + worldGuardHook.isWorldGuardAvailable()
                + " WE=" + worldGuardHook.isWorldEditAvailable());
        }
        if (geyserHook.isGeyserAvailable() || geyserHook.isFloodgateAvailable()) {
            logger.info("Geyser/Floodgate hook: Geyser="
                + geyserHook.isGeyserAvailable()
                + " Floodgate=" + geyserHook.isFloodgateAvailable());
        }
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(chatListener, plugin);
        pm.registerEvents(playerQuitListener, plugin);
        logger.info("Registered event listeners.");
    }

    private void registerCommands() {
        registerCommand("lazydialect", lazyDialectCommand);
        registerCommand("language", languageCommand);
        logger.info("Registered commands.");
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            if (executor instanceof LazyDialectCommand dc) {
                cmd.setExecutor(dc);
                cmd.setTabCompleter(dc);
            } else if (executor instanceof LanguageCommand lc) {
                cmd.setExecutor(lc);
                cmd.setTabCompleter(lc);
            }
        } else {
            logger.warning("Command '" + name + "' is not defined in plugin.yml");
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public AiProvider getAiProvider() { return aiProvider; }
    public DeepLClient getDeepLClient() { return deepLClient; }
    public CacheService getCacheService() { return cacheService; }
    public RedisService getRedisService() { return redisService; }
    public DetectionService getDetectionService() { return detectionService; }
    public TranslationService getTranslationService() { return translationService; }
    public ModerationService getModerationService() { return moderationService; }
    public ChatFormatter getChatFormatter() { return chatFormatter; }
    public LazyDialectCommand getLazyDialectCommand() { return lazyDialectCommand; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public VaultHook getVaultHook() { return vaultHook; }
    public WorldGuardHook getWorldGuardHook() { return worldGuardHook; }
    public GeyserHook getGeyserHook() { return geyserHook; }
}
