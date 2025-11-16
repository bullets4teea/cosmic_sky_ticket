package chat.cosmic.client.mixin;

import chat.cosmic.client.client.globe_booster.ProgressBarToggleMod;
import chat.cosmic.client.client.UniversalGuiMover;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(BossBarHud.class)
public class BossBarHudMixin {

    @Shadow @Final private Map<UUID, ClientBossBar> bossBars;

    private static final Identifier MARAUDER_TEXTURE = new Identifier("xpbooster", "textures/gui/custom_bar.png");
    private static final Identifier ISLAND_QUEST_TEXTURE = new Identifier("xpbooster", "textures/gui/custom_bar.png");
    private static final Identifier MARAUDER_HITLIST_TEXTURE = new Identifier("xpbooster", "textures/gui/custom_bar.png");
    private static final Identifier ISLAND_EXP_TEXTURE = new Identifier("xpbooster", "textures/gui/custom_bar.png");

    private static final int ICON_SIZE = 32;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int Y_OFFSET = 10;
    private static final float NAME_SCALE = 0.75f;
    private static final float TIMER_SCALE = 0.75f;
    private static final int SPACING = 50;

    private static final Map<String, UniversalGuiMover.HudContainer> bossBarContainers = new HashMap<>();
    private static final String MARAUDER_KEY = "2x Marauder Bar Progress";
    private static final String ISLAND_QUEST_KEY = "1.5x Island Quest Progress";
    private static final String MARAUDER_HITLIST_KEY = "2x Marauder Hitlist Progress";
    private static final String ISLAND_EXP_KEY = "2x Island EXP Multiplier";

    @Unique
    private final Map<String, String[]> detectedCustomBars = new HashMap<>();

    @Unique
    private final Map<UUID, ClientBossBar> removedBars = new HashMap<>();

    @Unique
    private static boolean wasToggled = false;

