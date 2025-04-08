package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ChestTrackerMod implements ClientModInitializer {
    private static final Map<String, Integer> chestCounts = new HashMap<>();
    private static final Map<String, Integer> tierColors = Map.of(
            "Basic", 0xFFFFFF,
            "Elite", 0x54FCFC,
            "Legendary", 0xFFA500,
            "Godly", 0xFF0000,
            "Heroic", 0xFF69B4,
            "Mythic", 0x800080
    );
    private static final String[] TIERS = {"Basic", "Elite", "Legendary", "Godly", "Heroic", "Mythic"};

    private static KeyBinding resetKey, toggleHudKey, startPauseTimerKey;
    private static boolean hudVisible = true;
    private static final Path CONFIG_PATH = Path.of("config/chesttracker_hud.dat");
    private static final Set<String> countedChests = new HashSet<>();
    private static long startTime = 0;
    private static long pausedTime = 0;
    private static boolean isTimerRunning = false;
    private static boolean needsBoundaryCheck = true;

    // HUD Container
    private static final UniversalGuiMover.HudContainer hudContainer =
            new UniversalGuiMover.HudContainer(10, 100, 120, 9, 7);

    @Override
    public void onInitializeClient() {
        initializeCounts();
        setupKeybinds();
        loadHudPosition();
        UniversalGuiMover.trackHudContainer("chestTrackerHud", hudContainer);

        ScreenEvents.AFTER_INIT.register(this::handleScreenInit);
        ClientTickEvents.END_CLIENT_TICK.register(this::handleClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(this::handleBoundaryCheck);
        HudRenderCallback.EVENT.register(this::renderHud);
    }

    private void initializeCounts() {
        for (String tier : TIERS) chestCounts.put(tier, 0);
    }

    private void setupKeybinds() {
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "chest tracker reset",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "chest tracker"
        ));

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "chest tracker togglehud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "chest tracker"
        ));

        startPauseTimerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "chest tracker start pause timer",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_T,
                "chest tracker"
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

    private void handleScreenInit(MinecraftClient client, Object screen, int width, int height) {
        if (screen instanceof HandledScreen<?> handledScreen) {
            countedChests.clear();
            ScreenEvents.beforeRender((Screen) screen).register((s, ctx, mx, my, delta) ->
                    handleChestOpen(handledScreen.getTitle().getString()));
        }
    }

    private void handleClientTick(MinecraftClient client) {
        if (resetKey.wasPressed()) handleReset();
        if (startPauseTimerKey.wasPressed()) handleTimerToggle();
        if (toggleHudKey.wasPressed()) toggleHudVisibility();
    }

    private void handleReset() {
        resetCounts();
        resetTimer();
        sendClientMessage("Reset all chest counts and timer!");
    }

    private void handleTimerToggle() {
        toggleTimer();
        sendClientMessage("Timer " + (isTimerRunning ? "started" : "paused"));
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

    private boolean isInAdventureDimension() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null &&
                client.world.getRegistryKey().getValue().toString().contains("minecraft:adventure");
    }

    private void handleChestOpen(String title) {
        if (!isInAdventureDimension()) return;

        String cleanedTitle = title.replaceAll("§[0-9a-fk-or]", "").trim();
        if (countedChests.add(cleanedTitle)) {
            Arrays.stream(TIERS)
                    .filter(cleanedTitle::contains)
                    .findFirst()
                    .ifPresent(tier -> chestCounts.put(tier, chestCounts.get(tier) + 1));
        }
    }

    private void resetCounts() {
        chestCounts.replaceAll((k, v) -> 0);
        countedChests.clear();
    }

    private void resetTimer() {
        startTime = 0;
        pausedTime = 0;
        isTimerRunning = false;
    }

    private void toggleTimer() {
        if (isTimerRunning) {
            pausedTime = System.currentTimeMillis() - startTime;
            isTimerRunning = false;
        } else {
            startTime = System.currentTimeMillis() - pausedTime;
            isTimerRunning = true;
        }
    }

    private String getElapsedTime() {
        long elapsedMillis = isTimerRunning ? System.currentTimeMillis() - startTime : pausedTime;
        long seconds = elapsedMillis / 1000;
        return String.format("%02d:%02d:%02d", (seconds / 3600) % 24, (seconds / 60) % 60, seconds % 60);
    }

    private void renderHud(DrawContext context, float tickDelta) {
        if (!hudVisible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        UniversalGuiMover.HudContainer container = UniversalGuiMover.getHudContainer("chestTrackerHud");

        if (client == null || window == null || container == null) return;


        hudContainer.x = container.x;
        hudContainer.y = container.y;

        if (UniversalGuiMover.isDragging()) {
            needsBoundaryCheck = true;
        } else if (needsBoundaryCheck) {
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
        yPos += renderer.fontHeight + (int) (padding / scale);


        if (isInAdventureDimension()) {
            for (String tier : TIERS) {
                context.drawTextWithShadow(renderer, tier + ": " + chestCounts.get(tier),
                        2, yPos, tierColors.getOrDefault(tier, 0xFFFFFF));
                yPos += renderer.fontHeight + (int) (padding / scale);
            }
        }

        context.getMatrices().pop();
    }

    private void saveHudPosition() {
        try {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("hudX", hudContainer.x);
            nbt.putInt("hudY", hudContainer.y);
            Files.createDirectories(CONFIG_PATH.getParent());
            NbtIo.write(nbt, CONFIG_PATH);
        } catch (IOException ignored) {}
    }

    private void loadHudPosition() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                NbtCompound nbt = NbtIo.read(CONFIG_PATH);
                hudContainer.x = nbt.getInt("hudX");
                hudContainer.y = nbt.getInt("hudY");
                needsBoundaryCheck = true;
            }
        } catch (IOException ignored) {}
    }
}