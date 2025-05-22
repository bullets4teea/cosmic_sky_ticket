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
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatusEffectsTracker {
    // Curse tracking
    private static final Map<String, Integer> curseTimers = new HashMap<>();
    private static final Pattern CURSE_PATTERN = Pattern.compile("You have been cursed! You will be siphoned to 1 HP in (\\d+)s!");
    private static final Identifier CURSE_TEXTURE = new Identifier("xpbooster", "textures/gui/curse.png");

    // Chaotic zone tracking
    private static final List<ChaoticMessage> chaoticZoneMessages = new ArrayList<>();
    private static final Pattern CHAOTIC_ZONE_PATTERN = Pattern.compile("(\\w+) has (entered|left) the Chaotic Zone!");
    private static final int CHAOTIC_MESSAGE_DURATION = 120;

    // Combat tracking
    private static int combatTimer = 0;
    private static final Identifier COMBAT_TEXTURE = new Identifier("xpbooster", "textures/gui/combat.png");
    private static final int COMBAT_COLOR = 0xFFAA00;
    private static float previousHealth = 0.0f;

    // Mule tracking
    private static int muleTimer = 0;
    private static boolean wasMuleScreenOpen = false;
    private static final Identifier MULE_TEXTURE = new Identifier("xpbooster", "textures/gui/mule.png");

    // Sound settings
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 2.0f;

    // Display constants
    private static final int ICON_SIZE = 16;
    private static final int CURSE_COLOR = 0xFF5555;
    private static final float NAME_SCALE = 0.7f;
    private static final float TIMER_SCALE = 0.7f;

    // HUD Containers
    private static final UniversalGuiMover.HudContainer curseContainer = new UniversalGuiMover.HudContainer(0, 60, ICON_SIZE, ICON_SIZE, 1);
    private static final UniversalGuiMover.HudContainer chaoticZoneContainer = new UniversalGuiMover.HudContainer(0, 30, 100, 60, 2);
    private static final UniversalGuiMover.HudContainer combatContainer = new UniversalGuiMover.HudContainer(0, 90, ICON_SIZE, ICON_SIZE, 3);
    private static final UniversalGuiMover.HudContainer muleContainer = new UniversalGuiMover.HudContainer(0, 120, ICON_SIZE, ICON_SIZE, 4);


    // Timer tracking
    private static int tickCounter = 0;

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

        // Track screen changes for mule GUI detection
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (isMuleScreen(screen)) {
                wasMuleScreenOpen = true;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Track health changes for combat timer
            if (client.player != null) {
                float currentHealth = client.player.getHealth();
                if (currentHealth < previousHealth) {
                    combatTimer = 10; // Reset to 10 seconds on damage
                }
                previousHealth = currentHealth;
            }

            // Check if mule screen was closed
            Screen currentScreen = client.currentScreen;
            if (wasMuleScreenOpen && !isMuleScreen(currentScreen)) {
                // Player left mule GUI, start 20-minute cooldown if they have access
                if (RankManager.hasMuleAccess()) {
                    muleTimer = 1200; // 20 minutes = 1200 seconds
                    tickCounter = 0; // Reset tick counter
                }
                wasMuleScreenOpen = false;
            }

            // Only decrement timers every 20 ticks (1 second)
            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                curseTimers.replaceAll((k, v) -> Math.max(v - 1, 0));
                combatTimer = Math.max(combatTimer - 1, 0);
                muleTimer = Math.max(muleTimer - 1, 0);
                // Remove curse if timer reaches 0
                curseTimers.entrySet().removeIf(entry -> entry.getValue() <= 0);
            }
            chaoticZoneMessages.removeIf(message -> --message.timer <= 0);
        });

        HudRenderCallback.EVENT.register(StatusEffectsTracker::renderHud);
    }

    public static void onPlayerAttack() {
        combatTimer = 10;
    }
    private static boolean isMuleScreen(Screen screen) {
        if (screen == null) return false;

        // Check if it's a container screen (chest, shulker box, etc.)
        if (screen instanceof GenericContainerScreen containerScreen) {
            // Check specifically for "Prime Mule" in the title
            String title = containerScreen.getTitle().getString();
            return title.equals("Prime Mule");
        }

        return false;
    }

    private static void handleGameMessage(Text text, boolean overlay) {
        if (overlay) return;

        String message = text.getString();
        String lowerMessage = message.toLowerCase();

        // Handle curse messages
        if (lowerMessage.contains("you have been cursed") && lowerMessage.contains("siphoned to 1 hp")) {
            Matcher matcher = CURSE_PATTERN.matcher(message);
            if (matcher.find()) {
                try {
                    int seconds = Integer.parseInt(matcher.group(1));
                    if (seconds > 0) {
                        curseTimers.put("Curse", seconds);
                        tickCounter = 0; // Reset tick counter when new curse is applied
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse curse duration: " + message);
                }
            }
        }

        // Handle chaotic zone messages
        Matcher matcher = CHAOTIC_ZONE_PATTERN.matcher(message);
        if (matcher.find()) {
            String playerName = matcher.group(1);
            String action = matcher.group(2).equalsIgnoreCase("entered") ? "entered" : "left";
            String formattedMessage = playerName + " has " + action + " the Chaotic Zone!";

            chaoticZoneMessages.add(new ChaoticMessage(formattedMessage, CHAOTIC_MESSAGE_DURATION));
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
            // Calculate scaled dimensions
            for (ChaoticMessage message : chaoticZoneMessages) {
                int messageWidth = (int) (client.textRenderer.getWidth(message.message) * globalScale);
                if (messageWidth > maxWidth) maxWidth = messageWidth;
            }
            totalHeight = (int) ((chaoticZoneMessages.size() + (forceShow ? 1 : 0)) * lineHeight);

            // Update container dimensions with scale
            chaoticZoneContainer.baseWidth = (int) (maxWidth + 4 * globalScale);
            chaoticZoneContainer.baseHeight = (int) (totalHeight + 4 * globalScale);

            // Boundary checks with scaling
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

            // Draw background in movement mode
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

            // Position handling
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

            // Semi-transparent if in movement mode without active timer
            if (forceShow && combatTimer <= 0) {
                context.setShaderColor(1, 1, 1, 0.4f);
            }

            // Draw combat icon
            context.drawTexture(COMBAT_TEXTURE, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            context.setShaderColor(1, 1, 1, 1);

            // Draw text labels
            if (combatTimer > 0 || forceShow) {
                float textYOffset = ICON_SIZE + 2;

                // Combat label
                context.getMatrices().push();
                context.getMatrices().translate(ICON_SIZE / 2f, textYOffset, 0);
                context.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
                int nameWidth = client.textRenderer.getWidth("Combat");
                context.drawText(client.textRenderer, "Combat", -nameWidth / 2, 0, COMBAT_COLOR, true);
                context.getMatrices().pop();

                // Timer text
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

        // Only show if player has Celestial rank access and there's an active timer, or if in movement mode
        if ((hasAccess && muleTimer > 0) || forceShow) {
            String muleText = muleTimer > 0 ? formatTime(muleTimer) : "Mule";

            // Position handling
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

            // Semi-transparent if in movement mode without active timer or no access
            if (forceShow && (muleTimer <= 0 || !hasAccess)) {
                context.setShaderColor(1, 1, 1, 0.4f);
            }

            // Draw mule icon
            context.drawTexture(MULE_TEXTURE, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            context.setShaderColor(1, 1, 1, 1);

            // Draw text labels
            if (muleTimer > 0 || forceShow) {
                float textYOffset = ICON_SIZE + 2;

                // Mule label
                context.getMatrices().push();
                context.getMatrices().translate(ICON_SIZE / 2f, textYOffset, 0);
                context.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
                int nameWidth = client.textRenderer.getWidth("Mule");
                context.drawText(client.textRenderer, "Mule", -nameWidth / 2, 0, 0xFFFFFF, true);
                context.getMatrices().pop();

                // Timer text
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

    // Helper method to format time in MM:SS format
    private static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

}