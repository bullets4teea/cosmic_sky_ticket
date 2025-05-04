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
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

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

    private static boolean isChangingDimension = false;
    private static long dimensionChangeTime = 0;
    private static final long DIMENSION_CHANGE_COOLDOWN = 5000;
    private static String currentDimension = "";

    @Override
    public void onInitializeClient() {
        UniversalGuiMover.trackHudContainer("playerListHud", hudContainer);
        loadConfig();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());

        toggleNotificationsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("toggle join server notifications", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "adv"));
        toggleGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("toggle play list on server gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "adv"));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ignore")
                    .then(ClientCommandManager.argument("username", StringArgumentType.string())
                            .executes(context -> {
                                String username = StringArgumentType.getString(context, "username").toLowerCase();
                                toggleIgnoredPlayer(username);
                                context.getSource().sendFeedback(Text.of(username + " is now " + (isIgnored(username) ? "ignored" : "unignored")));
                                return 1;
                            })));



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
                                })));
            }

            dispatcher.register(ClientCommandManager.literal("prr")
                    .executes(context -> {
                        resetPlayerColors();
                        context.getSource().sendFeedback(Text.of("Reset all player colors"));
                        return 1;
                    }));

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
                            })));
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

        if (client.getNetworkHandler() == null) return;

        String newDimension = "";
        if (client.world != null && client.world.getRegistryKey() != null) {
            newDimension = client.world.getRegistryKey().getValue().toString();
        }

        if (!newDimension.equals(currentDimension) && !currentDimension.isEmpty()) {
            isChangingDimension = true;
            dimensionChangeTime = System.currentTimeMillis();
        }
        currentDimension = newDimension;

        if (isChangingDimension && (System.currentTimeMillis() - dimensionChangeTime > DIMENSION_CHANGE_COOLDOWN)) {
            isChangingDimension = false;
            if (!isRestrictedDimension() && client.getNetworkHandler() != null) {
                Set<String> newPlayers = new HashSet<>();
                List<String> displayNames = new ArrayList<>();

                for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                    String username = entry.getProfile().getName().toLowerCase();
                    String displayName = entry.getProfile().getName();
                    if (!isIgnored(username) && isValidPlayer(username) && !username.equalsIgnoreCase(client.player.getName().getString())) {
                        newPlayers.add(username);
                        displayNames.add(displayName);
                    }
                }
                onlinePlayers = newPlayers;

                if (!displayNames.isEmpty()) {
                    Collections.sort(displayNames);
                    String playerList = String.join(", ", displayNames);
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                    client.player.sendMessage(Text.literal("§6§l" + displayNames.size() + " players in this world:"), false);
                    client.player.sendMessage(Text.literal("§6§l" + playerList), false);
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                } else {
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                    client.player.sendMessage(Text.literal("§6§lNo other players in this world"), false);
                    client.player.sendMessage(Text.of("------------------------------------------"), false);
                }
            }
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

        if (!isFirstConnect && NOTIFICATIONS_ENABLED) {
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

    private void onHudRender(DrawContext context, float tickDelta) {
        if (GUI_VISIBLE && !isRestrictedDimension() && MinecraftClient.getInstance().player != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            UniversalGuiMover.HudContainer container = UniversalGuiMover.getHudContainer("playerListHud");
            if (container == null) return;
            UniversalGuiMover.clampPosition(container, client.getWindow());

            float scale = UniversalGuiMover.getGlobalTextScale();
            int lineHeight = 12;
            int maxPlayersPerColumn = 15;
            int columnWidth = 90;

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

            context.drawText(client.textRenderer, Text.literal("Players Online: " + displayPlayers.size()).styled(s -> s.withBold(true)), 0, -lineHeight, 0xFFFFFF, true);

            List<String> sortedPlayers = new ArrayList<>(displayPlayers);
            sortedPlayers.sort((a, b) -> {
                String colorA = playerColors.getOrDefault(a.toLowerCase(), "default");
                String colorB = playerColors.getOrDefault(b.toLowerCase(), "default");

                int priorityA = getColorPriority(colorA);
                int priorityB = getColorPriority(colorB);

                if (priorityA != priorityB) return Integer.compare(priorityB, priorityA);
                return a.compareToIgnoreCase(b);
            });

            List<String> redPlayers = new ArrayList<>();
            List<String> otherPlayers = new ArrayList<>();
            for (String player : sortedPlayers) {
                if ("red".equals(playerColors.get(player.toLowerCase()))) {
                    redPlayers.add(player);
                } else {
                    otherPlayers.add(player);
                }
            }

            int redColumns = (int) Math.ceil((double) redPlayers.size() / maxPlayersPerColumn);
            int otherColumns = (int) Math.ceil((double) otherPlayers.size() / maxPlayersPerColumn);
            Set<Integer> usedColors = new HashSet<>();

            for (int col = 0; col < redColumns; col++) {
                int columnX = col * columnWidth;
                for (int row = 0; row < maxPlayersPerColumn; row++) {
                    int index = col * maxPlayersPerColumn + row;
                    if (index >= redPlayers.size()) break;
                    String player = redPlayers.get(index);
                    context.drawText(
                            client.textRenderer,
                            Text.literal(player).styled(s -> s.withBold(true)),
                            columnX,
                            row * lineHeight,
                            0xFFFF0000,
                            true
                    );
                }
            }

            for (int col = 0; col < otherColumns; col++) {
                int columnX = (redColumns + col) * columnWidth;
                for (int row = 0; row < maxPlayersPerColumn; row++) {
                    int index = col * maxPlayersPerColumn + row;
                    if (index >= otherPlayers.size()) break;
                    String player = otherPlayers.get(index);
                    int textColor = getPlayerColor(player, usedColors);
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

    private int getPlayerColor(String player, Set<Integer> usedColors) {
        String color = playerColors.get(player.toLowerCase());
        if (color != null) {
            return switch (color) {
                case "red" -> 0xFFFF0000;
                case "green" -> 0xFF00FF00;
                case "aqua" -> 0xFF00FFFF;
                default -> {
                    if (color.startsWith("gen_")) {
                        int rgb = Integer.parseInt(color.substring(4), 16) | 0xFF000000;
                        usedColors.add(rgb);
                        yield rgb;
                    }
                    yield generateUniqueColor(player, usedColors);
                }
            };
        }
        return generateUniqueColor(player, usedColors);
    }

    private int generateUniqueColor(String player, Set<Integer> usedColors) {
        String storedColor = playerColors.get(player.toLowerCase());
        if (storedColor != null && storedColor.startsWith("gen_")) {
            return Integer.parseInt(storedColor.substring(4), 16) | 0xFF000000;
        }

        int hash = player.hashCode();
        float hue = (Math.abs(hash) % 360) / 360.0f;
        float saturation = 0.85f;
        float brightness = 0.95f;
        hue = (hue + 0.618f) % 1.0f;

        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness) | 0xFF000000;

        int attempts = 0;
        // Corrected while condition
        while ((colorExists(rgb) || usedColors.contains(rgb)) && attempts < 100) {
            hue = (hue + 0.1f) % 1.0f;
            rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness) | 0xFF000000;
            attempts++;
        }

        if (!playerColors.containsKey(player.toLowerCase())) {
            playerColors.put(player.toLowerCase(), "gen_" + Integer.toHexString(rgb & 0x00FFFFFF));
            saveConfig();
        }

        return rgb;
    }

    private boolean colorExists(int rgb) {
        String hex = Integer.toHexString(rgb & 0x00FFFFFF);
        return playerColors.containsValue("gen_" + hex);
    }

    private boolean isIgnored(String username) {
        return ignoredPlayers.contains(username.toLowerCase());
    }

    private boolean isValidPlayer(String username) {
        return !isRestrictedDimension() &&
                !username.startsWith("slot_") &&
                !username.startsWith("minecraft:") &&
                username.length() >= 3 &&
                username.length() <= 16 &&
                username.matches("[a-zA-Z0-9_]+");
    }

    private void toggleIgnoredPlayer(String username) {
        username = username.toLowerCase();
        if (ignoredPlayers.contains(username)) {
            ignoredPlayers.remove(username);
        } else {
            ignoredPlayers.add(username);
        }
        saveIgnoredPlayers();
        saveConfig();
    }

    private void saveIgnoredPlayers() {
        File ignoreFile = new File("config/ignore.txt");
        try (FileWriter writer = new FileWriter(ignoreFile)) {
            for (String player : ignoredPlayers) {
                writer.write(player + System.lineSeparator());
            }
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
                if (!ignoredPlayersString.isEmpty()) {
                    ignoredPlayers.addAll(Set.of(ignoredPlayersString.split(",")));
                }

                String playerColorsString = props.getProperty("player_colors", "");
                if (!playerColorsString.isEmpty()) {
                    for (String entry : playerColorsString.split(",")) {
                        String[] parts = entry.split(":");
                        if (parts.length == 2) {
                            playerColors.put(parts[0].toLowerCase(), parts[1]);
                        }
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
        if (playerColorsString.length() > 0) {
            playerColorsString.setLength(playerColorsString.length() - 1);
        }
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