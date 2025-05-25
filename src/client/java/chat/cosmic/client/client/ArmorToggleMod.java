package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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
                }
            }
        });
    }
}