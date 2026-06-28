package com.vomlabs.dialect.command;

import com.vomlabs.dialect.bootstrap.LazyDialectPlugin;
import com.vomlabs.dialect.config.ConfigManager;
import com.vomlabs.dialect.config.DialectConfig;
import com.vomlabs.dialect.config.MessagesConfig;
import com.vomlabs.dialect.service.ai.AiProvider;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.service.detection.DetectionService;
import com.vomlabs.dialect.service.effect.ParticleService;
import com.vomlabs.dialect.service.effect.SoundService;
import com.vomlabs.dialect.service.translation.TranslationService;
import com.vomlabs.dialect.model.ChatMessage;
import com.vomlabs.dialect.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class LazyDialectCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_BASE = "lazydialect.admin";
    private static final String PERMISSION_RELOAD = "lazydialect.admin.reload";
    private static final String PERMISSION_STATUS = "lazydialect.admin.status";
    private static final String PERMISSION_DETECT = "lazydialect.admin.detect";
    private static final String PERMISSION_TRANSLATE = "lazydialect.admin.translate";
    private static final String PERMISSION_CACHE = "lazydialect.admin.cache";

    private final LazyDialectPlugin plugin;
    private final ConfigManager configManager;
    private final DetectionService detectionService;
    private final TranslationService translationService;
    private final CacheService cacheService;
    private final AiProvider aiProvider;
    private final SoundService soundService;
    private final ParticleService particleService;
    private final Logger logger;

    public LazyDialectCommand(
        LazyDialectPlugin plugin,
        ConfigManager configManager,
        DetectionService detectionService,
        TranslationService translationService,
        CacheService cacheService,
        AiProvider aiProvider,
        SoundService soundService,
        ParticleService particleService,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.detectionService = detectionService;
        this.translationService = translationService;
        this.cacheService = cacheService;
        this.aiProvider = aiProvider;
        this.soundService = soundService;
        this.particleService = particleService;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender, @NotNull Command command,
        @NotNull String label, @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "detect" -> handleDetect(sender, args);
            case "translate" -> handleTranslate(sender, args);
            case "cache" -> handleCache(sender, args);
            case "utils" -> handleUtils(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessagesConfig msg = configManager.messages();
        Component prefix = ColorUtil.deserializeUncached(msg.prefix());

        sender.sendMessage(prefix.append(
            ColorUtil.deserializeUncached("<color:#478FC6>─── Commands ───</color>")));

        if (sender.hasPermission(PERMISSION_RELOAD)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<click:run_command:'/lazydialect reload'>"
                + "<hover:show_text:'<color:#C2C7D3>Click to reload</color>'><color:#478FC6>/lazydialect reload</color></hover></click>"
                + " <color:#C2C7D3>- Reload configuration and caches</color>"
            )));
        }

        if (sender.hasPermission(PERMISSION_STATUS)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<click:run_command:'/lazydialect status'>"
                + "<hover:show_text:'<color:#C2C7D3>Click to view status</color>'><color:#478FC6>/lazydialect status</color></hover></click>"
                + " <color:#C2C7D3>- View plugin status and metrics</color>"
            )));
        }

        if (sender.hasPermission(PERMISSION_DETECT)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<color:#478FC6>/lazydialect detect <text></color>"
                + " <color:#C2C7D3>- Detect language of text</color>"
            )));
        }

        if (sender.hasPermission(PERMISSION_TRANSLATE)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<color:#478FC6>/lazydialect translate <lang> <text></color>"
                + " <color:#C2C7D3>- Translate text to target language</color>"
            )));
        }

        if (sender.hasPermission(PERMISSION_CACHE)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<click:run_command:'/lazydialect cache clear'>"
                + "<hover:show_text:'<color:#C2C7D3>Click to clear cache</color>'><color:#478FC6>/lazydialect cache clear</color></hover></click>"
                + " <color:#C2C7D3>- Clear cached data</color>"
            )));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sendNoPermission(sender);
            return;
        }

        try {
            configManager.reload();
            aiProvider.shutdown();
            plugin.getDependencyInjector().reinitializeServices();
            MessagesConfig msg = configManager.messages();
            sender.sendMessage(ColorUtil.deserializeUncached(msg.prefix() + msg.reloadSuccess()));
            if (sender instanceof Player p) {
                soundService.playReloadComplete(p);
            }
            logger.info("Plugin reloaded by " + sender.getName());
        } catch (Exception e) {
            logger.severe("Reload failed: " + e.getMessage());
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix()
                + "<color:#D1988C>✕ <color:#C2C7D3>Reload failed: " + e.getMessage() + "</color>"
            ));
        }
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_STATUS)) {
            sendNoPermission(sender);
            return;
        }

        DialectConfig config = configManager.config();
        String prefix = configManager.messages().prefix();

        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#478FC6>─── Status ───</color>"));

        boolean aiEnabled = config.ai().enabled();
        boolean apiConfigured = config.ai().isConfigured();
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>AI:</color> "
            + (aiEnabled ? "<color:#C2C7D3>Enabled</color>" : "<color:#D1988C>Disabled</color>")
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>AI Provider:</color> "
            + (apiConfigured ? "<color:#C2C7D3>Connected</color>" : "<color:#D1988C>Not Configured</color>")
        ));
        if (apiConfigured) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<color:#C2C7D3>Model:</color> <color:#C2C7D3>" + config.ai().model() + "</color>"
            ));
        }

        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>Language Mode:</color> <color:#C2C7D3>" + config.languages().mode() + "</color>"
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>Allowed Languages:</color> <color:#C2C7D3>"
            + String.join("<color:#C2C7D3>, </color>", config.languages().allowed()) + "</color>"
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>Default Language:</color> <color:#C2C7D3>"
            + config.languages().serverDefault() + "</color>"
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>Violation Action:</color> <color:#C2C7D3>"
            + config.moderation().onViolation().name() + "</color>"
        ));

        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>User Cache:</color> <color:#C2C7D3>"
            + cacheService.userLanguageCacheSize() + " entries</color>"
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>Analysis Cache:</color> <color:#C2C7D3>"
            + cacheService.analysisCacheSize() + " entries</color>"
        ));

        int remaining = aiProvider.getRateLimiter().getRemainingRequests();
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>Rate Limit Remaining:</color> <color:#C2C7D3>" + remaining + " requests</color>"
        ));
    }

    private void handleDetect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DETECT)) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<color:#D1988C>✕ <color:#C2C7D3>Usage:</color> <color:#478FC6>/lazydialect detect <text></color></color>"
            ));
            return;
        }

        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String prefix = configManager.messages().prefix();

        Player targetPlayer = sender instanceof Player ? (Player) sender : null;
        if (targetPlayer == null) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<color:#D1988C>✕ <color:#C2C7D3>This command must be used as a player.</color>"));
            return;
        }

        ChatMessage msg = ChatMessage.builder(
            targetPlayer.getUniqueId(), targetPlayer.getName(),
            text, ColorUtil.deserializeUncached(text)
        ).build();

        detectionService.detect(msg).thenAccept(result -> {
            result.detectedLanguage().ifPresentOrElse(lang -> {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Detected Language:</color> <color:#C2C7D3>"
                    + lang.code() + " (" + lang.displayName() + ")</color>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Confidence:</color> <color:#C2C7D3>"
                    + String.format("%.2f", result.confidence()) + "</color>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Contains Slang:</color> <color:#C2C7D3>" + result.containsSlang() + "</color>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Valid Slang:</color> <color:#C2C7D3>" + result.isValidSlangInContext() + "</color>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Translation:</color> <color:#C2C7D3>" + result.normalizedTranslation().orElse("N/A") + "</color>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Allowed:</color> " + (detectionService.isLanguageAllowed(lang.code()) ? "<color:#C2C7D3>Yes</color>" : "<color:#D1988C>No</color>")
                ));
                targetPlayer.sendActionBar(ColorUtil.deserializeUncached(
                    configManager.messages().actionbarDetected().replace("{lang}", lang.code())
                ));
                soundService.playDetectionComplete(targetPlayer);
                particleService.spawnDetectionParticles(targetPlayer);
            }, () -> {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#D1988C>✕ <color:#C2C7D3>Could not detect language for the provided text.</color>"
                ));
            });
        }).exceptionally(throwable -> {
            logger.warning("Detection command failed: " + throwable.getMessage());
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<color:#D1988C>✕ <color:#C2C7D3>Detection failed: " + throwable.getMessage() + "</color>"
            ));
            return null;
        });
    }

    private void handleTranslate(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_TRANSLATE)) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<color:#D1988C>✕ <color:#C2C7D3>Usage:</color> <color:#478FC6>/lazydialect translate <lang> <text></color></color>"
            ));
            return;
        }

        String targetLanguage = args[1];
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        String prefix = configManager.messages().prefix();

        sender.sendMessage(ColorUtil.deserializeUncached(prefix + "<color:#478FC6>⋯</color> <color:#C2C7D3>Translating...</color>"));

        translationService.translate(text, "auto", targetLanguage).thenAccept(translated -> {
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<color:#C2C7D3>Original:</color> <color:#C2C7D3>" + text + "</color>"
            ));
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<color:#C2C7D3>Translated (" + targetLanguage + "):</color> <color:#C2C7D3>" + translated + "</color>"
            ));
            if (sender instanceof Player p) {
                soundService.playTranslationComplete(p);
                particleService.spawnTranslationParticles(p);
            }
        }).exceptionally(throwable -> {
            logger.warning("Translate command failed: " + throwable.getMessage());
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<color:#D1988C>✕ <color:#C2C7D3>Translation failed: " + throwable.getMessage() + "</color>"
            ));
            return null;
        });
    }

    private void handleCache(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_CACHE)) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("clear")) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<color:#D1988C>✕ <color:#C2C7D3>Usage:</color> <color:#478FC6>/lazydialect cache clear</color></color>"
            ));
            return;
        }

        long userBefore = cacheService.userLanguageCacheSize();
        long analysisBefore = cacheService.analysisCacheSize();
        cacheService.clearAll();

        String prefix = configManager.messages().prefix();
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<color:#C2C7D3>Cache cleared.</color> <color:#C2C7D3>Removed " + userBefore + " user entries and " + analysisBefore + " analysis entries.</color>"
        ));
        logger.info("Cache cleared by " + sender.getName() + " (" + userBefore + " user, " + analysisBefore + " analysis entries)");
        if (sender instanceof Player p) {
            soundService.playCacheCleared(p);
        }
    }

    private void handleUtils(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<color:#D1988C>✕ <color:#C2C7D3>Usage:</color> <color:#478FC6>/lazydialect utils <papermc></color></color>"
            ));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "papermc" -> handlePaperMc(sender, args);
            default -> sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<color:#D1988C>✕ <color:#C2C7D3>Unknown utility. Use:</color> <color:#478FC6>papermc</color>"
            ));
        }
    }

    private void handlePaperMc(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("update")) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<color:#D1988C>✕ <color:#C2C7D3>Usage:</color> <color:#478FC6>/lazydialect utils papermc update</color></color>"
            ));
            return;
        }

        if (!sender.hasPermission(PERMISSION_BASE) || !(sender instanceof Player)) {
            sendNoPermission(sender);
            return;
        }

        String prefix = configManager.messages().prefix();
        sender.sendMessage(ColorUtil.deserializeUncached(prefix + "<color:#478FC6>⋯</color> <color:#C2C7D3>Checking for latest Paper build...</color>"));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

                String version = Bukkit.getMinecraftVersion();
                HttpRequest buildsReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.papermc.io/v2/projects/paper/versions/" + version + "/builds"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                HttpResponse<String> buildsRes = http.send(buildsReq, HttpResponse.BodyHandlers.ofString());

                if (buildsRes.statusCode() != 200) {
                    sender.sendMessage(ColorUtil.deserializeUncached(
                        prefix + "<color:#D1988C>✕ <color:#C2C7D3>Failed to fetch builds (HTTP " + buildsRes.statusCode() + ")</color>"
                    ));
                    return;
                }

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(buildsRes.body());
                com.fasterxml.jackson.databind.JsonNode builds = root.get("builds");
                if (builds == null || !builds.isArray() || builds.isEmpty()) {
                    sender.sendMessage(ColorUtil.deserializeUncached(
                        prefix + "<color:#D1988C>✕ <color:#C2C7D3>No builds found for version " + version + "</color>"
                    ));
                    return;
                }

                int latestBuild = builds.get(builds.size() - 1).get("build").asInt();
                int currentBuild = Bukkit.getCurrentTick();

                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Latest build:</color> <color:#C2C7D3>" + latestBuild + "</color>"
                ));

                String jarName = "paper-" + version + "-" + latestBuild + ".jar";
                String downloadUrl = "https://api.papermc.io/v2/projects/paper/versions/" + version
                    + "/builds/" + latestBuild + "/downloads/" + jarName;

                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#478FC6>⋯</color> <color:#C2C7D3>Downloading " + jarName + "...</color>"
                ));

                HttpRequest downloadReq = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
                HttpResponse<Path> downloadRes = http.send(downloadReq, HttpResponse.BodyHandlers.ofFile(
                    Path.of(System.getProperty("user.home"), "Downloads", jarName)
                ));

                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Downloaded to " + downloadRes.body() + "</color>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#C2C7D3>Replace your server's paper.jar manually and restart the server.</color>"
                ));
                logger.info("Paper updated: " + version + " build " + latestBuild);

            } catch (java.net.http.HttpTimeoutException e) {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#D1988C>✕ <color:#C2C7D3>Request timed out while contacting PaperMC API.</color>"
                ));
            } catch (java.net.ConnectException e) {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#D1988C>✕ <color:#C2C7D3>Could not connect to PaperMC API. Check your internet connection.</color>"
                ));
            } catch (java.io.IOException e) {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#D1988C>✕ <color:#C2C7D3>IO error: " + e.getMessage() + "</color>"
                ));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#D1988C>✕ <color:#C2C7D3>Download was interrupted.</color>"
                ));
            } catch (Exception e) {
                logger.warning("Paper update failed: " + e.getMessage());
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<color:#D1988C>✕ <color:#C2C7D3>Failed to update Paper: " + e.getMessage() + "</color>"
                ));
            }
        });
    }

    private void sendNoPermission(CommandSender sender) {
        MessagesConfig msg = configManager.messages();
        sender.sendMessage(ColorUtil.deserializeUncached(msg.prefix() + msg.noPermission()));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("help".startsWith(input) && sender.hasPermission(PERMISSION_BASE)) {
                completions.add("help");
            }
            if ("reload".startsWith(input) && sender.hasPermission(PERMISSION_RELOAD)) {
                completions.add("reload");
            }
            if ("status".startsWith(input) && sender.hasPermission(PERMISSION_STATUS)) {
                completions.add("status");
            }
            if ("detect".startsWith(input) && sender.hasPermission(PERMISSION_DETECT)) {
                completions.add("detect");
            }
            if ("translate".startsWith(input) && sender.hasPermission(PERMISSION_TRANSLATE)) {
                completions.add("translate");
            }
            if ("cache".startsWith(input) && sender.hasPermission(PERMISSION_CACHE)) {
                completions.add("cache");
            }
            if ("utils".startsWith(input) && sender.hasPermission(PERMISSION_BASE)) {
                completions.add("utils");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("cache")) {
                if ("clear".startsWith(args[1].toLowerCase())) {
                    completions.add("clear");
                }
            } else if (args[0].equalsIgnoreCase("translate")) {
                for (String lang : List.of("en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ko", "ar")) {
                    if (lang.startsWith(args[1].toLowerCase())) {
                        completions.add(lang);
                    }
                }
            }
        }

        return completions;
    }
}
