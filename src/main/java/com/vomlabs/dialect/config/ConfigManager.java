package com.vomlabs.dialect.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.vomlabs.dialect.bootstrap.DialectPlugin;
import com.vomlabs.dialect.model.Action;
import com.vomlabs.dialect.util.ColorUtil;
import net.kyori.adventure.text.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String MESSAGES_FILE_NAME = "messages.yml";

    private final DialectPlugin plugin;
    private final Path dataDirectory;
    private final ObjectMapper yamlMapper;
    private DialectConfig currentConfig;
    private MessagesConfig messagesConfig;

    public ConfigManager(DialectPlugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataFolder().toPath();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.currentConfig = loadDefaultConfig();
        this.messagesConfig = new MessagesConfig();
    }

    public void load() {
        try {
            Files.createDirectories(dataDirectory);
            saveDefaultConfig();
            saveDefaultMessages();
            this.currentConfig = parseConfig();
            this.messagesConfig = parseMessages();
            plugin.logger().info("Configuration loaded successfully.");
        } catch (IOException e) {
            plugin.logger().severe("Failed to load configuration: " + e.getMessage());
            this.currentConfig = loadDefaultConfig();
            this.messagesConfig = new MessagesConfig();
        }
    }

    public void reload() {
        load();
    }

    public DialectConfig config() {
        return currentConfig;
    }

    public MessagesConfig messages() {
        return messagesConfig;
    }

    public Component formatMessage(String template, java.util.Map<String, String> placeholders) {
        String resolved = template;
        for (var entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return ColorUtil.deserializeUncached(resolved);
    }

    private DialectConfig parseConfig() {
        try {
            File configFile = dataDirectory.resolve(CONFIG_FILE_NAME).toFile();
            if (!configFile.exists()) {
                return loadDefaultConfig();
            }
            JsonNode root = yamlMapper.readTree(configFile);
            return new DialectConfig(
                parseAI(root),
                parseDeepL(root),
                parseLanguages(root),
                parseCache(root),
                parseModeration(root),
                parseRedis(root),
                parseChatFormat(root)
            );
        } catch (IOException e) {
            plugin.logger().severe("Error parsing config.yml: " + e.getMessage());
            return loadDefaultConfig();
        }
    }

    private MessagesConfig parseMessages() {
        try {
            File messagesFile = dataDirectory.resolve(MESSAGES_FILE_NAME).toFile();
            if (!messagesFile.exists()) {
                return new MessagesConfig();
            }
            JsonNode root = yamlMapper.readTree(messagesFile);
            return new MessagesConfig(
                getString(root, "prefix", "<gradient:#4facfe:#00f2fe>[Dialect]</gradient> "),
                getString(root, "violation_warning", "<red>Your message was not sent because it violates the server language policy.</red>"),
                getString(root, "translation_format", "<gray>[Translated from {from}]: {message}</gray>"),
                getString(root, "no_permission", "<red>You do not have permission to execute this command.</red>"),
                getString(root, "reload_success", "<green>Configuration and caches successfully reloaded.</green>"),
                getString(root, "correction_suggested", "<gold>Did you mean:</gold> <click:suggest_command:'{correction}'><hover:show_text:'<gray>Click to accept</gray>'>{correction}</hover></click>"),
                getString(root, "api_error", "<red>An error occurred while processing your request. Please try again later.</red>"),
                getString(root, "rate_limited", "<red>Too many requests. Please wait before sending another message.</red>"),
                getString(root, "ai_unavailable", "<red>The AI service is unavailable. Please try again later.</red>")
            );
        } catch (IOException e) {
            plugin.logger().severe("Error parsing messages.yml: " + e.getMessage());
            return new MessagesConfig();
        }
    }

    private static java.util.Map<String, String[]> PROVIDER_DEFAULTS = java.util.Map.of(
        "openrouter", new String[]{"https://openrouter.ai/api/v1/chat/completions", "meta-llama/llama-3-8b-instruct:free"},
        "openai", new String[]{"https://api.openai.com/v1/chat/completions", "gpt-4o-mini"},
        "anthropic", new String[]{"https://api.anthropic.com/v1/messages", "claude-3-haiku-20240307"},
        "gemini", new String[]{"https://generativelanguage.googleapis.com/v1beta/models", "gemini-2.0-flash"},
        "huggingface", new String[]{"https://api-inference.huggingface.co/models", "mistralai/Mistral-7B-Instruct-v0.3"}
    );

    private DialectConfig.AIConfig parseAI(JsonNode root) {
        JsonNode node = root.get("ai");
        if (node == null) return new DialectConfig.AIConfig(true, "openrouter", "", "", "", 0.1, 5, 3, 500);

        String provider = getString(node, "provider", "openrouter");
        String[] defaults = PROVIDER_DEFAULTS.getOrDefault(provider, PROVIDER_DEFAULTS.get("openrouter"));
        String defaultEndpoint = defaults[0];
        String defaultModel = defaults[1];

        String apiKey = getString(node, "api_key", "");
        if (apiKey.isBlank()) {
            apiKey = getString(node, "openrouter_key", "");
        }

        return new DialectConfig.AIConfig(
            getBoolean(node, "enabled", true),
            provider,
            apiKey,
            getString(node, "endpoint", defaultEndpoint),
            getString(node, "model", defaultModel),
            getDouble(node, "temperature", 0.1),
            getInt(node, "timeout_seconds", 5),
            getInt(node, "max_retries", 3),
            getInt(node, "backoff_base_ms", 500)
        );
    }

    private DialectConfig.DeepLConfig parseDeepL(JsonNode root) {
        JsonNode node = root.get("deepl");
        if (node == null) return new DialectConfig.DeepLConfig("", true, 5);

        return new DialectConfig.DeepLConfig(
            getString(node, "api_key", ""),
            getBoolean(node, "use_free_plan", true),
            getInt(node, "timeout_seconds", 5)
        );
    }

    private DialectConfig.LanguageConfig parseLanguages(JsonNode root) {
        JsonNode node = root.get("languages");
        if (node == null) return new DialectConfig.LanguageConfig("WHITELIST", List.of("en", "de"), "en", Action.WARN, 0.75);

        String mode = getString(node, "mode", "WHITELIST");
        List<String> allowed = new ArrayList<>();
        JsonNode allowedNode = node.get("allowed");
        if (allowedNode instanceof ArrayNode arrayNode) {
            arrayNode.forEach(lang -> allowed.add(lang.asText("en")));
        }
        if (allowed.isEmpty()) allowed.add("en");

        String serverDefault = getString(node, "server_default", "en");
        Action fallback = parseAction(getString(node, "fallback_behavior", "WARN"));
        double confidence = getDouble(node, "confidence_threshold", 0.75);

        return new DialectConfig.LanguageConfig(mode, allowed, serverDefault, fallback, confidence);
    }

    private DialectConfig.CacheConfig parseCache(JsonNode root) {
        JsonNode node = root.get("cache");
        if (node == null) return new DialectConfig.CacheConfig(10000, 30);

        return new DialectConfig.CacheConfig(
            getInt(node, "maximum_size", 10000),
            getInt(node, "expire_after_access_minutes", 30)
        );
    }

    private DialectConfig.ModerationConfig parseModeration(JsonNode root) {
        JsonNode node = root.get("moderation");
        if (node == null) return new DialectConfig.ModerationConfig(Action.TRANSLATE, true, 2, true);

        return new DialectConfig.ModerationConfig(
            parseAction(getString(node, "on_violation", "TRANSLATE")),
            getBoolean(node, "enable_slang_validation", true),
            getInt(node, "cooldown_seconds", 2),
            getBoolean(node, "notify_staff", true)
        );
    }

    private DialectConfig.RedisConfig parseRedis(JsonNode root) {
        JsonNode node = root.get("redis");
        if (node == null) return new DialectConfig.RedisConfig(false, "redis://localhost:6379", "", 2, false);

        return new DialectConfig.RedisConfig(
            getBoolean(node, "enabled", false),
            getString(node, "uri", "redis://localhost:6379"),
            getString(node, "password", ""),
            getInt(node, "timeout_seconds", 2),
            getBoolean(node, "use_ssl", false)
        );
    }

    private DialectConfig.ChatFormatConfig parseChatFormat(JsonNode root) {
        JsonNode node = root.get("chat_format");
        if (node == null) return new DialectConfig.ChatFormatConfig(true, "<%luckperms_prefix%><player_name><gray>:</gray> %message%", true, true);

        return new DialectConfig.ChatFormatConfig(
            getBoolean(node, "enabled", true),
            getString(node, "template", "<%luckperms_prefix%><player_name><gray>:</gray> %message%"),
            getBoolean(node, "prefer_lpc", true),
            getBoolean(node, "prefer_lpcx", true)
        );
    }

    private void saveDefaultConfig() throws IOException {
        File configFile = dataDirectory.resolve(CONFIG_FILE_NAME).toFile();
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE_NAME, false);
        }
    }

    private void saveDefaultMessages() throws IOException {
        File messagesFile = dataDirectory.resolve(MESSAGES_FILE_NAME).toFile();
        if (!messagesFile.exists()) {
            plugin.saveResource(MESSAGES_FILE_NAME, false);
        }
    }

    private DialectConfig loadDefaultConfig() {
        return new DialectConfig(
            new DialectConfig.AIConfig(true, "openrouter", "", "", "", 0.1, 5, 3, 500),
            new DialectConfig.DeepLConfig("", true, 5),
            new DialectConfig.LanguageConfig("WHITELIST", List.of("en", "de"), "en", Action.WARN, 0.75),
            new DialectConfig.CacheConfig(10000, 30),
            new DialectConfig.ModerationConfig(Action.TRANSLATE, true, 2, true),
            new DialectConfig.RedisConfig(false, "redis://localhost:6379", "", 2, false),
            new DialectConfig.ChatFormatConfig(true, "<%luckperms_prefix%><player_name><gray>:</gray> %message%", true, true)
        );
    }

    private Action parseAction(String value) {
        if (value == null) return Action.TRANSLATE;
        try {
            return Action.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Action.TRANSLATE;
        }
    }

    private static String getString(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field)) return defaultValue;
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : defaultValue;
    }

    private static int getInt(JsonNode node, String field, int defaultValue) {
        if (node == null || !node.has(field)) return defaultValue;
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : defaultValue;
    }

    private static double getDouble(JsonNode node, String field, double defaultValue) {
        if (node == null || !node.has(field)) return defaultValue;
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : defaultValue;
    }

    private static boolean getBoolean(JsonNode node, String field, boolean defaultValue) {
        if (node == null || !node.has(field)) return defaultValue;
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : defaultValue;
    }
}
