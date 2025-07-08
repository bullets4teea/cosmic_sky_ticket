package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.util.Properties;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class MythicTrackerMod implements ClientModInitializer {

    private static int mythicCount = 0;
    private static int godlyCount = 0;
    private static int heroicCount = 0;
    private static int ArtifactfoundCount = 0;
    private static int SUNKENGEMSCount = 0;
    private static int skullCount = 0;
    private static boolean isHudVisible = true;
    public static KeyBinding toggleHudKey; // Changed to public
    private static boolean wasToggleKeyPressed = false;
    private static final String CONFIG_FILE = "config/mythictracker.properties";


    public static final Identifier MYTHIC_SOUND_ID = new Identifier("mythictracker", "mythic_sound");
    public static final Identifier GODLY_SOUND_ID = new Identifier("mythictracker", "godly_sound");
    public static final Identifier HEROIC_SOUND_ID = new Identifier("mythictracker", "heroic_sound");
    public static SoundEvent MYTHIC_SOUND = SoundEvent.of(MYTHIC_SOUND_ID);
    public static SoundEvent GODLY_SOUND = SoundEvent.of(GODLY_SOUND_ID);
    public static SoundEvent HEROIC_SOUND = SoundEvent.of(HEROIC_SOUND_ID);

    @Override
    public void onInitializeClient() {
        loadConfig();

        Registry.register(Registries.SOUND_EVENT, MYTHIC_SOUND_ID, MYTHIC_SOUND);
        Registry.register(Registries.SOUND_EVENT, GODLY_SOUND_ID, GODLY_SOUND);
        Registry.register(Registries.SOUND_EVENT, HEROIC_SOUND_ID, HEROIC_SOUND);

        UniversalGuiMover.HudContainer hudContainer = new UniversalGuiMover.HudContainer(
                10, 10,
                50,
                12,
                7
        );
        UniversalGuiMover.trackHudContainer("mythicTrackerHud", hudContainer);


        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Fishing Hud Toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F13,
                "Island"
        ));


        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("fish")
                            .then(literal("reset")
                                    .executes(context -> {
                                        mythicCount = 0;
                                        godlyCount = 0;
                                        heroicCount = 0;
                                        ArtifactfoundCount = 0;
                                        skullCount = 0;

                                        MinecraftClient client = MinecraftClient.getInstance();
                                        if (client.player != null) {
                                            client.player.sendMessage(Text.literal("§aAll fishing counters have been reset!"), false);
                                        }
                                        return 1;
                                    })
                            ));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isTogglePressed = toggleHudKey.isPressed();
            if (isTogglePressed && !wasToggleKeyPressed) {
                isHudVisible = !isHudVisible;
                saveConfig();
            }
            wasToggleKeyPressed = isTogglePressed;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String msg = message.getString();
            MinecraftClient client = MinecraftClient.getInstance();

            // Skip if the message is from a player (contains "->" or "[Player]")
            boolean isPlayerMessage = msg.contains("->") || msg.matches(".*\\[.*\\].*"); // Checks for brackets like [Player]
            boolean isServerMessage = !isPlayerMessage;

            if (isServerMessage || overlay) { // Allow overlay (action bar) or non-player messages
                if (msg.contains("Mythic Treasure")) {
                    mythicCount++;
                    playSound(client, MYTHIC_SOUND);
                } else if (msg.contains("Godly Treasure")) {
                    godlyCount++;
                    playSound(client, GODLY_SOUND);
                } else if (msg.contains("Heroic Treasure")) {
                    heroicCount++;
                    playSound(client, HEROIC_SOUND);
                } else if (msg.toUpperCase().contains("ARTIFACT FOUND:")) {
                    ArtifactfoundCount++;
                    playSound(client, HEROIC_SOUND);
                } else if (msg.toUpperCase().contains("* SUNKEN GEMS")) {
                    SUNKENGEMSCount++;
                    playSound(client, HEROIC_SOUND);
                } else if (msg.startsWith("+") && msg.contains("Marauder Skull")) {
                    String[] parts = msg.split(" ");
                    try {
                        String numberPart = parts[0].replace("+", "").trim();
                        int amount = Integer.parseInt(numberPart);
                        skullCount += amount;
                        playSound(client, HEROIC_SOUND);
                    } catch (NumberFormatException e) {
                        System.err.println("Failed to parse skull amount from: " + msg);
                    }
                }
            }
        });

        HudRenderCallback.EVENT.register((context, delta) -> {
            if (isHudVisible && isHoldingFishingRod()) {
                drawHud(context);
            }
        });
    }

    private boolean isHoldingFishingRod() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;

        PlayerEntity player = client.player;
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        return mainHand.getItem() instanceof FishingRodItem ||
                offHand.getItem() instanceof FishingRodItem;
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(CONFIG_FILE)) {
                Properties prop = new Properties();
                prop.load(input);
                isHudVisible = Boolean.parseBoolean(prop.getProperty("hudVisible", "true"));
            } catch (IOException ex) {
                System.err.println("Could not load config: " + ex.getMessage());
            }
        }
    }

    private void saveConfig() {
        try {
            new File(CONFIG_FILE).getParentFile().mkdirs();
            try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
                Properties prop = new Properties();
                prop.setProperty("hudVisible", Boolean.toString(isHudVisible));
                prop.store(output, "Mythic Tracker Config");
            }
        } catch (IOException ex) {
            System.err.println("Could not save config: " + ex.getMessage());
        }
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

        context.drawText(client.textRenderer, Text.literal("§bFishing Tracker"), 2, 2, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("§cGodly: " + godlyCount), 2, 14, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("§dHeroic: " + heroicCount), 2, 26, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("§5Mythic: " + mythicCount), 2, 38, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("§bArtifact found: " + ArtifactfoundCount), 2, 50, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("§6SUNKEN GEMS: " + SUNKENGEMSCount), 2, 62, 0xFFFFFF, true);
        context.drawText(client.textRenderer, Text.literal("§6Skulls: " + skullCount), 2, 74, 0xFFFFFF, true);

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