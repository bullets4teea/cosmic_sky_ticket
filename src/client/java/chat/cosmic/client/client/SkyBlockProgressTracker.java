package chat.cosmic.client.client;

import chat.cosmic.client.client.KeyBinds.KeyBinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

public class SkyBlockProgressTracker implements ClientModInitializer {
    private static boolean displayActive = true;
    private static boolean hudEnabled = true;
    private static final UniversalGuiMover.HudContainer hudContainer = new UniversalGuiMover.HudContainer(5, 0, 150, 32, 2);


    private static double currentProgress = -1;
    private static long lastProgressUpdate = 0;
    private static long estimatedCompletion = 0;
    private static double progressDifference = 0;
    private static long elapsedProgressTime = 0;

    @Override
    public void onInitializeClient() {
        hudEnabled = SettingsManager.getToggleSettings().getOrDefault("SkyBlock Progress Tracker", true);
        UniversalGuiMover.trackHudContainer("skyblockProgressTracker", hudContainer);


        ClientTickEvents.END_CLIENT_TICK.register(clientInstance -> {
            while (KeyBinds.getToggleSkyBlockTracker() != null && KeyBinds.getToggleSkyBlockTracker().wasPressed()) {
                displayActive = !displayActive;
                hudEnabled = displayActive;
                SettingsManager.getToggleSettings().put("SkyBlock Progress Tracker", displayActive);
                SettingsManager.saveSettings();
            }
        });


        HudRenderCallback.EVENT.register(this::drawProgressDisplay);
    }

    private void drawProgressDisplay(DrawContext renderer, float partialTicks) {
        MinecraftClient gameClient = MinecraftClient.getInstance();

        if (!hudEnabled || !displayActive) return;
        if (gameClient.player == null || gameClient.world == null) return;


        boolean isMovingGUI = UniversalGuiMover.isMovementModeActive();


        boolean inSkyblockWorld = gameClient.world.getRegistryKey().getValue().toString().contains("skyblock_world") ||
                gameClient.world.getRegistryKey().getValue().toString().contains("the_nether");


        if (!inSkyblockWorld && !isMovingGUI) return;

        if (inSkyblockWorld) {
            Scoreboard worldScoreboard = gameClient.world.getScoreboard();
            ScoreboardObjective sidebarObjective = worldScoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

            if (sidebarObjective != null) {

                double detectedProgress = extractProgressFromScoreboard(worldScoreboard, sidebarObjective);


                calculateLevelCompletionEstimate(detectedProgress);
            }
        }


        int windowHeight = renderer.getScaledWindowHeight();
        renderProgressUI(renderer, gameClient, windowHeight);
    }

    private double extractProgressFromScoreboard(Scoreboard board, ScoreboardObjective objective) {
        for (ScoreboardEntry scoreEntry : board.getScoreboardEntries(objective)) {
            if (scoreEntry.hidden()) continue;

            Team playerTeam = board.getScoreHolderTeam(scoreEntry.owner());
            Text formattedText = Team.decorateName(playerTeam, scoreEntry.name());
            String textContent = formattedText.getString();

            if (textContent.contains("%")) {
                try {
                    return Double.parseDouble(textContent.split(" ")[0].replace("%", ""));
                } catch (NumberFormatException ignore) {

                }
            }
        }
        return -1;
    }

    private void calculateLevelCompletionEstimate(double newProgress) {
        if (newProgress != -1 && newProgress != currentProgress) {
            long currentTimestamp = System.currentTimeMillis();
            progressDifference = newProgress - currentProgress;
            currentProgress = newProgress;

            double remainingProgress = 100 - newProgress;
            if (progressDifference > 0) {
                double progressInstances = remainingProgress / progressDifference;
                elapsedProgressTime = currentTimestamp - lastProgressUpdate;
                estimatedCompletion = (long) (progressInstances * elapsedProgressTime);
            }
            lastProgressUpdate = currentTimestamp;
        }
    }

    private void renderProgressUI(DrawContext renderer, MinecraftClient client, int screenHeight) {
        int displayX = hudContainer.x;
        int displayY = hudContainer.y;
        float scale = UniversalGuiMover.getGlobalTextScale();

        hudContainer.lineCount = 2;
        hudContainer.baseWidth = 150;
        hudContainer.baseHeight = 32;

        renderer.getMatrices().push();
        renderer.getMatrices().translate(displayX, displayY, 0);
        renderer.getMatrices().scale(scale, scale, 1.0f);


        renderer.drawText(client.textRenderer, "§9SkyBlock Tracker", 0, 0, 0x5555FF, true);
        renderer.drawText(client.textRenderer, "Level ETA: " + formatDuration(estimatedCompletion), 0, 12, 0xFFFFFF, true);

        renderer.getMatrices().pop();
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "Processing...";

        long totalSeconds = millis / 1000;
        long totalMinutes = totalSeconds / 60;
        long totalHours = totalMinutes / 60;
        long totalDays = totalHours / 24;

        totalSeconds %= 60;
        totalMinutes %= 60;
        totalHours %= 24;

        StringBuilder formatted = new StringBuilder();
        if (totalDays > 0) {
            formatted.append(totalDays).append("d ");
        }
        if (totalHours > 0) {
            formatted.append(totalHours).append("h ");
        }
        if (totalMinutes > 0 && totalDays < 1) {
            formatted.append(totalMinutes).append("m ");
        }
        if (totalSeconds > 0 && totalDays < 1 && totalHours < 1) {
            formatted.append(totalSeconds).append("s");
        }

        String result = formatted.toString().trim();
        return result.isEmpty() ? "0s" : result;
    }


    public static boolean isDisplayEnabled() {
        return displayActive;
    }

    public static void setDisplayEnabled(boolean enabled) {
        displayActive = enabled;
        hudEnabled = enabled;
    }

    public static void setHudEnabled(boolean enabled) {
        hudEnabled = enabled;
    }
}