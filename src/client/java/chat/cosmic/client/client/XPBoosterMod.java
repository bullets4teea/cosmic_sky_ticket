package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class XPBoosterMod implements ClientModInitializer {
    private static final Map<String, Integer> boosters = new HashMap<>();
    private static int tickCounter = 0;

    // Texture identifiers
    private static final Identifier XP_BOTTLE_TEXTURE = new Identifier("xpbooster", "textures/gui/xp_bottle.png");
    private static final Identifier ISLAND_BOOSTER_TEXTURE = new Identifier("xpbooster", "textures/gui/island_booster.png");
    private static final Identifier HEAL_COOLDOWN_TEXTURE = new Identifier("xpbooster", "textures/gui/heal_cooldown.png");
    private static final Identifier FEED_COOLDOWN_TEXTURE = new Identifier("xpbooster", "textures/gui/feed_cooldown.png");
    private static final Identifier FIX_COOLDOWN_TEXTURE = new Identifier("xpbooster", "textures/gui/fix_cooldown.png");
    private static final Identifier NEAR_COOLDOWN_TEXTURE = new Identifier("xpbooster", "textures/gui/near_cooldown.png");

    // Display constants
    private static final int ICON_SIZE = 16;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int Y_OFFSET = 10;
    private static final float NAME_SCALE = 0.7f;
    private static final float TIMER_SCALE = 0.7f;

    // HUD Containers
    private static final Map<String, UniversalGuiMover.HudContainer> boosterContainers = new HashMap<>();

    @Override
    public void onInitializeClient() {
        System.out.println("XP Booster Mod Initialized!");

        // Initialize HUD containers
        boosterContainers.put("2x Island XP Booster", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/heal", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/feed", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/fix", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/near", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));

        boosterContainers.forEach(UniversalGuiMover::trackHudContainer);

        HudRenderCallback.EVENT.register(this::renderHud);
        ClientReceiveMessageEvents.GAME.register(this::handleGameMessage);

        ClientSendMessageEvents.ALLOW_COMMAND.register((command) -> {
            onCommandSent(command);
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tickCounter >= 20) {
                tickCounter = 0;
                boosters.replaceAll((k, v) -> v > 0 ? v - 1 : 0);
                boosters.values().removeIf(v -> v <= 0);
            }
        });
    }

    private void handleGameMessage(Text text, boolean overlay) {
        if (overlay) return;

        String message = text.getString().toLowerCase();
        if (message.contains("2x island xp booster") && message.contains("activated for")) {
            String[] parts = message.split("activated for ");
            if (parts.length < 2) return;

            String durationPart = parts[1].split("[^0-9hHmM]")[0];
            int seconds = parseDuration(durationPart);

            if (seconds > 0) {
                boosters.put("2x Island XP Booster", seconds);
            }
        }
    }

    private int parseDuration(String durationStr) {
        durationStr = durationStr.replaceAll("[^0-9hHmM]", "").toLowerCase();
        int totalSeconds = 0;

        try {
            if (durationStr.contains("h")) {
                String[] hoursParts = durationStr.split("h");
                if (hoursParts[0].length() > 0) {
                    totalSeconds += Integer.parseInt(hoursParts[0]) * 3600;
                }
                durationStr = hoursParts.length > 1 ? hoursParts[1] : "";
            }

            if (durationStr.contains("m")) {
                String[] minutesParts = durationStr.split("m");
                if (minutesParts[0].length() > 0) {
                    totalSeconds += Integer.parseInt(minutesParts[0]) * 60;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse booster duration: " + durationStr);
        }

        return totalSeconds;
    }

    private void onCommandSent(String command) {
        String lowerCommand = command.toLowerCase();

        if (lowerCommand.equals("fix") && !boosters.containsKey("/fix")) {
            boosters.put("/fix", 120);
        } else if (lowerCommand.equals("heal") && !boosters.containsKey("/heal")) {
            boosters.put("/heal", 300);
        } else if ((lowerCommand.equals("feed") || lowerCommand.equals("eat")) && !boosters.containsKey("/feed")) {
            boosters.put("/feed", 300);
        } else if (lowerCommand.equals("fix all") && !boosters.containsKey("/fix")) {
            boosters.put("/fix", 120);
        } else if (lowerCommand.equals("near") && !boosters.containsKey("/near")) {
            boosters.put("/near", 30);
        }
    }

    private void renderHud(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Window window = client.getWindow();
        if (window == null) return;

        float globalScale = UniversalGuiMover.getGlobalTextScale();
        int scaledIconSize = (int) (ICON_SIZE * globalScale);
        int screenWidth = window.getScaledWidth();
        int screenHeight = window.getScaledHeight();

        // Always show elements in movement mode, even if inactive
        boolean forceShow = UniversalGuiMover.isMovementModeActive();

        // Get all containers that should be displayed
        Map<String, Integer> visibleBoosters = new HashMap<>(boosters);
        if (forceShow) {
            boosterContainers.keySet().forEach(key -> visibleBoosters.putIfAbsent(key, 0));
        }

        // Calculate positions only if needed
        if (!visibleBoosters.isEmpty()) {
            int maxX = screenWidth - scaledIconSize - 5;
            int minY = 5;
            int maxY = screenHeight - (scaledIconSize * 2) - 5;

            // Auto-center only when not in movement mode
            boolean needsCentering = !UniversalGuiMover.isMovementModeActive() &&
                    boosterContainers.values().stream().anyMatch(container -> container.x == 0);

            if (needsCentering) {
                int totalBoosters = visibleBoosters.size();
                int totalWidth = totalBoosters * (scaledIconSize + 20);
                int startX = (screenWidth / 2) - (totalWidth / 2);
                int xPos = startX;

                for (UniversalGuiMover.HudContainer container : boosterContainers.values()) {
                    if (container.x == 0) {
                        container.x = xPos;
                        container.y = Y_OFFSET;
                        xPos += scaledIconSize + 20;
                    }
                }
            }

            // Render all visible elements
            for (Map.Entry<String, Integer> entry : visibleBoosters.entrySet()) {
                String boosterType = entry.getKey();
                UniversalGuiMover.HudContainer container = boosterContainers.get(boosterType);
                if (container == null) continue;

                // Apply boundaries only when not moving
                if (!UniversalGuiMover.isMovementModeActive()) {
                    container.x = Math.max(5, Math.min(container.x, maxX));
                    container.y = Math.max(minY, Math.min(container.y, maxY));
                }

                int timeLeft = entry.getValue();
                Identifier texture = getBoosterTexture(boosterType);
                String timeText = timeLeft > 0 ? formatCountdown(timeLeft) : "Cooldown";

                context.getMatrices().push();
                context.getMatrices().translate(container.x, container.y, 0);
                context.getMatrices().scale(globalScale, globalScale, 1);

                // Draw semi-transparent icon in movement mode
                if (forceShow && timeLeft <= 0) {
                    context.setShaderColor(1, 1, 1, 0.4f);
                }

                // Draw icon
                context.drawTexture(texture, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

                // Reset transparency
                context.setShaderColor(1, 1, 1, 1);

                // Only draw text if active or in movement mode
                if (timeLeft > 0 || forceShow) {
                    float textYOffset = ICON_SIZE + 2;

                    // Booster name
                    context.getMatrices().push();
                    context.getMatrices().translate(ICON_SIZE / 2f, textYOffset, 0);
                    context.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
                    int nameWidth = client.textRenderer.getWidth(boosterType);
                    context.drawText(client.textRenderer, boosterType, -nameWidth / 2, 0, TEXT_COLOR, true);
                    context.getMatrices().pop();

                    // Timer text
                    context.getMatrices().push();
                    context.getMatrices().translate(ICON_SIZE / 2f, textYOffset + 10, 0);
                    context.getMatrices().scale(TIMER_SCALE, TIMER_SCALE, 1);
                    int timeWidth = client.textRenderer.getWidth(timeText);
                    context.drawText(client.textRenderer, timeText, -timeWidth / 2, 0, TEXT_COLOR, true);
                    context.getMatrices().pop();
                }

                context.getMatrices().pop();
            }
        }
    }

    private Identifier getBoosterTexture(String boosterType) {
        switch (boosterType) {
            case "2x Island XP Booster": return ISLAND_BOOSTER_TEXTURE;
            case "/heal": return HEAL_COOLDOWN_TEXTURE;
            case "/feed": return FEED_COOLDOWN_TEXTURE;
            case "/fix": return FIX_COOLDOWN_TEXTURE;
            case "/near": return NEAR_COOLDOWN_TEXTURE;
            default: return XP_BOTTLE_TEXTURE;
        }
    }

    private String formatCountdown(int seconds) {
        if (seconds > 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }
}