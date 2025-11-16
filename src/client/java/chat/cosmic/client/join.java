package chat.cosmic.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import chat.cosmic.client.client.KeyBinds.KeyBinds;
import chat.cosmic.client.client.SettingsManager;
import chat.cosmic.client.client.UniversalGuiMover;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class join implements ClientModInitializer {

    public static boolean NOTIFICATIONS_ENABLED = false;
    public static boolean GUI_VISIBLE = false;
    public static Set<String> onlinePlayers = new HashSet<>();
    public static Set<String> ignoredPlayers = new HashSet<>();
    public static Map<String, String> playerColors = new HashMap<>();
    public static int MAX_PLAYERS_PER_COLUMN = 15;

    private static final UniversalGuiMover.HudContainer hudContainer = new UniversalGuiMover.HudContainer(10, 10, 100, 40, 1);
    private static final File configFile = new File("config/cosmic_mod.properties");

    private static boolean isChangingDimension = false;
    private static long dimensionChangeTime = 0;
    private static final long DIMENSION_CHANGE_COOLDOWN = 5000;
    private static String currentDimension = "";
    private static boolean hasShownInitialList = false;

    @Override
    public void onInitializeClient() {
        SettingsManager.initialize();
        KeyBinds.registerKeyBinds();

        UniversalGuiMover.trackHudContainer("playerListHud", hudContainer);
        loadConfig();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("player")
                    .then(ClientCommandManager.literal("list")
                            .then(ClientCommandManager.literal("ignore")
                                    .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                            .executes(context -> {
                                                String username = StringArgumentType.getString(context, "username").toLowerCase();
                                                toggleIgnoredPlayer(username);
                                                context.getSource().sendFeedback(Text.of(username + " is now " + (isIgnored(username) ? "ignored" : "unignored")));
                                                return 1;
                                            })
                                    )
                            )
                    ));

            String[] colors = {"pr", "pg", "pdb"};
            String[] colorNames = {"red", "green", "aqua"};
            for(int i = 0; i < colors.length; i++) {
                final int index = i;
                dispatcher.register(ClientCommandManager.literal(colors[index])
                        .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    getOnlinePlayerNames().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String username = StringArgumentType.getString(context, "username").toLowerCase();
                                    setPlayerColor(username, colorNames[index]);
                                    context.getSource().sendFeedback(Text.of(username + "'s name color set to " + colorNames[index]));
                                    return 1;
                                })
                        )
                );
            }

            dispatcher.register(ClientCommandManager.literal("prr")
                    .executes(context -> {
                        resetPlayerColors();
                        context.getSource().sendFeedback(Text.of("Reset all player colors"));
                        return 1;
                    })
            );

            dispatcher.register(ClientCommandManager.literal("pw")
                    .then(ClientCommandManager.argument("username", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                getOnlinePlayerNames().forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                String username = StringArgumentType.getString(context, "username").toLowerCase();
                                resetPlayerColor(username);
                                context.getSource().sendFeedback(Text.of(username + "'s color reset"));
                                return 1;
                            })
                    )
            );

            dispatcher.register(ClientCommandManager.literal("setmaxplayers")
                    .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 50))
                            .executes(context -> {
                                int newCount = IntegerArgumentType.getInteger(context, "count");
                                MAX_PLAYERS_PER_COLUMN = newCount;
                                context.getSource().sendFeedback(Text.of("Max players per column set to " + newCount));
                                saveConfig();
                                return 1;
                            })
                    ));
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    private boolean isRestrictedDimension() {
        return currentDimension.equals("minecraft:overworld") ||
                currentDimension.equals("your_spawn_dimension_id");
    }

    private List<String> getOnlinePlayerNames() {
        if(isRestrictedDimension()) return Collections.emptyList();
        List<String> playerNames = new ArrayList<>();
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            for (PlayerListEntry entry : MinecraftClient.getInstance().getNetworkHandler().getPlayerList()) {
                String username = entry.getProfile().getName();
                if (!isIgnored(username)) playerNames.add(username);
            }
        }
        return playerNames;
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        GUI_VISIBLE = SettingsManager.getToggleSettings().getOrDefault("Show Player List", false);
        NOTIFICATIONS_ENABLED = SettingsManager.getToggleSettings().getOrDefault("Show Notifications", false);

        if (KeyBinds.getToggleNotifications().wasPressed()) {
            NOTIFICATIONS_ENABLED = !NOTIFICATIONS_ENABLED;
            SettingsManager.getToggleSettings().put("Show Notifications", NOTIFICATIONS_ENABLED);
            SettingsManager.saveSettings();
            client.player.sendMessage(Text.of("Notifications " + (NOTIFICATIONS_ENABLED ? "enabled" : "disabled")), false);
        }

        if (KeyBinds.getTogglePlayerList().wasPressed()) {
            GUI_VISIBLE = !GUI_VISIBLE;
            SettingsManager.getToggleSettings().put("Show Player List", GUI_VISIBLE);
            SettingsManager.saveSettings();
            client.player.sendMessage(Text.of("GUI " + (GUI_VISIBLE ? "enabled" : "disabled")), false);
        }

        if (client.getNetworkHandler() == null) return;

        String newDimension = "";
        if (client.world != null && client.world.getRegistryKey() != null) {
            newDimension = client.world.getRegistryKey().getValue().toString();
        }

        if (!newDimension.equals(currentDimension) && !currentDimension.isEmpty()) {
            isChangingDimension = true;
            dimensionChangeTime = System.currentTimeMillis();
            onlinePlayers.clear();
            hasShownInitialList = false;
        }
        currentDimension = newDimension;

        if (isChangingDimension && (System.currentTimeMillis() - dimensionChangeTime > DIMENSION_CHANGE_COOLDOWN)) {
            isChangingDimension = false;
        }

        if (isChangingDimension || isRestrictedDimension()) return;

        Set<String> currentPlayers = new HashSet<>();
        boolean isFirstConnect = onlinePlayers.isEmpty();

        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String username = entry.getProfile().getName().toLowerCase();
            if (!isIgnored(username) && isValidPlayer(username) && !username.equalsIgnoreCase(client.player.getName().getString())) {
                currentPlayers.add(username);
            }
        }

        if (NOTIFICATIONS_ENABLED && !hasShownInitialList && !currentPlayers.isEmpty()) {
            showInitialPlayerList(client, currentPlayers);
            hasShownInitialList = true;
        }

        if (NOTIFICATIONS_ENABLED && !isFirstConnect) {
            for (String player : currentPlayers) {
                if (!onlinePlayers.contains(player)) {
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                    client.player.sendMessage(Text.literal("§6§l" + player + " has joined your world"), false);
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                }
            }

            for (String player : onlinePlayers) {
                if (!currentPlayers.contains(player)) {
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                    client.player.sendMessage(Text.literal("§c§l" + player + " has left your world"), false);
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                }
            }
        }

        onlinePlayers = currentPlayers;
    }

    private void showInitialPlayerList(MinecraftClient client, Set<String> players) {
        if (players.isEmpty()) return;

        List<String> playerList = new ArrayList<>(players);
        Collections.sort(playerList);

        StringBuilder playerNames = new StringBuilder();
        for (int i = 0; i < playerList.size(); i++) {
            String player = playerList.get(i);
            String color = playerColors.get(player.toLowerCase());

            if (color != null) {
                switch (color) {
                    case "red":
                        playerNames.append("§c");
                        break;
                    case "green":
                        playerNames.append("§a");
                        break;
                    case "aqua":
                        playerNames.append("§b");
                        break;
                    default:
                        playerNames.append("§f");
                        break;
                }
            } else {
                playerNames.append("§f");
            }

            playerNames.append(player);

            if (i < playerList.size() - 1) {
                playerNames.append("§7, ");
            }
        }

        client.player.sendMessage(Text.of("§e§lPlayers in your world are: "), false);
        client.player.sendMessage(Text.literal(playerNames.toString()), false);
        client.player.sendMessage(Text.literal("§e§lTotal: " + players.size() + " player(s)"), false);
    }

    private void onHudRender(DrawContext context, float tickDelta) {
        if (!GUI_VISIBLE) return;

        if (!isRestrictedDimension() && MinecraftClient.getInstance().player != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            UniversalGuiMover.HudContainer container = UniversalGuiMover.getHudContainer("playerListHud");
            if (container == null) return;
            UniversalGuiMover.clampPosition(container, client.getWindow());

            float scale = UniversalGuiMover.getGlobalTextScale();
            int lineHeight = 12;
            int maxPlayersPerColumn = MAX_PLAYERS_PER_COLUMN;
            int columnWidth = 120;

            Set<String> displayPlayers = new HashSet<>();
            if (client.getNetworkHandler() != null) {
                for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                    String username = entry.getProfile().getName().toLowerCase();
                    if (!isIgnored(username) && isValidPlayer(username)) {
                        displayPlayers.add(username);
                    }
                }
            }

            context.getMatrices().push();
            context.getMatrices().translate(container.x + 3, container.y + 3, 0);
            context.getMatrices().scale(scale, scale, 1);

            context.drawText(client.textRenderer,
                    Text.literal("Players Online: " + displayPlayers.size()).styled(s -> s.withBold(true)),
                    0, -lineHeight, 0xFFFFFF, true
            );

            List<String> sortedPlayers = new ArrayList<>(displayPlayers);
            sortedPlayers.sort((a, b) -> {
                String colorA = playerColors.getOrDefault(a.toLowerCase(), "default");
                String colorB = playerColors.getOrDefault(b.toLowerCase(), "default");
                int priorityA = getColorPriority(colorA);
                int priorityB = getColorPriority(colorB);
                return priorityA != priorityB ? Integer.compare(priorityB, priorityA) : a.compareToIgnoreCase(b);
            });

            List<String> commandPlayers = new ArrayList<>();
            List<String> otherPlayers = new ArrayList<>();
            for (String player : sortedPlayers) {
                String color = playerColors.get(player.toLowerCase());
                if (color != null && !color.equals("default")) {
                    commandPlayers.add(player);
                } else {
                    otherPlayers.add(player);
                }
            }

            int commandColumns = (int) Math.ceil((double) commandPlayers.size() / maxPlayersPerColumn);
            int otherColumns = (int) Math.ceil((double) otherPlayers.size() / maxPlayersPerColumn);

            for (int col = 0; col < commandColumns; col++) {
                int columnX = col * columnWidth;
                for (int row = 0; row < maxPlayersPerColumn; row++) {
                    int index = col * maxPlayersPerColumn + row;
                    if (index >= commandPlayers.size()) break;
                    String player = commandPlayers.get(index);
                    String color = playerColors.get(player.toLowerCase());
                    int textColor = getCommandColor(color);

                    context.drawText(
                            client.textRenderer,
                            Text.literal(player).styled(s -> s.withBold(true)),
                            columnX,
                            row * lineHeight,
                            textColor,
                            true
                    );
                }
            }

            for (int col = 0; col < otherColumns; col++) {
                int columnX = (commandColumns + col) * columnWidth;
                for (int row = 0; row < maxPlayersPerColumn; row++) {
                    int index = col * maxPlayersPerColumn + row;
                    if (index >= otherPlayers.size()) break;
                    String player = otherPlayers.get(index);

                    int slotPosition = col * maxPlayersPerColumn + row;
                    int textColor = getSlotBasedGradientColor(slotPosition);

                    context.drawText(
                            client.textRenderer,
                            Text.literal(player).styled(s -> s.withBold(true)),
                            columnX,
                            row * lineHeight,
                            textColor,
                            true
                    );
                }
            }

            context.getMatrices().pop();
        }
    }

    private int getColorPriority(String color) {
        return switch (color) {
            case "red" -> 3;
            case "green" -> 2;
            case "aqua" -> 1;
            default -> 0;
        };
    }

    private int getCommandColor(String color) {
        return switch (color) {
            case "red" -> 0xFFFF0000;
            case "green" -> 0xFF00FF00;
            case "aqua" -> 0xFF00FFFF;
            default -> 0xFFFFFFFF;
        };
    }

    private int getSlotBasedGradientColor(int slotPosition) {
        float cyclePosition = (slotPosition % 24) / 24.0f;
        int r, g, b;

        if (cyclePosition < 0.2f) {
            float progress = cyclePosition / 0.2f;
            r = (int) (0x00 + (0x00 - 0x00) * progress);
            g = (int) (0xFF + (0x80 - 0xFF) * progress);
            b = (int) (0xFF + (0xFF - 0xFF) * progress);
        } else if (cyclePosition < 0.4f) {
            float progress = (cyclePosition - 0.2f) / 0.2f;
            r = (int) (0x00 + (0x80 - 0x00) * progress);
            g = (int) (0x80 + (0x00 - 0x80) * progress);
            b = 0xFF;
        } else if (cyclePosition < 0.6f) {
            float progress = (cyclePosition - 0.4f) / 0.2f;
            r = (int) (0x80 + (0xFF - 0x80) * progress);
            g = 0x00;
            b = (int) (0xFF + (0x00 - 0xFF) * progress);
        } else if (cyclePosition < 0.8f) {
            float progress = (cyclePosition - 0.6f) / 0.2f;
            r = 0xFF;
            g = (int) (0x00 + (0x80 - 0x00) * progress);
            b = 0x00;
        } else {
            float progress = (cyclePosition - 0.8f) / 0.2f;
            r = (int) (0xFF + (0x80 - 0xFF) * progress);
            g = (int) (0x80 + (0xFF - 0x80) * progress);
            b = (int) (0x00 + (0x40 - 0x00) * progress);
        }

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private boolean isIgnored(String username) {
        return ignoredPlayers.contains(username.toLowerCase());
    }

    private boolean isValidPlayer(String username) {
        if (username.matches("slot_[1-9]|slot_[1-9][0-9]|slot_100")) {
            return false;
        }
        return !username.startsWith("minecraft:") &&
                username.matches("[a-zA-Z0-9_]+");
    }

    private void toggleIgnoredPlayer(String username) {
        username = username.toLowerCase();
        if (ignoredPlayers.contains(username)) ignoredPlayers.remove(username);
        else ignoredPlayers.add(username);
        saveIgnoredPlayers();
        saveConfig();
    }

    private void saveIgnoredPlayers() {
        try (FileWriter writer = new FileWriter(new File("config/ignore.txt"))) {
            for (String player : ignoredPlayers) writer.write(player + System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        Properties props = new Properties();
        if (configFile.exists()) {
            try (FileInputStream input = new FileInputStream(configFile)) {
                props.load(input);
                NOTIFICATIONS_ENABLED = Boolean.parseBoolean(props.getProperty("notifications_enabled", "false"));
                GUI_VISIBLE = Boolean.parseBoolean(props.getProperty("gui_visible", "true"));
                MAX_PLAYERS_PER_COLUMN = Integer.parseInt(props.getProperty("max_players_per_column", "15"));
                UniversalGuiMover.loadGuiPositions(props);

                String ignored = props.getProperty("ignored_players", "");
                if (!ignored.isEmpty()) ignoredPlayers.addAll(Arrays.asList(ignored.split(",")));

                String colors = props.getProperty("player_colors", "");
                for (String entry : colors.split(",")) {
                    if (entry.isEmpty()) continue;
                    String[] parts = entry.split(":");
                    if (parts.length == 2) {
                        String color = parts[1];
                        if (color.equals("red") || color.equals("green") || color.equals("aqua")) {
                            playerColors.put(parts[0].toLowerCase(), color);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveConfig() {
        Properties props = new Properties();
        props.setProperty("notifications_enabled", String.valueOf(NOTIFICATIONS_ENABLED));
        props.setProperty("gui_visible", String.valueOf(GUI_VISIBLE));
        props.setProperty("max_players_per_column", String.valueOf(MAX_PLAYERS_PER_COLUMN));
        props.setProperty("ignored_players", String.join(",", ignoredPlayers));

        StringBuilder colors = new StringBuilder();
        playerColors.forEach((k, v) -> {
            if (v.equals("red") || v.equals("green") || v.equals("aqua")) {
                colors.append(k).append(":").append(v).append(",");
            }
        });
        if (colors.length() > 0) colors.setLength(colors.length() - 1);
        props.setProperty("player_colors", colors.toString());

        UniversalGuiMover.saveGuiPositions(props);

        try (FileOutputStream output = new FileOutputStream(configFile)) {
            props.store(output, "Player List Settings");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setPlayerColor(String username, String color) {
        playerColors.put(username.toLowerCase(), color);
        saveConfig();
    }

    private void resetPlayerColors() {
        playerColors.clear();
        saveConfig();
    }

    private void resetPlayerColor(String username) {
        playerColors.remove(username.toLowerCase());
        saveConfig();
    }
}