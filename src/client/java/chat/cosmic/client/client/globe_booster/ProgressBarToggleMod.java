package chat.cosmic.client.client.globe_booster;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import chat.cosmic.client.client.KeyBinds.KeyBinds;

public class ProgressBarToggleMod implements ClientModInitializer {
    private static boolean bossBarsHidden = true;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBinds.getToggleBossBars().wasPressed()) {
                bossBarsHidden = !bossBarsHidden;
                if (client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§6[NoBossBar] §fBoss bars " + (bossBarsHidden ? "§ahidden" : "§cvisible")),
                            false
                    );
                }
            }
        });
    }

    public static boolean shouldHideBossBars() {
        return bossBarsHidden;
    }
}