package chat.cosmic.client.client.playerheadcooldown;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.*;

public class ActivePetEffectsHud {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final float TEXT_SCALE = 0.75f;

    // Store active effects and their end times
    private static final Map<String, ActiveEffect> activeEffects = new HashMap<>();

    // Effect durations by pet level (in milliseconds)
    private static final Map<String, Map<Integer, Long>> effectDurations = new HashMap<>();

    // Pet effect descriptions
    private static final Map<String, String> petEffects = new HashMap<>();

    static {
        // Initialize effect descriptions
        petEffects.put("Battle Pig Pet", "Damage Boost");
        petEffects.put("Miner Matt Pet", "Mine Area 3x3x3 of nodes ");
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

        // Initialize effect durations by level (in milliseconds)
        initializeEffectDurations();
    }

    private static void initializeEffectDurations() {
        // Battle Pig Pet - level 1: 10min, level 10: 20min
        Map<Integer, Long> battlePigDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            battlePigDurations.put(i, (10L + (i - 1)) * 60 * 1000); // 10min + (level-1) min
        }
        effectDurations.put("Battle Pig Pet", battlePigDurations);

        // Miner Matt Pet - level 1: 20s, level 10: 1m50s
        Map<Integer, Long> minerMattDurations = new HashMap<>();
        minerMattDurations.put(1, 20L * 1000);
        minerMattDurations.put(2, 30L * 1000);
        minerMattDurations.put(3, 40L * 1000);
        minerMattDurations.put(4, 50L * 1000);
        minerMattDurations.put(5, 60L * 1000);
        minerMattDurations.put(6, 70L * 1000);
        minerMattDurations.put(7, 80L * 1000);
        minerMattDurations.put(8, 90L * 1000);
        minerMattDurations.put(9, 100L * 1000);
        minerMattDurations.put(10, 110L * 1000);
        effectDurations.put("Miner Matt Pet", minerMattDurations);

