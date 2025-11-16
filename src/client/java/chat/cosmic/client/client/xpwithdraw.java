package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class xpwithdraw implements ClientModInitializer {

    private static final Map<Character, Long> MULTIPLIERS = new HashMap<>();

    static {
        MULTIPLIERS.put('k', 1_000L);
        MULTIPLIERS.put('m', 1_000_000L);
        MULTIPLIERS.put('b', 1_000_000_000L);
        MULTIPLIERS.put('t', 1_000_000_000_000L); // trillion, optional
    }

    @Override
    public void onInitializeClient() {
        System.out.println("[CosmicV2] XPWithdraw mod initialized!");
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("§a[XPWithdraw] Client mod loaded."), false);
        }
    }

    /**
     * Handles outgoing /xpbottle or /xpb commands intercepted by the Mixin.
     * Returns true if the command was handled (and the original should be cancelled).
     */
    public static boolean handleOutgoingCommand(String command) {
        if (command == null || command.isEmpty()) return false;

        String trimmed = command.trim();
        if (trimmed.startsWith("xpbottle ") || trimmed.startsWith("xpb ")) {
            try {
                String[] parts = trimmed.split(" ", 2);
                if (parts.length < 2) return false;

                String base = parts[0];
                String amountStr = parts[1].trim();

                xpwithdraw instance = new xpwithdraw();
                long amount = instance.parseShorthandNumber(amountStr);
                String newCommand = (base.equals("xpb") ? "xpbottle " : base + " ") + amount;

                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.getNetworkHandler() != null) {
                    client.player.sendMessage(
                            Text.literal("§a[XPWithdraw] Converted " + amountStr + " → " + amount),
                            false
                    );
                    client.getNetworkHandler().sendChatCommand(newCommand);
                    System.out.println("[XPWithdraw] Rewrote " + command + " → " + newCommand);
                }

                return true; // handled
            } catch (Exception e) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("§c[XPWithdraw] Invalid number format."),
                            false
                    );
                }
                System.err.println("[XPWithdraw] Invalid command: " + command);
                return true; // cancel original
            }
        }

        return false;
    }

    private long parseShorthandNumber(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new NumberFormatException("Empty input");
        }

        String cleaned = input.trim().toLowerCase().replace(",", "").replace(" ", "");

        try {
            if (cleaned.matches(".*[kmbt]$")) {
                char suffix = cleaned.charAt(cleaned.length() - 1);
                double number = Double.parseDouble(cleaned.substring(0, cleaned.length() - 1));
                Long multiplier = MULTIPLIERS.get(suffix);

                if (multiplier == null) throw new NumberFormatException("Unknown suffix: " + suffix);

                long result = (long) (number * multiplier);
                if (result <= 0 || result > 100_000_000_000L) {
                    throw new NumberFormatException("Amount out of range");
                }

                return result;
            } else {
                long result = Long.parseLong(cleaned);
                if (result <= 0) throw new NumberFormatException("Amount must be positive");
                return result;
            }
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid number: " + input);
        }
    }
}
