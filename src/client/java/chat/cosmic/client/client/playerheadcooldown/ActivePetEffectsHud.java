package chat.cosmic.client.client.playerheadcooldown;

import chat.cosmic.client.client.SettingsManager;
import chat.cosmic.client.client.UniversalGuiMover;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.*;

public class ActivePetEffectsHud {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final float TEXT_SCALE = 0.75f;


    private static UniversalGuiMover.HudContainer petHudContainer = null;


    private static final Map<String, ActiveEffect> activeEffects = new HashMap<>();


    private static final Map<String, Map<Integer, Long>> effectDurations = new HashMap<>();


    private static final Map<String, String> petEffects = new HashMap<>();

    static {

        petEffects.put("Battle Pig Pet", "Damage Boost");
        petEffects.put("Miner Matt Pet", "Multiply Mining Island Quest progress by 2x");
        petEffects.put("Slayer Sam Pet", "Increase Mob drops");
        petEffects.put("Chaos Cow Pet", "Damage Boost");
        petEffects.put("Blacksmith Brandon Pet", "Better Gear Repairs");
        petEffects.put("Fisherman Fred Pet", "Increase artifact find chance");
        petEffects.put("Alchemist Alex Pet", "Potion Enhancements");
        petEffects.put("Blood Sheep Pet", "Damage Boost");
        petEffects.put("Merchant Pet", "Better Trading Prices");
        petEffects.put("Dire Wolf Pet", "kill mob faster");
        petEffects.put("Void Chicken Pet", "Damage Boost");
        petEffects.put("Loot Llama Pet", "Extra Loot Drops");
        petEffects.put("Barry Bee Pet", "Bee hives in a 15 area will gain high chance for workers to gather an empty honeycomb");
        petEffects.put("Farmer Bob Pet", "Multiply Farming Island Quest progress by 2x"); // Added Farmer Bob Pet


        initializeEffectDurations();
    }

    private static void initializeEffectDurations() {

        Map<Integer, Long> battlePigDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            battlePigDurations.put(i, (10L + (i - 1)) * 60 * 1000);
        }
        effectDurations.put("Battle Pig Pet", battlePigDurations);


