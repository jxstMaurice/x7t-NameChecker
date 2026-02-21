package com.x7t.namechecker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class NameCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("x7t-namechecker");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_DIR = FabricLoader.getInstance().getConfigDir().resolve("x7tnamechecker");
    private static final Path CACHE_FILE = CACHE_DIR.resolve("cache.json");
    private static final Path CONFIG_FILE = CACHE_DIR.resolve("cache_config.json");
    
    private static final Map<String, CachedPlayer> playerCache = new ConcurrentHashMap<>();
    
    // Konfigurierbare Werte (Nr. 9)
    private static long cacheExpiryMinutes = 30;
    private static long getCacheExpiryMs() { return cacheExpiryMinutes * 60 * 1000; }
    
    // Cache-Statistiken (Nr. 8)
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    
    // Debounced Save (Nr. 1)
    private static ScheduledExecutorService saveScheduler;
    private static volatile boolean savePending = false;
    private static final long SAVE_DELAY_MS = 5000;
    
    // Offline-Modus (Nr. 10)
    private static volatile boolean offlineMode = false;
    private static long lastOnlineCheck = 0;
    private static final long ONLINE_CHECK_INTERVAL_MS = 30000;
    
    public static class CachedPlayer {
        public String name;
        public String uuid;
        public JsonObject data;
        public long timestamp;
        public boolean available;
        
        public CachedPlayer(String name, String uuid, JsonObject data, boolean available) {
            this.name = name;
            this.uuid = uuid;
            this.data = data;
            this.available = available;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > getCacheExpiryMs();
        }
    }
    
    public static void init() {
        try {
            if (!Files.exists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
            }
            loadCacheConfig();
            loadCache();
            startSaveScheduler();
            startCleanupScheduler();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize cache: {}", e.getMessage());
        }
    }
    
    public static void shutdown() {
        if (saveScheduler != null && !saveScheduler.isShutdown()) {
            if (savePending) {
                doSaveCache();
            }
            saveScheduler.shutdown();
            try {
                if (!saveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    saveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                saveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // Nr. 1: Debounced Save Scheduler
    private static void startSaveScheduler() {
        saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "x7t-cache-saver");
            t.setDaemon(true);
            return t;
        });
    }
    
    // Nr. 2: Expired Cache Cleanup
    private static void startCleanupScheduler() {
        saveScheduler.scheduleAtFixedRate(() -> {
            int removed = cleanupExpiredEntries();
            if (removed > 0) {
                LOGGER.info("Cleaned up {} expired cache entries", removed);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    public static int cleanupExpiredEntries() {
        int removed = 0;
        Iterator<Map.Entry<String, CachedPlayer>> it = playerCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CachedPlayer> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            scheduleSave();
        }
        return removed;
    }
    
    public static void cachePlayer(String name, String uuid, JsonObject data, boolean available) {
        playerCache.put(name.toLowerCase(), new CachedPlayer(name, uuid, data, available));
        scheduleSave();
    }
    
    // Nr. 1: Debounced Save
    private static void scheduleSave() {
        if (!savePending && saveScheduler != null && !saveScheduler.isShutdown()) {
            savePending = true;
            saveScheduler.schedule(() -> {
                doSaveCache();
                savePending = false;
            }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }
    
    // Nr. 8: Cache-Statistiken
    public static CachedPlayer getCached(String name) {
        CachedPlayer cached = playerCache.get(name.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return null;
    }
    
    public static CachedPlayer getCachedOffline(String name) {
        CachedPlayer cached = playerCache.get(name.toLowerCase());
        if (cached != null) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
        return cached;
    }
    
    public static boolean hasCached(String name) {
        return playerCache.containsKey(name.toLowerCase());
    }
    
    public static void clearCache() {
        playerCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        scheduleSave();
    }
    
    public static int getCacheSize() {
        return playerCache.size();
    }
    
    // Nr. 8: Cache-Statistiken Getter
    public static long getCacheHits() {
        return cacheHits.get();
    }
    
    public static long getCacheMisses() {
        return cacheMisses.get();
    }
    
    public static double getCacheHitRate() {
        long hits = cacheHits.get();
        long total = hits + cacheMisses.get();
        return total > 0 ? (double) hits / total * 100.0 : 0.0;
    }
    
    public static String getCacheStats() {
        return String.format("Size: %d | Hits: %d | Misses: %d | Hit Rate: %.1f%%",
            getCacheSize(), getCacheHits(), getCacheMisses(), getCacheHitRate());
    }
    
    // Nr. 9: Konfigurierbare Cache-Expiry
    public static long getCacheExpiryMinutes() {
        return cacheExpiryMinutes;
    }
    
    public static void setCacheExpiryMinutes(long minutes) {
        cacheExpiryMinutes = Math.max(1, Math.min(1440, minutes)); // 1 min - 24h
        saveCacheConfig();
    }
    
    // Nr. 10: Offline-Modus
    public static boolean isOfflineMode() {
        return offlineMode;
    }
    
    public static void setOfflineMode(boolean offline) {
        offlineMode = offline;
        LOGGER.info("Offline mode: {}", offline ? "enabled" : "disabled");
    }
    
    public static boolean checkOnlineStatus() {
        long now = System.currentTimeMillis();
        if (now - lastOnlineCheck < ONLINE_CHECK_INTERVAL_MS) {
            return !offlineMode;
        }
        lastOnlineCheck = now;
        
        try {
            InetAddress address = InetAddress.getByName("api.crafty.gg");
            boolean reachable = address.isReachable(3000);
            if (offlineMode && reachable) {
                LOGGER.info("Connection restored, switching to online mode");
                offlineMode = false;
            } else if (!offlineMode && !reachable) {
                LOGGER.warn("Connection lost, switching to offline mode");
                offlineMode = true;
            }
            return reachable;
        } catch (Exception e) {
            if (!offlineMode) {
                LOGGER.warn("Network check failed, switching to offline mode: {}", e.getMessage());
                offlineMode = true;
            }
            return false;
        }
    }
    
    private static void loadCache() {
        if (!Files.exists(CACHE_FILE)) {
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(CACHE_FILE)) {
            Type type = new TypeToken<Map<String, CachedPlayer>>(){}.getType();
            Map<String, CachedPlayer> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                playerCache.clear();
                playerCache.putAll(loaded);
                LOGGER.info("Loaded {} cached players", loaded.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load cache: {}", e.getMessage());
        }
    }
    
    private static void doSaveCache() {
        try {
            if (!Files.exists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
            }
            try (Writer writer = Files.newBufferedWriter(CACHE_FILE)) {
                GSON.toJson(playerCache, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save cache: {}", e.getMessage());
        }
    }
    
    private static void loadCacheConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject config = GSON.fromJson(reader, JsonObject.class);
            if (config != null && config.has("cacheExpiryMinutes")) {
                cacheExpiryMinutes = config.get("cacheExpiryMinutes").getAsLong();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load cache config: {}", e.getMessage());
        }
    }
    
    private static void saveCacheConfig() {
        try {
            if (!Files.exists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
            }
            JsonObject config = new JsonObject();
            config.addProperty("cacheExpiryMinutes", cacheExpiryMinutes);
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save cache config: {}", e.getMessage());
        }
    }
}
