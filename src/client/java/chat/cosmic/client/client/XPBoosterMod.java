package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class XPBoosterMod implements ClientModInitializer {
    private static final Map<String, Integer> boosters = new HashMap<>();
    private static int tickCounter = 0;
    private static String currentRank = "Comet";
    private static final Path configPath = Paths.get("config/xpbooster_rank.cfg");

    // Rank color definitions (RGB)
    private static final int GALACTIC_COLOR = 0xFFFF55;    // Yellow
    private static final int TITAN_COLOR = 0x55FFFF;       // Cyan
    private static final int CELESTIAL_COLOR = 0xAA00AA;   // Purple
    private static final int COMET_COLOR = 0xFFFFFF;       // White

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
        loadRankFromConfig();
        System.out.println("XP Booster Mod Initialized!");

        // Initialize HUD containers
        boosterContainers.put("2x Island XP Booster", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/heal", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/feed", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/fix", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));
        boosterContainers.put("/near", new UniversalGuiMover.HudContainer(0, Y_OFFSET, ICON_SIZE, ICON_SIZE, 1));

        boosterContainers.forEach(UniversalGuiMover::trackHudContainer);

        HudRenderCallback.EVENT.register(this::renderHud);

        ClientSendMessageEvents.ALLOW_COMMAND.register((command) -> {
            onCommandSent(command);
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Update boosters
            if (++tickCounter >= 20) {
                tickCounter = 0;
                boosters.replaceAll((k, v) -> v > 0 ? v - 1 : 0);
                boosters.values().removeIf(v -> v <= 0);
            }

            // Check player name color from F3 data (first-person view handling)
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                Text nameText = mc.player.getName();
                checkRankFromName(nameText);  // Check and update rank if necessary
            }
        });
    }

    private void checkRankFromName(Text nameText) {
        boolean rankUpdated = false;

        // Check main text style
        if (checkStyleForRank(nameText.getStyle())) {
            rankUpdated = true;
        }

        // Check all text siblings (this can be useful in case there are multiple components to the name)
        for (Text sibling : nameText.getSiblings()) {
            if (checkStyleForRank(sibling.getStyle())) {
                rankUpdated = true;
                break;
            }
        }

        // If the rank has changed, update and notify the player
        if (rankUpdated) {
            saveRankToConfig();
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§bRank updated to: " + currentRank));
        }
    }

    private boolean checkStyleForRank(Style style) {
        if (style.getColor() != null) {
            int color = style.getColor().getRgb();

            if (color == GALACTIC_COLOR) {
                currentRank = "Galactic";
                return true;
            } else if (color == TITAN_COLOR) {
                currentRank = "Titan";
                return true;
            } else if (color == CELESTIAL_COLOR) {
                currentRank = "Celestial";
                return true;
            } else if (color == COMET_COLOR) {
                currentRank = "Comet";
                return true;
            }
        }
        return false;
    }

    private void loadRankFromConfig() {
        try {
            if (Files.exists(configPath)) {
                String savedRank = Files.readString(configPath).trim();
                if (!savedRank.isEmpty()) {
                    currentRank = savedRank;
                    System.out.println("Loaded rank from config: " + currentRank);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load rank config: " + e.getMessage());
        }
    }

    private void saveRankToConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, currentRank);
            System.out.println("Saved rank to config: " + currentRank);
        } catch (IOException e) {
            System.err.println("Failed to save rank config: " + e.getMessage());
        }
    }

    private void onCommandSent(String command) {
        String lowerCommand = command.toLowerCase().trim();
        if (lowerCommand.equals("fix all") && currentRank.equals("Comet")) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§cYou don't have access to this command!"));
            return;
        }
        setCooldownBasedOnCommand(command);
    }

    private void setCooldownBasedOnCommand(String command) {
        String lowerCommand = command.toLowerCase().trim();
        switch (lowerCommand) {
            case "fix" -> setCooldown("/fix", "fix");
            case "fix all" -> setCooldown("/fix", "fix_all");
            case "heal" -> setCooldown("/heal", "heal");
            case "feed", "eat" -> setCooldown("/feed", "feed");
            case "near" -> setCooldown("/near", "near");
        }
    }

    private void setCooldown(String boosterKey, String commandType) {
        int cooldown = switch (currentRank) {
            case "Titan" -> switch (commandType) {
                case "fix" -> 300;
                case "heal", "feed" -> 600;
                default -> 0;
            };
            case "Galactic" -> switch (commandType) {
                case "fix_all" -> 120;
                case "heal" -> 300;
                case "feed" -> 30;
                case "near" -> 30;
                default -> 0;
            };
            case "Celestial" -> switch (commandType) {
                case "feed" -> 180;
                case "fix_all" -> 90;
                case "heal" -> 180;
                case "near" -> 15;
                default -> 0;
            };
            default -> switch (commandType) {
                case "fix" -> 600;
                case "near" -> 30;
                default -> 0;
            };
        };

        if (cooldown > 0 && !boosters.containsKey(boosterKey)) {
            boosters.put(boosterKey, cooldown);
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

        boolean forceShow = UniversalGuiMover.isMovementModeActive();

        Map<String, Integer> visibleBoosters = new HashMap<>(boosters);
        if (forceShow) {
            boosterContainers.keySet().forEach(key -> visibleBoosters.putIfAbsent(key, 0));
        }

        if (!visibleBoosters.isEmpty()) {
            int maxX = screenWidth - scaledIconSize - 5;
            int minY = 5;
            int maxY = screenHeight - (scaledIconSize * 2) - 5;

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

            for (Map.Entry<String, Integer> entry : visibleBoosters.entrySet()) {
                String boosterType = entry.getKey();
                UniversalGuiMover.HudContainer container = boosterContainers.get(boosterType);
                if (container == null) continue;

                if (!UniversalGuiMover.isMovementModeActive()) {
                    container.x = Math.max(5, Math.min(container.x, maxX));
                    container.y = Math.max(minY, Math.min(container.y, maxY));
                }

                int timeLeft = entry.getValue();
                Identifier texture = getBoosterTexture(boosterType);
                String timeText = timeLeft > 0 ? formatCountdown(timeLeft) : "Cooldown";
                timeText += " (" + currentRank + ")";

                context.getMatrices().push();
                context.getMatrices().translate(container.x, container.y, 0);
                context.getMatrices().scale(globalScale, globalScale, 1);

                if (forceShow && timeLeft <= 0) {
                    context.setShaderColor(1, 1, 1, 0.4f);
                }

                context.drawTexture(texture, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                context.setShaderColor(1, 1, 1, 1);

                if (timeLeft > 0 || forceShow) {
                    float textYOffset = ICON_SIZE + 2;

                    context.getMatrices().push();
                    context.getMatrices().translate(ICON_SIZE / 2f, textYOffset, 0);
                    context.getMatrices().scale(NAME_SCALE, NAME_SCALE, 1);
                    int nameWidth = client.textRenderer.getWidth(boosterType);
                    context.drawText(client.textRenderer, boosterType, -nameWidth / 2, 0, TEXT_COLOR, true);
                    context.getMatrices().pop();

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
        return switch (boosterType) {
            case "2x Island XP Booster" -> ISLAND_BOOSTER_TEXTURE;
            case "/heal" -> HEAL_COOLDOWN_TEXTURE;
            case "/feed" -> FEED_COOLDOWN_TEXTURE;
            case "/fix" -> FIX_COOLDOWN_TEXTURE;
            case "/near" -> NEAR_COOLDOWN_TEXTURE;
            default -> XP_BOTTLE_TEXTURE;
        };
    }

    private String formatCountdown(int seconds) {
        return seconds > 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
    }
}
