package chat.cosmic.client.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import chat.cosmic.client.client.KeyBinds.KeyBinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class MythicTrackerMod implements ClientModInitializer {

    private static int mythicCount = 0;
    private static int godlyCount = 0;
    private static int heroicCount = 0;
    private static int ArtifactfoundCount = 0;
    private static int SUNKENGEMSCount = 0;
    private static int skullCount = 0;
    public static boolean isHudVisible = true;
    private static boolean wasToggleKeyPressed = false;
    private static final String CONFIG_FILE = "config/cosmic_trackers.properties";

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
            boolean isTogglePressed = KeyBinds.getToggleFishingHud().isPressed();
            if (isTogglePressed && !wasToggleKeyPressed) {
                isHudVisible = !isHudVisible;
                SettingsManager.getToggleSettings().put("Mythic Fishing HUD", isHudVisible);
                SettingsManager.saveSettings();
                saveConfig();
            }
            wasToggleKeyPressed = isTogglePressed;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String msg = message.getString();
            MinecraftClient client = MinecraftClient.getInstance();

            boolean isPlayerMessage = msg.contains("->") || msg.matches(".*\\[.*\\].*");
            boolean isServerMessage = !isPlayerMessage;

            if (isServerMessage || overlay) {
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
                isHudVisible = Boolean.parseBoolean(prop.getProperty("mythic.hudVisible", "true"));
                mythicCount = Integer.parseInt(prop.getProperty("mythic.count", "0"));
                godlyCount = Integer.parseInt(prop.getProperty("godly.count", "0"));
                heroicCount = Integer.parseInt(prop.getProperty("heroic.count", "0"));
                ArtifactfoundCount = Integer.parseInt(prop.getProperty("artifact.count", "0"));
                SUNKENGEMSCount = Integer.parseInt(prop.getProperty("sunkengems.count", "0"));
                skullCount = Integer.parseInt(prop.getProperty("skull.count", "0"));
            } catch (IOException ex) {
                System.err.println("Could not load config: " + ex.getMessage());
            }
        }
    }

    private void saveConfig() {
        try {
            new File(CONFIG_FILE).getParentFile().mkdirs();
            Properties prop = new Properties();
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                try (InputStream input = new FileInputStream(CONFIG_FILE)) {
                    prop.load(input);
                }
            }
            prop.setProperty("mythic.hudVisible", Boolean.toString(isHudVisible));
            prop.setProperty("mythic.count", Integer.toString(mythicCount));
            prop.setProperty("godly.count", Integer.toString(godlyCount));
            prop.setProperty("heroic.count", Integer.toString(heroicCount));
            prop.setProperty("artifact.count", Integer.toString(ArtifactfoundCount));
            prop.setProperty("sunkengems.count", Integer.toString(SUNKENGEMSCount));
            prop.setProperty("skull.count", Integer.toString(skullCount));
            try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
                prop.store(output, "Cosmic Trackers Configuration");
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