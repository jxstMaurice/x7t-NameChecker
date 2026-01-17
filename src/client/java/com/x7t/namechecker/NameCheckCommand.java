package com.x7t.namechecker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NameCheckCommand {
    private static final String API_URL = "https://api.crafty.gg/api/v2/players/";
    private static final String AVAILABILITY_API = "https://api.ashcon.app/mojang/v2/user/";
    private static final String BEDROCK_API = "https://api.geysermc.org/v2/xbox/xuid/";
    private static final Gson GSON = new Gson();
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 3000;

    public static void execute(FabricClientCommandSource source, String playerName) {
        String oderId = source.getPlayer().getUuidAsString();
        long currentTime = System.currentTimeMillis();

        if (COOLDOWNS.containsKey(oderId)) {
            long lastUse = COOLDOWNS.get(oderId);
            long remaining = COOLDOWN_MS - (currentTime - lastUse);
            if (remaining > 0) {
                // Check cache first during cooldown
                NameCache.CachedPlayer cached = NameCache.getCached(playerName);
                if (cached != null && cached.data != null) {
                    source.sendFeedback(Text.literal("§8[§bx7t§8] §7Using cached data..."));
                    displayPlayerInfo(source, cached.data);
                    return;
                }
                source.sendFeedback(Text.literal("§8[§bx7t§8] §cPlease wait §e" + (remaining / 1000.0) + "s §cbefore checking again."));
                return;
            }
        }
        COOLDOWNS.put(oderId, currentTime);

        source.sendFeedback(Text.literal("§8[§bx7t§8] §7Checking name history for §e" + playerName + "§7..."));

        CompletableFuture.runAsync(() -> {
            try {
                String response = fetchPlayerData(playerName);
                if (response == null) {
                    // Try offline cache
                    NameCache.CachedPlayer cached = NameCache.getCachedOffline(playerName);
                    if (cached != null && cached.data != null) {
                        source.sendFeedback(Text.literal("§8[§bx7t§8] §eAPI unavailable, using cached data..."));
                        displayPlayerInfo(source, cached.data);
                        source.sendFeedback(Text.literal("§7§o(Cached " + getTimeAgo(cached.timestamp) + " ago)"));
                        return;
                    }
                    source.sendFeedback(Text.literal("§8[§bx7t§8] §cError: Could not connect to API"));
                    return;
                }

                JsonObject json = GSON.fromJson(response, JsonObject.class);

                if (!json.has("success") || !json.get("success").getAsBoolean()) {
                    String message = json.has("message") ? json.get("message").getAsString() : "Player not found";
                    // Try offline cache
                    NameCache.CachedPlayer cached = NameCache.getCachedOffline(playerName);
                    if (cached != null && cached.data != null) {
                        source.sendFeedback(Text.literal("§8[§bx7t§8] §ePlayer not found, showing cached data..."));
                        displayPlayerInfo(source, cached.data);
                        source.sendFeedback(Text.literal("§7§o(Cached " + getTimeAgo(cached.timestamp) + " ago)"));
                        return;
                    }
                    source.sendFeedback(Text.literal("§8[§bx7t§8] §cError: " + message));
                    return;
                }

                if (!json.has("data") || json.get("data").isJsonNull()) {
                    source.sendFeedback(Text.literal("§8[§bx7t§8] §cPlayer not found"));
                    return;
                }

                JsonObject data = json.getAsJsonObject("data");
                
                // Cache the result
                String uuid = data.has("uuid") ? data.get("uuid").getAsString() : null;
                NameCache.cachePlayer(playerName, uuid, data, false);
                
                displayPlayerInfo(source, data);

            } catch (Exception e) {
                // Try offline cache on error
                NameCache.CachedPlayer cached = NameCache.getCachedOffline(playerName);
                if (cached != null && cached.data != null) {
                    source.sendFeedback(Text.literal("§8[§bx7t§8] §eError occurred, using cached data..."));
                    displayPlayerInfo(source, cached.data);
                    source.sendFeedback(Text.literal("§7§o(Cached " + getTimeAgo(cached.timestamp) + " ago)"));
                    return;
                }
                source.sendFeedback(Text.literal("§8[§bx7t§8] §cError: " + e.getMessage()));
            }
        });
    }

    public static void checkAvailability(FabricClientCommandSource source, String name) {
        String oderId = source.getPlayer().getUuidAsString();
        long currentTime = System.currentTimeMillis();

        if (COOLDOWNS.containsKey(oderId)) {
            long lastUse = COOLDOWNS.get(oderId);
            long remaining = COOLDOWN_MS - (currentTime - lastUse);
            if (remaining > 0) {
                source.sendFeedback(Text.literal("§8[§bx7t§8] §cPlease wait §e" + (remaining / 1000.0) + "s §cbefore checking again."));
                return;
            }
        }
        COOLDOWNS.put(oderId, currentTime);

        source.sendFeedback(Text.literal("§8[§bx7t§8] §7Checking availability for §e" + name + "§7..."));

        CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI(AVAILABILITY_API + name);
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "x7t-name-checker/1.0");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();

                source.sendFeedback(Text.literal("§8§m                                        "));
                source.sendFeedback(Text.literal("§8[§bx7t Name Checker§8]"));
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

                source.sendFeedback(Text.literal("§8§m                                        "));

            } catch (Exception e) {
                source.sendFeedback(Text.literal("§8[§bx7t§8] §cError: " + e.getMessage()));
            }
        });
    }

    public static void checkBedrock(FabricClientCommandSource source, String gamertag) {
        String oderId = source.getPlayer().getUuidAsString();
        long currentTime = System.currentTimeMillis();

        if (COOLDOWNS.containsKey(oderId)) {
            long lastUse = COOLDOWNS.get(oderId);
            long remaining = COOLDOWN_MS - (currentTime - lastUse);
            if (remaining > 0) {
                source.sendFeedback(Text.literal("§8[§bx7t§8] §cPlease wait §e" + (remaining / 1000.0) + "s §cbefore checking again."));
                return;
            }
        }
        COOLDOWNS.put(oderId, currentTime);

        source.sendFeedback(Text.literal("§8[§bx7t§8] §7Checking Bedrock player §e" + gamertag + "§7..."));

        CompletableFuture.runAsync(() -> {
            try {
                // Try GeyserMC API first
                String encodedGamertag = java.net.URLEncoder.encode(gamertag, "UTF-8");
                String xuid = null;
                boolean found = false;
                
                // Try GeyserMC API
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
                } catch (Exception ignored) {
                    // API might be down, continue
                }

                source.sendFeedback(Text.literal("§8§m                                        "));
                source.sendFeedback(Text.literal("§8[§bx7t Name Checker§8] §a(Bedrock)"));
                source.sendFeedback(Text.literal(""));

                if (found && xuid != null) {
                    Text nameText = Text.literal("§7Gamertag: §a" + gamertag)
                        .setStyle(Style.EMPTY
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§7Click to copy")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, gamertag)));
                    source.sendFeedback(nameText);
                    
                    Text xuidText = Text.literal("§7XUID: §e" + xuid)
                        .setStyle(Style.EMPTY
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§7Click to copy XUID")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, xuid)));
                    source.sendFeedback(xuidText);
                    
                    // Format Floodgate UUID properly
                    String floodgateUuid = null;
                    try {
                        long xuidLong = Long.parseLong(xuid);
                        floodgateUuid = String.format("00000000-0000-0000-%04x-%012x", 
                            (xuidLong >> 48) & 0xFFFF, xuidLong & 0xFFFFFFFFFFFFL);
                        Text uuidText = Text.literal("§7Floodgate UUID: §e" + floodgateUuid)
                            .setStyle(Style.EMPTY
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§7Click to copy UUID")))
                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, floodgateUuid)));
                        source.sendFeedback(uuidText);
                    } catch (NumberFormatException e) {
                        source.sendFeedback(Text.literal("§7XUID (raw): §e" + xuid));
                    }
                    
                    // Fetch Name History from crafty.gg using Floodgate UUID
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
                                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hoverInfo)))
                                                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, name)));
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

                source.sendFeedback(Text.literal("§8§m                                        "));

            } catch (Exception e) {
                source.sendFeedback(Text.literal("§8[§bx7t§8] §cError: " + e.getMessage()));
            }
        });
    }

    private static String fetchPlayerData(String playerName) {
        try {
            URI uri = new URI(API_URL + playerName);
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

        } catch (Exception e) {
            return null;
        }
    }

    private static void displayPlayerInfo(FabricClientCommandSource source, JsonObject data) {
        source.sendFeedback(Text.literal("§8§m                                        "));
        source.sendFeedback(Text.literal("§8[§bx7t Name Checker§8]"));
        source.sendFeedback(Text.literal(""));

        if (data.has("username") && !data.get("username").isJsonNull()) {
            String username = data.get("username").getAsString();
            Text nameText = Text.literal("§7Current Name: §a" + username)
                .setStyle(Style.EMPTY
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§7Click to copy name")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, username)));
            source.sendFeedback(nameText);
        }

        if (data.has("uuid") && !data.get("uuid").isJsonNull()) {
            String uuid = data.get("uuid").getAsString();
            Text uuidText = Text.literal("§7UUID: §e" + uuid)
                .setStyle(Style.EMPTY
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§7Click to copy UUID")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid)));
            source.sendFeedback(uuidText);
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
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hoverInfo)))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, name)));
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

        source.sendFeedback(Text.literal("§8§m                                        "));
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
