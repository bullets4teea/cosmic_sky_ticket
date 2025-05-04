package chat.cosmic.client.client;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.EntityHitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;

public class DamageDisplayMod implements ModInitializer {
    public static final List<DamageEntry> DAMAGE_ENTRIES = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Float> ENTITY_LAST_HEALTH = new HashMap<>();
    private static int tickCounter = 0;
    private static final int HEALTH_CHECK_INTERVAL = 1; // Check health every tick

    // Toggle state
    private static boolean enabled = true;
    private static KeyBinding toggleKeybind;

    // Tracking last hit entity
    private static UUID lastHitEntityId = null;
    private static long lastHitTime = 0;
    private static final long HIT_DETECTION_WINDOW = 250; // ms to associate damage with hit
    private static boolean wasCriticalHit = false;

    // Config values
    private static final float TEXT_SCALE = 0.035f;
    private static final float FLOAT_SPEED = 0.7f;
    private static final long DISPLAY_DURATION = 1500; // 1.5 seconds
    private static final float HORIZONTAL_SPREAD = 0.6f;
    private static final float VERTICAL_SPREAD = 0.6f;
    private static final float DEPTH_SPREAD = 0.6f;
    private static final float FRONT_OFFSET = 1.2f;

    // Colors for damage text
    private static final int[] DAMAGE_COLORS = {
            0xFFFF2222, // Red
            0xFFFF3333, // Lighter red
            0xFFFF0000  // Pure red
    };

    // Colors for critical hits (orange)
    private static final int[] CRIT_COLORS = {
            0xFFFFA500, // Orange
            0xFFFF8C00, // Darker orange
            0xFFFF6347  // Tomato
    };

    // Colors for healing (green)
    private static final int[] HEAL_COLORS = {
            0xFF22FF22, // Bright green
            0xFF00FF00, // Pure green
            0xFF00AA00  // Darker green
    };