        Map<Integer, Long> minerMattDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            minerMattDurations.put(i, (25L + (i - 1) * 5) * 1000);
        }
        effectDurations.put("Miner Matt Pet", minerMattDurations);


        Map<Integer, Long> slayerSamDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            slayerSamDurations.put(i, (50L + (i - 1) * 10) * 1000);
        }
        effectDurations.put("Slayer Sam Pet", slayerSamDurations);


        effectDurations.put("Chaos Cow Pet", battlePigDurations);


        Map<Integer, Long> blacksmithDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            blacksmithDurations.put(i, 20L * 60 * 1000);
        }
        effectDurations.put("Blacksmith Brandon Pet", blacksmithDurations);


        Map<Integer, Long> fishermanDurations = new HashMap<>();
        fishermanDurations.put(1, (2L * 60 + 40) * 1000);
        fishermanDurations.put(2, (4L * 60 + 20) * 1000);
        fishermanDurations.put(3, 6L * 60 * 1000);
        fishermanDurations.put(4, (7L * 60 + 40) * 1000);
        fishermanDurations.put(5, (9L * 60 + 20) * 1000);
        fishermanDurations.put(6, 11L * 60 * 1000);
        fishermanDurations.put(7, (12L * 60 + 40) * 1000);
        fishermanDurations.put(8, (14L * 60 + 20) * 1000);
        fishermanDurations.put(9, 16L * 60 * 1000);
        fishermanDurations.put(10, (17L * 60 + 40) * 1000);
        effectDurations.put("Fisherman Fred Pet", fishermanDurations);


        Map<Integer, Long> alchemistDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            alchemistDurations.put(i, 2L * 60 * 1000);
        }
        effectDurations.put("Alchemist Alex Pet", alchemistDurations);


        effectDurations.put("Blood Sheep Pet", battlePigDurations);


        Map<Integer, Long> merchantDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            merchantDurations.put(i, 15L * 60 * 1000);
        }
        effectDurations.put("Merchant Pet", merchantDurations);


        Map<Integer, Long> direWolfDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            direWolfDurations.put(i, (25L + (i - 1) * 5) * 1000);
        }
        effectDurations.put("Dire Wolf Pet", direWolfDurations);


        effectDurations.put("Void Chicken Pet", battlePigDurations);


        Map<Integer, Long> lootLlamaDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            lootLlamaDurations.put(i, 24L * 60 * 60 * 1000);
        }
        effectDurations.put("Loot Llama Pet", lootLlamaDurations);


        Map<Integer, Long> barrybeeDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            barrybeeDurations.put(i, (10L + i) * 60 * 1000);
        }
        effectDurations.put("Barry Bee Pet", barrybeeDurations);


        Map<Integer, Long> farmerBobDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            farmerBobDurations.put(i, (25L + (i - 1) * 5) * 1000);
        }
        effectDurations.put("Farmer Bob Pet", farmerBobDurations);
    }

    public static void activatePetEffect(String petName, int level, ItemStack petStack) {
        long duration = getEffectDuration(petName, level);
        if (duration > 0) {
            activeEffects.put(petName, new ActiveEffect(petName, level, System.currentTimeMillis() + duration, petStack.copy()));
        }
    }

    public static boolean isPetTypeActive(String petName) {
        long currentTime = System.currentTimeMillis();
        ActiveEffect effect = activeEffects.get(petName);
        return effect != null && currentTime < effect.endTime;
    }

    public static void initializePetHud() {
        if (petHudContainer == null && client != null && client.getWindow() != null) {
            Window window = client.getWindow();
            int defaultX = window.getScaledWidth() - 140;
            int defaultY = 30;

            petHudContainer = new UniversalGuiMover.HudContainer(defaultX, defaultY, 130, 40, 5);
            UniversalGuiMover.trackHudContainer("pet_hud", petHudContainer);
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        if (client.player == null || client.options.hudHidden) return;


        initializePetHud();


        if (petHudContainer == null) return;


        long currentTime = System.currentTimeMillis();
        activeEffects.entrySet().removeIf(entry -> currentTime >= entry.getValue().endTime);

        if (activeEffects.isEmpty()) return;


        int startX = petHudContainer.x;
        int startY = petHudContainer.y;

        int entryHeight = 40;
        List<ActiveEffect> effects = new ArrayList<>(activeEffects.values());


        effects.removeIf(effect -> !isPetEnabled(effect.petName));

        if (effects.isEmpty()) return;


        effects.sort(Comparator.comparingLong(e -> e.endTime));

        for (int i = 0; i < effects.size(); i++) {
            ActiveEffect effect = effects.get(i);
            renderEffectEntry(context, effect, startX, startY + (i * entryHeight), currentTime);
        }
    }

    private static boolean isPetEnabled(String petName) {

        String settingKey = "Pet " + petName;
        return SettingsManager.getPetToggleSettings().getOrDefault(settingKey, true);
    }

    private static void renderEffectEntry(DrawContext context, ActiveEffect effect, int x, int y, long currentTime) {
        MatrixStack matrices = context.getMatrices();



        matrices.push();


        renderPetIcon(context, effect.petStack, x + 2, y + 2);


        matrices.push();
        matrices.scale(TEXT_SCALE, TEXT_SCALE, 1f);
        context.drawText(client.textRenderer,
                Text.literal(effect.petName + " [Lvl " + effect.level + "]"),
                (int)((x + 25) / TEXT_SCALE),
                (int)((y + 3) / TEXT_SCALE),
                0xFFFFFF,
                true);  // Shadow enabled
        matrices.pop();


        String effectDesc = petEffects.getOrDefault(effect.petName, "Active");
        matrices.push();
        matrices.scale(TEXT_SCALE * 0.8f, TEXT_SCALE * 0.8f, 1f);
        context.drawText(client.textRenderer,
                Text.literal(effectDesc),
                (int)((x + 25) / (TEXT_SCALE * 0.8f)),
                (int)((y + 15) / (TEXT_SCALE * 0.8f)),
                0xCCCCCC,
                true);
        matrices.pop();


        long remaining = effect.endTime - currentTime;
        String timeText = formatTime(remaining);
        matrices.push();
        matrices.scale(TEXT_SCALE, TEXT_SCALE, 1f);
        int timeColor = getTimeColor(remaining);
        context.drawText(client.textRenderer,
                Text.literal(timeText),
                (int)((x + 25) / TEXT_SCALE),
                (int)((y + 25) / TEXT_SCALE),
                timeColor,
                true);
        matrices.pop();

        matrices.pop();
    }

    private static void renderPetIcon(DrawContext context, ItemStack petStack, int x, int y) {
        // Render the actual pet ItemStack (shows the player head texture with SkullOwner)
        if (petStack != null && !petStack.isEmpty()) {
            context.drawItem(petStack, x, y);
        } else {

            context.drawText(client.textRenderer,
                    Text.literal("P"),
                    x + 6,
                    y + 6,
                    0xFFFFFF,
                    true);
        }
    }

    private static long getEffectDuration(String petName, int level) {
        Map<Integer, Long> durations = effectDurations.get(petName);
        if (durations != null) {
            return durations.getOrDefault(level, 0L);
        }
        return 0L;
    }

    private static int getTimeColor(long remainingMs) {
        if (remainingMs < 30000) return 0xFF5555;
        if (remainingMs < 120000) return 0xFFFF55;
        return 0x55FF55;
    }

    private static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        long hours = minutes / 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public static int getPetLevel(ItemStack stack) {

        if (stack.hasCustomName()) {
            String name = stack.getName().getString();

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[LVL (\\d+)\\]");
            java.util.regex.Matcher matcher = pattern.matcher(name);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }

        // Also check NBT for level
        if (stack.hasNbt()) {
            NbtCompound nbt = stack.getNbt();
            if (nbt.contains("level")) {
                return nbt.getInt("level");
            }
        }

        return 1; // Default to level 1 if not found
    }

    private static class ActiveEffect {
        public final String petName;
        public final int level;
        public final long endTime;
        public final ItemStack petStack;  // Store the actual pet item

        public ActiveEffect(String petName, int level, long endTime, ItemStack petStack) {
            this.petName = petName;
            this.level = level;
            this.endTime = endTime;
            this.petStack = petStack;
        }
    }
}