package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ArmorToggleMod implements ClientModInitializer {
    public static boolean hideArmor = false;
    private static KeyBinding toggleKey;
    private static final String CONFIG_FILE_NAME = "armorvisibility.cfg";

    @Override
    public void onInitializeClient() {

        loadConfig();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Toggle Armor Visibility",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "adv"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey.wasPressed()) {
                hideArmor = !hideArmor;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Armor visibility: " + (hideArmor ? "HIDDEN" : "SHOWN")), true);
                    refreshEntityRendering(client);

                    // Save the new state
                    saveConfig();
                }
            }
        });
    }

    private void refreshEntityRendering(MinecraftClient client) {
        if (client.world != null) {
            ClientWorld world = client.world;

            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity && !(entity instanceof PlayerEntity)) {
                    if (client.getEntityRenderDispatcher().getRenderer(entity) != null) {
                        entity.age = entity.age;
                    }
                }
            }

            if (client.worldRenderer != null) {
                client.worldRenderer.reload();
            }
        }
    }

    private void loadConfig() {
        Path configPath = getConfigPath();
        if (configPath.toFile().exists()) {
            try (InputStream input = new FileInputStream(configPath.toFile())) {
                Properties prop = new Properties();
                prop.load(input);
                hideArmor = Boolean.parseBoolean(prop.getProperty("hideArmor", "false"));
            } catch (IOException ex) {
                System.err.println("Error loading armor visibility config: " + ex.getMessage());
            }
        }
    }

    private void saveConfig() {
        Path configPath = getConfigPath();
        try (OutputStream output = new FileOutputStream(configPath.toFile())) {
            Properties prop = new Properties();
            prop.setProperty("hideArmor", String.valueOf(hideArmor));
            prop.store(output, "Armor Visibility Configuration");
        } catch (IOException ex) {
            System.err.println("Error saving armor visibility config: " + ex.getMessage());
        }
    }

    private Path getConfigPath() {

        Path configDir = Paths.get(MinecraftClient.getInstance().runDirectory.getPath(), "config");
        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdirs();
        }
        return configDir.resolve(CONFIG_FILE_NAME);
    }
}