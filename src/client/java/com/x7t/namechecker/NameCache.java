package com.x7t.namechecker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NameCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_DIR = FabricLoader.getInstance().getConfigDir().resolve("x7tnamechecker");
    private static final Path CACHE_FILE = CACHE_DIR.resolve("cache.json");
    
    private static final Map<String, CachedPlayer> playerCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes
    
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
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
    
    public static void init() {
        try {
            if (!Files.exists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
            }
            loadCache();
        } catch (IOException e) {
            System.err.println("[x7t Name Checker] Failed to initialize cache: " + e.getMessage());
        }
    }
    
    public static void cachePlayer(String name, String uuid, JsonObject data, boolean available) {
        playerCache.put(name.toLowerCase(), new CachedPlayer(name, uuid, data, available));
        saveCache();
    }
    
    public static CachedPlayer getCached(String name) {
        CachedPlayer cached = playerCache.get(name.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        return null;
    }
    
    public static CachedPlayer getCachedOffline(String name) {
        // Returns cached data even if expired (for offline mode)
        return playerCache.get(name.toLowerCase());
    }
    
    public static boolean hasCached(String name) {
        return playerCache.containsKey(name.toLowerCase());
    }
    
    public static void clearCache() {
        playerCache.clear();
        saveCache();
    }
    
    public static int getCacheSize() {
        return playerCache.size();
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
            }
        } catch (Exception e) {
            System.err.println("[x7t Name Checker] Failed to load cache: " + e.getMessage());
        }
    }
    
    private static void saveCache() {
        try {
            if (!Files.exists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
            }
            try (Writer writer = Files.newBufferedWriter(CACHE_FILE)) {
                GSON.toJson(playerCache, writer);
            }
        } catch (IOException e) {
            System.err.println("[x7t Name Checker] Failed to save cache: " + e.getMessage());
        }
    }
}
