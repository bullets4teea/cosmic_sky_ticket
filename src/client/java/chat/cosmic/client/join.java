
        package chat.cosmic.client;

import chat.cosmic.client.client.UniversalGuiMover;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class join implements ClientModInitializer {
    public static boolean NOTIFICATIONS_ENABLED = false;
    public static boolean GUI_VISIBLE = false;
    public static Set<String> onlinePlayers = new HashSet<>();
    public static Set<String> ignoredPlayers = new HashSet<>();
    public static Map<String, String> playerColors = new HashMap<>();
    private static KeyBinding toggleNotificationsKey;
    private static KeyBinding toggleGuiKey;
    private static final UniversalGuiMover.HudContainer hudContainer = new UniversalGuiMover.HudContainer(10, 10, 100, 40, 1);
    private static final File configFile = new File("config/untitled20_mod.properties");

    // Track when the client is changing dimensions
    private static boolean isChangingDimension = false;
    private static long dimensionChangeTime = 0;
    private static final long DIMENSION_CHANGE_COOLDOWN = 5000; // 5 seconds cooldown
    private static String currentDimension = "";

    @Override
    public void onInitializeClient() {
        UniversalGuiMover.trackHudContainer("playerListHud", hudContainer);
        loadConfig();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());

        toggleNotificationsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("toggle notifications", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "join"));
        toggleGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("toggle gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "join"));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ignore").then(ClientCommandManager.argument("username", StringArgumentType.string()).executes(context -> {
                String username = StringArgumentType.getString(context, "username").toLowerCase();
                toggleIgnoredPlayer(username);
                context.getSource().sendFeedback(Text.of(username + " is now " + (isIgnored(username) ? "ignored" : "unignored")));
                return 1;
            })));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("setguipos").then(ClientCommandManager.argument("x", IntegerArgumentType.integer()).then(ClientCommandManager.argument("y", IntegerArgumentType.integer()).executes(context -> {
                int x = IntegerArgumentType.getInteger(context, "x");
                int y = IntegerArgumentType.getInteger(context, "y");
                hudContainer.x = x;
                hudContainer.y = y;
                UniversalGuiMover.clampPosition(hudContainer, MinecraftClient.getInstance().getWindow());
                saveConfig();
                context.getSource().sendFeedback(Text.of("GUI position set to (" + x + ", " + y + ")"));
                return 1;
            }))));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("pr").then(ClientCommandManager.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                getOnlinePlayerNames().forEach(player -> builder.suggest(player));
                return builder.buildFuture();
            }).executes(context -> {
                String username = StringArgumentType.getString(context, "username").toLowerCase();
                setPlayerColor(username, "red");
                context.getSource().sendFeedback(Text.of(username + "'s name color set to red."));
                return 1;
            })));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("pg").then(ClientCommandManager.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                getOnlinePlayerNames().forEach(player -> builder.suggest(player));
                return builder.buildFuture();
            }).executes(context -> {
                String username = StringArgumentType.getString(context, "username").toLowerCase();
                setPlayerColor(username, "green");
                context.getSource().sendFeedback(Text.of(username + "'s name color set to green."));
                return 1;
            })));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("pdb").then(ClientCommandManager.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                getOnlinePlayerNames().forEach(player -> builder.suggest(player));
                return builder.buildFuture();
            }).executes(context -> {
                String username = StringArgumentType.getString(context, "username").toLowerCase();
                setPlayerColor(username, "dark_blue");
                context.getSource().sendFeedback(Text.of(username + "'s name color set to dark blue."));
                return 1;
            })));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("prr").executes(context -> {
                resetPlayerColors();
                context.getSource().sendFeedback(Text.of("Reset all player colors to default."));
                return 1;
            }));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("pw").then(ClientCommandManager.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                getOnlinePlayerNames().forEach(player -> builder.suggest(player));
                return builder.buildFuture();
            }).executes(context -> {
                String username = StringArgumentType.getString(context, "username").toLowerCase();
                resetPlayerColor(username);
                context.getSource().sendFeedback(Text.of(username + "'s name color reset to default."));
                return 1;
            })));
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    private List<String> getOnlinePlayerNames() {
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

        // Handle keybindings
        if (toggleNotificationsKey.wasPressed()) {
            NOTIFICATIONS_ENABLED = !NOTIFICATIONS_ENABLED;
            client.player.sendMessage(Text.of("Notifications " + (NOTIFICATIONS_ENABLED ? "enabled" : "disabled")), false);
            saveConfig();
        }
        if (toggleGuiKey.wasPressed()) {
            GUI_VISIBLE = !GUI_VISIBLE;
            client.player.sendMessage(Text.of("GUI " + (GUI_VISIBLE ? "enabled" : "disabled")), false);
            saveConfig();
        }

        // Check if we're currently on a server
        if (client.getNetworkHandler() == null) return;

        // Check for dimension changes
        String newDimension = "";
        if (client.world != null && client.world.getRegistryKey() != null) {
            newDimension = client.world.getRegistryKey().getValue().toString();
        }

        if (!newDimension.equals(currentDimension) && !currentDimension.isEmpty()) {
            // Dimension change detected
            isChangingDimension = true;
            dimensionChangeTime = System.currentTimeMillis();
        }
        currentDimension = newDimension;

        // Reset dimension change flag after cooldown period
        if (isChangingDimension && (System.currentTimeMillis() - dimensionChangeTime > DIMENSION_CHANGE_COOLDOWN)) {
            isChangingDimension = false;
            // Update player list to prevent false leave messages
            if (client.getNetworkHandler() != null) {
                Set<String> newPlayers = new HashSet<>();
                for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                    String username = entry.getProfile().getName().toLowerCase();
                    if (!isIgnored(username) && isValidPlayer(username) && !username.equalsIgnoreCase(client.player.getName().getString())) {
                        newPlayers.add(username);
                    }
                }
                onlinePlayers = newPlayers;
            }
        }

        // Skip player list updates during dimension changes
        if (isChangingDimension) return;

        // Process player list
        Set<String> currentPlayers = new HashSet<>();
        boolean isFirstConnect = onlinePlayers.isEmpty();

        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String username = entry.getProfile().getName().toLowerCase();
            if (!isIgnored(username) && isValidPlayer(username) && !username.equalsIgnoreCase(client.player.getName().getString())) {
                currentPlayers.add(username);
            }
        }

        // Only process join/leave notifications if we're not in the initial connection
        if (!isFirstConnect && NOTIFICATIONS_ENABLED) {
            // Check for new players (joined)
            for (String player : currentPlayers) {
                if (!onlinePlayers.contains(player)) {
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                    client.player.sendMessage(Text.literal("§6§l" + player + " has joined your world"), false);
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                }
            }

            // Check for players who left
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

    private void onHudRender(DrawContext context, float tickDelta) {
        if (GUI_VISIBLE && MinecraftClient.getInstance().player != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            UniversalGuiMover.HudContainer container = UniversalGuiMover.getHudContainer("playerListHud");
            if (container == null) return;
            UniversalGuiMover.clampPosition(container, client.getWindow());

            float scale = UniversalGuiMover.getGlobalTextScale();
            int lineHeight = 12;
            int maxPlayersPerColumn = 15;
            int columnWidth = 90;

            // Create a Set that includes all online players, filtered appropriately
            Set<String> displayPlayers = new HashSet<>();
            if (client.getNetworkHandler() != null) {
                for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                    String username = entry.getProfile().getName().toLowerCase();
                    if (!isIgnored(username) && isValidPlayer(username)) {
                        displayPlayers.add(username);
                    }
                }
            }

            int totalPlayers = displayPlayers.size();
            int columns = (int) Math.ceil((double) totalPlayers / maxPlayersPerColumn);

            // Draw background rectangle
            context.fill(container.x, container.y, container.x + (columnWidth * columns),
                    container.y + Math.min(totalPlayers, maxPlayersPerColumn) * lineHeight + 5, 0x80000000);

            context.getMatrices().push();
            context.getMatrices().translate(container.x + 3, container.y + 3, 0);
            context.getMatrices().scale(scale, scale, 1);

            // First draw a title
            context.drawText(client.textRenderer, "Players Online: " + totalPlayers, 0, -lineHeight, 0xFFFFFF, true);

            // Display players from the direct player list rather than the cached onlinePlayers
            List<String> sortedPlayers = new ArrayList<>(displayPlayers);
            java.util.Collections.sort(sortedPlayers); // Sort alphabetically

            int playerIndex = 0;
            for (int col = 0; col < columns; col++) {
                int columnX = col * columnWidth;
                for (int row = 0; row < maxPlayersPerColumn; row++) {
                    if (playerIndex >= sortedPlayers.size()) break;
                    String player = sortedPlayers.get(playerIndex);

                    String color = playerColors.getOrDefault(player.toLowerCase(), "default");
                    int textColor = switch (color) {
                        case "red" -> 0xFF0000;
                        case "green" -> 0x00FF00;
                        case "dark_blue" -> 0x336699;
                        default -> 0xFFFFFF;
                    };
                    context.drawText(client.textRenderer, player, columnX, row * lineHeight, textColor, true);
                    playerIndex++;
                }
            }
            context.getMatrices().pop();
        }
    }

    private boolean isIgnored(String username) {
        return ignoredPlayers.contains(username.toLowerCase());
    }

    private boolean isValidPlayer(String username) {
        return !username.startsWith("slot_") && !username.startsWith("minecraft:") && username.length() >= 3 && username.length() <= 16 && username.matches("[a-zA-Z0-9_]+");
    }

    private void toggleIgnoredPlayer(String username) {
        username = username.toLowerCase();
        if (ignoredPlayers.contains(username)) ignoredPlayers.remove(username);
        else ignoredPlayers.add(username);
        saveIgnoredPlayers();
        saveConfig();
    }

    private void saveIgnoredPlayers() {
        File ignoreFile = new File("config/ignore.txt");
        try (FileWriter writer = new FileWriter(ignoreFile)) {
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

                UniversalGuiMover.loadGuiPositions(props);

                String ignoredPlayersString = props.getProperty("ignored_players", "");
                if (!ignoredPlayersString.isEmpty()) ignoredPlayers.addAll(Set.of(ignoredPlayersString.split(",")));

                String playerColorsString = props.getProperty("player_colors", "");
                if (!playerColorsString.isEmpty()) {
                    for (String entry : playerColorsString.split(",")) {
                        String[] parts = entry.split(":");
                        if (parts.length == 2) playerColors.put(parts[0].toLowerCase(), parts[1]);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveConfig() {
        Properties props = new Properties();
        props.setProperty("notifications_enabled", Boolean.toString(NOTIFICATIONS_ENABLED));
        props.setProperty("gui_visible", Boolean.toString(GUI_VISIBLE));
        props.setProperty("ignored_players", String.join(",", ignoredPlayers));

        UniversalGuiMover.saveGuiPositions(props);

        StringBuilder playerColorsString = new StringBuilder();
        playerColors.forEach((k, v) -> playerColorsString.append(k).append(":").append(v).append(","));
        if (playerColorsString.length() > 0) playerColorsString.setLength(playerColorsString.length() - 1);
        props.setProperty("player_colors", playerColorsString.toString());

        try (FileOutputStream output = new FileOutputStream(configFile)) {
            props.store(output, "Untitled20 Mod Configuration");
        } catch (IOException e) {
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
