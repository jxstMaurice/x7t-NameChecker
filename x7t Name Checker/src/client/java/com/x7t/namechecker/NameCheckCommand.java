package com.x7t.namechecker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NameCheckCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("x7t-namechecker");
    
    // Konstanten zentralisiert (Nr. 5)
    public static final String PREFIX = "§8[§bx7t§8] ";
    public static final String HEADER = "§8[§bx7t Name Checker§8]";
    public static final String SEPARATOR = "§8§m                                        ";
    
    private static final String API_URL = "https://api.crafty.gg/api/v2/players/";
    private static final String AVAILABILITY_API = "https://api.ashcon.app/mojang/v2/user/";
    private static final String BEDROCK_API = "https://api.geysermc.org/v2/xbox/xuid/";
    private static final Gson GSON = new Gson();
    
    // Nr. 3: ConcurrentHashMap für Thread-Safety
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000;
    
    private static final Map<String, Long> API_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long CRAFTY_API_COOLDOWN_MS = 1500;
    private static final long ASHCON_API_COOLDOWN_MS = 2000;
    private static final long GEYSER_API_COOLDOWN_MS = 1000;
    
    private static boolean checkApiCooldown(String apiName, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long lastCall = API_COOLDOWNS.get(apiName);
        if (lastCall != null && (now - lastCall) < cooldownMs) {
            return false;
        }
        API_COOLDOWNS.put(apiName, now);
        return true;
    }
    
    private static long getApiCooldownRemaining(String apiName, long cooldownMs) {
        Long lastCall = API_COOLDOWNS.get(apiName);
        if (lastCall == null) return 0;
        long remaining = cooldownMs - (System.currentTimeMillis() - lastCall);
        return Math.max(0, remaining);
    }

    public static void execute(FabricClientCommandSource source, String playerName) {
        // Nr. 10: Offline-Modus Check
        if (NameCache.isOfflineMode()) {
            NameCache.CachedPlayer cached = NameCache.getCachedOffline(playerName);
            if (cached != null && cached.data != null) {
                source.sendFeedback(Text.literal(PREFIX + "§eOffline mode - using cached data..."));
                displayPlayerInfo(source, cached.data);
                source.sendFeedback(Text.literal("§7§o(Cached " + getTimeAgo(cached.timestamp) + " ago)"));
                return;
            } else {
                source.sendFeedback(Text.literal(PREFIX + "§cOffline mode - no cached data available"));
                return;
            }
        }
        
        final String nameToCheck = playerName;
        String playerId = source.getPlayer().getUuidAsString();
        long currentTime = System.currentTimeMillis();

        if (COOLDOWNS.containsKey(playerId)) {
            long lastUse = COOLDOWNS.get(playerId);
            long remaining = COOLDOWN_MS - (currentTime - lastUse);
            if (remaining > 0) {
                NameCache.CachedPlayer cached = NameCache.getCached(nameToCheck);
                if (cached != null && cached.data != null) {
                    source.sendFeedback(Text.literal(PREFIX + "§7Using cached data..."));
                    displayPlayerInfo(source, cached.data);
                    return;
                }
                source.sendFeedback(Text.literal(PREFIX + "§cPlease wait §e" + String.format("%.1f", remaining / 1000.0) + "s §cbefore checking again."));
                return;
            }
        }
        COOLDOWNS.put(playerId, currentTime);

        source.sendFeedback(Text.literal(PREFIX + "§7Checking name history for §e" + nameToCheck + "§7..."));

        CompletableFuture.runAsync(() -> {
            if (!checkApiCooldown("crafty", CRAFTY_API_COOLDOWN_MS)) {
                long remaining = getApiCooldownRemaining("crafty", CRAFTY_API_COOLDOWN_MS);
                try { Thread.sleep(remaining); } catch (InterruptedException e) {
                    LOGGER.debug("API cooldown sleep interrupted"); // Nr. 7: Logging
                    Thread.currentThread().interrupt();
                }
            }
            
            try {
                String response = fetchPlayerData(nameToCheck);
                if (response == null) {
                    NameCache.CachedPlayer cached = NameCache.getCachedOffline(nameToCheck);
                    if (cached != null && cached.data != null) {
                        source.sendFeedback(Text.literal(PREFIX + "§eAPI unavailable, using cached data..."));
                        displayPlayerInfo(source, cached.data);
                        source.sendFeedback(Text.literal("§7§o(Cached " + getTimeAgo(cached.timestamp) + " ago)"));
                        return;
                    }
                    source.sendFeedback(Text.literal(PREFIX + "§cError: Could not connect to API"));
                    LOGGER.warn("Failed to fetch player data for: {}", nameToCheck); // Nr. 7
                    return;
                }

                JsonObject json = GSON.fromJson(response, JsonObject.class);

                if (!json.has("success") || !json.get("success").getAsBoolean()) {
                    String message = json.has("message") ? json.get("message").getAsString() : "Player not found";
                    NameCache.CachedPlayer cached = NameCache.getCachedOffline(nameToCheck);
                    if (cached != null && cached.data != null) {
                        source.sendFeedback(Text.literal(PREFIX + "§ePlayer not found, showing cached data..."));
                        displayPlayerInfo(source, cached.data);
                        source.sendFeedback(Text.literal("§7§o(Cached " + getTimeAgo(cached.timestamp) + " ago)"));
                        return;
                    }
                    source.sendFeedback(Text.literal(PREFIX + "§cError: " + message));
                    return;
                }

                if (!json.has("data") || json.get("data").isJsonNull()) {
                    source.sendFeedback(Text.literal(PREFIX + "§cPlayer not found"));
                    return;
                }

                JsonObject data = json.getAsJsonObject("data");
                
                String uuid = data.has("uuid") ? data.get("uuid").getAsString() : null;
                NameCache.cachePlayer(nameToCheck, uuid, data, false);
                
                displayPlayerInfo(source, data);

            } catch (Exception e) {
                LOGGER.error("Error checking player {}: {}", nameToCheck, e.getMessage()); // Nr. 7
                NameCache.CachedPlayer cached = NameCache.getCachedOffline(nameToCheck);
                if (cached != null && cached.data != null) {
                    source.sendFeedback(Text.literal(PREFIX + "§eError occurred, using cached data..."));
                    displayPlayerInfo(source, cached.data);
                    source.sendFeedback(Text.literal("§7§o(Cached " + getTimeAgo(cached.timestamp) + " ago)"));
                    return;
                }
                source.sendFeedback(Text.literal(PREFIX + "§cError: " + e.getMessage()));
            }
        });
    }

    public static void checkAvailability(FabricClientCommandSource source, String name) {
        String playerId = source.getPlayer().getUuidAsString();
        long currentTime = System.currentTimeMillis();

        if (COOLDOWNS.containsKey(playerId)) {
            long lastUse = COOLDOWNS.get(playerId);
            long remaining = COOLDOWN_MS - (currentTime - lastUse);
            if (remaining > 0) {
                source.sendFeedback(Text.literal(PREFIX + "§cPlease wait §e" + String.format("%.1f", remaining / 1000.0) + "s §cbefore checking again."));
                return;
            }
        }
        COOLDOWNS.put(playerId, currentTime);

        source.sendFeedback(Text.literal(PREFIX + "§7Checking availability for §e" + name + "§7..."));

        CompletableFuture.runAsync(() -> {
            if (!checkApiCooldown("ashcon", ASHCON_API_COOLDOWN_MS)) {
                long remaining = getApiCooldownRemaining("ashcon", ASHCON_API_COOLDOWN_MS);
                try { Thread.sleep(remaining); } catch (InterruptedException e) {
                    LOGGER.debug("API cooldown sleep interrupted");
                    Thread.currentThread().interrupt();
                }
            }
            
            try {
                URI uri = new URI(AVAILABILITY_API + name);
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "x7t-name-checker/1.0");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();

                source.sendFeedback(Text.literal(SEPARATOR));
                source.sendFeedback(Text.literal(HEADER));
                source.sendFeedback(Text.literal(""));
                source.sendFeedback(Text.literal("§7Name: §e" + name));

                if (responseCode == 200) {
                    source.sendFeedback(Text.literal("§7Status: §cTaken"));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JsonObject json = GSON.fromJson(response.toString(), JsonObject.class);
                    if (json.has("username")) {
                        source.sendFeedback(Text.literal("§7Current Owner: §a" + json.get("username").getAsString()));
                    }
                } else if (responseCode == 404 || responseCode == 204) {
                    source.sendFeedback(Text.literal("§7Status: §aAvailable!"));
                    source.sendFeedback(Text.literal("§7This name can be claimed."));
                } else {
                    source.sendFeedback(Text.literal("§7Status: §eUnknown"));
                }

                source.sendFeedback(Text.literal(SEPARATOR));

            } catch (Exception e) {
                LOGGER.error("Error checking availability for {}: {}", name, e.getMessage());
                source.sendFeedback(Text.literal(PREFIX + "§cError: " + e.getMessage()));
            }
        });
    }

    public static void checkBedrock(FabricClientCommandSource source, String gamertag) {
        String playerId = source.getPlayer().getUuidAsString();
        long currentTime = System.currentTimeMillis();

        if (COOLDOWNS.containsKey(playerId)) {
            long lastUse = COOLDOWNS.get(playerId);
            long remaining = COOLDOWN_MS - (currentTime - lastUse);
            if (remaining > 0) {
                source.sendFeedback(Text.literal(PREFIX + "§cPlease wait §e" + String.format("%.1f", remaining / 1000.0) + "s §cbefore checking again."));
                return;
            }
        }
        COOLDOWNS.put(playerId, currentTime);

        source.sendFeedback(Text.literal(PREFIX + "§7Checking Bedrock player §e" + gamertag + "§7..."));

        CompletableFuture.runAsync(() -> {
            if (!checkApiCooldown("geyser", GEYSER_API_COOLDOWN_MS)) {
                long remaining = getApiCooldownRemaining("geyser", GEYSER_API_COOLDOWN_MS);
                try { Thread.sleep(remaining); } catch (InterruptedException e) {
                    LOGGER.debug("API cooldown sleep interrupted");
                    Thread.currentThread().interrupt();
                }
            }
            
            try {
                String encodedGamertag = java.net.URLEncoder.encode(gamertag, "UTF-8");
                String xuid = null;
                boolean found = false;
                
                try {
                    URI uri = new URI(BEDROCK_API + encodedGamertag);
                    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", "x7t-name-checker/1.0");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    int responseCode = connection.getResponseCode();
                    
                    if (responseCode == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        xuid = response.toString().replace("\"", "").trim();
                        if (xuid != null && !xuid.isEmpty() && !xuid.equals("null")) {
                            found = true;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Bedrock API call failed: {}", e.getMessage()); // Nr. 7
                }

                source.sendFeedback(Text.literal(SEPARATOR));
                source.sendFeedback(Text.literal(HEADER + " §a(Bedrock)"));
                source.sendFeedback(Text.literal(""));

                if (found && xuid != null) {
                    Text nameText = Text.literal("§7Gamertag: §a" + gamertag)
                        .setStyle(Style.EMPTY
                            .withHoverEvent(TextEventCompat.showText(Text.literal("§7Click to copy")))
                            .withClickEvent(TextEventCompat.copyToClipboard(gamertag)));
                    source.sendFeedback(nameText);
                    
                    Text xuidText = Text.literal("§7XUID: §e" + xuid)
                        .setStyle(Style.EMPTY
                            .withHoverEvent(TextEventCompat.showText(Text.literal("§7Click to copy XUID")))
                            .withClickEvent(TextEventCompat.copyToClipboard(xuid)));
                    source.sendFeedback(xuidText);
                    
                    String floodgateUuid = null;
                    try {
                        if (xuid == null || xuid.isEmpty() || !xuid.matches("\\d+")) {
                            throw new IllegalArgumentException("Invalid XUID format");
                        }
                        long xuidLong = Long.parseLong(xuid);
                        if (xuidLong <= 0) {
                            throw new IllegalArgumentException("XUID must be positive");
                        }
                        floodgateUuid = String.format("00000000-0000-0000-%04x-%012x", 
                            (xuidLong >> 48) & 0xFFFF, xuidLong & 0xFFFFFFFFFFFFL);
                        Text uuidText = Text.literal("§7Floodgate UUID: §e" + floodgateUuid)
                            .setStyle(Style.EMPTY
                                .withHoverEvent(TextEventCompat.showText(Text.literal("§7Click to copy UUID")))
                                .withClickEvent(TextEventCompat.copyToClipboard(floodgateUuid)));
                        source.sendFeedback(uuidText);
                    } catch (IllegalArgumentException e) {
                        source.sendFeedback(Text.literal("§7XUID (raw): §e" + xuid + " §8(§cInvalid format§8)"));
                    }
                    
                    source.sendFeedback(Text.literal(""));
                    if (floodgateUuid != null) {
                        try {
                            String historyResponse = fetchPlayerData(floodgateUuid);
                            if (historyResponse != null) {
                                JsonObject historyJson = GSON.fromJson(historyResponse, JsonObject.class);
                                if (historyJson.has("success") && historyJson.get("success").getAsBoolean() 
                                    && historyJson.has("data") && !historyJson.get("data").isJsonNull()) {
                                    JsonObject historyData = historyJson.getAsJsonObject("data");
                                    if (historyData.has("usernames") && historyData.get("usernames").isJsonArray()) {
                                        JsonArray usernames = historyData.getAsJsonArray("usernames");
                                        if (usernames.size() > 0) {
                                            source.sendFeedback(Text.literal("§7Name History §8(§f" + usernames.size() + "§8):"));
                                            for (int i = 0; i < usernames.size(); i++) {
                                                JsonElement element = usernames.get(i);
                                                if (element.isJsonObject()) {
                                                    JsonObject entry = element.getAsJsonObject();
                                                    String name = entry.has("username") ? entry.get("username").getAsString() : "Unknown";
                                                    String changedAt = "";
                                                    String hoverInfo = "§7Name: §f" + name;

                                                    if (entry.has("changed_at") && !entry.get("changed_at").isJsonNull()) {
                                                        changedAt = " §8(§7" + entry.get("changed_at").getAsString() + "§8)";
                                                        hoverInfo += "\n§7Changed: §e" + entry.get("changed_at").getAsString();
                                                    } else if (i == usernames.size() - 1) {
                                                        changedAt = " §8(§7Original§8)";
                                                        hoverInfo += "\n§7Original name";
                                                    }

                                                    hoverInfo += "\n\n§8Click to copy";

                                                    Text nameEntry = Text.literal("  §8» §f" + name + changedAt)
                                                        .setStyle(Style.EMPTY
                                                            .withHoverEvent(TextEventCompat.showText(Text.literal(hoverInfo)))
                                                            .withClickEvent(TextEventCompat.copyToClipboard(name)));
                                                    source.sendFeedback(nameEntry);
                                                }
                                            }
                                        } else {
                                            source.sendFeedback(Text.literal("§7Name History: §eNo history available"));
                                        }
                                    } else {
                                        source.sendFeedback(Text.literal("§7Name History: §eNo history available"));
                                    }
                                } else {
                                    source.sendFeedback(Text.literal("§7Name History: §eNot tracked yet"));
                                }
                            } else {
                                source.sendFeedback(Text.literal("§7Name History: §eNot tracked yet"));
                            }
                        } catch (Exception e) {
                            source.sendFeedback(Text.literal("§7Name History: §cUnavailable"));
                        }
                    } else {
                        source.sendFeedback(Text.literal("§7Name History: §eXbox does not provide public name history"));
                    }
                    
                    source.sendFeedback(Text.literal(""));
                    source.sendFeedback(Text.literal("§7Status: §aBedrock Account Found!"));
                    source.sendFeedback(Text.literal("§7Platform: §bXbox/Bedrock"));
                } else {
                    source.sendFeedback(Text.literal("§7Gamertag: §e" + gamertag));
                    source.sendFeedback(Text.literal(""));
                    source.sendFeedback(Text.literal("§7Status: §cNot Found / API Unavailable"));
                    source.sendFeedback(Text.literal("§7The Bedrock account was not found or the API is currently unavailable."));
                    source.sendFeedback(Text.literal("§7Try again later or verify the gamertag is correct."));
                }

                source.sendFeedback(Text.literal(SEPARATOR));

            } catch (Exception e) {
                LOGGER.error("Error checking Bedrock player {}: {}", gamertag, e.getMessage());
                source.sendFeedback(Text.literal(PREFIX + "§cError: " + e.getMessage()));
            }
        });
    }

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private static String fetchPlayerData(String playerName) {
        return fetchWithRetry(API_URL + playerName, MAX_RETRIES);
    }

    private static String fetchWithRetry(String url, int maxRetries) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String result = doFetch(url);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return null;
    }

    private static String doFetch(String urlString) throws Exception {
        URI uri = new URI(urlString);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "x7t-name-checker/1.0");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                if (connection.getErrorStream() != null) {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    errorReader.close();
                    return errorResponse.toString();
                }
                return "{\"success\":false,\"message\":\"HTTP " + responseCode + "\"}";
            }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    private static void displayPlayerInfo(FabricClientCommandSource source, JsonObject data) {
        source.sendFeedback(Text.literal(SEPARATOR));
        source.sendFeedback(Text.literal(HEADER));
        source.sendFeedback(Text.literal(""));

        if (data.has("username") && !data.get("username").isJsonNull()) {
            String username = data.get("username").getAsString();
            Text nameText = Text.literal("§7Current Name: §a" + username)
                .setStyle(Style.EMPTY
                    .withHoverEvent(TextEventCompat.showText(Text.literal("§7Click to copy name")))
                    .withClickEvent(TextEventCompat.copyToClipboard(username)));
            source.sendFeedback(nameText);
        }

        if (data.has("uuid") && !data.get("uuid").isJsonNull()) {
            String uuid = data.get("uuid").getAsString();
            Text uuidText = Text.literal("§7UUID: §e" + uuid)
                .setStyle(Style.EMPTY
                    .withHoverEvent(TextEventCompat.showText(Text.literal("§7Click to copy UUID")))
                    .withClickEvent(TextEventCompat.copyToClipboard(uuid)));
            source.sendFeedback(uuidText);
            
            String username = data.has("username") ? data.get("username").getAsString() : null;
            if (username != null) {
                String namemcUrl = "https://namemc.com/profile/" + username;
                Text skinLink = Text.literal("§7Skin: §b[View on NameMC]")
                    .setStyle(Style.EMPTY
                        .withHoverEvent(TextEventCompat.showText(Text.literal("§7Click to open NameMC profile\n§8" + namemcUrl)))
                        .withClickEvent(TextEventCompat.openUrl(namemcUrl)));
                source.sendFeedback(skinLink);
            }
        }

        if (data.has("usernames") && data.get("usernames").isJsonArray()) {
            JsonArray usernames = data.getAsJsonArray("usernames");
            source.sendFeedback(Text.literal(""));
            source.sendFeedback(Text.literal("§7Name History §8(§f" + usernames.size() + "§8):"));

            for (int i = 0; i < usernames.size(); i++) {
                JsonElement element = usernames.get(i);
                if (element.isJsonObject()) {
                    JsonObject entry = element.getAsJsonObject();
                    String name = entry.has("username") ? entry.get("username").getAsString() : "Unknown";
                    String changedAt = "";
                    String hoverInfo = "§7Name: §f" + name;

                    if (entry.has("changed_at") && !entry.get("changed_at").isJsonNull()) {
                        changedAt = " §8(§7" + entry.get("changed_at").getAsString() + "§8)";
                        hoverInfo += "\n§7Changed: §e" + entry.get("changed_at").getAsString();
                    } else if (i == usernames.size() - 1) {
                        changedAt = " §8(§7Original§8)";
                        hoverInfo += "\n§7Original name";
                    }

                    if (entry.has("available") && !entry.get("available").isJsonNull()) {
                        boolean available = entry.get("available").getAsBoolean();
                        hoverInfo += "\n§7Available: " + (available ? "§aYes" : "§cNo");
                    }

                    hoverInfo += "\n\n§8Click to copy";

                    Text nameEntry = Text.literal("  §8» §f" + name + changedAt)
                        .setStyle(Style.EMPTY
                            .withHoverEvent(TextEventCompat.showText(Text.literal(hoverInfo)))
                            .withClickEvent(TextEventCompat.copyToClipboard(name)));
                    source.sendFeedback(nameEntry);
                }
            }
        } else {
            source.sendFeedback(Text.literal(""));
            source.sendFeedback(Text.literal("§7No name history available"));
        }

        if (data.has("created_at") && !data.get("created_at").isJsonNull()) {
            source.sendFeedback(Text.literal(""));
            source.sendFeedback(Text.literal("§7First Seen: §e" + data.get("created_at").getAsString()));
        }

        if (data.has("views_lifetime") && !data.get("views_lifetime").isJsonNull()) {
            source.sendFeedback(Text.literal("§7Profile Views: §e" + data.get("views_lifetime").getAsInt()));
        }

        source.sendFeedback(Text.literal(SEPARATOR));
    }
    
    private static String getTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }
}
