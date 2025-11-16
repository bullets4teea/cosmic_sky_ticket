package chat.cosmic.client.client;

import chat.cosmic.client.client.KeyBinds.KeyBinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChestTrackerMod implements ClientModInitializer {

    private static int mythicCount = 0;
    private static int godlyCount = 0;
    private static int heroicCount = 0;
    private static int artifactCount = 0;
    private static int sunkenGemsCount = 0;
    private static int skullCount = 0;

    private static final Map<String, Integer> chestCounts = new HashMap<>();
    private static final Map<String, Integer> sysChestCounts = new HashMap<>();
    private static final Map<String, Integer> questCounts = new HashMap<>();
    private static final Map<String, Map<String, Integer>> gemSourceCounts = new HashMap<>();

    private static final Map<String, Integer> tierColors = Map.of(
            "Basic", 0xFFFFFF,
            "Elite", 0x54FCFC,
            "Legendary", 0xFFA500,
            "Godly", 0xFF0000,
            "Heroic", 0xFF69B4,
            "Mythic", 0x800080,
            "Tire", 0xC0C0C0
    );

    private static final String[] TIERS = {"Basic", "Elite", "Legendary", "Godly", "Heroic", "Mythic"};
    private static final String[] SYS_TIERS = {"Basic", "Elite", "Legendary", "Godly", "Heroic", "Mythic", "Tire"};
    private static final String[] GEM_SOURCES = {"Attribute", "Gem Finder", "Mining Rush", "Dug Up", "Mystery"};
    private static final String[] GEM_TYPES = {"Minor", "Major", "Perfect"};

    private static final Pattern CHEST_PATTERN = Pattern.compile("\\* (Basic|Elite|Legendary|Godly|Heroic|Mythic) Chest dropped nearby! \\*");
    private static final Pattern SYS_CHEST_PATTERN = Pattern.compile("\\* (Basic|Elite|Legendary|Godly|Heroic|Mythic) Chest dropped nearby!\\s+\\(System Override\\) \\*");
    private static final Pattern TIRE_SYS_CHEST_PATTERN = Pattern.compile("\\* Tire Chest dropped nearby!\\s+\\(System Override\\) \\*");
    private static final Pattern MAX_GEM_PATTERN = Pattern.compile("\\* \\+1 MAX GEM FOUND \\*");
    private static final Pattern QUEST_PATTERN = Pattern.compile(".*Island Quest COMPLETE: (Basic|Elite|Legendary|Godly|Heroic|Mythic).*");
    private static final Pattern PICKAXE_ATTRIBUTE_PATTERN = Pattern.compile(".*attribute found you a (Minor|Major|Perfect) (Diamond|Iron|Stone) Gem.*");
    private static final Pattern GEM_FINDER_PATTERN = Pattern.compile(".*Gem Finder \\(.*\\) found you a (Minor|Major|Perfect) (Diamond|Iron|Stone) Gem.*");
    private static final Pattern DUG_UP_PATTERN = Pattern.compile(".*You've dug up a (Minor|Major|Perfect) (Diamond|Iron|Stone) Gem.*");
    private static final Pattern MINING_RUSH_GEM_PATTERN = Pattern.compile(".*Mining Rush found you a (Minor|Major|Perfect) (Diamond|Iron|Stone) Gem.*");

    private static int maxGemCount = 0;
    private static int minorGemCount = 0;
    private static int majorGemCount = 0;
    private static int perfectGemCount = 0;

    private static boolean hudVisible = true;
    private static final Path CONFIG_PATH = Path.of("config/unifiedtracker_hud.dat");
    private static long startTime = 0;
    private static long pausedTime = 0;
    private static boolean isTimerRunning = false;
    private static boolean needsBoundaryCheck = true;
    private static boolean showSysView = false;
    private static boolean showGemsView = false;
    private static boolean showQuestView = false;
    private static boolean modEnabled = true;
    private static long sysViewEndTime = 0;
    private static long gemsViewEndTime = 0;
    private static long questViewEndTime = 0;

    public static final Identifier MYTHIC_SOUND_ID = new Identifier("mythictracker", "mythic_sound");
    public static final Identifier GODLY_SOUND_ID = new Identifier("mythictracker", "godly_sound");
    public static final Identifier HEROIC_SOUND_ID = new Identifier("mythictracker", "heroic_sound");
    public static SoundEvent MYTHIC_SOUND = SoundEvent.of(MYTHIC_SOUND_ID);
    public static SoundEvent GODLY_SOUND = SoundEvent.of(GODLY_SOUND_ID);
    public static SoundEvent HEROIC_SOUND = SoundEvent.of(HEROIC_SOUND_ID);

    public static void setModEnabled(boolean enabled) {
        modEnabled = enabled;
    }

    public static void setHudEnabled(boolean enabled) {
        hudVisible = enabled;
    }

    private static final UniversalGuiMover.HudContainer hudContainer =
            new UniversalGuiMover.HudContainer(10, 100, 120, 9, 8);

    @Override
    public void onInitializeClient() {
        initializeCounts();
        initializeGemSourceCounts();
        loadHudPosition();
        UniversalGuiMover.trackHudContainer("unifiedTrackerHud", hudContainer);

        Registry.register(Registries.SOUND_EVENT, MYTHIC_SOUND_ID, MYTHIC_SOUND);
        Registry.register(Registries.SOUND_EVENT, GODLY_SOUND_ID, GODLY_SOUND);
        Registry.register(Registries.SOUND_EVENT, HEROIC_SOUND_ID, HEROIC_SOUND);

        ClientTickEvents.END_CLIENT_TICK.register(this::handleClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(this::handleBoundaryCheck);
        HudRenderCallback.EVENT.register(this::renderHud);
        ClientReceiveMessageEvents.GAME.register(this::handleChatMessage);

        registerCommands();
    }

    private void initializeCounts() {
        for (String tier : TIERS) {
            chestCounts.put(tier, 0);
            questCounts.put(tier, 0);
        }
        for (String tier : SYS_TIERS) sysChestCounts.put(tier, 0);
    }

    private void initializeGemSourceCounts() {
        for (String source : GEM_SOURCES) {
            gemSourceCounts.put(source, new HashMap<>());
            for (String type : GEM_TYPES) {
                gemSourceCounts.get(source).put(type, 0);
            }
        }
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("sys")
                    .executes(ctx -> {
                        showSysView = true;
                        showGemsView = false;
                        showQuestView = false;
                        sysViewEndTime = System.currentTimeMillis() + 10000;
                        ctx.getSource().sendFeedback(Text.literal("Showing System Override stats for 10 seconds"));
                        return 1;
                    }));

            dispatcher.register(ClientCommandManager.literal("gems")
                    .executes(ctx -> {
                        showGemsView = true;
                        showSysView = false;
                        showQuestView = false;
                        gemsViewEndTime = System.currentTimeMillis() + 10000;
                        ctx.getSource().sendFeedback(Text.literal("Showing Gem Source stats for 10 seconds"));
                        return 1;
                    }));

            dispatcher.register(ClientCommandManager.literal("trackerreset")
                    .executes(ctx -> {
                        resetAllCounts();
                        resetTimer();
                        ctx.getSource().sendFeedback(Text.literal("Reset all counts and timer!"));
                        return 1;
                    }));

            dispatcher.register(ClientCommandManager.literal("timer")
                    .then(ClientCommandManager.literal("reset")
                            .executes(ctx -> {
                                resetTimer();
                                ctx.getSource().sendFeedback(Text.literal("Timer reset!"));
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("start")
                            .executes(ctx -> {
                                if (!isTimerRunning) startTimer();
                                ctx.getSource().sendFeedback(Text.literal("Timer started!"));
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("pause")
                            .executes(ctx -> {
                                if (isTimerRunning) pauseTimer();
                                ctx.getSource().sendFeedback(Text.literal("Timer paused!"));
                                return 1;
                            })));
        });
    }

    private void handleClientTick(MinecraftClient client) {
        if (KeyBinds.getChestTrackerReset().wasPressed()) {
            resetAllCounts();
            resetTimer();
            sendClientMessage("Reset all counts and timer!");
        }
        if (KeyBinds.getChestTrackerStartPause().wasPressed()) handleTimerToggle();
        if (KeyBinds.getChestTrackerToggleHud().wasPressed()) toggleHudVisibility();

        if (showSysView && System.currentTimeMillis() > sysViewEndTime) showSysView = false;
        if (showGemsView && System.currentTimeMillis() > gemsViewEndTime) showGemsView = false;
    }

    private void handleBoundaryCheck(MinecraftClient client) {
        if (needsBoundaryCheck && client.getWindow() != null) {
            Window window = client.getWindow();
            hudContainer.x = clampHudX(hudContainer.x, window);
            hudContainer.y = clampHudY(hudContainer.y, window);
            needsBoundaryCheck = false;
            saveHudPosition();
        }
    }

    private int clampHudX(int x, Window window) {
        return Math.max(5, Math.min(x, window.getScaledWidth() - hudContainer.getScaledWidth() - 5));
    }

    private int clampHudY(int y, Window window) {
        return Math.max(5, Math.min(y, window.getScaledHeight() - hudContainer.getScaledHeight() - 5));
    }

    private void handleChatMessage(Text message, boolean overlay) {
        String msg = message.getString();
        MinecraftClient client = MinecraftClient.getInstance();

        boolean isPlayerMessage = msg.contains("->") || msg.matches(".*\\[.*\\].*");
        boolean isServerMessage = !isPlayerMessage;

        if (isServerMessage || overlay) {
            String clean = msg.replaceAll("§[0-9a-fk-or]", "").trim();

            if (clean.contains("Mythic Treasure")) {
                mythicCount++;
                playSound(client, MYTHIC_SOUND);
            } else if (clean.contains("Godly Treasure")) {
                godlyCount++;
                playSound(client, GODLY_SOUND);
            } else if (clean.contains("Heroic Treasure")) {
                heroicCount++;
                playSound(client, HEROIC_SOUND);
            } else if (clean.toUpperCase().contains("ARTIFACT FOUND:")) {
                artifactCount++;
                playSound(client, HEROIC_SOUND);
            } else if (clean.toUpperCase().contains("* SUNKEN GEMS")) {
                sunkenGemsCount++;
                playSound(client, HEROIC_SOUND);
            } else if (clean.startsWith("+") && clean.contains("Marauder Skull")) {
                String[] parts = clean.split(" ");
                try {
                    String numberPart = parts[0].replace("+", "").trim();
                    int amount = Integer.parseInt(numberPart);
                    skullCount += amount;
                    playSound(client, HEROIC_SOUND);
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse skull amount from: " + msg);
                }
            }

            Matcher chestMatcher = CHEST_PATTERN.matcher(clean);
            Matcher sysMatcher = SYS_CHEST_PATTERN.matcher(clean);
            Matcher tireSysMatcher = TIRE_SYS_CHEST_PATTERN.matcher(clean);
            Matcher maxGemMatcher = MAX_GEM_PATTERN.matcher(clean);
            Matcher questMatcher = QUEST_PATTERN.matcher(clean);
            Matcher pickaxeAttributeMatcher = PICKAXE_ATTRIBUTE_PATTERN.matcher(clean);
            Matcher gemFinderMatcher = GEM_FINDER_PATTERN.matcher(clean);
            Matcher dugUpMatcher = DUG_UP_PATTERN.matcher(clean);
            Matcher miningRushMatcher = MINING_RUSH_GEM_PATTERN.matcher(clean);

            if (chestMatcher.matches()) {
                String tier = chestMatcher.group(1);
                chestCounts.put(tier, chestCounts.getOrDefault(tier, 0) + 1);
            } else if (sysMatcher.matches()) {
                String tier = sysMatcher.group(1);
                sysChestCounts.put(tier, sysChestCounts.getOrDefault(tier, 0) + 1);
            } else if (tireSysMatcher.matches()) {
                sysChestCounts.put("Tire", sysChestCounts.getOrDefault("Tire", 0) + 1);
            } else if (questMatcher.matches() && isInSkyblockWorld()) {
                String tier = questMatcher.group(1);
                questCounts.put(tier, questCounts.getOrDefault(tier, 0) + 1);
            } else if (maxGemMatcher.matches() && isInSkyblockWorld() && isHoldingPickaxe()) {
                maxGemCount++;
            } else if (isInSkyblockWorld() && isHoldingPickaxe()) {
                if (pickaxeAttributeMatcher.matches()) {
                    String gemType = pickaxeAttributeMatcher.group(1);
                    addGemToSource("Attribute", gemType);
                    updateGemCounts(gemType);
                } else if (gemFinderMatcher.matches()) {
                    String gemType = gemFinderMatcher.group(1);
                    addGemToSource("Gem Finder", gemType);
                    updateGemCounts(gemType);
                } else if (dugUpMatcher.matches()) {
                    String gemType = dugUpMatcher.group(1);
                    addGemToSource("Dug Up", gemType);
                    updateGemCounts(gemType);
                } else if (miningRushMatcher.matches()) {
                    String gemType = miningRushMatcher.group(1);
                    addGemToSource("Mining Rush", gemType);
                    updateGemCounts(gemType);
                }
            }
        }
    }

    private void addGemToSource(String source, String type) {
        Map<String, Integer> sourceMap = gemSourceCounts.get(source);
        if (sourceMap != null) {
            sourceMap.put(type, sourceMap.getOrDefault(type, 0) + 1);
        }
    }

    private void updateGemCounts(String gemType) {
        switch (gemType) {
            case "Minor" -> minorGemCount++;
            case "Major" -> majorGemCount++;
            case "Perfect" -> perfectGemCount++;
        }
    }

    private void renderHud(DrawContext context, float tickDelta) {
        if (!modEnabled || !hudVisible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        UniversalGuiMover.HudContainer container = UniversalGuiMover.getHudContainer("unifiedTrackerHud");

        if (client == null || window == null || container == null) return;

        hudContainer.x = container.x;
        hudContainer.y = container.y;

        if (UniversalGuiMover.isDragging()) needsBoundaryCheck = true;
        else if (needsBoundaryCheck) {
            hudContainer.x = clampHudX(hudContainer.x, window);
            hudContainer.y = clampHudY(hudContainer.y, window);
            saveHudPosition();
            needsBoundaryCheck = false;
        }

        float scale = UniversalGuiMover.getGlobalTextScale();
        context.getMatrices().push();
        context.getMatrices().translate(hudContainer.x, hudContainer.y, 0);
        context.getMatrices().scale(scale, scale, 1);

        TextRenderer renderer = client.textRenderer;
        int yPos = 2;
        final int padding = 2;

        context.drawTextWithShadow(renderer, "§bTime: " + getElapsedTime(), 2, yPos, 0xFFFFFF);
        yPos += renderer.fontHeight + (int)(padding / scale);

        if (isHoldingFishingRod()) {
            context.drawTextWithShadow(renderer, "§b=== Fishing Tracker ===", 2, yPos, 0xFFFFFF);
            yPos += renderer.fontHeight + (int)(padding / scale);
            context.drawTextWithShadow(renderer, "§cGodly: " + godlyCount, 2, yPos, 0xFFFFFF);
            yPos += renderer.fontHeight + (int)(padding / scale);
            context.drawTextWithShadow(renderer, "§dHeroic: " + heroicCount, 2, yPos, 0xFFFFFF);
            yPos += renderer.fontHeight + (int)(padding / scale);
            context.drawTextWithShadow(renderer, "§5Mythic: " + mythicCount, 2, yPos, 0xFFFFFF);
            yPos += renderer.fontHeight + (int)(padding / scale);
            context.drawTextWithShadow(renderer, "§bArtifact: " + artifactCount, 2, yPos, 0xFFFFFF);
            yPos += renderer.fontHeight + (int)(padding / scale);
            context.drawTextWithShadow(renderer, "§6Gems: " + sunkenGemsCount, 2, yPos, 0xFFFFFF);
            yPos += renderer.fontHeight + (int)(padding / scale);
            context.drawTextWithShadow(renderer, "§6Skulls: " + skullCount, 2, yPos, 0xFFFFFF);
        } else if (showGemsView) {
            context.drawTextWithShadow(renderer, "=== Gem Sources ===", 2, yPos, 0xFFD700);
            yPos += renderer.fontHeight + (int)(padding / scale);
            for (String source : GEM_SOURCES) {
                Map<String, Integer> sourceMap = gemSourceCounts.get(source);
                int total = sourceMap.values().stream().mapToInt(Integer::intValue).sum();
                if (total > 0) {
                    context.drawTextWithShadow(renderer, source + ": " + total, 2, yPos, 0xFFFFFF);
                    yPos += renderer.fontHeight + (int)(padding / scale);
                }
            }
        } else if (showSysView) {
            for (String tier : SYS_TIERS) {
                int count = sysChestCounts.get(tier);
                if (count > 0) {
                    context.drawTextWithShadow(renderer, tier + " (Sys): " + count, 2, yPos, tierColors.getOrDefault(tier, 0xFFFFFF));
                    yPos += renderer.fontHeight + (int)(padding / scale);
                }
            }
        } else if (isInSkyblockWorld() && isHoldingQuestTool()) {

            for (String tier : TIERS) {
                int count = questCounts.get(tier);
                context.drawTextWithShadow(renderer, tier + " Quest: " + count, 2, yPos, tierColors.getOrDefault(tier, 0xFFFFFF));
                yPos += renderer.fontHeight + (int)(padding / scale);
            }

            if (isHoldingPickaxe()) {
                yPos += (int)(padding / scale);
                context.drawTextWithShadow(renderer, "=== Gems ===", 2, yPos, 0xFFD700);
                yPos += renderer.fontHeight + (int)(padding / scale);
                context.drawTextWithShadow(renderer, "Minor: " + minorGemCount, 2, yPos, 0xFFFFFF);
                yPos += renderer.fontHeight + (int)(padding / scale);
                context.drawTextWithShadow(renderer, "Major: " + majorGemCount, 2, yPos, 0x54FCFC);
                yPos += renderer.fontHeight + (int)(padding / scale);
                context.drawTextWithShadow(renderer, "Perfect: " + perfectGemCount, 2, yPos, 0xFFA500);
                yPos += renderer.fontHeight + (int)(padding / scale);
                context.drawTextWithShadow(renderer, "Max: " + maxGemCount, 2, yPos, 0x800080);
            }
        } else if (isInSkyblockWorld()) {
            if (isHoldingPickaxe()) {
                context.drawTextWithShadow(renderer, "=== Gems ===", 2, yPos, 0xFFD700);
                yPos += renderer.fontHeight + (int)(padding / scale);
                context.drawTextWithShadow(renderer, "Minor: " + minorGemCount, 2, yPos, 0xFFFFFF);
                yPos += renderer.fontHeight + (int)(padding / scale);
                context.drawTextWithShadow(renderer, "Major: " + majorGemCount, 2, yPos, 0x54FCFC);
                yPos += renderer.fontHeight + (int)(padding / scale);
                context.drawTextWithShadow(renderer, "Perfect: " + perfectGemCount, 2, yPos, 0xFFA500);
                yPos += renderer.fontHeight + (int)(padding / scale);
                context.drawTextWithShadow(renderer, "Max: " + maxGemCount, 2, yPos, 0x800080);
            }
        } else if (isInTrackedDimension()) {
            for (String tier : TIERS) {
                context.drawTextWithShadow(renderer, tier + ": " + chestCounts.get(tier), 2, yPos, tierColors.getOrDefault(tier, 0xFFFFFF));
                yPos += renderer.fontHeight + (int)(padding / scale);
            }
        }

        context.getMatrices().pop();
    }

    private boolean isHoldingFishingRod() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        PlayerEntity player = client.player;
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        return mainHand.getItem() instanceof FishingRodItem || offHand.getItem() instanceof FishingRodItem;
    }

    private boolean isHoldingPickaxe() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        ItemStack heldItem = client.player.getMainHandStack();
        String itemName = heldItem.getItem().toString().toLowerCase();
        return itemName.contains("pickaxe");
    }

    private boolean isHoldingAxe() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        ItemStack heldItem = client.player.getMainHandStack();
        String itemName = heldItem.getItem().toString().toLowerCase();
        return itemName.contains("axe");
    }

    private boolean isHoldingSword() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        ItemStack heldItem = client.player.getMainHandStack();
        String itemName = heldItem.getItem().toString().toLowerCase();
        return itemName.contains("sword");
    }

    private boolean isHoldingHoe() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        ItemStack heldItem = client.player.getMainHandStack();
        String itemName = heldItem.getItem().toString().toLowerCase();
        return itemName.contains("hoe");
    }

    private boolean isHoldingQuestTool() {
        return isHoldingPickaxe() || isHoldingAxe() || isHoldingSword() || isHoldingHoe();
    }

    private boolean isInTrackedDimension() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        String dimension = client.world.getRegistryKey().getValue().toString();
        return dimension.contains("minecraft:adventure") || dimension.contains("minecraft:the_end");
    }

    private boolean isInSkyblockWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        String dimension = client.world.getRegistryKey().getValue().toString();
        return dimension.contains("skyblock_world") || dimension.contains("minecraft:the_nether");
    }

    private void handleTimerToggle() {
        if (isTimerRunning) pauseTimer();
        else startTimer();
        sendClientMessage("Timer " + (isTimerRunning ? "started" : "paused"));
    }

    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis() - pausedTime;
            isTimerRunning = true;
        }
    }

    private void pauseTimer() {
        if (isTimerRunning) {
            pausedTime = System.currentTimeMillis() - startTime;
            isTimerRunning = false;
        }
    }

    private void resetTimer() {
        startTime = 0;
        pausedTime = 0;
        isTimerRunning = false;
    }

    private String getElapsedTime() {
        long elapsedMillis = isTimerRunning ? System.currentTimeMillis() - startTime : pausedTime;
        long seconds = elapsedMillis / 1000;
        return String.format("%02d:%02d:%02d", (seconds / 3600) % 24, (seconds / 60) % 60, seconds % 60);
    }

    private void resetAllCounts() {
        mythicCount = 0;
        godlyCount = 0;
        heroicCount = 0;
        artifactCount = 0;
        sunkenGemsCount = 0;
        skullCount = 0;

        for (String tier : TIERS) {
            chestCounts.put(tier, 0);
            questCounts.put(tier, 0);
        }
        for (String tier : SYS_TIERS) sysChestCounts.put(tier, 0);

        maxGemCount = 0;
        minorGemCount = 0;
        majorGemCount = 0;
        perfectGemCount = 0;

        for (String source : GEM_SOURCES) {
            for (String type : GEM_TYPES) {
                gemSourceCounts.get(source).put(type, 0);
            }
        }
    }

    private void toggleHudVisibility() {
        hudVisible = !hudVisible;
        sendClientMessage("HUD " + (hudVisible ? "enabled" : "disabled"));
    }

    private void sendClientMessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
        }
    }

    private void playSound(MinecraftClient client, SoundEvent sound) {
        if (client.player != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0f, 1.0f));
        }
    }

    private void saveHudPosition() {
        try {
            net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
            nbt.putInt("hudX", hudContainer.x);
            nbt.putInt("hudY", hudContainer.y);
            Files.createDirectories(CONFIG_PATH.getParent());
            net.minecraft.nbt.NbtIo.write(nbt, CONFIG_PATH);
        } catch (IOException ignored) {}
    }

    private void loadHudPosition() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.NbtIo.read(CONFIG_PATH);
                hudContainer.x = nbt.getInt("hudX");
                hudContainer.y = nbt.getInt("hudY");
            }
        } catch (IOException ignored) {}
    }
}