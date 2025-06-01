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

public class ArmorToggleMod implements ClientModInitializer {
    public static boolean hideArmor = false;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
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
}