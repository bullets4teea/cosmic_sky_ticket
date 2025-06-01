package chat.cosmic.client.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatusEffectsTracker {
    private static final Map<String, Integer> curseTimers = new HashMap<>();
    private static final Pattern CURSE_PATTERN = Pattern.compile("You have been cursed! You will be siphoned to 1 HP in (\\d+)s!");
    private static final Identifier CURSE_TEXTURE = new Identifier("xpbooster", "textures/gui/curse.png");
    private static final List<ChaoticMessage> chaoticZoneMessages = new ArrayList<>();
    private static final Pattern CHAOTIC_ZONE_PATTERN = Pattern.compile("(\\w+) has (entered|left) the Chaotic Zone!");
    private static final Pattern COSMONAUTS_PATTERN = Pattern.compile("\\*\\s*Cosmonauts\\s+are\\s+attempting\\s+to\\s+contact\\s+the\\s+Facility!\\s*\\*");
    private static final int CHAOTIC_MESSAGE_DURATION = 120;
    private static int combatTimer = 0;
    private static final Identifier COMBAT_TEXTURE = new Identifier("xpbooster", "textures/gui/combat.png");
    private static final int COMBAT_COLOR = 0xFFAA00;
    private static float previousHealth = 0.0f;
    private static int muleTimer = 0;
    private static boolean wasMuleScreenOpen = false;
    private static final Identifier MULE_TEXTURE = new Identifier("xpbooster", "textures/gui/mule.png");
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 2.0f;
    private static final int ICON_SIZE = 16;
    private static final int CURSE_COLOR = 0xFF5555;
    private static final float NAME_SCALE = 0.7f;
    private static final float TIMER_SCALE = 0.7f;
    private static final UniversalGuiMover.HudContainer curseContainer = new UniversalGuiMover.HudContainer(0, 60, ICON_SIZE, ICON_SIZE, 1);
    private static final UniversalGuiMover.HudContainer chaoticZoneContainer = new UniversalGuiMover.HudContainer(0, 30, 100, 60, 2);
    private static final UniversalGuiMover.HudContainer combatContainer = new UniversalGuiMover.HudContainer(0, 90, ICON_SIZE, ICON_SIZE, 3);
    private static final UniversalGuiMover.HudContainer muleContainer = new UniversalGuiMover.HudContainer(0, 120, ICON_SIZE, ICON_SIZE, 4);
    private static int tickCounter = 0;

    private static final Identifier ABYSS_DIMENSION = new Identifier("minecraft", "adventure_abyss-0");

    private static class ChaoticMessage {
        final String message;
        int timer;
        final UUID uuid;

        ChaoticMessage(String message, int duration) {
            this.message = message;
            this.timer = duration;
            this.uuid = UUID.randomUUID();
        }
    }

    public static void initialize() {
        UniversalGuiMover.trackHudContainer("Curse", curseContainer);
        UniversalGuiMover.trackHudContainer("ChaoticZone", chaoticZoneContainer);
        UniversalGuiMover.trackHudContainer("Combat", combatContainer);
        UniversalGuiMover.trackHudContainer("Mule", muleContainer);

        ClientReceiveMessageEvents.GAME.register(StatusEffectsTracker::handleGameMessage);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (isMuleScreen(screen)) {
                wasMuleScreenOpen = true;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                float currentHealth = client.player.getHealth();
                if (currentHealth < previousHealth) {
                    combatTimer = isInAbyssDimension(client.player) ? 20 : 10;
                }
                previousHealth = currentHealth;
            }

            Screen currentScreen = client.currentScreen;
            if (wasMuleScreenOpen && !isMuleScreen(currentScreen)) {
                if (RankManager.hasMuleAccess()) {
                    muleTimer = 1200;
                    tickCounter = 0;
                }
                wasMuleScreenOpen = false;
            }

            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                curseTimers.replaceAll((k, v) -> Math.max(v - 1, 0));
                combatTimer = Math.max(combatTimer - 1, 0);
                muleTimer = Math.max(muleTimer - 1, 0);
                curseTimers.entrySet().removeIf(entry -> entry.getValue() <= 0);
            }
            chaoticZoneMessages.removeIf(message -> --message.timer <= 0);
        });

        HudRenderCallback.EVENT.register(StatusEffectsTracker::renderHud);
    }

    private static boolean isInAbyssDimension(PlayerEntity player) {
        return player.getWorld().getRegistryKey().getValue().equals(ABYSS_DIMENSION);
    }

    public static void onPlayerAttack() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            combatTimer = isInAbyssDimension(player) ? 20 : 10;
        }
    }

    private static boolean isMuleScreen(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof GenericContainerScreen containerScreen) {
            String title = containerScreen.getTitle().getString();
            return title.equals("Prime Mule");
        }
        return false;
    }

    private static void handleGameMessage(Text text, boolean overlay) {
        if (overlay) return;

        String message = text.getString();
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("you have been cursed") && lowerMessage.contains("siphoned to 1 hp")) {
            Matcher matcher = CURSE_PATTERN.matcher(message);
            if (matcher.find()) {
                try {
                    int seconds = Integer.parseInt(matcher.group(1));
                    if (seconds > 0) {
                        curseTimers.put("Curse", seconds);
                        tickCounter = 0;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse curse duration: " + message);
                }
            }
        }

        Matcher chaoticMatcher = CHAOTIC_ZONE_PATTERN.matcher(message);
        if (chaoticMatcher.find()) {
            String playerName = chaoticMatcher.group(1);
            String action = chaoticMatcher.group(2).equalsIgnoreCase("entered") ? "entered" : "left";
            String formattedMessage = playerName + " has " + action + " the Chaotic Zone!";
            chaoticZoneMessages.add(new ChaoticMessage(formattedMessage, CHAOTIC_MESSAGE_DURATION));
            playChaoticSound();
        }

        Matcher cosmonautsMatcher = COSMONAUTS_PATTERN.matcher(message);
        if (cosmonautsMatcher.find()) {
            chaoticZoneMessages.add(new ChaoticMessage("Cosmonauts contacting Facility!", CHAOTIC_MESSAGE_DURATION));
            playChaoticSound();
        }
    }

    private static void playChaoticSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.getSoundManager().play(
                    PositionedSoundInstance.master(
                            SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                            SOUND_PITCH,
                            SOUND_VOLUME
                    )
            );
        }
    }

    public static void renderHud(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();

        if (client.player == null || window == null) return;

        renderChaoticZoneMessages(context, client, window);
        renderCurseIcon(context, client, window);
        renderCombatTimer(context, client, window);
        renderMuleTimer(context, client, window);
    }

    private static void renderChaoticZoneMessages(DrawContext context, MinecraftClient client, Window window) {
        boolean forceShow = UniversalGuiMover.isMovementModeActive();
        float globalScale = UniversalGuiMover.getGlobalTextScale();

        int maxWidth = forceShow ? 100 : 0;
        int lineHeight = (int) (12 * globalScale);
        int totalHeight = 0;

        if (!chaoticZoneMessages.isEmpty() || forceShow) {
            for (ChaoticMessage message : chaoticZoneMessages) {
                int messageWidth = (int) (client.textRenderer.getWidth(message.message) * globalScale);
                if (messageWidth > maxWidth) maxWidth = messageWidth;
            }
            totalHeight = (int) ((chaoticZoneMessages.size() + (forceShow ? 1 : 0)) * lineHeight);

            chaoticZoneContainer.baseWidth = (int) (maxWidth + 4 * globalScale);
            chaoticZoneContainer.baseHeight = (int) (totalHeight + 4 * globalScale);

            if (!UniversalGuiMover.isMovementModeActive()) {
                chaoticZoneContainer.x = Math.max(5, Math.min(
                        chaoticZoneContainer.x,
                        window.getScaledWidth() - (int)(chaoticZoneContainer.baseWidth * globalScale) - 5
                ));
                chaoticZoneContainer.y = Math.max(5, Math.min(
                        chaoticZoneContainer.y,
                        window.getScaledHeight() - (int)(chaoticZoneContainer.baseHeight * globalScale) - 5
                ));
            }

            long time = System.currentTimeMillis();
            int yOffset = (int) (2 * globalScale);

            context.getMatrices().push();
            context.getMatrices().translate(chaoticZoneContainer.x, chaoticZoneContainer.y, 0);
            context.getMatrices().scale(globalScale, globalScale, 1);

            if (UniversalGuiMover.isMovementModeActive()) {
                context.fill(0, 0,
                        (int)(chaoticZoneContainer.baseWidth / globalScale),
                        (int)(chaoticZoneContainer.baseHeight / globalScale),
                        0x80000000
                );
            }

            for (ChaoticMessage entry : chaoticZoneMessages) {
                String message = entry.message;
                float alpha = (float) (0.5 + 0.5 * Math.cos(time * 0.0020));
                int textColor = (0xFF << 24) | ((int)(0xFF * alpha) << 16);

                context.drawText(client.textRenderer, message,
                        (int)(2 / globalScale),
                        (int)(yOffset / globalScale),
                        textColor, false
                );
                yOffset += lineHeight;
            }

            if (forceShow && chaoticZoneMessages.isEmpty()) {
                context.drawText(client.textRenderer, "Chaotic Zone",
                        (int)(2 / globalScale),
                        (int)(2 / globalScale),
                        0x80FF0000, false
                );
            }

            context.getMatrices().pop();
        }
    }

    private static void renderCurseIcon(DrawContext context, MinecraftClient client, Window window) {
        float globalScale = UniversalGuiMover.getGlobalTextScale();
        int scaledIconSize = (int) (ICON_SIZE * globalScale);
        boolean forceShow = UniversalGuiMover.isMovementModeActive();

        if (curseTimers.containsKey("Curse") || forceShow) {
            int curseTime = curseTimers.getOrDefault("Curse", 0);
            String curseText = curseTime > 0 ? String.valueOf(curseTime) + "s" : "Curse";

            if (!UniversalGuiMover.isMovementModeActive()) {
                curseContainer.x = Math.max(5, Math.min(
                        curseContainer.x,
                        window.getScaledWidth() - scaledIconSize - 5
                ));
                curseContainer.y = Math.max(5, Math.min(
                        curseContainer.y,
                        window.getScaledHeight() - (scaledIconSize * 2) - 5
                ));
            }

            context.getMatrices().push();
            context.getMatrices().translate(curseContainer.x, curseContainer.y, 0);
            context.getMatrices().scale(globalScale, globalScale, 1);

            if (forceShow && curseTime <= 0) {
                context.setShaderColor(1, 1, 1, 0.4f);
            }

            context.drawTexture(CURSE_TEXTURE, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            context.setShaderColor(1, 1, 1, 1);

            if (curseTime > 0 || forceShow) {
                float textYOffset = ICON_SIZE + 2;

                context.getMatrices().push();
                context.getMatrices().translate(ICON_SIZE / 2f, textYOffset, 0);
                context.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
                int nameWidth = client.textRenderer.getWidth("Curse");
                context.drawText(client.textRenderer, "Curse", -nameWidth / 2, 0, CURSE_COLOR, true);
                context.getMatrices().pop();

                if (curseTime > 0) {
                    context.getMatrices().push();
                    context.getMatrices().translate(ICON_SIZE / 2f, textYOffset + 10, 0);
                    context.getMatrices().scale(TIMER_SCALE, TIMER_SCALE, 1);
                    int timeWidth = client.textRenderer.getWidth(curseText);
                    context.drawText(client.textRenderer, curseText, -timeWidth / 2, 0, CURSE_COLOR, true);
                    context.getMatrices().pop();
                }
            }

            context.getMatrices().pop();
        }
    }

    private static void renderCombatTimer(DrawContext context, MinecraftClient client, Window window) {
        float globalScale = UniversalGuiMover.getGlobalTextScale();
        int scaledIconSize = (int) (ICON_SIZE * globalScale);
        boolean forceShow = UniversalGuiMover.isMovementModeActive();

        if (combatTimer > 0 || forceShow) {
            String combatText = combatTimer > 0 ? combatTimer + "s" : "Combat";

            if (!UniversalGuiMover.isMovementModeActive()) {
                combatContainer.x = Math.max(5, Math.min(
                        combatContainer.x,
                        window.getScaledWidth() - scaledIconSize - 5
                ));
                combatContainer.y = Math.max(5, Math.min(
                        combatContainer.y,
                        window.getScaledHeight() - (scaledIconSize * 3) - 5
                ));
            }

            context.getMatrices().push();
            context.getMatrices().translate(combatContainer.x, combatContainer.y, 0);
            context.getMatrices().scale(globalScale, globalScale, 1);

            if (forceShow && combatTimer <= 0) {
                context.setShaderColor(1, 1, 1, 0.4f);
            }

            context.drawTexture(COMBAT_TEXTURE, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            context.setShaderColor(1, 1, 1, 1);

            if (combatTimer > 0 || forceShow) {
                float textYOffset = ICON_SIZE + 2;

                context.getMatrices().push();
                context.getMatrices().translate(ICON_SIZE / 2f, textYOffset, 0);
                context.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
                int nameWidth = client.textRenderer.getWidth("Combat");
                context.drawText(client.textRenderer, "Combat", -nameWidth / 2, 0, COMBAT_COLOR, true);
                context.getMatrices().pop();

                if (combatTimer > 0) {
                    context.getMatrices().push();
                    context.getMatrices().translate(ICON_SIZE / 2f, textYOffset + 10, 0);
                    context.getMatrices().scale(TIMER_SCALE, TIMER_SCALE, 1);
                    int timeWidth = client.textRenderer.getWidth(combatText);
                    context.drawText(client.textRenderer, combatText, -timeWidth / 2, 0, COMBAT_COLOR, true);
                    context.getMatrices().pop();
                }
            }

            context.getMatrices().pop();
        }
    }

    private static void renderMuleTimer(DrawContext context, MinecraftClient client, Window window) {
        float globalScale = UniversalGuiMover.getGlobalTextScale();
        int scaledIconSize = (int) (ICON_SIZE * globalScale);
        boolean forceShow = UniversalGuiMover.isMovementModeActive();
        boolean hasAccess = RankManager.hasMuleAccess();

        if ((hasAccess && muleTimer > 0) || forceShow) {
            String muleText = muleTimer > 0 ? formatTime(muleTimer) : "Mule";

            if (!UniversalGuiMover.isMovementModeActive()) {
                muleContainer.x = Math.max(5, Math.min(
                        muleContainer.x,
                        window.getScaledWidth() - scaledIconSize - 5
                ));
                muleContainer.y = Math.max(5, Math.min(
                        muleContainer.y,
                        window.getScaledHeight() - (scaledIconSize * 4) - 5
                ));
            }

            context.getMatrices().push();
            context.getMatrices().translate(muleContainer.x, muleContainer.y, 0);
            context.getMatrices().scale(globalScale, globalScale, 1);

            if (forceShow && (muleTimer <= 0 || !hasAccess)) {
                context.setShaderColor(1, 1, 1, 0.4f);
            }

            context.drawTexture(MULE_TEXTURE, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            context.setShaderColor(1, 1, 1, 1);

            if (muleTimer > 0 || forceShow) {
                float textYOffset = ICON_SIZE + 2;

                context.getMatrices().push();
                context.getMatrices().translate(ICON_SIZE / 2f, textYOffset, 0);
                context.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
                int nameWidth = client.textRenderer.getWidth("Mule");
                context.drawText(client.textRenderer, "Mule", -nameWidth / 2, 0, 0xFFFFFF, true);
                context.getMatrices().pop();

                if (muleTimer > 0) {
                    context.getMatrices().push();
                    context.getMatrices().translate(ICON_SIZE / 2f, textYOffset + 10, 0);
                    context.getMatrices().scale(TIMER_SCALE, TIMER_SCALE, 1);
                    int timeWidth = client.textRenderer.getWidth(muleText);
                    context.drawText(client.textRenderer, muleText, -timeWidth / 2, 0, 0xFFFFFF, true);
                    context.getMatrices().pop();
                }
            }

            context.getMatrices().pop();
        }
    }

    private static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
}