package com.x7t.namechecker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class WatchlistManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("x7t-namechecker");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("x7tnamechecker");
    private static final Path WATCHLIST_FILE = CONFIG_DIR.resolve("watchlist.json");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    
    private static final Set<String> watchlist = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> lastStatus = new ConcurrentHashMap<>();
    private static ScheduledExecutorService scheduler;
    private static final long CHECK_INTERVAL_MS = 60000;
    
    private static String notificationSoundId = "entity.player.levelup";
    private static float notificationVolume = 1.0f;
    
    public static void init() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            loadConfig();
            loadWatchlist();
            startWatcher();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize watchlist: {}", e.getMessage());
        }
    }
    
    public static void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                        LOGGER.warn("Watchlist scheduler did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public static void setNotificationSound(String soundId) {
        notificationSoundId = soundId;
        saveConfig();
    }
    
    public static void setNotificationVolume(float volume) {
        notificationVolume = Math.max(0.0f, Math.min(1.0f, volume));
        saveConfig();
    }
    
    public static String getNotificationSoundId() {
        return notificationSoundId;
    }
    
    public static float getNotificationVolume() {
        return notificationVolume;
    }
    
    private static final int MAX_WATCHLIST_SIZE = 10;

    public static boolean addToWatchlist(String name) {
        if (watchlist.contains(name.toLowerCase())) {
            return false;
        }
        if (watchlist.size() >= MAX_WATCHLIST_SIZE) {
            return false;
        }
        watchlist.add(name.toLowerCase());
        saveWatchlist();
        return true;
    }

    public static boolean isWatchlistFull() {
        return watchlist.size() >= MAX_WATCHLIST_SIZE;
    }

    public static int getMaxWatchlistSize() {
        return MAX_WATCHLIST_SIZE;
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
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
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
            
            if (available && previousStatus != null && !previousStatus) {
                notifyAvailable(name);
            }
            
            lastStatus.put(name.toLowerCase(), available);
            
        } catch (Exception e) {
            LOGGER.debug("Error checking watchlist name {}: {}", name, e.getMessage()); // Nr. 7
        }
    }
    
    private static void notifyAvailable(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(NameCheckCommand.SEPARATOR), false);
            client.player.sendMessage(Text.literal(NameCheckCommand.PREFIX + "§a§lName Available!"), false);
            client.player.sendMessage(Text.literal(""), false);
            client.player.sendMessage(Text.literal("§7The name §e" + name + " §7is now §aavailable§7!")
                .setStyle(Style.EMPTY
                    .withHoverEvent(TextEventCompat.showText(Text.literal("§7Click to copy")))
                    .withClickEvent(TextEventCompat.copyToClipboard(name))), false);
            client.player.sendMessage(Text.literal("§7Claim it now at §bminecraft.net"), false);
            client.player.sendMessage(Text.literal(NameCheckCommand.SEPARATOR), false);
            
            LOGGER.info("Name '{}' became available!", name); // Nr. 7
            
            if (notificationVolume > 0) {
                SoundEvent sound = getSoundFromId(notificationSoundId);
                if (sound != null) {
                    client.player.playSound(sound, notificationVolume, 1.0f);
                }
            }
        }
    }
    
    private static SoundEvent getSoundFromId(String soundId) {
        return switch (soundId.toLowerCase()) {
            case "entity.player.levelup", "levelup" -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            case "entity.experience_orb.pickup", "xp" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "block.note_block.pling", "pling" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case "block.note_block.bell", "bell" -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case "block.note_block.chime", "chime" -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
            case "entity.arrow.hit_player", "hit" -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
            case "ui.toast.challenge_complete", "toast" -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case "none", "off", "disabled" -> null;
            default -> SoundEvents.ENTITY_PLAYER_LEVELUP;
        };
    }
    
    private static void loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject config = GSON.fromJson(reader, JsonObject.class);
            if (config != null) {
                if (config.has("notificationSound")) {
                    notificationSoundId = config.get("notificationSound").getAsString();
                }
                if (config.has("notificationVolume")) {
                    notificationVolume = config.get("notificationVolume").getAsFloat();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config: {}", e.getMessage());
        }
    }
    
    private static void saveConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            JsonObject config = new JsonObject();
            config.addProperty("notificationSound", notificationSoundId);
            config.addProperty("notificationVolume", notificationVolume);
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config: {}", e.getMessage());
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
            LOGGER.error("Failed to load watchlist: {}", e.getMessage());
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
            LOGGER.error("Failed to save watchlist: {}", e.getMessage());
        }
    }
}
