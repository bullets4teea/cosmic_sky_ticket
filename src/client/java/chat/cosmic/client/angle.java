package chat.cosmic.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class angle implements ClientModInitializer {
    private static Float targetYaw = null;
    private static Float targetPitch = null;
    private static float currentSpeedYaw = 0f;
    private static float currentSpeedPitch = 0f;
    private static final float MAX_SPEED = 3.0f;
    private static final float MIN_SPEED = 0.2f;
    private static final Random random = new Random();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && targetYaw != null && targetPitch != null) {
                gradualRotatePlayerHead(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerCommands(dispatcher);
        });
    }

    private void gradualRotatePlayerHead(MinecraftClient client) {
        if (client.player != null) {
            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();

            float yawDiff = calculateShortestYawDiff(currentYaw, targetYaw);
            float pitchDiff = targetPitch - currentPitch;

            float distanceYaw = Math.abs(yawDiff);
            float distancePitch = Math.abs(pitchDiff);

            currentSpeedYaw = Math.max(MIN_SPEED, Math.min(MAX_SPEED, distanceYaw * 0.1f));
            currentSpeedPitch = Math.max(MIN_SPEED, Math.min(MAX_SPEED, distancePitch * 0.1f));

            if (Math.abs(yawDiff) > currentSpeedYaw) {
                currentYaw += Math.signum(yawDiff) * currentSpeedYaw;
            } else {
                currentYaw = targetYaw;
            }

            if (Math.abs(pitchDiff) > currentSpeedPitch) {
                currentPitch += Math.signum(pitchDiff) * currentSpeedPitch;
            } else {
                currentPitch = targetPitch;
            }

            currentYaw = normalizeAngle(currentYaw);

            client.player.setYaw(currentYaw);
            client.player.setPitch(currentPitch);
            client.player.setHeadYaw(currentYaw);
            client.player.bodyYaw = currentYaw;

            if (Math.abs(yawDiff) <= MIN_SPEED && Math.abs(pitchDiff) <= MIN_SPEED) {
                targetYaw = null;
                targetPitch = null;
                client.player.sendMessage(Text.of("Rotation complete."), false);
            }
        }
    }

    private float calculateShortestYawDiff(float currentYaw, float targetYaw) {
        float diff = targetYaw - currentYaw;
        while (diff < -180.0f) {
            diff += 360.0f;
        }
        while (diff >= 180.0f) {
            diff -= 360.0f;
        }
        return diff;
    }

    private float normalizeAngle(float angle) {
        while (angle < 0.0f) {
            angle += 360.0f;
        }
        while (angle >= 360.0f) {
            angle -= 360.0f;
        }
        return angle;
    }

    public static void setTargetDirection(float yaw, float pitch) {
        targetYaw = yaw;
        targetPitch = pitch;
        currentSpeedYaw = MAX_SPEED;
        currentSpeedPitch = MAX_SPEED;
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("setangle")
                .then(argument("yaw", FloatArgumentType.floatArg())
                        .then(argument("pitch", FloatArgumentType.floatArg())
                                .executes(context -> {
                                    float yaw = FloatArgumentType.getFloat(context, "yaw");
                                    float pitch = FloatArgumentType.getFloat(context, "pitch");
                                    setTargetDirection(yaw, pitch);
                                    MinecraftClient.getInstance().player.sendMessage(Text.of(
                                            "Rotating to Yaw: " + yaw + ", Pitch: " + pitch
                                    ), false);
                                    return 1;
                                })
                        )
                )
        );
    }
}
