
package chat.cosmic.client.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class SettingsInputHandler {
    private static boolean wasClickHandled = false;
    private static boolean lastMouseState = false;

    private static String editingKeybind = null;
    private static boolean waitingForKey = false;
    private static long keyPressTime = 0;
    private static final long KEY_PRESS_DELAY = 150;

    public static void handleClick(double mouseX, double mouseY, boolean mouseDown) {
        if (!mouseDown) {
            wasClickHandled = false;
            return;
        }

        if (wasClickHandled) {
            return;
        }

        if (SettingsRenderer.isMouseOverSettings(mouseX, mouseY)) {
            wasClickHandled = true;
        }
    }

    public static boolean shouldToggleSettings(double mouseX, double mouseY, boolean mouseDown) {
        if (!mouseDown || wasClickHandled) {
            return false;
        }

        UniversalGuiMover.HudContainer button = UniversalGuiMover.getHudContainer("settings_button");
        if (button == null) {
            return false;
        }

        int buttonX = button.x;
        int buttonY = button.y;
        int buttonWidth = button.getScaledWidth();
        int buttonHeight = button.getScaledHeight();

        boolean overButton = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                mouseY >= buttonY && mouseY <= buttonY + buttonHeight;

        if (overButton) {
            wasClickHandled = true;
            return true;
        }

        return false;
    }

    public static void handleKeyPress(int key, int scancode, int action, int modifiers) {
        if (!SettingsManager.isSettingsOpen() || !waitingForKey || editingKeybind == null) {
            return;
        }

        if (action == GLFW.GLFW_PRESS && System.currentTimeMillis() - keyPressTime > KEY_PRESS_DELAY) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelKeybindEditing();
                return;
            }

            KeyBinding keyBinding = SettingsManager.getKeybindList().get(editingKeybind);
            if (keyBinding != null) {
                keyBinding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(key));
                KeyBinding.updateKeysByCode();
            }

            cancelKeybindEditing();
        }
    }

    public static void handleCharInput(char character) {

    }

    public static void startEditingKeybind(String keybindName) {
        editingKeybind = keybindName;
        waitingForKey = true;
        keyPressTime = System.currentTimeMillis();
    }

    public static void cancelKeybindEditing() {
        editingKeybind = null;
        waitingForKey = false;
    }

    public static String getEditingKeybind() {
        return editingKeybind;
    }

    public static boolean isWaitingForKey() {
        return waitingForKey;
    }

    public static boolean getLastMouseState() {
        return lastMouseState;
    }

    public static void setLastMouseState(boolean state) {
        lastMouseState = state;
    }

    public static void saveThresholdsIfEditing() {

    }
}