    private static void initializeContainersIfNeeded() {
        if (bossBarContainers.isEmpty()) {
            bossBarContainers.put(MARAUDER_KEY, new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
            bossBarContainers.put(ISLAND_QUEST_KEY, new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
            bossBarContainers.put(MARAUDER_HITLIST_KEY, new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
            bossBarContainers.put(ISLAND_EXP_KEY, new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(DrawContext context, CallbackInfo ci) {
        boolean isToggled = ProgressBarToggleMod.shouldHideBossBars();


        if (wasToggled != isToggled) {
            wasToggled = isToggled;
        }

        if (!isToggled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        initializeContainersIfNeeded();
        detectedCustomBars.clear();


        removedBars.clear();


        List<UUID> toRemove = new ArrayList<>();
        for (var entry : bossBars.entrySet()) {
            ClientBossBar bar = entry.getValue();
            Text name = bar.getName();
            if (name == null) continue;

            String text = name.getString();

            if (text.contains("Marauder Bar Progress")) {
                String time = extractTime(text);
                String multiplier = extractMultiplier(text);
                detectedCustomBars.put(MARAUDER_KEY, new String[]{time, multiplier});
                toRemove.add(entry.getKey());
                removedBars.put(entry.getKey(), bar);
            } else if (text.contains("Island Quest Progress")) {
                String time = extractTime(text);
                String multiplier = extractMultiplier(text);
                detectedCustomBars.put(ISLAND_QUEST_KEY, new String[]{time, multiplier});
                toRemove.add(entry.getKey());
                removedBars.put(entry.getKey(), bar);
            } else if (text.contains("Marauder Hitlist Progress")) {
                String time = extractTime(text);
                String multiplier = extractMultiplier(text);
                detectedCustomBars.put(MARAUDER_HITLIST_KEY, new String[]{time, multiplier});
                toRemove.add(entry.getKey());
                removedBars.put(entry.getKey(), bar);
            } else if (text.contains("Island EXP Multiplier")) {
                String time = extractTime(text);
                String multiplier = extractMultiplier(text);
                detectedCustomBars.put(ISLAND_EXP_KEY, new String[]{time, multiplier});
                toRemove.add(entry.getKey());
                removedBars.put(entry.getKey(), bar);
            }
        }


        for (UUID uuid : toRemove) {
            bossBars.remove(uuid);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(DrawContext context, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;


        if (!removedBars.isEmpty()) {
            bossBars.putAll(removedBars);
            removedBars.clear();
        }

        if (!ProgressBarToggleMod.shouldHideBossBars() || detectedCustomBars.isEmpty()) {
            return;
        }


        renderCustomBars(context, client, detectedCustomBars);
    }

    private void renderCustomBars(DrawContext ctx, MinecraftClient client, Map<String, String[]> customBars) {
        float scale = UniversalGuiMover.getGlobalTextScale();
        int iconSz = (int) (ICON_SIZE * scale);
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        boolean force = UniversalGuiMover.isMovementModeActive();


        Map<String, UniversalGuiMover.HudContainer> hudContainers = UniversalGuiMover.getHudContainers();
        for (var entry : bossBarContainers.entrySet()) {
            if (!hudContainers.containsKey(entry.getKey())) {
                UniversalGuiMover.trackHudContainer(entry.getKey(), entry.getValue());
            }
        }


        if (!force) {
            for (var entry : bossBarContainers.entrySet()) {
                UniversalGuiMover.HudContainer container = entry.getValue();

                if (container.x == 0 && customBars.containsKey(entry.getKey())) {
                    int total = customBars.size();
                    int width = total * (iconSz + SPACING);
                    int startX = (sw / 2) - (width / 2);


                    int index = 0;
                    for (String key : customBars.keySet()) {
                        if (key.equals(entry.getKey())) {
                            container.x = startX + (index * (iconSz + SPACING));
                            container.y = Y_OFFSET;
                            break;
                        }
                        index++;
                    }
                }
            }
        }


        for (var entry : customBars.entrySet()) {
            String key = entry.getKey();
            String[] barData = entry.getValue();
            String timeText = barData[0];
            String multiplierText = barData[1];

            UniversalGuiMover.HudContainer container = bossBarContainers.get(key);
            if (container == null) continue;

            Identifier texture = getTextureForKey(key);
            String displayName = getDisplayNameForKey(key);

            ctx.getMatrices().push();
            ctx.getMatrices().translate(container.x, container.y, 0);
            ctx.getMatrices().scale(scale, scale, 1);


            ctx.drawTexture(texture, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

            float yOffset = ICON_SIZE + 2;


            ctx.getMatrices().push();
            ctx.getMatrices().translate(ICON_SIZE / 2f, yOffset, 0);
            ctx.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
            int nameWidth = client.textRenderer.getWidth(displayName);
            ctx.drawText(client.textRenderer, displayName, -nameWidth / 2, 0, TEXT_COLOR, true);
            ctx.getMatrices().pop();


            ctx.getMatrices().push();
            ctx.getMatrices().translate(ICON_SIZE / 2f, yOffset + 10, 0);
            ctx.getMatrices().scale(TIMER_SCALE, TIMER_SCALE, 1);
            int multiplierWidth = client.textRenderer.getWidth(multiplierText);
            ctx.drawText(client.textRenderer, multiplierText, -multiplierWidth / 2, 0, TEXT_COLOR, true);
            ctx.getMatrices().pop();

            // Draw time
            ctx.getMatrices().push();
            ctx.getMatrices().translate(ICON_SIZE / 2f, yOffset + 20, 0);
            ctx.getMatrices().scale(TIMER_SCALE, TIMER_SCALE, 1);
            int timeWidth = client.textRenderer.getWidth(timeText);
            ctx.drawText(client.textRenderer, timeText, -timeWidth / 2, 0, TEXT_COLOR, true);
            ctx.getMatrices().pop();

            ctx.getMatrices().pop();
        }
    }

    private Identifier getTextureForKey(String key) {
        return switch (key) {
            case MARAUDER_KEY -> MARAUDER_TEXTURE;
            case ISLAND_QUEST_KEY -> ISLAND_QUEST_TEXTURE;
            case MARAUDER_HITLIST_KEY -> MARAUDER_HITLIST_TEXTURE;
            case ISLAND_EXP_KEY -> ISLAND_EXP_TEXTURE;
            default -> MARAUDER_TEXTURE;
        };
    }

    private String getDisplayNameForKey(String key) {
        return switch (key) {
            case MARAUDER_KEY -> "Marauder Bar Progress";
            case ISLAND_QUEST_KEY -> "Island Quest Progress";
            case MARAUDER_HITLIST_KEY -> "Marauder Hitlist Progress";
            case ISLAND_EXP_KEY -> "Island EXP Multiplier";
            default -> "Progress";
        };
    }

    private String extractTime(String text) {

        int start = text.indexOf('[') + 1;
        int end = text.indexOf(']');
        if (start > 0 && end > start) {
            return text.substring(start, end);
        }
        return "0s";
    }

    private String extractMultiplier(String text) {

        int xIndex = text.indexOf('x');
        if (xIndex > 0) {

            int start = xIndex - 1;
            while (start >= 0 && (Character.isDigit(text.charAt(start)) || text.charAt(start) == '.')) {
                start--;
            }
            start++; // Move back to the first digit
            return text.substring(start, xIndex + 1);
        }
        return "1x";
    }
}