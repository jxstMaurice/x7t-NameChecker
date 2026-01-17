package com.x7t.namechecker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class WatchlistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("x7tnamechecker");
    private static final Path WATCHLIST_FILE = CONFIG_DIR.resolve("watchlist.json");
    
    private static final Set<String> watchlist = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> lastStatus = new ConcurrentHashMap<>();
    private static ScheduledExecutorService scheduler;
    private static final long CHECK_INTERVAL_MS = 60000; // Check every 60 seconds
    
    public static void init() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            loadWatchlist();
            startWatcher();
        } catch (IOException e) {
            System.err.println("[x7t Name Checker] Failed to initialize watchlist: " + e.getMessage());
        }
    }
    
    public static void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
    
    public static boolean addToWatchlist(String name) {
        if (watchlist.contains(name.toLowerCase())) {
            return false;
        }
        watchlist.add(name.toLowerCase());
        saveWatchlist();
        return true;
    }
    
    public static boolean removeFromWatchlist(String name) {
        boolean removed = watchlist.remove(name.toLowerCase());
        if (removed) {
            lastStatus.remove(name.toLowerCase());
            saveWatchlist();
        }
        return removed;
    }
    
    public static Set<String> getWatchlist() {
        return new HashSet<>(watchlist);
    }
    
    public static int getWatchlistSize() {
        return watchlist.size();
    }
    
    public static void clearWatchlist() {
        watchlist.clear();
        lastStatus.clear();
        saveWatchlist();
    }
    
    public static boolean isWatching(String name) {
        return watchlist.contains(name.toLowerCase());
    }
    
    private static void startWatcher() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "x7t-watchlist-checker");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(() -> {
            if (watchlist.isEmpty()) return;
            
            for (String name : watchlist) {
                try {
                    checkName(name);
                    Thread.sleep(1000); // Rate limiting between checks
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Ignore individual check failures
                }
            }
        }, 10, CHECK_INTERVAL_MS / 1000, TimeUnit.SECONDS);
    }
    
    private static void checkName(String name) {
        try {
            URI uri = new URI("https://api.ashcon.app/mojang/v2/user/" + name);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "x7t-name-checker/1.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            boolean available = (responseCode == 404 || responseCode == 204);
            
            Boolean previousStatus = lastStatus.get(name.toLowerCase());
            
            // If name became available (was taken before, now available)
            if (available && previousStatus != null && !previousStatus) {
                notifyAvailable(name);
            }
            
            lastStatus.put(name.toLowerCase(), available);
            
        } catch (Exception e) {
            // Silently ignore check failures
        }
    }
    
    private static void notifyAvailable(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            // Send chat notification
            client.player.sendMessage(Text.literal("§8§m                                        "), false);
            client.player.sendMessage(Text.literal("§8[§bx7t§8] §a§lName Available!"), false);
            client.player.sendMessage(Text.literal(""), false);
            client.player.sendMessage(Text.literal("§7The name §e" + name + " §7is now §aavailable§7!")
                .setStyle(Style.EMPTY
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§7Click to copy")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, name))), false);
            client.player.sendMessage(Text.literal("§7Claim it now at §bminecraft.net"), false);
            client.player.sendMessage(Text.literal("§8§m                                        "), false);
            
            // Play sound
            client.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }
    
    private static void loadWatchlist() {
        if (!Files.exists(WATCHLIST_FILE)) {
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(WATCHLIST_FILE)) {
            Type type = new TypeToken<Set<String>>(){}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                watchlist.clear();
                watchlist.addAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("[x7t Name Checker] Failed to load watchlist: " + e.getMessage());
        }
    }
    
    private static void saveWatchlist() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            try (Writer writer = Files.newBufferedWriter(WATCHLIST_FILE)) {
                GSON.toJson(watchlist, writer);
            }
        } catch (IOException e) {
            System.err.println("[x7t Name Checker] Failed to save watchlist: " + e.getMessage());
        }
    }
}