        // Slayer Sam Pet - level 1: 50s, level 10: 2m20s
        Map<Integer, Long> slayerSamDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            slayerSamDurations.put(i, (50L + (i - 1) * 10) * 1000);
        }
        effectDurations.put("Slayer Sam Pet", slayerSamDurations);

        // Chaos Cow Pet - same as Battle Pig
        effectDurations.put("Chaos Cow Pet", battlePigDurations);

        // Blacksmith Brandon Pet - 20 minutes fixed
        Map<Integer, Long> blacksmithDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            blacksmithDurations.put(i, 20L * 60 * 1000);
        }
        effectDurations.put("Blacksmith Brandon Pet", blacksmithDurations);

        // Fisherman Fred Pet - level 1: 2m40s, level 10: 17m40s
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

        // Alchemist Alex Pet - 2 minutes fixed
        Map<Integer, Long> alchemistDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            alchemistDurations.put(i, 2L * 60 * 1000);
        }
        effectDurations.put("Alchemist Alex Pet", alchemistDurations);

        // Blood Sheep Pet - same as Battle Pig
        effectDurations.put("Blood Sheep Pet", battlePigDurations);

        // Merchant Pet - 15 minutes fixed
        Map<Integer, Long> merchantDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            merchantDurations.put(i, 15L * 60 * 1000);
        }
        effectDurations.put("Merchant Pet", merchantDurations);

        // Dire Wolf Pet - level 1: 25s, level 10: 1m10s
        Map<Integer, Long> direWolfDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            direWolfDurations.put(i, (25L + (i - 1) * 5) * 1000);
        }
        effectDurations.put("Dire Wolf Pet", direWolfDurations);

        // Void Chicken Pet - same as Battle Pig
        effectDurations.put("Void Chicken Pet", battlePigDurations);

        // Loot Llama Pet - 24 hours fixed
        Map<Integer, Long> lootLlamaDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            lootLlamaDurations.put(i, 24L * 60 * 60 * 1000);
        }
        effectDurations.put("Loot Llama Pet", lootLlamaDurations);

        // Barry Bee Pet - level 1: 11m, level 2: 12m, etc. up to level 10: 20m
        Map<Integer, Long> barrybeeDurations = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            barrybeeDurations.put(i, (10L + i) * 60 * 1000); // 10 + level = duration in minutes
        }
        effectDurations.put("Barry Bee Pet", barrybeeDurations);
    }

    public static void activatePetEffect(String petName, int level) {
        long duration = getEffectDuration(petName, level);
        if (duration > 0) {
            activeEffects.put(petName, new ActiveEffect(petName, level, System.currentTimeMillis() + duration));
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        if (client.player == null || client.options.hudHidden) return;

        // Remove expired effects
        long currentTime = System.currentTimeMillis();
        activeEffects.entrySet().removeIf(entry -> currentTime >= entry.getValue().endTime);

        if (activeEffects.isEmpty()) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int startX = screenWidth - 140; // Right side of screen
        int startY = 30; // Below coordinates/chat

        int entryHeight = 40; // Height per effect entry
        List<ActiveEffect> effects = new ArrayList<>(activeEffects.values());

        // Sort by remaining time (soonest to expire first)
        effects.sort(Comparator.comparingLong(e -> e.endTime));

        for (int i = 0; i < effects.size(); i++) {
            ActiveEffect effect = effects.get(i);
            renderEffectEntry(context, effect, startX, startY + (i * entryHeight), currentTime);
        }
    }

    private static void renderEffectEntry(DrawContext context, ActiveEffect effect, int x, int y, long currentTime) {
        MatrixStack matrices = context.getMatrices();

        // Background
        context.fill(x, y, x + 130, y + 38, 0x80000000);

        matrices.push();

        // Render pet icon
        renderPetIcon(context, effect.petName, x + 2, y + 2);

        // Render pet name and level
        matrices.push();
        matrices.scale(TEXT_SCALE, TEXT_SCALE, 1f);
        context.drawText(client.textRenderer,
                Text.literal(effect.petName + " [Lvl " + effect.level + "]"),
                (int)((x + 25) / TEXT_SCALE),
                (int)((y + 3) / TEXT_SCALE),
                0xFFFFFF,
                false);
        matrices.pop();

        // Render effect description
        String effectDesc = petEffects.getOrDefault(effect.petName, "Active");
        matrices.push();
        matrices.scale(TEXT_SCALE * 0.8f, TEXT_SCALE * 0.8f, 1f);
        context.drawText(client.textRenderer,
                Text.literal(effectDesc),
                (int)((x + 25) / (TEXT_SCALE * 0.8f)),
                (int)((y + 15) / (TEXT_SCALE * 0.8f)),
                0xCCCCCC,
                false);
        matrices.pop();

        // Render remaining effect time
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
                false);
        matrices.pop();

        matrices.pop();
    }

    private static void renderPetIcon(DrawContext context, String petName, int x, int y) {
        // Placeholder icon rendering
        context.fill(x, y, x + 20, y + 20, 0x80FFFFFF);
        context.drawText(client.textRenderer,
                Text.literal("P"),
                x + 6,
                y + 6,
                0xFFFFFF,
                false);

        //
        // context.drawTexture(new Identifier("playerheadcooldown", "textures/pets/" + getPetTextureName(petName) + ".png"), x, y, 0, 0, 20, 20, 20, 20);
    }

    private static String getPetTextureName(String petName) {
        return petName.toLowerCase().replace(" ", "_").replace(" pet", "");
    }

    private static long getEffectDuration(String petName, int level) {
        Map<Integer, Long> durations = effectDurations.get(petName);
        if (durations != null) {
            return durations.getOrDefault(level, 0L);
        }
        return 0L;
    }

    private static int getTimeColor(long remainingMs) {
        if (remainingMs < 30000) return 0xFF5555; // Red when under 30 seconds
        if (remainingMs < 120000) return 0xFFFF55; // Yellow when under 2 minutes
        return 0x55FF55; // Green otherwise
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
        // Extract level from item name or NBT
        if (stack.hasCustomName()) {
            String name = stack.getName().getString();
            // Look for [LVL XX] pattern
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[LVL (\\d+)\\]");
            java.util.regex.Matcher matcher = pattern.matcher(name);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 1; // Default to level 1 if not found
    }

    private static class ActiveEffect {
        public final String petName;
        public final int level;
        public final long endTime;

        public ActiveEffect(String petName, int level, long endTime) {
            this.petName = petName;
            this.level = level;
            this.endTime = endTime;
        }
    }
}