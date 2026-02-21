package com.x7t.namechecker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import java.util.Collection;
import java.util.Set;

public class X7tNameCheckerClient implements ClientModInitializer {

    private static final SuggestionProvider<FabricClientCommandSource> PLAYER_SUGGESTIONS = (context, builder) -> {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            Collection<PlayerListEntry> players = client.getNetworkHandler().getPlayerList();
            for (PlayerListEntry entry : players) {
                String name = entry.getProfile().getName();
                if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> BEDROCK_PLAYER_SUGGESTIONS = (context, builder) -> {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            Collection<PlayerListEntry> players = client.getNetworkHandler().getPlayerList();
            for (PlayerListEntry entry : players) {
                String name = entry.getProfile().getName();
                if (name.startsWith(".") || name.startsWith("*")) {
                    String cleanName = name.substring(1);
                    if (cleanName.toLowerCase().startsWith(builder.getRemainingLowerCase()) ||
                        name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                        builder.suggest(cleanName);
                    }
                } else if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> WATCHLIST_SUGGESTIONS = (context, builder) -> {
        Set<String> watchlist = WatchlistManager.getWatchlist();
        for (String name : watchlist) {
            if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    @Override
    public void onInitializeClient() {
        NameCache.init();
        WatchlistManager.init();
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("namecheck")
                .executes(context -> {
                    showInfo(context.getSource());
                    return 1;
                })
                .then(ClientCommandManager.literal("available")
                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            NameCheckCommand.checkAvailability(context.getSource(), name);
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("bedrock")
                    .then(ClientCommandManager.argument("gamertag", StringArgumentType.greedyString())
                        .suggests(BEDROCK_PLAYER_SUGGESTIONS)
                        .executes(context -> {
                            String gamertag = StringArgumentType.getString(context, "gamertag");
                            NameCheckCommand.checkBedrock(context.getSource(), gamertag);
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("watch")
                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            if (WatchlistManager.isWatching(name)) {
                                context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§cThis name is already on your watchlist!"));
                            } else if (WatchlistManager.isWatchlistFull()) {
                                context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§cWatchlist is full! §7(Max " + WatchlistManager.getMaxWatchlistSize() + " names)"));
                                context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§7Use §f/namecheck unwatch <name> §7to remove a name."));
                            } else if (WatchlistManager.addToWatchlist(name)) {
                                context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§aAdded §e" + name + " §ato watchlist!"));
                                context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§7You will be notified when this name becomes available."));
                            } else {
                                context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§cFailed to add name to watchlist!"));
                            }
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("unwatch")
                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                        .suggests(WATCHLIST_SUGGESTIONS)
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            if (WatchlistManager.removeFromWatchlist(name)) {
                                context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§aRemoved §e" + name + " §afrom watchlist!"));
                            } else {
                                context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§cThis name is not on your watchlist!"));
                            }
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("watchlist")
                    .executes(context -> {
                        showWatchlist(context.getSource());
                        return 1;
                    })
                    .then(ClientCommandManager.literal("clear")
                        .executes(context -> {
                            WatchlistManager.clearWatchlist();
                            context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§aWatchlist cleared!"));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("cache")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal(NameCheckCommand.SEPARATOR));
                        context.getSource().sendFeedback(Text.literal("§8[§bx7t Cache§8]"));
                        context.getSource().sendFeedback(Text.literal(""));
                        context.getSource().sendFeedback(Text.literal("§7Cached Players: §e" + NameCache.getCacheSize()));
                        context.getSource().sendFeedback(Text.literal("§7Cache Hits: §a" + NameCache.getCacheHits()));
                        context.getSource().sendFeedback(Text.literal("§7Cache Misses: §c" + NameCache.getCacheMisses()));
                        context.getSource().sendFeedback(Text.literal("§7Hit Rate: §e" + String.format("%.1f", NameCache.getCacheHitRate()) + "%"));
                        context.getSource().sendFeedback(Text.literal("§7Expiry: §e" + NameCache.getCacheExpiryMinutes() + " §7minutes"));
                        context.getSource().sendFeedback(Text.literal("§7Offline Mode: " + (NameCache.isOfflineMode() ? "§aEnabled" : "§cDisabled")));
                        context.getSource().sendFeedback(Text.literal(NameCheckCommand.SEPARATOR));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("clear")
                        .executes(context -> {
                            NameCache.clearCache();
                            context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§aCache cleared!"));
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("offline")
                        .executes(context -> {
                            boolean newState = !NameCache.isOfflineMode();
                            NameCache.setOfflineMode(newState);
                            context.getSource().sendFeedback(Text.literal(NameCheckCommand.PREFIX + "§7Offline mode: " + (newState ? "§aEnabled" : "§cDisabled")));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .suggests(PLAYER_SUGGESTIONS)
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "player");
                        NameCheckCommand.execute(context.getSource(), name);
                        return 1;
                    })
                )
            );
        });
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            WatchlistManager.shutdown();
            NameCache.shutdown();
        }));
    }

    private static void showWatchlist(FabricClientCommandSource source) {
        Set<String> watchlist = WatchlistManager.getWatchlist();
        
        source.sendFeedback(Text.literal(NameCheckCommand.SEPARATOR));
        source.sendFeedback(Text.literal("§8[§bx7t Watchlist§8]"));
        source.sendFeedback(Text.literal(""));
        
        if (watchlist.isEmpty()) {
            source.sendFeedback(Text.literal("§7Your watchlist is empty."));
            source.sendFeedback(Text.literal("§7Use §f/namecheck watch <name> §7to add names."));
        } else {
            source.sendFeedback(Text.literal("§7Watching §e" + watchlist.size() + "/" + WatchlistManager.getMaxWatchlistSize() + " §7names:"));
            source.sendFeedback(Text.literal(""));
            for (String name : watchlist) {
                Text nameText = Text.literal("  §8» §f" + name)
                    .setStyle(Style.EMPTY
                        .withHoverEvent(TextEventCompat.showText(Text.literal("§7Click to remove from watchlist")))
                        .withClickEvent(TextEventCompat.runCommand("/namecheck unwatch " + name)));
                source.sendFeedback(nameText);
            }
            source.sendFeedback(Text.literal(""));
            source.sendFeedback(Text.literal("§7Checking every §e60 §7seconds..."));
        }
        
        source.sendFeedback(Text.literal(NameCheckCommand.SEPARATOR));
    }

    private static void showInfo(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal(NameCheckCommand.SEPARATOR));
        source.sendFeedback(Text.literal(NameCheckCommand.HEADER));
        source.sendFeedback(Text.literal(""));
        source.sendFeedback(Text.literal("§7Owner: §bJxstMaurice §7& §eVeridon"));
        source.sendFeedback(Text.literal("§7Bedrock Support!"));
        source.sendFeedback(Text.literal(""));
        source.sendFeedback(Text.literal("§7Commands:"));
        source.sendFeedback(Text.literal("  §f/namecheck <name> §8- §7Check name history"));
        source.sendFeedback(Text.literal("  §f/namecheck available <name> §8- §7Check availability"));
        source.sendFeedback(Text.literal("  §f/namecheck bedrock <gamertag> §8- §7Check Bedrock player"));
        source.sendFeedback(Text.literal("  §f/namecheck watch <name> §8- §7Watch name"));
        source.sendFeedback(Text.literal("  §f/namecheck unwatch <name> §8- §7Stop watching"));
        source.sendFeedback(Text.literal("  §f/namecheck watchlist §8- §7Show watchlist"));
        source.sendFeedback(Text.literal("  §f/namecheck cache §8- §7Show cache info"));
        source.sendFeedback(Text.literal(""));
        source.sendFeedback(Text.literal("§7Modrinth: §a[Click]").setStyle(Style.EMPTY.withClickEvent(TextEventCompat.openUrl("https://modrinth.com/mod/x7t-name-checker"))));
        source.sendFeedback(Text.literal("§7Discord: §9[Click]").setStyle(Style.EMPTY.withClickEvent(TextEventCompat.openUrl("https://discord.gg/x7t"))));
        source.sendFeedback(Text.literal("§7Github: §f[Click]").setStyle(Style.EMPTY.withClickEvent(TextEventCompat.openUrl("https://github.com/jxstmaurice"))));
        source.sendFeedback(Text.literal(NameCheckCommand.SEPARATOR));
    }
}
