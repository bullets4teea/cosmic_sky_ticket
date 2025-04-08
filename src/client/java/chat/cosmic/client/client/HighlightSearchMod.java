package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class HighlightSearchMod implements ClientModInitializer {
    public static KeyBinding TOGGLE_SEARCH_KEY;
    public static boolean isSearchVisible = true;

    @Override
    public void onInitializeClient() {
        // Register the key binding
        TOGGLE_SEARCH_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "toggle search",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "search bar "
        ));


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_SEARCH_KEY.wasPressed()) {
                isSearchVisible = !isSearchVisible;
            }
        });
    }
}