package chat.cosmic.client.client;

import chat.cosmic.client.client.KeyBinds.KeyBinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class ArmorToggleMod implements ClientModInitializer {
    public static boolean hideArmor = false;

    @Override
    public void onInitializeClient() {
        SettingsManager.initialize();
        hideArmor = SettingsManager.getToggleSettings().getOrDefault("Hide Armor", false);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (KeyBinds.getToggleArmorVisibility().wasPressed()) {
                hideArmor = !hideArmor;
                SettingsManager.getToggleSettings().put("Hide Armor", hideArmor);
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Armor visibility: " + (hideArmor ? "HIDDEN" : "SHOWN")), true);
                    refreshEntityRendering(client);
                }
                SettingsManager.saveSettings();
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
}