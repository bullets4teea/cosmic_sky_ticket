package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChestTrackerMod implements ClientModInitializer {
    private static final Map<String, Integer> chestCounts = new HashMap<>();
    private static final Map<String, Integer> sysChestCounts = new HashMap<>();
    private static final Map<String, Integer> tierColors = Map.of(
            "Basic", 0xFFFFFF,
            "Elite", 0x54FCFC,
            "Legendary", 0xFFA500,
            "Godly", 0xFF0000,
            "Heroic", 0xFF69B4,
            "Mythic", 0x800080,
            "Tire", 0xC0C0C0
    );

    private static final String[] TIERS = {"Basic", "Elite", "Legendary", "Godly", "Heroic", "Mythic"};
    private static final String[] SYS_TIERS = {"Basic", "Elite", "Legendary", "Godly", "Heroic", "Mythic", "Tire"};

    private static final Pattern CHEST_PATTERN = Pattern.compile("\\* (Basic|Elite|Legendary|Godly|Heroic|Mythic) Chest dropped nearby! \\*");
    private static final Pattern SYS_CHEST_PATTERN = Pattern.compile("\\* (Basic|Elite|Legendary|Godly|Heroic|Mythic) Chest dropped nearby!\\s+\\(System Override\\) \\*");
    private static final Pattern TIRE_SYS_CHEST_PATTERN = Pattern.compile("\\* Tire Chest dropped nearby!\\s+\\(System Override\\) \\*");
    private static final Pattern MAX_GEM_PATTERN = Pattern.compile("\\* \\+1 MAX GEM FOUND \\*");

    public static KeyBinding resetKey, toggleHudKey, startPauseTimerKey; // Changed to public
    private static boolean hudVisible = true;
    private static final Path CONFIG_PATH = Path.of("config/chesttracker_hud.dat");
    private static long startTime = 0;
    private static long pausedTime = 0;
    private static boolean isTimerRunning = false;
    private static boolean needsBoundaryCheck = true;
    private static boolean showSysView = false;
    private static long sysViewEndTime = 0;
    private static int maxGemCount = 0;
    private static boolean modEnabled = true;

    private static final UniversalGuiMover.HudContainer hudContainer =
            new UniversalGuiMover.HudContainer(10, 100, 120, 9, 8);

    public static void setModEnabled(boolean enabled) {
        modEnabled = enabled;
    }

    @Override
    public void onInitializeClient() {
        initializeCounts();
        setupKeybinds();
        loadHudPosition();
        UniversalGuiMover.trackHudContainer("chestTrackerHud", hudContainer);

        ClientTickEvents.END_CLIENT_TICK.register(this::handleClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(this::handleBoundaryCheck);
        HudRenderCallback.EVENT.register(this::renderHud);

        ClientReceiveMessageEvents.GAME.register(this::handleChatMessage);
        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("sys")
                    .executes(this::handleSysCommand));

            // Chest reset command (only resets counts, not timer)
            dispatcher.register(ClientCommandManager.literal("chestreset")
                    .executes(this::handleChestResetCommand));

            // Timer control commands
            dispatcher.register(ClientCommandManager.literal("chesttimer")
                    .then(ClientCommandManager.literal("reset")
                            .executes(this::handleTimerResetCommand))
                    .then(ClientCommandManager.literal("start")
                            .executes(this::handleTimerStartCommand))
                    .then(ClientCommandManager.literal("pause")
                            .executes(this::handleTimerPauseCommand)));
        });
    }

    private int handleSysCommand(CommandContext<FabricClientCommandSource> context) {
        showSysView = true;
        sysViewEndTime = System.currentTimeMillis() + 10000;
        context.getSource().sendFeedback(Text.literal("Showing System Override stats for 10 seconds"));
        return 1;
    }

    private int handleChestResetCommand(CommandContext<FabricClientCommandSource> context) {
        resetCounts();
        context.getSource().sendFeedback(Text.literal("Reset all chest counts and gems!"));
        return 1;
    }

    private int handleTimerResetCommand(CommandContext<FabricClientCommandSource> context) {
        resetTimer();
        context.getSource().sendFeedback(Text.literal("Reset timer to 00:00:00!"));
        return 1;
    }

    private int handleTimerStartCommand(CommandContext<FabricClientCommandSource> context) {
        if (!isTimerRunning) {
            startTimer();
            context.getSource().sendFeedback(Text.literal("Timer started!"));
        } else {
            context.getSource().sendFeedback(Text.literal("Timer is already running!"));
        }
        return 1;
    }

    private int handleTimerPauseCommand(CommandContext<FabricClientCommandSource> context) {
        if (isTimerRunning) {
            pauseTimer();
            context.getSource().sendFeedback(Text.literal("Timer paused!"));
        } else {
            context.getSource().sendFeedback(Text.literal("Timer is not running!"));
        }
        return 1;
    }

    private void initializeCounts() {
        for (String tier : TIERS) chestCounts.put(tier, 0);
        for (String tier : SYS_TIERS) sysChestCounts.put(tier, 0);
    }

    private void setupKeybinds() {
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "chest tracker reset and timer", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "adv"
        ));
        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "chest tracker togglehud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "adv"
        ));
        startPauseTimerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "chest tracker start pause timer", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_T, "adv"
        ));
    }

    private void handleBoundaryCheck(MinecraftClient client) {
        if (needsBoundaryCheck && client.getWindow() != null) {
            Window window = client.getWindow();
            hudContainer.x = clampHudX(hudContainer.x, window);
            hudContainer.y = clampHudY(hudContainer.y, window);
            needsBoundaryCheck = false;
            saveHudPosition();
        }
    }

    private int clampHudX(int x, Window window) {
        return Math.max(5, Math.min(x, window.getScaledWidth() - hudContainer.getScaledWidth() - 5));
    }

    private int clampHudY(int y, Window window) {
        return Math.max(5, Math.min(y, window.getScaledHeight() - hudContainer.getScaledHeight() - 5));
    }

    private void handleClientTick(MinecraftClient client) {
        if (resetKey.wasPressed()) {
            resetCounts();
            resetTimer();
            sendClientMessage("Reset all chest counts, gems, and timer!");
        }
        if (startPauseTimerKey.wasPressed()) handleTimerToggle();
        if (toggleHudKey.wasPressed()) toggleHudVisibility();

        if (showSysView && System.currentTimeMillis() > sysViewEndTime) {
            showSysView = false;
        }
    }

    private void handleTimerToggle() {
        if (isTimerRunning) {
            pauseTimer();
        } else {
            startTimer();
        }
        sendClientMessage("Timer " + (isTimerRunning ? "started" : "paused"));
    }

    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis() - pausedTime;
            isTimerRunning = true;
        }
    }

    private void pauseTimer() {
        if (isTimerRunning) {
            pausedTime = System.currentTimeMillis() - startTime;
            isTimerRunning = false;
        }
    }

    private void toggleHudVisibility() {
        hudVisible = !hudVisible;
        sendClientMessage("HUD " + (hudVisible ? "enabled" : "disabled"));
    }

    private void sendClientMessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
        }
    }

    private boolean isInTrackedDimension() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        String dimension = client.world.getRegistryKey().getValue().toString();
        return dimension.contains("minecraft:adventure") || dimension.contains("minecraft:the_nether");
    }

    private boolean isInSkyblockWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        String dimension = client.world.getRegistryKey().getValue().toString();
        return dimension.contains("skyblock_world");
    }

    private boolean isHoldingPickaxe() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        ItemStack heldItem = client.player.getMainHandStack();
        return heldItem.getItem().toString().toLowerCase().contains("pickaxe");
    }

    private void handleChatMessage(Text message, boolean overlay) {
        if (overlay) return;

        String raw = message.getString();
        String clean = raw.replaceAll("§[0-9a-fk-or]", "").trim();

        Matcher matcher = CHEST_PATTERN.matcher(clean);
        Matcher sysMatcher = SYS_CHEST_PATTERN.matcher(clean);
        Matcher tireSysMatcher = TIRE_SYS_CHEST_PATTERN.matcher(clean);
        Matcher maxGemMatcher = MAX_GEM_PATTERN.matcher(clean);

        if (matcher.matches()) {
            String tier = matcher.group(1);
            chestCounts.put(tier, chestCounts.getOrDefault(tier, 0) + 1);
        } else if (sysMatcher.matches()) {
            String tier = sysMatcher.group(1);
            sysChestCounts.put(tier, sysChestCounts.getOrDefault(tier, 0) + 1);
        } else if (tireSysMatcher.matches()) {
            sysChestCounts.put("Tire", sysChestCounts.getOrDefault("Tire", 0) + 1);
        } else if (maxGemMatcher.matches() && isInSkyblockWorld() && isHoldingPickaxe()) {
            maxGemCount++;
        }
    }

    private void resetCounts() {
        for (String tier : TIERS) chestCounts.put(tier, 0);
        for (String tier : SYS_TIERS) sysChestCounts.put(tier, 0);
        maxGemCount = 0;
    }

    private void resetTimer() {
        startTime = 0;
        pausedTime = 0;
        isTimerRunning = false;
    }

    private String getElapsedTime() {
        long elapsedMillis = isTimerRunning ? System.currentTimeMillis() - startTime : pausedTime;
        long seconds = elapsedMillis / 1000;
        return String.format("%02d:%02d:%02d", (seconds / 3600) % 24, (seconds / 60) % 60, seconds % 60);
    }

    private void renderHud(DrawContext context, float tickDelta) {
        if (!modEnabled || !hudVisible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        UniversalGuiMover.HudContainer container = UniversalGuiMover.getHudContainer("chestTrackerHud");

        if (client == null || window == null || container == null) return;

        hudContainer.x = container.x;
        hudContainer.y = container.y;

        if (UniversalGuiMover.isDragging()) needsBoundaryCheck = true;
        else if (needsBoundaryCheck) {
            hudContainer.x = clampHudX(hudContainer.x, window);
            hudContainer.y = clampHudY(hudContainer.y, window);
            saveHudPosition();
            needsBoundaryCheck = false;
        }

        float scale = UniversalGuiMover.getGlobalTextScale();
        context.getMatrices().push();
        context.getMatrices().translate(hudContainer.x, hudContainer.y, 0);
        context.getMatrices().scale(scale, scale, 1);

        TextRenderer renderer = client.textRenderer;
        int yPos = 2;
        final int padding = 2;

        context.drawTextWithShadow(renderer, "Time: " + getElapsedTime(), 2, yPos, 0xFFFFFF);
        yPos += renderer.fontHeight + (int)(padding / scale);

        if (showSysView) {
            for (String tier : SYS_TIERS) {
                int count = sysChestCounts.get(tier);
                if (count > 0) {
                    context.drawTextWithShadow(renderer, tier + " (Sys): " + count, 2, yPos, tierColors.getOrDefault(tier, 0xFFFFFF));
                    yPos += renderer.fontHeight + (int)(padding / scale);
                }
            }
        } else if (isInTrackedDimension()) {
            for (String tier : TIERS) {
                context.drawTextWithShadow(renderer, tier + ": " + chestCounts.get(tier), 2, yPos, tierColors.getOrDefault(tier, 0xFFFFFF));
                yPos += renderer.fontHeight + (int)(padding / scale);
            }
            int sysTotal = 0;
            for (int count : sysChestCounts.values()) sysTotal += count;
            context.drawTextWithShadow(renderer, "System Override: " + sysTotal, 2, yPos, 0x00FF00);
            yPos += renderer.fontHeight + (int)(padding / scale);
        }

        if (isInSkyblockWorld() && isHoldingPickaxe()) {
            context.drawTextWithShadow(renderer, "Max Gems: " + maxGemCount, 2, yPos, 0xFFD700);
        }

        context.getMatrices().pop();
    }

    private void saveHudPosition() {
        try {
            net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
            nbt.putInt("hudX", hudContainer.x);
            nbt.putInt("hudY", hudContainer.y);
            Files.createDirectories(CONFIG_PATH.getParent());
            net.minecraft.nbt.NbtIo.write(nbt, CONFIG_PATH);
        } catch (IOException ignored) {}
    }

    private void loadHudPosition() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.NbtIo.read(CONFIG_PATH);
                hudContainer.x = nbt.getInt("hudX");
                hudContainer.y = nbt.getInt("hudY");
                needsBoundaryCheck = true;
            }
        } catch (IOException ignored) {}
    }
}