// XPBoosterMod.java
package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XPBoosterMod implements ClientModInitializer {
    private static final Map<String, Integer> boosters = new HashMap<>();
    private static int tickCounter = 0;
    private static boolean modEnabled = true;
    private static boolean colorAlertsEnabled = true;

    private static final Identifier XP_BOTTLE_TEXTURE = new Identifier("xpbooster", "textures/gui/xp_bottle.png");
    private static final Identifier ISLAND_BOOSTER_TEXTURE = new Identifier("xpbooster", "textures/gui/island_booster.png");
    private static final Identifier FEED_COOLDOWN_TEXTURE = new Identifier("xpbooster", "textures/gui/feed_cooldown.png");
    private static final Identifier FIX_COOLDOWN_TEXTURE = new Identifier("xpbooster", "textures/gui/fix_cooldown.png");
    private static final Identifier NEAR_COOLDOWN_TEXTURE = new Identifier("xpbooster", "textures/gui/near_cooldown.png");
    private static final Identifier ENDER_PEARL_TEXTURE = new Identifier("xpbooster", "textures/gui/ender.png");
    private static final Identifier HEAL_COOLDOWN_TEXTURE = new Identifier("xpbooster", "textures/gui/heal_cooldown.png");

    private static final int ICON_SIZE = 16;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int Y_OFFSET = 10;
    private static final float NAME_SCALE = 0.7f;
    private static final float TIMER_SCALE = 0.7f;

    static final Map<String, UniversalGuiMover.HudContainer> boosterContainers = new HashMap<>();

    private static final Pattern XP_BOOSTER_PATTERN = Pattern.compile(
            "\\(!\\)\\s*(\\d+(?:\\.\\d+)?)x\\s*Island\\s*EXP\\s*Booster\\s*has\\s*been\\s*activated\\s*for\\s*(.+?)!",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TREASURE_BOOSTER_PATTERN = Pattern.compile(
            "\\(!\\)\\s*(\\d+(?:\\.\\d+)?)x\\s*Treasure\\s*Chance\\s*Booster\\s*has\\s*been\\s*activated\\s*for\\s*(.+?)!",
            Pattern.CASE_INSENSITIVE
    );

    private static int enderPearlCooldown = 0;
    private static final String ENDER_PEARL_KEY = "Ender Pearl";
    private static final String ISLAND_BOOSTER_KEY = "Island XP Booster";
    private static final String TREASURE_BOOSTER_KEY = "Treasure Chance Booster";

    public static void setModEnabled(boolean enabled) {
        modEnabled = enabled;
    }

    public static void setColorAlertsEnabled(boolean enabled) {
        colorAlertsEnabled = enabled;
    }

    @Override
    public void onInitializeClient() {
        System.out.println("XP Booster Mod Initialized!");
        RankManager.initialize();
        StatusEffectsTracker.initialize();

        boosterContainers.put(ISLAND_BOOSTER_KEY, new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put(TREASURE_BOOSTER_KEY, new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/feed", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/heal", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/fix", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/near", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put(ENDER_PEARL_KEY, new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));

        boosterContainers.forEach(UniversalGuiMover::trackHudContainer);

        HudRenderCallback.EVENT.register(this::renderHud);
        ClientReceiveMessageEvents.GAME.register(this::handleGameMessage);
        ClientSendMessageEvents.ALLOW_COMMAND.register(cmd -> {
            onCommandSent(cmd);
            return true;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player.getStackInHand(hand).getItem() == Items.ENDER_PEARL) {
                if (enderPearlCooldown == 0) {
                    enderPearlCooldown = 60;
                }
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tickCounter >= 20) {
                tickCounter = 0;

                boosters.replaceAll((k, v) -> v > 0 ? v - 1 : 0);
                boosters.values().removeIf(v -> v <= 0);

                if (enderPearlCooldown > 0) {
                    enderPearlCooldown--;
                }
            }
        });
    }

    private void handleGameMessage(Text text, boolean overlay) {
        if (overlay) return;
        String message = text.getString();

        Matcher xpMatcher = XP_BOOSTER_PATTERN.matcher(message);
        if (xpMatcher.find()) {
            String multiplier = xpMatcher.group(1);
            String duration = xpMatcher.group(2);

            int seconds = parseDuration(duration);
            if (seconds > 0) {
                String displayName = multiplier + "x " + ISLAND_BOOSTER_KEY;
                boosters.put(ISLAND_BOOSTER_KEY, seconds);
                System.out.println("Detected XP Booster: " + displayName + " for " + seconds + " seconds");
            }
        }

        Matcher treasureMatcher = TREASURE_BOOSTER_PATTERN.matcher(message);
        if (treasureMatcher.find()) {
            String multiplier = treasureMatcher.group(1);
            String duration = treasureMatcher.group(2);

            int seconds = parseDuration(duration);
            if (seconds > 0) {
                String displayName = multiplier + "x " + TREASURE_BOOSTER_KEY;
                boosters.put(TREASURE_BOOSTER_KEY, seconds);
                System.out.println("Detected Treasure Booster: " + displayName + " for " + seconds + " seconds");
            }
        }

        if (message.contains("Landing location failed")) {
            if (enderPearlCooldown > 45) {
                enderPearlCooldown = 0;
            }
        }
    }

    private int parseDuration(String duration) {
        duration = duration.trim().toLowerCase();
        int totalSeconds = 0;

        try {
            Pattern timePattern = Pattern.compile("(\\d+)([hms])");
            Matcher matcher = timePattern.matcher(duration);

            while (matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);

                if (unit.equals("h")) {
                    totalSeconds += value * 3600;
                } else if (unit.equals("m")) {
                    totalSeconds += value * 60;
                } else if (unit.equals("s")) {
                    totalSeconds += value;
                }
            }

            if (totalSeconds == 0 && duration.matches("\\d+")) {
                totalSeconds = Integer.parseInt(duration) * 60;
            }

        } catch (NumberFormatException e) {
            System.err.println("Failed to parse duration: " + duration);
        }

        return totalSeconds;
    }

    private void onCommandSent(String command) {
        String[] parts = command.toLowerCase().split(" ");
        String base = parts[0];

        if (base.equals("eat")) {
            base = "feed";
        } else if (base.equals("heal")) {
            base = "heal";
        }

        if (base.equals("fix") && parts.length > 1 && parts[1].equals("all")) base = "fix";
        int cd = RankManager.getCooldown(base);
        if (cd <= 0) return;

        String key = "/" + base;
        if (!boosters.containsKey(key)) boosters.put(key, cd);
    }

    private void renderHud(DrawContext ctx, float tickDelta) {
        if (!modEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Window w = mc.getWindow();
        float scale = UniversalGuiMover.getGlobalTextScale();
        int iconSz = (int) (ICON_SIZE * scale);
        int sw = w.getScaledWidth(), sh = w.getScaledHeight();
        boolean force = UniversalGuiMover.isMovementModeActive();

        Map<String, Integer> vis = new HashMap<>(boosters);
        if (force) boosterContainers.keySet().forEach(k -> vis.putIfAbsent(k, 0));
        if (force || enderPearlCooldown > 0) vis.put(ENDER_PEARL_KEY, enderPearlCooldown);

        // Import and add boss bar containers


        if (vis.isEmpty()) return;

        boolean needsCenter = !force && boosterContainers.values().stream().anyMatch(c -> c.x == 0);
        if (needsCenter) {
            int total = vis.size();
            int width = total * (iconSz + 20);
            int startX = (sw / 2) - (width / 2), xPos = startX;
            for (UniversalGuiMover.HudContainer c : boosterContainers.values()) {
                if (c.x == 0) {
                    c.x = xPos;
                    c.y = Y_OFFSET;
                    xPos += iconSz + 20;
                }
            }
        }

        int maxX = sw - iconSz - 5, minY = 5, maxY = sh - iconSz * 2 - 5;

        // Render booster items (existing code)
        for (var e : vis.entrySet()) {
            String key = e.getKey();

            if (!SettingsManager.getBoosterToggleSettings().getOrDefault(key, true)) continue;

            UniversalGuiMover.HudContainer c = boosterContainers.get(key);
            if (c == null) continue;

            if (!force) {
                c.x = Math.max(5, Math.min(c.x, maxX));
                c.y = Math.max(minY, Math.min(c.y, maxY));
            }

            int time = e.getValue();
            Identifier tex = getBoosterTexture(key);
            String txt = time > 0 ? formatCountdown(time) : "Cooldown";

            ctx.getMatrices().push();
            ctx.getMatrices().translate(c.x, c.y, 0);
            ctx.getMatrices().scale(scale, scale, 1);

            if (force && time <= 0) ctx.setShaderColor(1, 1, 1, 0.4f);
            ctx.drawTexture(tex, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            ctx.setShaderColor(1, 1, 1, 1);

            if (time > 0 || force) {
                float yOff = ICON_SIZE + 2;

                ctx.getMatrices().push();
                ctx.getMatrices().translate(ICON_SIZE / 2f, yOff, 0);
                ctx.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
                int wName = mc.textRenderer.getWidth(key);
                ctx.drawText(mc.textRenderer, key, -wName / 2, 0, TEXT_COLOR, true);
                ctx.getMatrices().pop();

                ctx.getMatrices().push();
                ctx.getMatrices().translate(ICON_SIZE / 2f, yOff + 10, 0);
                ctx.getMatrices().scale(TIMER_SCALE, TIMER_SCALE, 1);
                int wTime = mc.textRenderer.getWidth(txt);
                ctx.drawText(mc.textRenderer, txt, -wTime / 2, 0, TEXT_COLOR, true);
                ctx.getMatrices().pop();
            }

            ctx.getMatrices().pop();
        }
    }

    private Identifier getBoosterTexture(String key) {
        return switch (key) {
            case ISLAND_BOOSTER_KEY -> ISLAND_BOOSTER_TEXTURE;
            case TREASURE_BOOSTER_KEY -> ISLAND_BOOSTER_TEXTURE;
            case "/feed" -> FEED_COOLDOWN_TEXTURE;
            case "/heal" -> HEAL_COOLDOWN_TEXTURE;
            case "/fix" -> FIX_COOLDOWN_TEXTURE;
            case "/near" -> NEAR_COOLDOWN_TEXTURE;
            case ENDER_PEARL_KEY -> ENDER_PEARL_TEXTURE;
            default -> XP_BOTTLE_TEXTURE;
        };
    }

    private String formatCountdown(int s) {
        if (s > 60) {
            return (s / 60) + "m " + (s % 60) + "s";
        }
        return s + "s";
    }
}