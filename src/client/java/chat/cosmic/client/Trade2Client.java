package chat.cosmic.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class Trade2Client implements ClientModInitializer {
    private boolean wereKeysPressed = false; // Track previous key state

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check if BOTH sneak (shift) and use (right-click) keys are pressed
            boolean areKeysPressed = client.options.sneakKey.isPressed() && client.options.useKey.isPressed();


            if (areKeysPressed && !wereKeysPressed) {
                handleShiftRightClick(client);
            }

            wereKeysPressed = areKeysPressed; // Update state
        });
    }

    private void handleShiftRightClick(MinecraftClient client) {
        if (client.player == null || client.crosshairTarget == null) return;

        // Verify the player is looking at another player
        if (client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) client.crosshairTarget;
            if (entityHit.getEntity() instanceof PlayerEntity) {
                String targetName = entityHit.getEntity().getName().getString();
                sendTradeRequest(targetName);
            }
        }
    }

    private void sendTradeRequest(String targetName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            // Send the /trade command to the server
            client.player.networkHandler.sendChatCommand("trade " + targetName);

            // Optional: Feedback message (green text)
            client.player.sendMessage(Text.of("§aSent trade request to " + targetName), false);
        }
    }
}