package chat.cosmic.client.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.HashMap;
import java.util.UUID;
import java.util.prefs.Preferences;

public class RankManager {
    private static final HashMap<UUID, String> playerRanks = new HashMap<>();
    private static final String DEFAULT_RANK = "Default";
    private static final Preferences prefs = Preferences.userNodeForPackage(RankManager.class);
    private static int reminderTimer = 0;
    private static boolean reminderSent = false;

    public static void initialize() {
        loadSavedRanks();
        registerSetRankCommand();
        setupReminderSystem();
    }

    private static void setupReminderSystem() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (!reminderSent && client.player.age > 100) {
                if (getCurrentRank().equals(DEFAULT_RANK)) {
                    sendRankReminder();
                    reminderSent = true;
                }
            }
        });
    }

    private static void sendRankReminder() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        MutableText message = Text.literal("\n[Cooldown Mod] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Set your rank so the cooldown mod will work for you rank :\n")
                        .formatted(Formatting.YELLOW))
                .append(Text.literal("Available ranks:\n").formatted(Formatting.GRAY));

        String[] ranks = {"Non-Rank", "Comet", "Titan", "Galactic", "Celestial"};
        for (String rank : ranks) {
            message.append(createClickableRank(rank)).append(Text.literal("\n"));
        }

        message.append(Text.literal("\nNon-Rank = No command access")
                .formatted(Formatting.RED));

        client.player.sendMessage(message, false);
    }

    private static Text createClickableRank(String rank) {
        String commandRank = rank.equals("Non-Rank") ? "Default" : rank;
        return Text.literal("-> " + rank)
                .styled(style -> style
                        .withColor(rank.equals("Non-Rank") ? Formatting.RED : Formatting.GREEN)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.SUGGEST_COMMAND,
                                "/setrank " + commandRank
                        ))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Text.literal("Click to set " + rank +
                                        (rank.equals("Non-Rank") ? "\n(Blocks all commands)" :
                                                "\nCooldowns: " + getRankDescription(rank)))
                        ))
                );
    }

    private static String getRankDescription(String rank) {
        return switch (rank) {
            case "Comet" -> "/fix: 10m";
            case "Titan" -> "/fix: 5m, /feed/heal: 10m";
            case "Galactic" -> "/fix & /fix all: 2m, /near: 30s, /feed/heal: 5m";
            case "Celestial" -> "/fix & /fix all: 90s, /near: 30s, /feed/heal: 3m";
            default -> "";
        };
    }

    private static void loadSavedRanks() {
        try {
            for (String key : prefs.keys()) {
                UUID uuid = UUID.fromString(key);
                String rank = prefs.get(key, DEFAULT_RANK);
                playerRanks.put(uuid, rank);
            }
        } catch (Exception e) {
            System.err.println("Error loading saved ranks: " + e.getMessage());
        }
    }

    private static void registerSetRankCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("setrank")
                    .then(ClientCommandManager.argument("rank", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                builder.suggest("Default");
                                builder.suggest("Comet");
                                builder.suggest("Titan");
                                builder.suggest("Galactic");
                                builder.suggest("Celestial");
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                String rank = StringArgumentType.getString(context, "rank");
                                UUID playerId = MinecraftClient.getInstance().player.getUuid();
                                setPlayerRank(playerId, rank);
                                context.getSource().sendFeedback(Text.literal("Set rank to: " + getCurrentRankDisplay()));
                                reminderSent = true;
                                return 1;
                            })
                    )
            );
        });
    }

    private static void setPlayerRank(UUID playerId, String rank) {
        playerRanks.put(playerId, rank);
        prefs.put(playerId.toString(), rank);
        try {
            prefs.flush();
        } catch (Exception e) {
            System.err.println("Failed to save rank: " + e.getMessage());
        }
    }

    public static int getCooldown(String command) {
        if (getCurrentRankDisplay().equals("Non-Rank")) return -1;

        String[] parts = command.toLowerCase().split(" ");
        String baseCommand = parts[0];
        boolean isFixAll = command.equalsIgnoreCase("fix all");
        String rank = getCurrentRank();


        if (isFixAll && !(rank.equals("Galactic") || rank.equals("Celestial"))) {
            return -1;
        }


        if (baseCommand.equals("fix")) {
            return switch (rank) {
                case "Galactic" -> 120;
                case "Celestial" -> 90;
                case "Comet" -> 600;
                case "Titan" -> 300;
                default -> -1;
            };
        }


        return switch (rank) {
            case "Galactic" -> getGalacticCooldown(baseCommand);
            case "Celestial" -> getCelestialCooldown(baseCommand);
            case "Titan" -> getTitanCooldown(baseCommand);
            default -> -1;
        };
    }

    private static int getGalacticCooldown(String command) {
        return switch (command) {
            case "near" -> 30;
            case "feed", "heal" -> 300;
            default -> -1;
        };
    }

    private static int getCelestialCooldown(String command) {
        return switch (command) {
            case "near" -> 30;
            case "feed", "heal" -> 180;
            default -> -1;
        };
    }

    private static int getTitanCooldown(String command) {
        return switch (command) {
            case "feed", "heal" -> 600;
            default -> -1;
        };
    }

    private static String getCurrentRank() {
        UUID playerId = MinecraftClient.getInstance().player.getUuid();
        return playerRanks.getOrDefault(playerId, DEFAULT_RANK);
    }

    public static String getCurrentRankDisplay() {
        String rank = getCurrentRank();
        return rank.equals("Default") ? "Non-Rank" : rank;
    }
}