    @Override
    public void onInitialize() {
        // Register keybind (default: G key)
        toggleKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Damage over lay toggle ",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_J,
                "adv"
        ));

        // Register client tick event for both attack detection and keybind handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Handle keybind toggle
            if (toggleKeybind.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Damage Display: " + (enabled ? "§aON" : "§cOFF")), false);
                }
            }

            // Only process damage if enabled
            if (enabled) {
                checkAttackAndEntityHealth(client);
            }
        });

        // Register render event (will check enabled state internally)
        WorldRenderEvents.AFTER_ENTITIES.register(this::renderDamageNumbers);
    }

    private void checkAttackAndEntityHealth(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        tickCounter++;

        // Check for direct attack detection (left mouse button)
        boolean attacking = client.options.attackKey.isPressed();

        // If attacking and there's a hit entity
        if (attacking && client.crosshairTarget instanceof EntityHitResult hitResult) {
            Entity hitEntity = hitResult.getEntity();
            if (hitEntity instanceof LivingEntity livingEntity) {
                lastHitEntityId = livingEntity.getUuid();
                lastHitTime = System.currentTimeMillis();

                // Check if this was a critical hit (player is falling and has velocity)
                wasCriticalHit = client.player.fallDistance > 0.0F &&
                        !client.player.isOnGround() &&
                        !client.player.isClimbing() &&
                        !client.player.isTouchingWater() &&
                        !client.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS) &&
                        !client.player.isRiding();

                // Store current health to track changes
                if (!ENTITY_LAST_HEALTH.containsKey(lastHitEntityId)) {
                    ENTITY_LAST_HEALTH.put(lastHitEntityId, livingEntity.getHealth());
                }
            }
        }

        // Only check health periodically to reduce overhead
        if (tickCounter % HEALTH_CHECK_INTERVAL == 0) {
            // Process all tracked entities for health changes
            List<UUID> keysToRemove = new ArrayList<>();

            for (Map.Entry<UUID, Float> entry : ENTITY_LAST_HEALTH.entrySet()) {
                UUID entityId = entry.getKey();
                float lastKnownHealth = entry.getValue();

                // Find the entity in the world
                client.world.getEntitiesByClass(LivingEntity.class,
                                client.player.getBoundingBox().expand(20), // Search radius
                                e -> e.getUuid().equals(entityId))
                        .stream().findFirst().ifPresent(livingEntity -> {
                            float currentHealth = livingEntity.getHealth();

                            // If health decreased, it took damage
                            if (currentHealth < lastKnownHealth) {
                                float damageDealt = lastKnownHealth - currentHealth;

                                // Create damage entry
                                if (damageDealt > 0) {
                                    // Only create entry if recent hit or if player is nearby
                                    long currentTime = System.currentTimeMillis();
                                    boolean isRecentHit = entityId.equals(lastHitEntityId) &&
                                            (currentTime - lastHitTime < HIT_DETECTION_WINDOW);

                                    double distanceToPlayer = livingEntity.squaredDistanceTo(client.player);
                                    boolean isPlayerNearby = distanceToPlayer < 36; // 6 blocks

                                    if (isRecentHit || isPlayerNearby) {
                                        boolean isCrit = isRecentHit && wasCriticalHit;
                                        DAMAGE_ENTRIES.add(new DamageEntry(livingEntity, damageDealt, client.player.getPos(), isCrit, false));
                                    }
                                }

                                // Update the stored health
                                ENTITY_LAST_HEALTH.put(entityId, currentHealth);
                            } else if (currentHealth > lastKnownHealth) {
                                // Health increased - healing!
                                float healingAmount = currentHealth - lastKnownHealth;

                                // Create healing entry
                                long currentTime = System.currentTimeMillis();
                                boolean isRecentHit = entityId.equals(lastHitEntityId) &&
                                        (currentTime - lastHitTime < HIT_DETECTION_WINDOW);
                                double distanceToPlayer = livingEntity.squaredDistanceTo(client.player);
                                boolean isPlayerNearby = distanceToPlayer < 36; // 6 blocks

                                if (healingAmount > 0 && (isRecentHit || isPlayerNearby)) {
                                    DAMAGE_ENTRIES.add(new DamageEntry(livingEntity, healingAmount, client.player.getPos(), false, true));
                                }

                                // Update the stored health
                                ENTITY_LAST_HEALTH.put(entityId, currentHealth);
                            }
                        });

                // Remove entities we haven't seen in a while (5 seconds)
                if (System.currentTimeMillis() - lastHitTime > 5000 && !entityId.equals(lastHitEntityId)) {
                    keysToRemove.add(entityId);
                }
            }

            // Clean up tracked entities
            for (UUID id : keysToRemove) {
                ENTITY_LAST_HEALTH.remove(id);
            }

            // Add any new entities in range to track
            client.world.getEntitiesByClass(LivingEntity.class,
                            client.player.getBoundingBox().expand(20),
                            e -> e instanceof LivingEntity && !ENTITY_LAST_HEALTH.containsKey(e.getUuid()))
                    .forEach(entity -> ENTITY_LAST_HEALTH.put(entity.getUuid(), entity.getHealth()));
        }

        // Reset critical hit flag after each tick
        wasCriticalHit = false;
    }

    private void renderDamageNumbers(WorldRenderContext context) {
        if (!enabled) return;

        DAMAGE_ENTRIES.removeIf(DamageEntry::isExpired);

        for (DamageEntry entry : DAMAGE_ENTRIES) {
            renderFloatingDamage(context, entry);
        }
    }

    private void renderFloatingDamage(WorldRenderContext context, DamageEntry entry) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();

        float amount = entry.getAmount();
        if (amount <= 0) return;

        matrices.push();

        // Get the camera's yaw and position
        float cameraYaw = camera.getYaw();
        float cameraPitch = camera.getPitch();
        Vec3d camPos = camera.getPos();

        // Calculate the elapsed time for animation
        float elapsedTimeSeconds = (System.currentTimeMillis() - entry.getCreatedTime()) / 1000.0f;

        // Calculate vertical movement (floating up slightly and then down)
        float verticalMovement;
        float animationProgress = elapsedTimeSeconds / (DISPLAY_DURATION / 1000.0f);

        if (animationProgress < 0.2f) {
            // Initial rise phase
            verticalMovement = animationProgress * 0.5f;
        } else {
            // Fall phase
            verticalMovement = 0.1f - (animationProgress - 0.2f) * 0.5f;
        }

        Vec3d entityPos = entry.getCurrentEntityPos();

        Vec3d offset = new Vec3d(
                entry.getHorizontalOffset(),
                entry.getVerticalOffset() + verticalMovement,
                entry.getDepthOffset()
        );

        Vec3d directionToCamera = camPos.subtract(entityPos).normalize();

        float entityWidth = entry.getEntityWidth();
        Vec3d textPos = entityPos.add(
                // Base position in front (toward camera) + random horizontal spread
                directionToCamera.x * FRONT_OFFSET + offset.x * HORIZONTAL_SPREAD,
                // Vertical position: halfway up entity height + random vertical offset
                entry.getEntityHeight() * 0.5f + offset.y * VERTICAL_SPREAD,
                // Z position: in front (toward camera) + random depth spread
                directionToCamera.z * FRONT_OFFSET + offset.z * DEPTH_SPREAD
        );

        // Translate to camera space
        matrices.translate(
                textPos.x - camPos.x,
                textPos.y - camPos.y,
                textPos.z - camPos.z
        );

        // Always face the text toward the player
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        // Scale based on progress - slightly grow at start then shrink
        float scale = TEXT_SCALE;
        if (animationProgress < 0.2f) {
            scale *= (0.8f + animationProgress);
        } else if (animationProgress > 0.7f) {
            scale *= (1.0f - (animationProgress - 0.7f) * 0.7f);
        }

        matrices.scale(-scale, -scale, scale);

        // Draw text
        TextRenderer textRenderer = client.textRenderer;
        String text;
        if (entry.isHealing()) {
            text = String.format("+%d ❤", Math.round(amount));
        } else {
            text = String.format("-%d ❤", Math.round(amount));
        }
        float width = textRenderer.getWidth(text);

        // Get color based on damage type
        int textColor;
        if (entry.isHealing()) {
            textColor = HEAL_COLORS[entry.getColorIndex()];
        } else if (entry.isCritical()) {
            textColor = CRIT_COLORS[entry.getColorIndex()];
        } else {
            textColor = DAMAGE_COLORS[entry.getColorIndex()];
        }

        // Calculate opacity based on lifetime (fade out at the end)
        int alpha = 255;
        if (animationProgress > 0.7f) {
            alpha = (int)(255 * (1.0f - (animationProgress - 0.7f) / 0.3f));
        }
        int colorWithAlpha = (textColor & 0x00FFFFFF) | (alpha << 24);

        // Draw with shadow
        textRenderer.draw(
                text,
                -width / 2,
                -textRenderer.fontHeight / 2,
                colorWithAlpha,
                true, // shadow enabled
                matrices.peek().getPositionMatrix(),
                context.consumers(),
                TextRenderer.TextLayerType.NORMAL,
                0,
                0xF000F0
        );

        matrices.pop();
    }

    private static class DamageEntry {
        private final UUID entityUUID;
        private Vec3d entityPos;
        private final float entityHeight;
        private final float entityWidth;
        private final long createdTime;
        private final float amount;
        private final float horizontalOffset;
        private final float verticalOffset;
        private final float depthOffset;
        private final int colorIndex;
        private final boolean isCritical;
        private final boolean isHealing;
        public final Vec3d attackPos;

        public DamageEntry(LivingEntity entity, float amount, Vec3d attackPos, boolean isCritical, boolean isHealing) {
            this.entityUUID = entity.getUuid();
            this.entityPos = entity.getPos();
            this.entityHeight = entity.getHeight();
            this.entityWidth = entity.getWidth();
            this.attackPos = attackPos;
            this.createdTime = System.currentTimeMillis();
            this.amount = amount;
            this.isCritical = isCritical;
            this.isHealing = isHealing;

            // Random offsets to make damage numbers spread out around the entity hitbox
            this.horizontalOffset = (RANDOM.nextFloat() * 2 - 1);
            this.verticalOffset = (RANDOM.nextFloat() * 2 - 1) * VERTICAL_SPREAD;
            this.depthOffset = (RANDOM.nextFloat() * 2 - 1);

            // Random color index
            if (isHealing) {
                this.colorIndex = RANDOM.nextInt(HEAL_COLORS.length);
            } else if (isCritical) {
                this.colorIndex = RANDOM.nextInt(CRIT_COLORS.length);
            } else {
                this.colorIndex = RANDOM.nextInt(DAMAGE_COLORS.length);
            }
        }

        public long getCreatedTime() {
            return createdTime;
        }

        public Vec3d getCurrentEntityPos() {
            // Try to get the current position of the entity if it's still alive
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                LivingEntity entity = client.world.getEntitiesByClass(LivingEntity.class,
                                new net.minecraft.util.math.Box(
                                        entityPos.x - 10, entityPos.y - 10, entityPos.z - 10,
                                        entityPos.x + 10, entityPos.y + 10, entityPos.z + 10),
                                e -> e.getUuid().equals(entityUUID))
                        .stream().findFirst().orElse(null);

                if (entity != null) {
                    // Update the stored position
                    entityPos = entity.getPos();
                }
            }
            return entityPos;
        }

        public float getEntityHeight() {
            return entityHeight;
        }

        public float getEntityWidth() {
            return entityWidth;
        }

        public float getAmount() {
            return amount;
        }

        public float getHorizontalOffset() {
            return horizontalOffset;
        }

        public float getVerticalOffset() {
            return verticalOffset;
        }

        public float getDepthOffset() {
            return depthOffset;
        }

        public int getColorIndex() {
            return colorIndex;
        }

        public boolean isCritical() {
            return isCritical;
        }

        public boolean isHealing() {
            return isHealing;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdTime > DISPLAY_DURATION;
        }
    }
}