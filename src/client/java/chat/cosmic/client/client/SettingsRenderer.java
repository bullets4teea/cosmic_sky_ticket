package chat.cosmic.client.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsRenderer {
    public enum Tab {
        SETTINGS("Settings", 0),
        KEYBINDS("Keybinds", 1),
        COMMANDS("Commands", 2),
        HUD_SETTINGS("HUD Settings", 3);

        public final String name;
        public final int index;

        Tab(String name, int index) {
            this.name = name;
            this.index = index;
        }
    }

    private static Tab currentTab = Tab.SETTINGS;
    private static int settingsX = 0;
    private static int settingsY = 0;
    private static final int WIDTH = 350;
    private static final int HEIGHT = 250;
    private static final int TAB_HEIGHT = 20;
    private static final Map<String, Boolean> lastToggleState = new HashMap<>();
    private static final Map<String, Boolean> lastSliderState = new HashMap<>();
    private static int sliderDragX = -1;

    public static void setPosition(int x, int y) {
        settingsX = x;
        settingsY = y;
    }

    public static int getWidth() {
        return WIDTH;
    }

    public static int getHeight() {
        return HEIGHT;
    }

    public static void render(DrawContext context, MinecraftClient client) {
        if (!SettingsManager.isSettingsOpen()) return;

        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), 0x80000000);

        context.fill(settingsX, settingsY, settingsX + WIDTH, settingsY + HEIGHT, 0xAA202020);
        context.drawBorder(settingsX, settingsY, WIDTH, HEIGHT, 0x80FFFFFF);

        int tabWidth = WIDTH / Tab.values().length;
        for (Tab tab : Tab.values()) {
            int tabX = settingsX + tab.index * tabWidth;
            boolean isActive = tab == currentTab;
            int color = isActive ? 0xAA404040 : 0xAA303030;

            context.fill(tabX, settingsY, tabX + tabWidth, settingsY + TAB_HEIGHT, color);
            context.drawCenteredTextWithShadow(client.textRenderer, Text.of(tab.name),
                    tabX + tabWidth/2, settingsY + 6, 0xFFFFFF);

            double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
            boolean mouseOver = mouseX >= tabX && mouseX <= tabX + tabWidth &&
                    mouseY >= settingsY && mouseY <= settingsY + TAB_HEIGHT;

            if (mouseOver && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                SettingsInputHandler.saveThresholdsIfEditing();
                currentTab = tab;
            }
        }

        int contentY = settingsY + TAB_HEIGHT;
        int contentHeight = HEIGHT - TAB_HEIGHT;

        switch (currentTab) {
            case SETTINGS -> renderSettingsTab(context, client, settingsX, contentY, WIDTH, contentHeight);
            case KEYBINDS -> renderKeybindsTab(context, client, settingsX, contentY, WIDTH, contentHeight);
            case COMMANDS -> renderCommandsTab(context, client, settingsX, contentY, WIDTH, contentHeight);
            case HUD_SETTINGS -> renderHudSettingsTab(context, client, settingsX, contentY, WIDTH, contentHeight);
        }
    }

    private static void renderSettingsTab(DrawContext context, MinecraftClient client, int x, int y, int width, int height) {
        int optionY = y + 10;

        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        boolean mouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        boolean mousePressed = mouseDown && !SettingsInputHandler.getLastMouseState();
        SettingsInputHandler.setLastMouseState(mouseDown);

        for (Map.Entry<String, Boolean> entry : SettingsManager.getToggleSettings().entrySet()) {
            String key = entry.getKey();
            // Only skip other HUD settings, but show Trinket Display HUD
            if (key.contains("HUD") && !key.equals("Trinket Display HUD")) continue;

            String text = key + ": " + (entry.getValue() ? "ON" : "OFF");
            context.drawTextWithShadow(client.textRenderer, text, x + 10, optionY, 0xFFFFFF);

            boolean mouseOver = mouseX >= x + 10 && mouseX <= x + width - 10 &&
                    mouseY >= optionY && mouseY <= optionY + 10;

            if (mouseOver) {
                context.fill(x + 10, optionY, x + width - 10, optionY + 10, 0x20FFFFFF);
                if (mousePressed && !lastToggleState.getOrDefault(key, false)) {
                    SettingsInputHandler.saveThresholdsIfEditing();
                    SettingsManager.getToggleSettings().put(key, !entry.getValue());
                    SettingsManager.applySettings();
                    lastToggleState.put(key, true);
                }
            }

            if (!mouseOver || !mouseDown) {
                lastToggleState.put(key, false);
            }

            optionY += 12;
        }

        optionY += 5;

        for (Map.Entry<String, Float> entry : SettingsManager.getSliderSettings().entrySet()) {
            context.drawTextWithShadow(client.textRenderer, entry.getKey() + ": " + entry.getValue().intValue(), x + 10, optionY, 0xFFFFFF);
            optionY += 12;

            int sliderX = x + 10;
            int sliderY = optionY;
            int sliderWidth = width - 20;
            int sliderHeight = 5;
            int sliderHandleWidth = 6;
            int sliderHandleHeight = 12;

            context.fill(sliderX, sliderY + sliderHeight/2 - 1, sliderX + sliderWidth, sliderY + sliderHeight/2 + 1, 0x80666666);

            float minValue = 1f;
            float maxValue = 50f;
            float normalizedValue = (entry.getValue() - minValue) / (maxValue - minValue);
            int handleX = sliderX + (int)((sliderWidth - sliderHandleWidth) * normalizedValue);
            context.fill(handleX, sliderY, handleX + sliderHandleWidth, sliderY + sliderHandleHeight, 0xFFFFFFFF);

            boolean mouseOverSlider = mouseX >= sliderX && mouseX <= sliderX + sliderWidth &&
                    mouseY >= sliderY && mouseY <= sliderY + sliderHandleHeight;

            if (mouseOverSlider && mouseDown) {
                if (sliderDragX == -1) {
                    SettingsInputHandler.saveThresholdsIfEditing();
                }
                sliderDragX = (int)mouseX;
            }

            if (sliderDragX != -1) {
                float newValue = ((sliderDragX - sliderX) / (float)sliderWidth) * (maxValue - minValue) + minValue;
                SettingsManager.getSliderSettings().put(entry.getKey(), Math.max(minValue, Math.min(maxValue, newValue)));
                SettingsManager.applySettings();
                if (!mouseDown) {
                    sliderDragX = -1;
                }
            }

            optionY += 20;
        }
    }

    private static void renderHudSettingsTab(DrawContext context, MinecraftClient client, int x, int y, int width, int height) {
        int optionY = y + 10;

        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        boolean mouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        boolean mousePressed = mouseDown && !SettingsInputHandler.getLastMouseState();
        SettingsInputHandler.setLastMouseState(mouseDown);

        context.drawTextWithShadow(client.textRenderer, "HUD Modules:", x + 10, optionY, 0x55FFFF);
        optionY += 12;

        Map<String, Boolean> allHudSettings = new LinkedHashMap<>();

        allHudSettings.put("XP Booster HUD", SettingsManager.getToggleSettings().getOrDefault("XP Booster HUD", true));
        allHudSettings.put("Chest Tracker HUD", SettingsManager.getToggleSettings().getOrDefault("Chest Tracker HUD", true));
        allHudSettings.put("Curse HUD", SettingsManager.getToggleSettings().getOrDefault("Curse HUD", true));
        allHudSettings.put("Chaotic Zone HUD", SettingsManager.getToggleSettings().getOrDefault("Chaotic Zone HUD", true));
        allHudSettings.put("Combat HUD", SettingsManager.getToggleSettings().getOrDefault("Combat HUD", true));
        allHudSettings.put("Mule HUD", SettingsManager.getToggleSettings().getOrDefault("Mule HUD", true));

        for (Map.Entry<String, Boolean> entry : SettingsManager.getBoosterToggleSettings().entrySet()) {
            allHudSettings.put("  " + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Boolean> entry : allHudSettings.entrySet()) {
            String module = entry.getKey();
            boolean value = entry.getValue();
            String text = module + ": " + (value ? "ON" : "OFF");

            int indent = module.startsWith("  ") ? 10 : 0;

            context.drawTextWithShadow(client.textRenderer, text, x + 20 + indent, optionY, 0xFFFFFF);

            boolean mouseOver = mouseX >= x + 20 + indent && mouseX <= x + width - 20 &&
                    mouseY >= optionY && mouseY <= optionY + 10;

            if (mouseOver) {
                context.fill(x + 20 + indent, optionY, x + width - 20, optionY + 10, 0x20FFFFFF);
                if (mousePressed && !lastToggleState.getOrDefault(module, false)) {
                    SettingsInputHandler.saveThresholdsIfEditing();

                    if (module.startsWith("  ")) {
                        SettingsManager.getBoosterToggleSettings().put(module.trim(), !value);
                    } else {
                        SettingsManager.getToggleSettings().put(module, !value);
                    }

                    SettingsManager.applySettings();
                    SettingsManager.saveSettings();
                    lastToggleState.put(module, true);
                }
            }

            if (!mouseOver || !mouseDown) {
                lastToggleState.put(module, false);
            }

            optionY += 12;
        }
    }

    private static void renderKeybindsTab(DrawContext context, MinecraftClient client, int x, int y, int width, int height) {
        int optionY = y + 10;

        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        boolean mouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean mousePressed = mouseDown && !SettingsInputHandler.getLastMouseState();

        for (Map.Entry<String, KeyBinding> entry : SettingsManager.getKeybindList().entrySet()) {
            String keyName = entry.getValue().getBoundKeyLocalizedText().getString();
            boolean isEditing = SettingsInputHandler.getEditingKeybind() != null && SettingsInputHandler.getEditingKeybind().equals(entry.getKey());
            boolean hasConflict = hasKeybindConflict(entry.getValue());

            String displayText = entry.getKey() + ": ";
            if (isEditing && SettingsInputHandler.isWaitingForKey()) {
                displayText += "[Press a key...]";
            } else {
                displayText += keyName;
            }

            int textColor = 0xFFFFFF;
            if (hasConflict) {
                textColor = 0xFFFF5555;
            } else if (isEditing) {
                textColor = 0xFF55FF55;
            }

            context.drawTextWithShadow(client.textRenderer, displayText, x + 10, optionY, textColor);

            boolean mouseOver = mouseX >= x + 10 && mouseX <= x + width - 10 &&
                    mouseY >= optionY && mouseY <= optionY + 10;

            if (mouseOver) {
                context.fill(x + 10, optionY, x + width - 10, optionY + 10, 0x20FFFFFF);

                if (mousePressed) {
                    SettingsInputHandler.saveThresholdsIfEditing();
                    if (isEditing) {
                        SettingsInputHandler.cancelKeybindEditing();
                    } else {
                        SettingsInputHandler.startEditingKeybind(entry.getKey());
                    }
                }
            }

            optionY += 12;
        }

        optionY += 10;
        context.drawTextWithShadow(client.textRenderer, "Click a keybind to change it", x + 10, optionY, 0xFFAAAAAA);
        optionY += 10;
        context.drawTextWithShadow(client.textRenderer, "Press ESC to cancel editing", x + 10, optionY, 0xFFAAAAAA);
    }

    private static void renderCommandsTab(DrawContext context, MinecraftClient client, int x, int y, int width, int height) {
        int optionY = y + 10;
        for (String command : SettingsManager.getCommandHelp()) {
            context.drawTextWithShadow(client.textRenderer, command, x + 10, optionY, 0xFFFFFF);
            optionY += 12;
        }
    }

    private static boolean hasKeybindConflict(KeyBinding keyBinding) {
        if (keyBinding.isUnbound()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) {
            return false;
        }

        KeyBinding[] allKeybinds = client.options.allKeys;
        String boundKey = keyBinding.getBoundKeyTranslationKey();

        int conflictCount = 0;
        for (KeyBinding kb : allKeybinds) {
            if (kb.getBoundKeyTranslationKey().equals(boundKey)) {
                conflictCount++;
                if (conflictCount > 1) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isMouseOverSettings(double mouseX, double mouseY) {
        if (!SettingsManager.isSettingsOpen()) return false;
        return mouseX >= settingsX && mouseX <= settingsX + WIDTH &&
                mouseY >= settingsY && mouseY <= settingsY + HEIGHT;
    }
}