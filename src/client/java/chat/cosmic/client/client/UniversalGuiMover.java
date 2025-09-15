// UniversalGuiMover.java
package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class UniversalGuiMover implements ClientModInitializer {
    public static KeyBinding moveGuisKey;
    public static KeyBinding scaleUpKey, scaleDownKey;

    private static boolean isMovementMode = false;
    private static HudContainer draggedContainer = null;
    private static final Map<String, HudContainer> hudContainers = new HashMap<>();
    private static final String CONFIG_FILE = "config/untitled20_mod.properties";
    private static float globalTextScale = 1.0f;
    private static boolean dragging;
    private static int lastWindowWidth = 0;
    private static int lastWindowHeight = 0;
    private static final HudContainer settingsButton = new HudContainer(0, 0, 60, 20, 1);

    private static class DummyScreen extends Screen {
        public DummyScreen() {
            super(Text.of(""));
        }

        @Override
        public boolean shouldPause() {
            return true;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                toggleMovementMode(MinecraftClient.getInstance());
                return true;
            }

            if (UniversalGuiMover.moveGuisKey.matchesKey(keyCode, scanCode)) {
                toggleMovementMode(MinecraftClient.getInstance());
                return true;
            }
            if (UniversalGuiMover.scaleUpKey.matchesKey(keyCode, scanCode)) {
                updateScale(0.1f, MinecraftClient.getInstance());
                return true;
            }
            if (UniversalGuiMover.scaleDownKey.matchesKey(keyCode, scanCode)) {
                updateScale(-0.1f, MinecraftClient.getInstance());
                return true;
            }

            SettingsInputHandler.handleKeyPress(keyCode, scanCode, 0, 0);
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            SettingsInputHandler.handleCharInput(chr);
            return super.charTyped(chr, modifiers);
        }
    }

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            saveGuiPositions();
            SettingsManager.saveSettings();
        });

        setupKeybinds();
        HudRenderCallback.EVENT.register(this::onHudRender);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (isMovementMode) {
                client.mouse.updateMouse();
            }
        });

        SettingsManager.initialize();
        trackHudContainer("settings_button", settingsButton);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.world == null) return;

        Window window = client.getWindow();
        if (window == null) return;

        int currentWidth = window.getScaledWidth();
        int currentHeight = window.getScaledHeight();

        if ((lastWindowWidth != currentWidth || lastWindowHeight != currentHeight) && !isMovementMode) {
            if (lastWindowWidth > 0 && lastWindowHeight > 0) {
                float widthRatio = (float) currentWidth / lastWindowWidth;
                float heightRatio = (float) currentHeight / lastWindowHeight;

                for (HudContainer container : hudContainers.values()) {
                    container.x = (int) (container.x * widthRatio);
                    container.y = (int) (container.y * heightRatio);
                    clampPosition(container, window);
                }
            }

            lastWindowWidth = currentWidth;
            lastWindowHeight = currentHeight;
        }

        if (isMovementMode) {
            settingsButton.x = window.getScaledWidth() - settingsButton.getScaledWidth() - 5;
            settingsButton.y = 5;
        }

        if (scaleUpKey.wasPressed()) {
            updateScale(0.1f, client);
        }
        if (scaleDownKey.wasPressed()) {
            updateScale(-0.1f, client);
        }

        if (moveGuisKey.wasPressed() && client.currentScreen == null) {
            toggleMovementMode(client);
        }
    }

    public static void toggleMovementMode(MinecraftClient client) {
        isMovementMode = !isMovementMode;

        if (isMovementMode) {
            client.setScreen(new DummyScreen());
            client.options.pauseOnLostFocus = true;
        } else {
            client.setScreen(null);
            client.options.pauseOnLostFocus = false;
            SettingsManager.closeSettings();
        }

        if (client.player != null) {
            client.player.sendMessage(Text.literal("GUI Movement: " + (isMovementMode ? "ON" : "OFF")), true);
        }

        Window window = client.getWindow();
        if (window != null) {
            lastWindowWidth = window.getScaledWidth();
            lastWindowHeight = window.getScaledHeight();
        }
    }

    public static void loadGuiPositions(Properties props) {
        if (props.containsKey("scale")) {
            globalTextScale = Float.parseFloat(props.getProperty("scale"));
        }

        props.stringPropertyNames().stream()
                .filter(key -> key.contains("."))
                .forEach(key -> {
                    String[] parts = key.split("\\.", 2);
                    String id = parts[0];
                    String prop = parts[1];

                    HudContainer container = hudContainers.get(id);
                    if (container != null) {
                        switch (prop) {
                            case "x" -> container.x = Integer.parseInt(props.getProperty(key));
                            case "y" -> container.y = Integer.parseInt(props.getProperty(key));
                        }
                    }
                });
    }

    public static void saveGuiPositions(Properties props) {
        props.setProperty("scale", String.valueOf(globalTextScale));
        hudContainers.forEach((id, container) -> {
            props.setProperty(id + ".x", String.valueOf(container.x));
            props.setProperty(id + ".y", String.valueOf(container.y));
        });
    }

    public static void saveGuiPositions() {
        Properties props = new Properties();
        saveGuiPositions(props);
        try {
            Files.createDirectories(Path.of(CONFIG_FILE).getParent());
            props.store(Files.newOutputStream(Path.of(CONFIG_FILE)), "GUI Positions");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadGuiPositions() {
        Properties props = new Properties();
        try {
            if (Files.exists(Path.of(CONFIG_FILE))) {
                props.load(Files.newInputStream(Path.of(CONFIG_FILE)));
                loadGuiPositions(props);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupKeybinds() {
        moveGuisKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Move GUIs", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "Universal GUI Mover"));

        scaleUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Scale Up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "Universal GUI Mover"));

        scaleDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Scale Down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "Universal GUI Mover"));
    }

    public static void handleKeyPress(int key, int scancode, int action, int modifiers) {
        SettingsInputHandler.handleKeyPress(key, scancode, action, modifiers);
    }

    public static void handleCharInput(char character) {
        SettingsInputHandler.handleCharInput(character);
    }

    private void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        handleDragging(client);

        if (isMovementMode) {
            context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), 0x80000000);

            for (Map.Entry<String, HudContainer> entry : hudContainers.entrySet()) {
                HudContainer container = entry.getValue();
                if (container == settingsButton) continue;

                if (!isContainerEnabled(entry.getKey())) continue;

                int x = container.x;
                int y = container.y;
                int width = container.getScaledWidth();
                int height = container.getScaledHeight();

                context.fill(x, y, x + width, y + height, 0x20FFFFFF);
                context.fill(x, y, x + width, y + 2, 0xFFFFFFFF);
                context.fill(x, y + height - 2, x + width, y + height, 0xFFFFFFFF);
                context.fill(x, y, x + 2, y + height, 0xFFFFFFFF);
                context.fill(x + width - 2, y, x + width, y + height, 0xFFFFFFFF);
            }

            HudContainer button = getHudContainer("settings_button");
            if (button != null) {
                context.fill(button.x, button.y, button.x + button.getScaledWidth(), button.y + button.getScaledHeight(), 0x55000000);
                context.drawCenteredTextWithShadow(client.textRenderer, Text.of("Settings"),
                        button.x + button.getScaledWidth()/2, button.y + 6, 0xFFFFFF);
            }

            double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
            boolean mouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (!SettingsInputHandler.isWaitingForKey()) {
                SettingsInputHandler.handleClick(mouseX, mouseY, mouseDown);

                if (SettingsInputHandler.shouldToggleSettings(mouseX, mouseY, mouseDown)) {
                    SettingsManager.toggleSettings();
                }
            }

            if (SettingsManager.isSettingsOpen()) {
                SettingsRenderer.render(context, client);
            }
        }
    }

    private static boolean isContainerEnabled(String containerId) {
        Map<String, Boolean> toggleSettings = SettingsManager.getToggleSettings();
        Map<String, Boolean> boosterToggleSettings = SettingsManager.getBoosterToggleSettings();

        // Special case for trinket display
        if ("trinket_display".equals(containerId)) {
            return toggleSettings.getOrDefault("Trinket Display HUD", true);
        }

        if (toggleSettings.containsKey(containerId + " HUD")) {
            return toggleSettings.get(containerId + " HUD");
        }
        if (boosterToggleSettings.containsKey(containerId)) {
            return boosterToggleSettings.get(containerId);
        }
        return true;
    }

    private static void updateScale(float delta, MinecraftClient client) {
        globalTextScale = Math.max(0.5f, Math.min(2.5f, globalTextScale + delta));
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Scale: " + String.format("%.1f", globalTextScale)), true);
        }
    }

    private void handleDragging(MinecraftClient client) {
        if (!isMovementMode || SettingsManager.isSettingsOpen() || SettingsInputHandler.isWaitingForKey()) {
            return;
        }

        Window window = client.getWindow();
        double mouseX = client.mouse.getX() * window.getScaledWidth() / window.getWidth();
        double mouseY = client.mouse.getY() * window.getScaledHeight() / window.getHeight();

        boolean mouseDown = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (mouseDown) {
            if (draggedContainer == null) {
                hudContainers.values().stream()
                        .filter(container -> container != settingsButton)
                        .filter(container -> isMouseOver(container, mouseX, mouseY))
                        .findFirst()
                        .ifPresent(container -> draggedContainer = container);
            } else {
                draggedContainer.x = clampX((int) mouseX, draggedContainer, window);
                draggedContainer.y = clampY((int) mouseY, draggedContainer, window);
            }
            dragging = true;
        } else {
            draggedContainer = null;
            dragging = false;
        }
    }

    private boolean isMouseOver(HudContainer container, double mouseX, double mouseY) {
        return mouseX >= container.x && mouseX <= container.x + container.getScaledWidth() &&
                mouseY >= container.y && mouseY <= container.y + container.getScaledHeight();
    }

    public static void trackHudContainer(String id, HudContainer container) {
        if (hudContainers.containsKey(id)) {
            HudContainer saved = hudContainers.get(id);
            container.x = saved.x;
            container.y = saved.y;
        }
        hudContainers.put(id, container);
        clampPosition(container, MinecraftClient.getInstance().getWindow());
    }

    public static void clampPosition(HudContainer container, Window window) {
        if (window != null) {
            container.x = clampX(container.x, container, window);
            container.y = clampY(container.y, container, window);
        }
    }

    private static int clampX(int x, HudContainer container, Window window) {
        return Math.max(5, Math.min(x, window.getScaledWidth() - container.getScaledWidth() - 5));
    }

    private static int clampY(int y, HudContainer container, Window window) {
        return Math.max(5, Math.min(y, window.getScaledHeight() - container.getScaledHeight() - 5));
    }

    public static HudContainer getHudContainer(String id) {
        return hudContainers.get(id);
    }

    public static class HudContainer {
        public int x, y;
        public int baseWidth;
        public int baseHeight;
        public int lineCount;

        public HudContainer(int x, int y, int baseWidth, int baseHeight, int lineCount) {
            this.x = x;
            this.y = y;
            this.baseWidth = baseWidth;
            this.baseHeight = baseHeight;
            this.lineCount = lineCount;
        }

        public int getScaledWidth() {
            return (int) (baseWidth * globalTextScale);
        }

        public int getScaledHeight() {
            return (int) (baseHeight * lineCount * globalTextScale);
        }
    }

    public static float getGlobalTextScale() {
        return globalTextScale;
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static boolean isMovementModeActive() {
        return isMovementMode;
    }
}