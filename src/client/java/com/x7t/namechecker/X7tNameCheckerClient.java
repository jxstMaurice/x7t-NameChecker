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
                // Bedrock players often have . prefix (Floodgate) or start with *
                if (name.startsWith(".") || name.startsWith("*")) {
                    String cleanName = name.substring(1); // Remove prefix for suggestion
                    if (cleanName.toLowerCase().startsWith(builder.getRemainingLowerCase()) ||
                        name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                        builder.suggest(cleanName);
                    }
                } else if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    // Also suggest regular players in case they're Bedrock without prefix
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
        // Initialize cache and watchlist
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
                            if (WatchlistManager.addToWatchlist(name)) {
                                context.getSource().sendFeedback(Text.literal("§8[§bx7t§8] §aAdded §e" + name + " §ato watchlist!"));
                                context.getSource().sendFeedback(Text.literal("§8[§bx7t§8] §7You will be notified when this name becomes available."));
                            } else {
                                context.getSource().sendFeedback(Text.literal("§8[§bx7t§8] §cThis name is already on your watchlist!"));
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
                                context.getSource().sendFeedback(Text.literal("§8[§bx7t§8] §aRemoved §e" + name + " §afrom watchlist!"));
                            } else {
                                context.getSource().sendFeedback(Text.literal("§8[§bx7t§8] §cThis name is not on your watchlist!"));
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
                            context.getSource().sendFeedback(Text.literal("§8[§bx7t§8] §aWatchlist cleared!"));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("cache")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("§8[§bx7t§8] §7Cache contains §e" + NameCache.getCacheSize() + " §7players."));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("clear")
                        .executes(context -> {
                            NameCache.clearCache();
                            context.getSource().sendFeedback(Text.literal("§8[§bx7t§8] §aCache cleared!"));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.argument("name", StringArgumentType.string())
                    .suggests(PLAYER_SUGGESTIONS)
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        NameCheckCommand.execute(context.getSource(), name);
                        return 1;
                    })
                )
            );
        });
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(WatchlistManager::shutdown));
    }

    private static void showWatchlist(FabricClientCommandSource source) {
        Set<String> watchlist = WatchlistManager.getWatchlist();
        
        source.sendFeedback(Text.literal("§8§m                                        "));
        source.sendFeedback(Text.literal("§8[§bx7t Watchlist§8]"));
        source.sendFeedback(Text.literal(""));
        
        if (watchlist.isEmpty()) {
            source.sendFeedback(Text.literal("§7Your watchlist is empty."));
            source.sendFeedback(Text.literal("§7Use §f/namecheck watch <name> §7to add names."));
        } else {
            source.sendFeedback(Text.literal("§7Watching §e" + watchlist.size() + " §7names:"));
            source.sendFeedback(Text.literal(""));
            for (String name : watchlist) {
                Text nameText = Text.literal("  §8» §f" + name)
                    .setStyle(Style.EMPTY
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§7Click to remove from watchlist")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/namecheck unwatch " + name)));
                source.sendFeedback(nameText);
            }
            source.sendFeedback(Text.literal(""));
            source.sendFeedback(Text.literal("§7Checking every §e60 §7seconds..."));
        }
        
        source.sendFeedback(Text.literal("§8§m                                        "));
    }

    private static void showInfo(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("§8§m                                        "));
        source.sendFeedback(Text.literal("§8[§bx7t Name Checker§8]"));
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
        source.sendFeedback(Text.literal("§7Modrinth: §a[Click]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/x7t-name-checker"))));
        source.sendFeedback(Text.literal("§7Discord: §9[Click]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://discord.gg/x7t"))));
        source.sendFeedback(Text.literal("§7Github: §f[Click]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/jxstmaurice"))));
        source.sendFeedback(Text.literal("§8§m                                        "));
    }
}
