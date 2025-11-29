package chat.cosmic.client.client.playerheadcooldown;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

public class PlayerHeadCooldownMod implements ClientModInitializer {
    private static final float TEXT_SCALE = 0.60f;
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Pattern COOLDOWN_PATTERN = Pattern.compile("PET: Ability on cooldown: (\\d+)m (\\d+)s");

    private static final Map<String, Long> itemCooldowns = new HashMap<>();


    private static String lastActivatedPetId = null;
    private static long lastActivationTime = 0;
    private static int lastActivatedSlot = -1;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateCooldowns();
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            renderCooldownOverlay(context, tickDelta);
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            ActivePetEffectsHud.render(context, tickDelta);
        });
    }

    public static void handlePetActivationMessage(String petName) {
        if (client.player == null) return;

        System.out.println("Detected pet activation: " + petName);


        ItemStack foundPet = null;
        int foundSlot = -1;
        String foundPetId = null;


        if (lastActivatedPetId != null && (System.currentTimeMillis() - lastActivationTime) < 2000) {
            for (int i = 0; i < client.player.getInventory().size(); i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (isPet(stack)) {
                    String stackPetId = getPersistentItemId(stack);
                    if (stackPetId != null && stackPetId.equals(lastActivatedPetId)) {
                        foundPet = stack;
                        foundSlot = i;
                        foundPetId = stackPetId;
                        break;
                    }
                }
            }
        }

        // If not found by ID, try by name and slot
        if (foundPet == null && lastActivatedSlot != -1 && (System.currentTimeMillis() - lastActivationTime) < 2000) {
            ItemStack stack = client.player.getInventory().getStack(lastActivatedSlot);
            if (isPet(stack)) {
                String stackPetName = getPetName(stack);
                if (stackPetName != null && stackPetName.equals(petName)) {
                    foundPet = stack;
                    foundSlot = lastActivatedSlot;
                    foundPetId = getPersistentItemId(stack);
                }
            }
        }


        if (foundPet == null) {
            for (int i = 0; i < client.player.getInventory().size(); i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (isPet(stack)) {
                    String stackPetName = getPetName(stack);
                    if (stackPetName != null && stackPetName.equals(petName)) {

                        if (!isOnCooldown(stack)) {
                            foundPet = stack;
                            foundSlot = i;
                            foundPetId = getPersistentItemId(stack);
                            break;
                        }
                    }
                }
            }
        }

        if (foundPet != null) {
            int petLevel = ActivePetEffectsHud.getPetLevel(foundPet);
            ActivePetEffectsHud.activatePetEffect(petName, petLevel, foundPet);
            ActivePetEffectsHud.initializePetHud();


            lastActivatedPetId = foundPetId;
            lastActivatedSlot = foundSlot;
            lastActivationTime = System.currentTimeMillis();

            System.out.println("Activated HUD for: " + petName + " Level: " + petLevel + " Slot: " + foundSlot);
        } else {
            System.out.println("Could not find pet in inventory: " + petName);
        }
    }


    public static void trackPetUsage(ItemStack pet, int slot) {
        String persistentId = getPersistentItemId(pet);
        if (persistentId != null) {
            lastActivatedPetId = persistentId;
            lastActivatedSlot = slot;
            lastActivationTime = System.currentTimeMillis();
            System.out.println("Tracked pet usage: " + getPetName(pet) + " in slot " + slot + " ID: " + persistentId);
        }
    }

    public static void startCooldown(ItemStack pet, int slotIndex) {
        if (PetManager.isPet(pet)) {
            String persistentId = getPersistentItemId(pet);
            if (persistentId != null) {
                long cooldownDuration = PetManager.getCooldownDuration(pet);
                itemCooldowns.put(persistentId, System.currentTimeMillis() + cooldownDuration);

                if (!pet.hasNbt()) {
                    pet.setNbt(new NbtCompound());
                }
                pet.getNbt().putLong("lastUsed", System.currentTimeMillis());


                trackPetUsage(pet, slotIndex);
            }
        }
    }

    public static boolean isOnCooldown(ItemStack stack) {
        if (PetManager.isPet(stack)) {
            String persistentId = getPersistentItemId(stack);
            if (persistentId != null) {
                Long endTime = itemCooldowns.get(persistentId);
                if (endTime != null && System.currentTimeMillis() < endTime) {
                    return true;
                }
            }

            long lastUsed = getLastUsedTime(stack);
            if (lastUsed > 0) {
                long cooldownDuration = PetManager.getCooldownDuration(stack);
                return System.currentTimeMillis() < lastUsed + cooldownDuration;
            }
        }
        return false;
    }

    public static long getRemainingCooldown(ItemStack stack) {
        if (PetManager.isPet(stack)) {
            String persistentId = getPersistentItemId(stack);
            if (persistentId != null) {
                Long endTime = itemCooldowns.get(persistentId);
                if (endTime != null) {
                    long remaining = endTime - System.currentTimeMillis();
                    return Math.max(0, remaining);
                }
            }

            long lastUsed = getLastUsedTime(stack);
            if (lastUsed > 0) {
                long cooldownDuration = PetManager.getCooldownDuration(stack);
                long remaining = (lastUsed + cooldownDuration) - System.currentTimeMillis();
                return Math.max(0, remaining);
            }
        }
        return 0;
    }

    public static boolean isPet(ItemStack stack) {
        return PetManager.isPet(stack);
    }

    public static String getPetName(ItemStack stack) {
        return PetManager.getPetName(stack);
    }

    public static String getPersistentItemId(ItemStack stack) {
        if (!stack.hasNbt()) return null;

        NbtCompound nbt = stack.getNbt();

        // Use c_iid as primary identifier (most reliable)
        if (nbt.contains("c_iid")) {
            return "c_iid_" + nbt.getLong("c_iid");
        }


        if (nbt.contains("petType")) {
            String petType = nbt.getString("petType");
            String skullId = "unknown";

            if (nbt.contains("SkullOwner")) {
                NbtCompound skullOwner = nbt.getCompound("SkullOwner");
                if (skullOwner.contains("Id")) {

                    if (skullOwner.get("Id") instanceof NbtCompound idCompound) {

                        skullId = idCompound.toString();
                    } else {

                        int[] idArray = skullOwner.getIntArray("Id");
                        if (idArray.length == 4) {
                            skullId = Arrays.toString(idArray);
                        }
                    }
                } else if (skullOwner.contains("Name")) {
                    skullId = skullOwner.getString("Name");
                }
            }

            return skullId + "_" + petType;
        }


        String petName = PetManager.getPetName(stack);
        if (petName != null) {
            return "name_" + petName + "_" + System.identityHashCode(stack);
        }

        return null;
    }

    public static long getLastUsedTime(ItemStack stack) {
        if (stack.hasNbt()) {
            NbtCompound nbt = stack.getNbt();
            if (nbt.contains("lastUsed")) {
                return nbt.getLong("lastUsed");
            }
        }
        return 0;
    }

    public static void handleServerCooldownMessage(long remainingCooldownMs) {
        if (client.player == null) return;


        if (lastActivatedPetId != null && (System.currentTimeMillis() - lastActivationTime) < 5000) {
            for (int i = 0; i < client.player.getInventory().size(); i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                String stackPetId = getPersistentItemId(stack);
                if (stackPetId != null && stackPetId.equals(lastActivatedPetId)) {
                    setCooldownFromServer(stack, remainingCooldownMs);
                    return;
                }
            }
        }


        if (lastActivatedSlot != -1 && (System.currentTimeMillis() - lastActivationTime) < 5000) {
            ItemStack stack = client.player.getInventory().getStack(lastActivatedSlot);
            if (PetManager.isPet(stack)) {
                setCooldownFromServer(stack, remainingCooldownMs);
                return;
            }
        }


        System.out.println("Warning: Using fallback cooldown assignment");
    }

    private static void setCooldownFromServer(ItemStack stack, long remainingCooldownMs) {
        String persistentId = getPersistentItemId(stack);
        if (persistentId != null) {
            long endTime = System.currentTimeMillis() + remainingCooldownMs;
            itemCooldowns.put(persistentId, endTime);

            if (!stack.hasNbt()) {
                stack.setNbt(new NbtCompound());
            }
            stack.getNbt().putLong("lastUsed", System.currentTimeMillis());

            System.out.println("Set server cooldown for: " + getPetName(stack) + " ID: " + persistentId);
        }
    }

    public static void renderInventoryCooldown(DrawContext context, ItemStack stack, int x, int y) {
        if (isOnCooldown(stack)) {
            long remaining = getRemainingCooldown(stack);
            String timeText = formatTime(remaining);

            int centerX = x + 8;
            int centerY = y + 8;

            context.getMatrices().push();
            context.getMatrices().translate(centerX, centerY, 300);
            context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);

            TextRenderer textRenderer = client.textRenderer;
            int textWidth = textRenderer.getWidth(timeText);
            int textHeight = textRenderer.fontHeight;
            int drawX = -textWidth / 2;
            int drawY = -textHeight / 2;

            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    if (ox == 0 && oy == 0) continue;
                    context.drawText(textRenderer, timeText, drawX + ox, drawY + oy, 0x000000, false);
                }
            }

            context.drawText(textRenderer, timeText, drawX, drawY, 0xFFFF5555, false);
            context.getMatrices().pop();
        }
    }

    private void updateCooldowns() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = itemCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime >= entry.getValue()) {
                iterator.remove();
            }
        }
    }

    private void renderCooldownOverlay(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        if (player == null) {
            return;
        }

        if (!(client.currentScreen instanceof HandledScreen)) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (isOnCooldown(stack)) {
                    long remaining = getRemainingCooldown(stack);
                    String timeText = formatTime(remaining);

                    int screenWidth = client.getWindow().getScaledWidth();
                    int screenHeight = client.getWindow().getScaledHeight();
                    int hotbarStartX = screenWidth / 2 - 91;
                    int hotbarY = screenHeight - 20;
                    int slotX = hotbarStartX + i * 20;
                    int slotY = hotbarY;

                    drawCooldownText(context, timeText, slotX, slotY, 20);
                }
            }
        }
    }

    private void drawCooldownText(DrawContext context, String timeText, int x, int y, int slotSize) {
        int centerX = x + (slotSize / 2);
        int centerY = y + (slotSize / 2);

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 500);
        context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);

        TextRenderer textRenderer = client.textRenderer;
        int textWidth = textRenderer.getWidth(timeText);
        int textHeight = textRenderer.fontHeight;
        int drawX = -textWidth / 2;
        int drawY = -textHeight / 2;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox == 0 && oy == 0) continue;
                context.drawText(textRenderer, timeText, drawX + ox, drawY + oy, 0x000000, false);
            }
        }

        context.drawText(textRenderer, timeText, drawX, drawY, 0xFFFF5555, false);
        context.getMatrices().pop();
    }

    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else if (seconds > 0) {
            return String.format("%ds", seconds);
        } else {
            return "Ready!";
        }
    }

    public static Pattern getCooldownPattern() {
        return COOLDOWN_PATTERN;
    }
}