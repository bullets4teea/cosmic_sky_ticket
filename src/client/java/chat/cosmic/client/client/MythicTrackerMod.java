package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.util.Properties;

public class MythicTrackerMod implements ClientModInitializer {

    private static int mythicCount = 0;
    private static int godlyCount = 0;
    private static int heroicCount = 0;
    private static boolean isHudVisible = true;
    private static KeyBinding toggleHudKey;
    private static boolean wasToggleKeyPressed = false;
    private static final String CONFIG_FILE = "config/mythictracker.properties";

    // Sound setup
    public static final Identifier MYTHIC_SOUND_ID = new Identifier("mythictracker", "mythic_sound");
    public static final Identifier GODLY_SOUND_ID = new Identifier("mythictracker", "godly_sound");
    public static final Identifier HEROIC_SOUND_ID = new Identifier("mythictracker", "heroic_sound");
    public static SoundEvent MYTHIC_SOUND = SoundEvent.of(MYTHIC_SOUND_ID);
    public static SoundEvent GODLY_SOUND = SoundEvent.of(GODLY_SOUND_ID);
    public static SoundEvent HEROIC_SOUND = SoundEvent.of(HEROIC_SOUND_ID);

    @Override
    public void onInitializeClient() {
        Registry.register(Registries.SOUND_EVENT, MYTHIC_SOUND_ID, MYTHIC_SOUND);
        Registry.register(Registries.SOUND_EVENT, GODLY_SOUND_ID, GODLY_SOUND);
        Registry.register(Registries.SOUND_EVENT, HEROIC_SOUND_ID, HEROIC_SOUND);

        UniversalGuiMover.HudContainer hudContainer = new UniversalGuiMover.HudContainer(
                10, 10,
                50,
                12,
                3
        );
        UniversalGuiMover.trackHudContainer("mythicTrackerHud", hudContainer);

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Toggle HUD",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "Fish Tracker"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isTogglePressed = toggleHudKey.isPressed();
            if (isTogglePressed && !wasToggleKeyPressed) {
                isHudVisible = !isHudVisible;
            }
            wasToggleKeyPressed = isTogglePressed;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String msg = message.getString();
            MinecraftClient client = MinecraftClient.getInstance();

            if (msg.contains("Mythic Treasure")) {
                mythicCount++;
                playSound(client, MYTHIC_SOUND);
            } else if (msg.contains("Godly Treasure")) {
                godlyCount++;
                playSound(client, GODLY_SOUND);
            } else if (msg.contains("Heroic Treasure")) {
                heroicCount++;
                playSound(client, HEROIC_SOUND);
            }
        });

        HudRenderCallback.EVENT.register((context, delta) -> {
            if (isHudVisible) {
                drawHud(context);
            }
        });
    }

    private void drawHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        UniversalGuiMover.HudContainer container = UniversalGuiMover.getHudContainer("mythicTrackerHud");
        if (container == null) return;

        float scale = UniversalGuiMover.getGlobalTextScale();
        int scaledX = container.x;
        int scaledY = container.y;

        context.getMatrices().push();
        context.getMatrices().translate(scaledX, scaledY, 0);
        context.getMatrices().scale(scale, scale, 1);

        // Text lines
        context.drawText(client.textRenderer, Text.literal("§bFishing Tracker"), 2, 2, 0xFFFFFF, true);

        context.drawText(client.textRenderer, Text.literal("§cGodly: " + godlyCount), 2, 14, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("§dHeroic: " + heroicCount), 2, 26, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("§5Mythic: " + mythicCount), 2, 38, 0xFFFFFF, true);

        context.getMatrices().pop();
    }

    private void playSound(MinecraftClient client, SoundEvent sound) {
        if (client.player != null) {
            client.getSoundManager().play(
                    PositionedSoundInstance.master(sound, 1.0f, 1.0f)
            );
        }
    }
}