package chat.cosmic.client.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import chat.cosmic.client.client.TrophyGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TrophyTrackerMod implements ClientModInitializer {

    public static final String MOD_ID = "trophytracker";
    private static final Map<String, Integer> playerTrophies = new ConcurrentHashMap<>();
    private static int totalPoints = 0;

    // Updated pattern to handle formatting codes and different message formats
    private static final Pattern TROPHY_PATTERN = Pattern.compile(
            "(?:§[0-9a-fk-or])*\\+ (\\d+) (?:§[0-9a-fk-or])*Trophy Points? \\(([^)]+)\\)(?:§[0-9a-fk-or])*",
            Pattern.CASE_INSENSITIVE
    );

    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        System.out.println("TrophyTracker client mod initialized!");

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trophytracker.opengui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.trophytracker"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                openTrophyGui();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerCommands(dispatcher);
        });
    }

    public static void openTrophyGui() {
        MinecraftClient.getInstance().setScreen(new TrophyGuiScreen());
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("trophy")
                .then(literal("gui")
                        .executes(context -> {
                            openTrophyGui();
                            return 1;
                        }))
                .then(literal("check")
                        .executes(this::checkTrophies)
                        .then(argument("type", StringArgumentType.string())
                                .executes(this::checkSpecificTrophy)))
                .then(literal("reset")
                        .executes(this::resetAllTrophies)
                        .then(argument("type", StringArgumentType.string())
                                .executes(this::resetSpecificTrophy))));

        dispatcher.register(literal("trophies").executes(this::checkTrophies));
        dispatcher.register(literal("resetpoints").executes(this::resetAllTrophies));
    }

    public static void processTrophyMessage(String message) {
        System.out.println("Processing message: " + message);

        // Remove formatting codes for easier pattern matching
        String cleanMessage = removeFormattingCodes(message);
        System.out.println("Clean message: " + cleanMessage);

        Matcher matcher = TROPHY_PATTERN.matcher(cleanMessage);

        if (matcher.find()) {
            System.out.println("Trophy message detected!");
            int points = Integer.parseInt(matcher.group(1));
            String type = matcher.group(2);

            // Clean up the type (remove any remaining formatting)
            type = removeFormattingCodes(type).trim();

            addTrophyPoints(type, points);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("✓ Tracked: +" + points + " " + type + " Trophy Points!")
                        .formatted(Formatting.GREEN), false);
            }
        } else {
            System.out.println("No trophy pattern match found.");

            // Try alternative patterns if the main one fails
            tryAlternativePatterns(cleanMessage);
        }
    }

    /**
     * Remove Minecraft formatting codes from a string
     */
    private static String removeFormattingCodes(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * Try alternative patterns for trophy detection
     */
    private static void tryAlternativePatterns(String message) {
        // Pattern 1: Just look for numbers followed by "Trophy Points"
        Pattern altPattern1 = Pattern.compile("(\\d+) Trophy Points? \\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = altPattern1.matcher(message);

        if (matcher1.find()) {
            System.out.println("Alternative pattern 1 matched!");
            int points = Integer.parseInt(matcher1.group(1));
            String type = removeFormattingCodes(matcher1.group(2)).trim();
            addTrophyPoints(type, points);
            return;
        }

        // Pattern 2: Look for "+ number Trophy Points"
        Pattern altPattern2 = Pattern.compile("\\+ (\\d+) Trophy Points? \\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = altPattern2.matcher(message);

        if (matcher2.find()) {
            System.out.println("Alternative pattern 2 matched!");
            int points = Integer.parseInt(matcher2.group(1));
            String type = removeFormattingCodes(matcher2.group(2)).trim();
            addTrophyPoints(type, points);
            return;
        }

        // Pattern 3: Just look for any number and "Trophy" in the message
        Pattern altPattern3 = Pattern.compile("(\\d+).*Trophy.*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher3 = altPattern3.matcher(message);

        if (matcher3.find()) {
            System.out.println("Alternative pattern 3 matched!");
            int points = Integer.parseInt(matcher3.group(1));
            String type = removeFormattingCodes(matcher3.group(2)).trim();
            addTrophyPoints(type, points);
        }
    }

    private int checkTrophies(CommandContext<FabricClientCommandSource> context) {
        if (playerTrophies.isEmpty()) {
            context.getSource().sendFeedback(Text.literal("You have no trophy points yet!").formatted(Formatting.YELLOW));
            return 0;
        }

        context.getSource().sendFeedback(Text.literal("=== Your Trophy Points ===").formatted(Formatting.GOLD));

        for (Map.Entry<String, Integer> entry : playerTrophies.entrySet()) {
            String type = entry.getKey();
            int points = entry.getValue();
            context.getSource().sendFeedback(Text.literal(type + ": " + points + " points").formatted(Formatting.AQUA));
        }

        context.getSource().sendFeedback(Text.literal("Total Points: " + totalPoints).formatted(Formatting.GREEN));

        return 1;
    }

    private int checkSpecificTrophy(CommandContext<FabricClientCommandSource> context) {
        String type = StringArgumentType.getString(context, "type");
        int points = playerTrophies.getOrDefault(type, 0);
        context.getSource().sendFeedback(Text.literal(type + " Trophy Points: " + points).formatted(Formatting.AQUA));
        return 1;
    }

    private int resetAllTrophies(CommandContext<FabricClientCommandSource> context) {
        resetAllTrophies();
        context.getSource().sendFeedback(Text.literal("All trophy points have been reset!").formatted(Formatting.RED));
        return 1;
    }

    private int resetSpecificTrophy(CommandContext<FabricClientCommandSource> context) {
        String type = StringArgumentType.getString(context, "type");
        int removedPoints = playerTrophies.getOrDefault(type, 0);
        playerTrophies.remove(type);

        totalPoints = Math.max(0, totalPoints - removedPoints);

        context.getSource().sendFeedback(Text.literal(type + " trophy points have been reset! (Removed " + removedPoints + " points)")
                .formatted(Formatting.RED));
        return 1;
    }

    public static void addTrophyPoints(String type, int points) {
        playerTrophies.merge(type, points, Integer::sum);
        totalPoints += points;

        // Debug output
        System.out.println("Added " + points + " points for " + type);
        System.out.println("Total points: " + totalPoints);
    }

    public static int getTotalPoints() {
        return totalPoints;
    }

    public static Map<String, Integer> getTrophies() {
        return new HashMap<>(playerTrophies);
    }

    public static void resetAllTrophies() {
        playerTrophies.clear();
        totalPoints = 0;
    }
}