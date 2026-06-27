package com.vomlabs.dialect.command;

import com.vomlabs.dialect.bootstrap.DialectPlugin;
import com.vomlabs.dialect.config.ConfigManager;
import com.vomlabs.dialect.config.DialectConfig;
import com.vomlabs.dialect.config.MessagesConfig;
import com.vomlabs.dialect.service.ai.AiProvider;
import com.vomlabs.dialect.service.cache.CacheService;
import com.vomlabs.dialect.service.detection.DetectionService;
import com.vomlabs.dialect.service.translation.TranslationService;
import com.vomlabs.dialect.model.ChatMessage;
import com.vomlabs.dialect.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DialectCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_BASE = "dialect.admin";
    private static final String PERMISSION_RELOAD = "dialect.admin.reload";
    private static final String PERMISSION_STATUS = "dialect.admin.status";
    private static final String PERMISSION_DETECT = "dialect.admin.detect";
    private static final String PERMISSION_TRANSLATE = "dialect.admin.translate";
    private static final String PERMISSION_CACHE = "dialect.admin.cache";

    private final DialectPlugin plugin;
    private final ConfigManager configManager;
    private final DetectionService detectionService;
    private final TranslationService translationService;
    private final CacheService cacheService;
    private final AiProvider aiProvider;
    private final Logger logger;

    public DialectCommand(
        DialectPlugin plugin,
        ConfigManager configManager,
        DetectionService detectionService,
        TranslationService translationService,
        CacheService cacheService,
        AiProvider aiProvider,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.detectionService = detectionService;
        this.translationService = translationService;
        this.cacheService = cacheService;
        this.aiProvider = aiProvider;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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

        sender.sendMessage(prefix.append(ColorUtil.deserializeUncached("<gold>===== Dialect Commands =====</gold>")));

        if (sender.hasPermission(PERMISSION_RELOAD)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<click:run_command:'/dialect reload'><hover:show_text:'<gray>Click to reload</gray>'><gold>/dialect reload</gold></hover></click>"
                + " <dark_gray>-</dark_gray> <gray>Reload configuration and caches</gray>"
            )));
        }

        if (sender.hasPermission(PERMISSION_STATUS)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<click:run_command:'/dialect status'><hover:show_text:'<gray>Click to view status</gray>'><gold>/dialect status</gold></hover></click>"
                + " <dark_gray>-</dark_gray> <gray>View plugin status and metrics</gray>"
            )));
        }

        if (sender.hasPermission(PERMISSION_DETECT)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<gold>/dialect detect <text></gold>"
                + " <dark_gray>-</dark_gray> <gray>Detect language of text</gray>"
            )));
        }

        if (sender.hasPermission(PERMISSION_TRANSLATE)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<gold>/dialect translate <lang> <text></gold>"
                + " <dark_gray>-</dark_gray> <gray>Translate text to target language</gray>"
            )));
        }

        if (sender.hasPermission(PERMISSION_CACHE)) {
            sender.sendMessage(prefix.append(ColorUtil.deserializeUncached(
                "<click:run_command:'/dialect cache clear'><hover:show_text:'<gray>Click to clear cache</gray>'><gold>/dialect cache clear</gold></hover></click>"
                + " <dark_gray>-</dark_gray> <gray>Clear cached data</gray>"
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
            openRouterClient.shutdown();
            plugin.getDependencyInjector().reinitializeServices();
            MessagesConfig msg = configManager.messages();
            sender.sendMessage(ColorUtil.deserializeUncached(msg.prefix() + msg.reloadSuccess()));
            logger.info("Plugin reloaded by " + sender.getName());
        } catch (Exception e) {
            logger.severe("Reload failed: " + e.getMessage());
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<red>Reload failed: " + e.getMessage() + "</red>"
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

        sender.sendMessage(ColorUtil.deserializeUncached(prefix + "<gold>===== Dialect Status =====</gold>"));

        boolean aiEnabled = config.ai().enabled();
        boolean apiConfigured = config.ai().isConfigured();
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>AI:</gray> " + (aiEnabled ? "<green>Enabled</green>" : "<red>Disabled</red>")
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>AI Provider:</gray> " + (apiConfigured ? "<green>Connected</green>" : "<red>Not Configured</red>")
        ));
        if (apiConfigured) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<gray>Model:</gray> <white>" + config.ai().model() + "</white>"
            ));
        }

        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>Language Mode:</gray> <white>" + config.languages().mode() + "</white>"
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>Allowed Languages:</gray> <white>" + String.join(", ", config.languages().allowed()) + "</white>"
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>Default Language:</gray> <white>" + config.languages().serverDefault() + "</white>"
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>Violation Action:</gray> <white>" + config.moderation().onViolation().name() + "</white>"
        ));

        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>User Cache:</gray> <white>" + cacheService.userLanguageCacheSize() + " entries</white>"
        ));
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>Analysis Cache:</gray> <white>" + cacheService.analysisCacheSize() + " entries</white>"
        ));

        int remaining = aiProvider.getRateLimiter().getRemainingRequests();
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<gray>Rate Limit Remaining:</gray> <white>" + remaining + " requests</white>"
        ));
    }

    private void handleDetect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DETECT)) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<red>Usage: /dialect detect <text></red>"
            ));
            return;
        }

        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String prefix = configManager.messages().prefix();

        Player targetPlayer = sender instanceof Player ? (Player) sender : null;
        if (targetPlayer == null) {
            sender.sendMessage(ColorUtil.deserializeUncached(prefix + "<red>This command must be used as a player.</red>"));
            return;
        }

        ChatMessage msg = ChatMessage.builder(targetPlayer.getUniqueId(), targetPlayer.getName(), text, ColorUtil.deserializeUncached(text)).build();

        detectionService.detect(msg).thenAccept(result -> {
            result.detectedLanguage().ifPresentOrElse(lang -> {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<gray>Detected Language:</gray> <white>" + lang.code() + " (" + lang.displayName() + ")</white>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<gray>Confidence:</gray> <white>" + String.format("%.2f", result.confidence()) + "</white>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<gray>Contains Slang:</gray> <white>" + result.containsSlang() + "</white>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<gray>Valid Slang:</gray> <white>" + result.isValidSlangInContext() + "</white>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<gray>Translation:</gray> <white>" + result.normalizedTranslation().orElse("N/A") + "</white>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<gray>Allowed:</gray> " + (detectionService.isLanguageAllowed(lang.code()) ? "<green>Yes</green>" : "<red>No</red>")
                ));
            }, () -> {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<red>Could not detect language for the provided text.</red>"
                ));
            });
        }).exceptionally(throwable -> {
            logger.warning("Detection command failed: " + throwable.getMessage());
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<red>Detection failed: " + throwable.getMessage() + "</red>"
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
                configManager.messages().prefix() + "<red>Usage: /dialect translate <lang> <text></red>"
            ));
            return;
        }

        String targetLanguage = args[1];
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        String prefix = configManager.messages().prefix();

        sender.sendMessage(ColorUtil.deserializeUncached(prefix + "<yellow>Translating...</yellow>"));

        translationService.translate(text, "auto", targetLanguage).thenAccept(translated -> {
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<gray>Original:</gray> <white>" + text + "</white>"
            ));
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<gray>Translated (" + targetLanguage + "):</gray> <white>" + translated + "</white>"
            ));
        }).exceptionally(throwable -> {
            logger.warning("Translate command failed: " + throwable.getMessage());
            sender.sendMessage(ColorUtil.deserializeUncached(
                prefix + "<red>Translation failed: " + throwable.getMessage() + "</red>"
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
                configManager.messages().prefix() + "<red>Usage: /dialect cache clear</red>"
            ));
            return;
        }

        long userBefore = cacheService.userLanguageCacheSize();
        long analysisBefore = cacheService.analysisCacheSize();
        cacheService.clearAll();

        String prefix = configManager.messages().prefix();
        sender.sendMessage(ColorUtil.deserializeUncached(
            prefix + "<green>Cache cleared.</green> <gray>Removed " + userBefore + " user entries and " + analysisBefore + " analysis entries.</gray>"
        ));
        logger.info("Cache cleared by " + sender.getName() + " (" + userBefore + " user, " + analysisBefore + " analysis entries)");
    }

    private void handleUtils(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<red>Usage: /dialect utils <papermc|...></red>"
            ));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "papermc" -> handlePaperMc(sender, args);
            default -> sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<red>Unknown utility. Use: papermc</red>"
            ));
        }
    }

    private void handlePaperMc(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("update")) {
            sender.sendMessage(ColorUtil.deserializeUncached(
                configManager.messages().prefix() + "<red>Usage: /dialect utils papermc update</red>"
            ));
            return;
        }

        if (!sender.hasPermission(PERMISSION_BASE) || !(sender instanceof Player)) {
            sendNoPermission(sender);
            return;
        }

        String prefix = configManager.messages().prefix();
        sender.sendMessage(ColorUtil.deserializeUncached(prefix + "<yellow>Checking for latest Paper build...</yellow>"));

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
                        prefix + "<red>Failed to fetch builds (HTTP " + buildsRes.statusCode() + ")</red>"
                    ));
                    return;
                }

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(buildsRes.body());
                com.fasterxml.jackson.databind.JsonNode builds = root.get("builds");
                if (builds == null || !builds.isArray() || builds.isEmpty()) {
                    sender.sendMessage(ColorUtil.deserializeUncached(
                        prefix + "<red>No builds found for version " + version + "</red>"
                    ));
                    return;
                }

                int latestBuild = builds.get(builds.size() - 1).get("build").asInt();
                int currentBuild = Bukkit.getCurrentTick(); // not the real build number, just a placeholder

                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<gray>Latest build:</gray> <white>" + latestBuild + "</white>"
                ));

                String jarName = "paper-" + version + "-" + latestBuild + ".jar";
                String downloadUrl = "https://api.papermc.io/v2/projects/paper/versions/" + version
                    + "/builds/" + latestBuild + "/downloads/" + jarName;

                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<yellow>Downloading " + jarName + "...</yellow>"
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
                    prefix + "<green>Downloaded to " + downloadRes.body() + "</green>"
                ));
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<gold>Replace your server's paper.jar manually and restart the server.</gold>"
                ));
                logger.info("Paper updated: " + version + " build " + latestBuild);

            } catch (java.net.http.HttpTimeoutException e) {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<red>Request timed out while contacting PaperMC API.</red>"
                ));
            } catch (java.net.ConnectException e) {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<red>Could not connect to PaperMC API. Check your internet connection.</red>"
                ));
            } catch (java.io.IOException e) {
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<red>IO error: " + e.getMessage() + "</red>"
                ));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<red>Download was interrupted.</red>"
                ));
            } catch (Exception e) {
                logger.warning("Paper update failed: " + e.getMessage());
                sender.sendMessage(ColorUtil.deserializeUncached(
                    prefix + "<red>Failed to update Paper: " + e.getMessage() + "</red>"
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
            if ("help".startsWith(input) && sender.hasPermission(PERMISSION_BASE)) completions.add("help");
            if ("reload".startsWith(input) && sender.hasPermission(PERMISSION_RELOAD)) completions.add("reload");
            if ("status".startsWith(input) && sender.hasPermission(PERMISSION_STATUS)) completions.add("status");
            if ("detect".startsWith(input) && sender.hasPermission(PERMISSION_DETECT)) completions.add("detect");
            if ("translate".startsWith(input) && sender.hasPermission(PERMISSION_TRANSLATE)) completions.add("translate");
            if ("cache".startsWith(input) && sender.hasPermission(PERMISSION_CACHE)) completions.add("cache");
            if ("utils".startsWith(input) && sender.hasPermission(PERMISSION_BASE)) completions.add("utils");
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
