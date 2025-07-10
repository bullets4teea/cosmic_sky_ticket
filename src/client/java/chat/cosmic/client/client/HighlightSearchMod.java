package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class HighlightSearchMod implements ClientModInitializer {
    public static KeyBinding TOGGLE_SEARCH_KEY;
    public static boolean isSearchVisible = true;
    private static final Path CONFIG_PATH = Paths.get("config", "highlight_search.properties");

    @Override
    public void onInitializeClient() {
        loadConfig();

        TOGGLE_SEARCH_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "toggle search",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F13,
                "Island"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_SEARCH_KEY.wasPressed()) {
                isSearchVisible = !isSearchVisible;
                SettingsManager.getToggleSettings().put("Highlight Search", isSearchVisible); // Add this
                SettingsManager.saveSettings(); // Add this
                saveConfig();
            }
        });
    }

    private void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                Properties props = new Properties();
                props.load(Files.newInputStream(CONFIG_PATH));
                isSearchVisible = Boolean.parseBoolean(props.getProperty("visible", "true"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            Properties props = new Properties();
            props.setProperty("visible", String.valueOf(isSearchVisible));
            Files.createDirectories(CONFIG_PATH.getParent());
            props.store(Files.newOutputStream(CONFIG_PATH), "Highlight Search Mod Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}