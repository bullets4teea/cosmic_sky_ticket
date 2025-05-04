package chat.cosmic.client.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class Amor implements ClientModInitializer {

    private final Map<ItemStack, Integer> notificationCounts = new HashMap<>();
    private final Map<UUID, Integer> playerThresholds = new HashMap<>();
    private boolean enabled = true;
    private KeyBinding toggleKeybind;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        registerCommands();
        registerKeybind();
    }

    private void registerKeybind() {
        toggleKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "armor alert toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "adv"
        ));
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        // Toggle enabled state
        while (toggleKeybind.wasPressed()) {
            enabled = !enabled;
            client.player.sendMessage(Text.of(enabled ? "§aDurability alerts enabled" : "§cDurability alerts disabled"), true);
        }

        if (!enabled) return;

        UUID playerUUID = client.player.getUuid();
        int threshold = playerThresholds.getOrDefault(playerUUID, 15);

        List<ItemStack> itemsToCheck = new ArrayList<>();

        // Add armor (excluding player head)
        for (int i = 0; i < 4; i++) {
            ItemStack armorStack = client.player.getInventory().armor.get(i);
            if (armorStack.isEmpty()) continue;
            if (i == 3 && isPlayerHead(armorStack)) continue; // Skip helmet if it's a player head
            itemsToCheck.add(armorStack);
        }

        // Add tools/weapons from hands
        addHandItem(itemsToCheck, client.player.getMainHandStack());
        addHandItem(itemsToCheck, client.player.getOffHandStack());

        // Check all collected items
        for (ItemStack stack : itemsToCheck) {
            int currentDurability = stack.getMaxDamage() - stack.getDamage();
            if (currentDurability <= threshold) {
                int count = notificationCounts.getOrDefault(stack, 0);
                if (count < 1) {
                    client.player.sendMessage(Text.of("§cYour " + stack.getName().getString() + " is about to break!"), false);
                    client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0F, 1.0F);
                    notificationCounts.put(stack, count + 1);
                }
            } else {
                notificationCounts.remove(stack);
            }
        }
    }

    private void addHandItem(List<ItemStack> list, ItemStack stack) {
        if (!stack.isEmpty() && isToolOrWeapon(stack)) {
            list.add(stack);
        }
    }

    private boolean isPlayerHead(ItemStack stack) {
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return itemId.toString().equals("minecraft:player_head");
    }

    private boolean isToolOrWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof ToolItem ||
                item instanceof SwordItem ||
                item instanceof TridentItem ||
                item instanceof BowItem ||
                item instanceof CrossbowItem ||
                item instanceof FishingRodItem;
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("dur")
                    .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1, 300))
                            .executes(context -> {
                                int value = IntegerArgumentType.getInteger(context, "value");
                                MinecraftClient client = MinecraftClient.getInstance();
                                if (client.player != null) {
                                    UUID playerUUID = client.player.getUuid();
                                    playerThresholds.put(playerUUID, value);
                                    client.player.sendMessage(Text.of("§aDurability warning set to: " + value), false);
                                }
                                return Command.SINGLE_SUCCESS;
                            })));
        });
    }
}