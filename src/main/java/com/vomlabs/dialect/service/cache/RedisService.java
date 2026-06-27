package com.vomlabs.dialect.service.cache;

import com.vomlabs.dialect.config.DialectConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class RedisService {

    private final DialectConfig.RedisConfig config;
    private final Logger logger;
    private JedisPool pool;
    private volatile boolean connected;

    public RedisService(DialectConfig.RedisConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.connected = false;
    }

    public void initialize() {
        if (!config.enabled()) {
            logger.info("Redis caching is disabled.");
            return;
        }

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(8);
            poolConfig.setMaxIdle(4);
            poolConfig.setMinIdle(1);
            poolConfig.setMaxWait(Duration.ofSeconds(config.timeoutSeconds()));

            String uriString = config.uri();
            String password = config.password();
            boolean pwInjected = false;

            if (password != null && !password.isBlank()) {
                URI parsed = URI.create(uriString);
                if (parsed.getUserInfo() == null || parsed.getUserInfo().isBlank()) {
                    String hostPart = parsed.getHost();
                    if (parsed.getPort() > 0) hostPart += ":" + parsed.getPort();
                    uriString = parsed.getScheme() + "://:" + password + "@" + hostPart
                        + (parsed.getPath() != null ? parsed.getPath() : "/0");
                    pwInjected = true;
                }
            }

            if (config.useSsl() && !uriString.startsWith("rediss://")) {
                uriString = "rediss://" + uriString.substring(uriString.indexOf("://") + 3);
            }

            URI redisUri = URI.create(uriString);
            pool = new JedisPool(poolConfig, redisUri);

            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
                connected = true;
                logger.info("Connected to Redis/Dragonfly at " + config.uri());
            }
        } catch (Exception e) {
            logger.warning("Failed to connect to Redis: " + e.getMessage());
            connected = false;
            pool = null;
        }
    }

    public CompletableFuture<Optional<String>> get(String key) {
        if (!connected || pool == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                String value = jedis.get(key);
                return Optional.ofNullable(value);
            } catch (Exception e) {
                logger.fine("Redis get failed: " + e.getMessage());
                return Optional.<String>empty();
            }
        });
    }

    public CompletableFuture<Void> set(String key, String value, int ttlSeconds) {
        if (!connected || pool == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key, ttlSeconds, value);
            } catch (Exception e) {
                logger.fine("Redis set failed: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> delete(String key) {
        if (!connected || pool == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(key);
            } catch (Exception e) {
                logger.fine("Redis delete failed: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> publish(String channel, String message) {
        if (!connected || pool == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(channel, message);
            } catch (Exception e) {
                logger.fine("Redis publish failed: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
            connected = false;
            logger.info("Redis connection closed.");
        }
    }

    public boolean isConnected() {
        return connected && pool != null && !pool.isClosed();
    }
}
