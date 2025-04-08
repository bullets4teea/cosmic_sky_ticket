package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class UniversalGuiMover implements ClientModInitializer {

    private static KeyBinding moveGuisKey;
    private static boolean isMovementMode = false;
    private static HudContainer draggedContainer = null;
    private static final Map<String, HudContainer> hudContainers = new HashMap<>();
    private static final String CONFIG_FILE = "config/untitled20_mod.properties";
    private static float globalTextScale = 1.0f;
    private static KeyBinding scaleUpKey, scaleDownKey;
    private static boolean dragging;

    @Override
    public void onInitializeClient() {
        // Loading moved to main mod class
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveGuiPositions());
        setupKeybinds();
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    // Add these new methods for property-based loading/saving
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

    // Remove the old file-based save/load methods and replace with these empty ones
    public static void saveGuiPositions() {}  // Now handled by main mod
    public static void loadGuiPositions() {}  // Now handled by main mod

    // Keep the rest of the original code exactly as you provided it
    private void setupKeybinds() {
        moveGuisKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Move GUIs", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "Universal GUI Mover"));

        scaleUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Scale Up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "Universal GUI Mover"));

        scaleDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Scale Down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "Universal GUI Mover"));
    }

    private void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        handleMovementMode(client);
        handleScaling(client);
        handleDragging(client);

        if (isMovementMode) {
            for (HudContainer container : hudContainers.values()) {
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
        }
    }

    private void handleMovementMode(MinecraftClient client) {
        if (moveGuisKey.wasPressed()) {
            isMovementMode = !isMovementMode;
            if (client.player != null) {
                client.player.sendMessage(Text.literal("GUI Movement: " + (isMovementMode ? "ON" : "OFF")), true);
            }
        }
    }

    private void handleScaling(MinecraftClient client) {
        if (scaleUpKey.wasPressed()) updateScale(0.1f, client);
        if (scaleDownKey.wasPressed()) updateScale(-0.1f, client);
    }

    private void updateScale(float delta, MinecraftClient client) {
        globalTextScale = Math.max(0.5f, Math.min(2.5f, globalTextScale + delta));
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Scale: " + String.format("%.1f", globalTextScale)), true);
        }
    }

    private void handleDragging(MinecraftClient client) {
        if (!isMovementMode) return;

        Window window = client.getWindow();
        double mouseX = client.mouse.getX() * window.getScaledWidth() / window.getWidth();
        double mouseY = client.mouse.getY() * window.getScaledHeight() / window.getHeight();

        boolean mouseDown = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (mouseDown) {
            if (draggedContainer == null) {
                hudContainers.values().stream()
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
        public final int baseWidth;
        public final int baseHeight;
        public final int lineCount;

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