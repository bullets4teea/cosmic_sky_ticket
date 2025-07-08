// File: src/main/java/com/example/announcementhider/AnnouncementHiderClient.java
package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AnnouncementHiderClient implements ClientModInitializer {

    public static boolean hideAnnouncements = false;

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        // Register keybinding
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.announcement_hider.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H, // Default key: H
                "category.announcement_hider"
        ));

        // Register tick event to check for key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey.wasPressed()) {
                hideAnnouncements = !hideAnnouncements;

                if (client.player != null) {
                    String status = hideAnnouncements ? "enabled" : "disabled";
                    client.player.sendMessage(
                            Text.literal("§6Announcement Hider: §r" + status),
                            false
                    );
                }
            }
        });
    }
}
