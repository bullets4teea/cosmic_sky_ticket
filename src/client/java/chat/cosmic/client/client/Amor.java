package chat.cosmic.client.client;

import chat.cosmic.client.client.KeyBinds.KeyBinds;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class Amor implements ClientModInitializer {
    private static final String DEFAULT_SOUND = "minecraft:block.note_block.pling";
    private final Map<ItemStack, Integer> notificationCounts = new HashMap<>();
    private final Map<UUID, Integer> playerThresholds = new HashMap<>();
    private final Map<UUID, String> playerSoundPreferences = new HashMap<>();
    public static boolean enabled = true;
    private final List<ArmorDisplayInfo> lowDurabilityItems = new ArrayList<>();

    private static class ArmorDisplayInfo {
        final ItemStack stack;
        final int durabilityPercent;
        final String name;

        ArmorDisplayInfo(ItemStack stack, int durabilityPercent, String name) {
            this.stack = stack;
            this.durabilityPercent = durabilityPercent;
            this.name = name;
        }
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        registerCommands();
        HudRenderCallback.EVENT.register(this::renderArmorDisplay);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        while (KeyBinds.getToggleDurabilityAlerts().wasPressed()) {
            enabled = !enabled;
            SettingsManager.getToggleSettings().put("Armor Durability Alerts", enabled);
            SettingsManager.saveSettings();
            client.player.sendMessage(Text.of(enabled ? "§aDurability alerts enabled" : "§cDurability alerts disabled"), true);
        }

        if (!enabled) {
            lowDurabilityItems.clear();
            return;
        }

        UUID playerUUID = client.player.getUuid();
        int threshold = playerThresholds.getOrDefault(playerUUID, 15);
        String soundName = playerSoundPreferences.getOrDefault(playerUUID, DEFAULT_SOUND);

        List<ItemStack> itemsToCheck = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            ItemStack armorStack = client.player.getInventory().armor.get(i);
            if (armorStack.isEmpty()) continue;
            if (i == 3 && isPlayerHead(armorStack)) continue;
            itemsToCheck.add(armorStack);
        }

        addHandItem(itemsToCheck, client.player.getMainHandStack());
        addHandItem(itemsToCheck, client.player.getOffHandStack());

        lowDurabilityItems.clear();
        boolean shouldPlaySound = false;

        for (ItemStack stack : itemsToCheck) {
            if (stack.getMaxDamage() <= 0) continue;

            int currentDurability = stack.getMaxDamage() - stack.getDamage();
            int durabilityPercent = (int) ((double) currentDurability / stack.getMaxDamage() * 100);

            if (currentDurability <= threshold) {
                String itemName = getSimpleName(stack.getName().getString());
                lowDurabilityItems.add(new ArmorDisplayInfo(stack, durabilityPercent, itemName));

                int count = notificationCounts.getOrDefault(stack, 0);
                if (count < 1) {
                    client.player.sendMessage(Text.of("§c" + itemName + " is low on durability! (" + durabilityPercent + "%)"), false);
                    notificationCounts.put(stack, count + 1);
                    shouldPlaySound = true;
                }
            } else {
                notificationCounts.remove(stack);
            }
        }

        if (shouldPlaySound) {
            playCustomSound(client, soundName);
        }
    }

    private String getSimpleName(String fullName) {
        return fullName.replace("Diamond ", "")
                .replace("Iron ", "")
                .replace("Golden ", "Gold ")
                .replace("Leather ", "")
                .replace("Chainmail ", "Chain ")
                .replace("Netherite ", "Neth ");
    }

    private void playCustomSound(MinecraftClient client, String soundName) {
        try {
            Identifier soundId = new Identifier(soundName);
            SoundEvent soundEvent = SoundEvent.of(soundId);
            client.getSoundManager().play(PositionedSoundInstance.master(soundEvent, 1.0f));
        } catch (Exception e) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(new Identifier(DEFAULT_SOUND)), 1.0f));
        }
    }

    private void renderArmorDisplay(DrawContext context, float tickDelta) {
        if (!enabled || lowDurabilityItems.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        TextRenderer textRenderer = client.textRenderer;

        ArmorDisplayInfo info = lowDurabilityItems.get(0);

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2 - 30;

        String alertText = "about to break";
        int textWidth = textRenderer.getWidth(alertText);

        int totalWidth = 16 + 4 + textWidth + 4 + textRenderer.getWidth("!");

        int itemX = centerX - totalWidth / 2;
        int itemY = centerY - 8;
        context.drawItem(info.stack, itemX, itemY);

        int textX = itemX + 16 + 4;
        int textY = centerY - 4;
        context.drawText(textRenderer, alertText, textX, textY, 0xFFFF0000, true);

        int exclamationX = textX + textWidth + 4;
        context.drawText(textRenderer, "!", exclamationX, textY, 0xFFFF0000, true);
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

            dispatcher.register(ClientCommandManager.literal("dursound")
                    .then(ClientCommandManager.argument("sound", StringArgumentType.string())
                            .suggests((SuggestionProvider<FabricClientCommandSource>) SOUND_SUGGESTIONS)
                            .executes(context -> {
                                String sound = StringArgumentType.getString(context, "sound");
                                MinecraftClient client = MinecraftClient.getInstance();
                                if (client.player != null) {
                                    UUID playerUUID = client.player.getUuid();
                                    if (sound.equalsIgnoreCase("reset")) {
                                        playerSoundPreferences.remove(playerUUID);
                                        client.player.sendMessage(Text.of("§aAlert sound reset to default."), false);
                                        playCustomSound(client, DEFAULT_SOUND);
                                    } else {
                                        playerSoundPreferences.put(playerUUID, sound);
                                        client.player.sendMessage(Text.of("§aAlert sound set to: " + sound), false);
                                        playCustomSound(client, sound);
                                    }
                                }
                                return Command.SINGLE_SUCCESS;
                            })));
        });
    }

    private static final SuggestionProvider<?> SOUND_SUGGESTIONS = (context, builder) -> {
        List<String> suggestions = Arrays.asList(
                DEFAULT_SOUND,
                "reset"
        );
        for (String suggestion : suggestions) {
            builder.suggest(suggestion);
        }
        return CompletableFuture.completedFuture(builder.build());
    };
}