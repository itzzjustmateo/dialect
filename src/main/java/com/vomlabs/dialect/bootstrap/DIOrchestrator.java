package com.vomlabs.dialect.bootstrap;

import com.vomlabs.dialect.command.DialectCommand;
import com.vomlabs.dialect.command.LanguageCommand;
import com.vomlabs.dialect.config.ConfigManager;
import com.vomlabs.dialect.listener.ChatListener;
import com.vomlabs.dialect.listener.PlayerQuitListener;
import com.vomlabs.dialect.service.ai.OpenRouterClient;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.service.cache.RedisService;
import com.vomlabs.dialect.service.detection.DetectionService;
import com.vomlabs.dialect.service.format.ChatFormatter;
import com.vomlabs.dialect.service.format.LPCHook;
import com.vomlabs.dialect.service.format.LuckPermsHook;
import com.vomlabs.dialect.service.format.PlaceholderAPIHook;
import com.vomlabs.dialect.service.moderation.ModerationService;
import com.vomlabs.dialect.service.translation.DeepLClient;
import com.vomlabs.dialect.service.translation.TranslationService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;

import java.util.logging.Logger;

public class DIOrchestrator {

    private final DialectPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;

    private OpenRouterClient openRouterClient;
    private DeepLClient deepLClient;
    private CacheService cacheService;
    private RedisService redisService;
    private PlaceholderAPIHook placeholderAPIHook;
    private LuckPermsHook luckPermsHook;
    private LPCHook lpcHook;
    private ChatFormatter chatFormatter;
    private DetectionService detectionService;
    private TranslationService translationService;
    private ModerationService moderationService;
    private ChatListener chatListener;
    private PlayerQuitListener playerQuitListener;
    private DialectCommand dialectCommand;
    private LanguageCommand languageCommand;

    public DIOrchestrator(DialectPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.logger();
        this.configManager = new ConfigManager(plugin);
    }

    public void initialize() {
        logger.info("Initializing Dialect services...");

        configManager.load();

        cacheService = new CacheService(configManager.config().cache());
        redisService = new RedisService(configManager.config().redis(), logger);
        redisService.initialize();
        cacheService.setRedisService(redisService);

        openRouterClient = new OpenRouterClient(configManager.config().ai(), logger);
        deepLClient = new DeepLClient(
            configManager.config().deepl().apiKey(),
            configManager.config().deepl().useFreePlan(),
            configManager.config().deepl().timeoutSeconds(),
            logger
        );

        placeholderAPIHook = new PlaceholderAPIHook(logger);
        luckPermsHook = new LuckPermsHook(logger);
        lpcHook = new LPCHook(logger);

        chatFormatter = new ChatFormatter(
            placeholderAPIHook, luckPermsHook, lpcHook,
            configManager.config().chatFormat(), logger
        );

        detectionService = new DetectionService(
            openRouterClient, cacheService, configManager.config().languages(),
            configManager.config().ai(), logger
        );

        translationService = new TranslationService(
            openRouterClient, deepLClient,
            configManager.config().ai(), configManager.config().deepl(),
            configManager.config().languages(),
            configManager.messages(), logger
        );

        moderationService = new ModerationService(
            configManager.config().moderation(), configManager.config().languages(),
            openRouterClient, cacheService, logger
        );

        chatListener = new ChatListener(
            detectionService, translationService, moderationService,
            cacheService, configManager, chatFormatter, logger
        );

        playerQuitListener = new PlayerQuitListener(cacheService);

        dialectCommand = new DialectCommand(
            plugin, configManager, detectionService, translationService,
            cacheService, openRouterClient, logger
        );

        languageCommand = new LanguageCommand(cacheService, configManager);

        logProviderStatus();
        registerListeners();
        registerCommands();
    }

    public void reinitializeServices() {
        openRouterClient.shutdown();
        deepLClient.shutdown();
        redisService.shutdown();

        cacheService = new CacheService(configManager.config().cache());
        redisService = new RedisService(configManager.config().redis(), logger);
        redisService.initialize();
        cacheService.setRedisService(redisService);

        openRouterClient = new OpenRouterClient(configManager.config().ai(), logger);
        deepLClient = new DeepLClient(
            configManager.config().deepl().apiKey(),
            configManager.config().deepl().useFreePlan(),
            configManager.config().deepl().timeoutSeconds(),
            logger
        );

        detectionService = new DetectionService(
            openRouterClient, cacheService, configManager.config().languages(),
            configManager.config().ai(), logger
        );

        translationService = new TranslationService(
            openRouterClient, deepLClient,
            configManager.config().ai(), configManager.config().deepl(),
            configManager.config().languages(),
            configManager.messages(), logger
        );

        moderationService = new ModerationService(
            configManager.config().moderation(), configManager.config().languages(),
            openRouterClient, cacheService, logger
        );

        chatListener = new ChatListener(
            detectionService, translationService, moderationService,
            cacheService, configManager, chatFormatter, logger
        );

        chatFormatter = new ChatFormatter(
            placeholderAPIHook, luckPermsHook, lpcHook,
            configManager.config().chatFormat(), logger
        );

        logger.info("Services reinitialized after reload.");
    }

    public void shutdown() {
        logger.info("Shutting down Dialect services...");
        if (openRouterClient != null) openRouterClient.shutdown();
        if (deepLClient != null) deepLClient.shutdown();
        if (redisService != null) redisService.shutdown();
        if (cacheService != null) cacheService.clearAll();
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
            logger.info("Translation provider: OpenRouter (" + configManager.config().ai().model() + ")");
        } else if (hasDeepL) {
            logger.info("Translation provider: DeepL (fallback mode)");
        } else if (aiEnabled) {
            logger.warning("No translation provider configured. Set 'ai.openrouter_key' or 'deepl.api_key' in config.yml");
        }

        if (hasRedis) {
            logger.info("Redis caching: " + (redisService.isConnected() ? "Connected" : "Disconnected"));
        }
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(chatListener, plugin);
        pm.registerEvents(playerQuitListener, plugin);
        logger.info("Registered event listeners.");
    }

    private void registerCommands() {
        registerCommand("dialect", dialectCommand);
        registerCommand("language", languageCommand);
        logger.info("Registered commands.");
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            if (executor instanceof DialectCommand dc) {
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
    public OpenRouterClient getOpenRouterClient() { return openRouterClient; }
    public DeepLClient getDeepLClient() { return deepLClient; }
    public CacheService getCacheService() { return cacheService; }
    public RedisService getRedisService() { return redisService; }
    public DetectionService getDetectionService() { return detectionService; }
    public TranslationService getTranslationService() { return translationService; }
    public ModerationService getModerationService() { return moderationService; }
    public ChatFormatter getChatFormatter() { return chatFormatter; }
    public DialectCommand getDialectCommand() { return dialectCommand; }
}
