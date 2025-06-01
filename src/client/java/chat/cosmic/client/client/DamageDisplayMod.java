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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.EntityHitResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DamageDisplayMod implements ModInitializer {
    public static final List<DamageEntry> DAMAGE_ENTRIES = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Float> ENTITY_LAST_HEALTH = new HashMap<>();
    private static final Path CONFIG_PATH = Paths.get("config", "damage_display.properties");
    private static int tickCounter = 0;
    private static final int HEALTH_CHECK_INTERVAL = 1;
    private static boolean enabled = true;
    private static KeyBinding toggleKeybind;
    private static UUID lastHitEntityId = null;
    private static long lastHitTime = 0;
    private static final long HIT_DETECTION_WINDOW = 250;
    private static boolean wasCriticalHit = false;
    private static final float TEXT_SCALE = 0.035f;
    private static final float FLOAT_SPEED = 0.7f;
    private static final long DISPLAY_DURATION = 1500;
    private static final float HORIZONTAL_SPREAD = 0.6f;
    private static final float VERTICAL_SPREAD = 0.6f;
    private static final float DEPTH_SPREAD = 0.6f;
    private static final float FRONT_OFFSET = 1.2f;
    private static final float SCALE_FACTOR_PER_ENTRY = 0.5f;

    @Override
    public void onInitialize() {
        loadConfig();

        toggleKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Damage Overlay Toggle",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_J,
                "adv"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKeybind.wasPressed()) {
                enabled = !enabled;
                saveConfig();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Damage Display: " + (enabled ? "§aON" : "§cOFF")), false);
                }
            }

            if (enabled) {
                checkAttackAndEntityHealth(client);
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(this::renderDamageNumbers);
    }

    private void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                Properties props = new Properties();
                props.load(Files.newInputStream(CONFIG_PATH));
                enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            Properties props = new Properties();
            props.setProperty("enabled", String.valueOf(enabled));
            Files.createDirectories(CONFIG_PATH.getParent());
            props.store(Files.newOutputStream(CONFIG_PATH), "Damage Display Mod Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkAttackAndEntityHealth(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        tickCounter++;
        boolean attacking = client.options.attackKey.isPressed();

        if (attacking && client.crosshairTarget instanceof EntityHitResult hitResult) {
            Entity hitEntity = hitResult.getEntity();
            if (hitEntity instanceof LivingEntity livingEntity) {
                lastHitEntityId = livingEntity.getUuid();
                lastHitTime = System.currentTimeMillis();
                wasCriticalHit = client.player.fallDistance > 0.0F &&
                        !client.player.isOnGround() &&
                        !client.player.isClimbing() &&
                        !client.player.isTouchingWater() &&
                        !client.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS) &&
                        !client.player.isRiding();

                if (!ENTITY_LAST_HEALTH.containsKey(lastHitEntityId)) {
                    ENTITY_LAST_HEALTH.put(lastHitEntityId, livingEntity.getHealth());
                }
            }
        }

        if (tickCounter % HEALTH_CHECK_INTERVAL == 0) {
            List<UUID> keysToRemove = new ArrayList<>();

            for (Map.Entry<UUID, Float> entry : ENTITY_LAST_HEALTH.entrySet()) {
                UUID entityId = entry.getKey();
                float lastKnownHealth = entry.getValue();

                client.world.getEntitiesByClass(LivingEntity.class,
                                client.player.getBoundingBox().expand(20),
                                e -> e.getUuid().equals(entityId))
                        .stream().findFirst().ifPresent(livingEntity -> {
                            float currentHealth = livingEntity.getHealth();

                            if (currentHealth < lastKnownHealth) {
                                float damageDealt = lastKnownHealth - currentHealth;
                                if (damageDealt > 0) {
                                    long currentTime = System.currentTimeMillis();
                                    boolean isRecentHit = entityId.equals(lastHitEntityId) &&
                                            (currentTime - lastHitTime < HIT_DETECTION_WINDOW);
                                    double distanceToPlayer = livingEntity.squaredDistanceTo(client.player);
                                    boolean isPlayerNearby = distanceToPlayer < 36;
                                    boolean isPlayer = livingEntity instanceof PlayerEntity;

                                    if ((isRecentHit || isPlayerNearby) && livingEntity != client.player) {
                                        boolean isCrit = isRecentHit && wasCriticalHit;
                                        DAMAGE_ENTRIES.add(new DamageEntry(livingEntity, damageDealt, client.player.getPos(), isCrit, false, isPlayer));
                                    }
                                }
                                ENTITY_LAST_HEALTH.put(entityId, currentHealth);
                            } else if (currentHealth > lastKnownHealth) {
                                float healingAmount = currentHealth - lastKnownHealth;
                                long currentTime = System.currentTimeMillis();
                                boolean isRecentHit = entityId.equals(lastHitEntityId) &&
                                        (currentTime - lastHitTime < HIT_DETECTION_WINDOW);
                                double distanceToPlayer = livingEntity.squaredDistanceTo(client.player);
                                boolean isPlayerNearby = distanceToPlayer < 36;
                                boolean isPlayer = livingEntity instanceof PlayerEntity;

                                if (healingAmount > 0 && (isRecentHit || isPlayerNearby) && livingEntity != client.player) {
                                    DAMAGE_ENTRIES.add(new DamageEntry(livingEntity, healingAmount, client.player.getPos(), false, true, isPlayer));
                                }
                                ENTITY_LAST_HEALTH.put(entityId, currentHealth);
                            }
                        });

                if (System.currentTimeMillis() - lastHitTime > 5000 && !entityId.equals(lastHitEntityId)) {
                    keysToRemove.add(entityId);
                }
            }

            for (UUID id : keysToRemove) {
                ENTITY_LAST_HEALTH.remove(id);
            }

            client.world.getEntitiesByClass(LivingEntity.class,
                            client.player.getBoundingBox().expand(20),
                            e -> e instanceof LivingEntity && !ENTITY_LAST_HEALTH.containsKey(e.getUuid()))
                    .forEach(entity -> ENTITY_LAST_HEALTH.put(entity.getUuid(), entity.getHealth()));
        }

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
        Vec3d camPos = camera.getPos();
        Vec3d entityPos = entry.getCurrentEntityPos();
        double distanceToEntity = entityPos.distanceTo(camPos);

        float elapsedTimeSeconds = (System.currentTimeMillis() - entry.getCreatedTime()) / 1000.0f;
        float verticalMovement = calculateVerticalMovement(elapsedTimeSeconds);

        Vec3d cameraForward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());

        Vec3d offset = new Vec3d(
                entry.getHorizontalOffset() * HORIZONTAL_SPREAD,
                entry.getVerticalOffset() * VERTICAL_SPREAD,
                entry.getDepthOffset() * DEPTH_SPREAD
        );

        float dynamicFrontOffset = FRONT_OFFSET * (float) Math.max(1.0, distanceToEntity * 0.3);
        Vec3d textPos = entityPos.add(
                cameraForward.x * dynamicFrontOffset + offset.x,
                entry.getEntityHeight() * 0.5f + offset.y + verticalMovement,
                cameraForward.z * dynamicFrontOffset + offset.z
        );

        matrices.translate(
                textPos.x - camPos.x,
                textPos.y - camPos.y,
                textPos.z - camPos.z
        );

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        float scale = TEXT_SCALE * (float) (1.0 + 1.0 / (distanceToEntity + 1.0));
        scale = Math.min(scale, 0.065f);

        List<DamageEntry> sameEntityEntries = DAMAGE_ENTRIES.stream()
                .filter(e -> e.entityUUID.equals(entry.entityUUID) && !e.isExpired())
                .sorted((e1, e2) -> Long.compare(e2.createdTime, e1.createdTime))
                .collect(Collectors.toList());

        int entryIndex = sameEntityEntries.indexOf(entry);
        float orderScale = (float) Math.pow(SCALE_FACTOR_PER_ENTRY, entryIndex);
        scale *= orderScale;

        float animationProgress = elapsedTimeSeconds / (DISPLAY_DURATION / 1000.0f);
        if (animationProgress < 0.2f) {
            scale *= (0.8f + animationProgress);
        } else if (animationProgress > 0.7f) {
            scale *= (1.0f - (animationProgress - 0.7f) * 0.7f);
        }

        matrices.scale(-scale, -scale, scale);

        TextRenderer textRenderer = client.textRenderer;
        String text = entry.isHealing() ?
                String.format("+%d ❤", Math.round(amount)) :
                String.format("-%d ❤", Math.round(amount));

        int colorWithAlpha = getTextColor(entry, animationProgress);

        textRenderer.draw(
                Text.literal(text),
                -textRenderer.getWidth(text) / 2,
                -textRenderer.fontHeight / 2,
                colorWithAlpha,
                true,
                matrices.peek().getPositionMatrix(),
                context.consumers(),
                TextRenderer.TextLayerType.SEE_THROUGH,
                0,
                0xF000F0
        );

        matrices.pop();
    }

    private float calculateVerticalMovement(float elapsedTime) {
        if (elapsedTime < 0.2f) {
            return elapsedTime * 2.5f;
        }
        return 0.5f - (elapsedTime - 0.2f) * 0.4f;
    }

    private int getTextColor(DamageEntry entry, float progress) {
        int color;
        if (entry.isHealing()) {
            color = 0xFF00FF00;
        } else {
            color = entry.isPlayer() ? 0xFFFF0000 : 0xFFFFFFFF;
        }
        int alpha = (int)(255 * (1.0f - Math.max(0, (progress - 0.7f) / 0.3f)));
        return (color & 0x00FFFFFF) | (alpha << 24);
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
        private final boolean isCritical;
        private final boolean isHealing;
        private final boolean isPlayer;
        public final Vec3d attackPos;

        public DamageEntry(LivingEntity entity, float amount, Vec3d attackPos, boolean isCritical, boolean isHealing, boolean isPlayer) {
            this.entityUUID = entity.getUuid();
            this.entityPos = entity.getPos();
            this.entityHeight = entity.getHeight();
            this.entityWidth = entity.getWidth();
            this.attackPos = attackPos;
            this.createdTime = System.currentTimeMillis();
            this.amount = amount;
            this.isCritical = isCritical;
            this.isHealing = isHealing;
            this.isPlayer = isPlayer;
            this.horizontalOffset = (RANDOM.nextFloat() * 2 - 1);
            this.verticalOffset = (RANDOM.nextFloat() * 2 - 1);
            this.depthOffset = (RANDOM.nextFloat() * 2 - 1);
        }

        public long getCreatedTime() { return createdTime; }
        public float getAmount() { return amount; }
        public float getHorizontalOffset() { return horizontalOffset; }
        public float getVerticalOffset() { return verticalOffset; }
        public float getDepthOffset() { return depthOffset; }
        public boolean isCritical() { return isCritical; }
        public boolean isHealing() { return isHealing; }
        public boolean isPlayer() { return isPlayer; }
        public boolean isExpired() { return System.currentTimeMillis() - createdTime > DISPLAY_DURATION; }

        public Vec3d getCurrentEntityPos() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                LivingEntity entity = client.world.getEntitiesByClass(LivingEntity.class,
                                new net.minecraft.util.math.Box(
                                        entityPos.x - 10, entityPos.y - 10, entityPos.z - 10,
                                        entityPos.x + 10, entityPos.y + 10, entityPos.z + 10),
                                e -> e.getUuid().equals(entityUUID))
                        .stream().findFirst().orElse(null);

                if (entity != null) {
                    entityPos = entity.getPos();
                }
            }
            return entityPos;
        }

        public float getEntityHeight() { return entityHeight; }
        public float getEntityWidth() { return entityWidth; }
    }
}