package com.vomlabs.dialect.service.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

public class UpdateChecker {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/REQUIRED_MODRINTH_PROJECT_ID/version";
    private static final String GITHUB_API = "https://api.github.com/repos/itzzjustmateo/dialect/releases/latest";

    private final Plugin plugin;
    private final String currentVersion;
    private final Logger logger;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private String latestVersion;
    private String downloadUrl;
    private UpdateSource source;

    public enum UpdateSource {
        MODRINTH, GITHUB, NONE
    }

    public UpdateChecker(Plugin plugin, String currentVersion, Logger logger) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
        this.logger = logger;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.mapper = new ObjectMapper();
        this.latestVersion = currentVersion;
        this.source = UpdateSource.NONE;
    }

    public void checkAsync() {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskAsynchronously(plugin, () -> {
            try {
                if (checkModrinth()) {
                    return;
                }
                if (checkGithub()) {
                    return;
                }
                logger.info("Update check: could not reach Modrinth or GitHub APIs.");
            } catch (Exception e) {
                logger.warning("Update check failed: " + e.getMessage());
            }
        });
    }

    private boolean checkModrinth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MODRINTH_API + "?loaders=%5B%22paper%22%5D&game_versions=%5B%221.21.11%22%5D"))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "LazyDialect/" + currentVersion)
                .GET()
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }

            JsonNode versions = mapper.readTree(response.body());
            if (versions.isArray() && versions.size() > 0) {
                JsonNode latest = versions.get(0);
                this.latestVersion = latest.get("version_number").asText();
                this.downloadUrl = latest.get("files").get(0).get("url").asText();
                this.source = UpdateSource.MODRINTH;
                return true;
            }
        } catch (Exception e) {
            logger.fine("Modrinth check failed: " + e.getMessage());
        }
        return false;
    }

    private boolean checkGithub() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "LazyDialect/" + currentVersion)
                .GET()
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }

            JsonNode release = mapper.readTree(response.body());
            this.latestVersion = release.get("tag_name").asText().replaceFirst("^v", "");
            JsonNode assets = release.get("assets");
            if (assets.isArray() && assets.size() > 0) {
                this.downloadUrl = assets.get(0).get("browser_download_url").asText();
            } else {
                this.downloadUrl = release.get("html_url").asText();
            }
            this.source = UpdateSource.GITHUB;
            return true;
        } catch (Exception e) {
            logger.fine("GitHub check failed: " + e.getMessage());
        }
        return false;
    }

    public boolean isUpdateAvailable() {
        if (source == UpdateSource.NONE) {
            return false;
        }
        return !currentVersion.equals(latestVersion);
    }

    public void notifyPlayer(Player player) {
        if (!isUpdateAvailable()) {
            return;
        }

        String sourceName = source == UpdateSource.MODRINTH ? "Modrinth" : "GitHub";
        player.sendMessage("§6[LazyDialect] §eUpdate available: §f" + currentVersion + " §7→ §a" + latestVersion);
        player.sendMessage("§7Source: §f" + sourceName);
        if (downloadUrl != null) {
            player.sendMessage("§7Download: §f" + downloadUrl);
        }
    }

    public String getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }
    public UpdateSource getSource() { return source; }
}
