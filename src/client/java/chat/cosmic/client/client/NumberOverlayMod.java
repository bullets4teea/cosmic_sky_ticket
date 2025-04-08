package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class NumberOverlayMod implements ClientModInitializer {
    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "\\$?(\\d{1,3}(?:,\\d{3})*|\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern XP_PATTERN = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})*|\\d+)\\s+xp",
            Pattern.CASE_INSENSITIVE
    );
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final float TEXT_SCALE = 0.60f;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int INVENTORY_SLOT_SIZE = 16;

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (shouldRenderHotbar()) {
                renderHotbarValues(context);
            }
        });
    }

    private boolean shouldRenderHotbar() {
        return client.currentScreen == null && client.player != null;
    }

    private void renderHotbarValues(DrawContext context) {
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int baseX = (screenWidth / 2) - 91;
        int baseY = screenHeight - 23;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!isTargetItem(stack)) continue;

            int slotCenterX = baseX + (i * HOTBAR_SLOT_SIZE) + (HOTBAR_SLOT_SIZE / 2);
            int slotCenterY = baseY + (HOTBAR_SLOT_SIZE / 2);
            renderItemValue(context, stack, slotCenterX, slotCenterY);
        }
    }

    public static void renderInventoryItemValue(DrawContext context, ItemStack stack, int x, int y) {
        if (!isTargetItem(stack)) return;
        if (!(client.currentScreen instanceof HandledScreen<?>)) return;

        int centerX = x + (INVENTORY_SLOT_SIZE / 2);
        int centerY = y + (INVENTORY_SLOT_SIZE / 2);
        renderItemValue(context, stack, centerX, centerY);
    }

    public static void renderItemValue(DrawContext context, ItemStack stack, int x, int y) {
        String value = getItemValue(stack);
        if (value.isEmpty()) return;

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 300);
        context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);

        int textWidth = client.textRenderer.getWidth(value);
        int textHeight = client.textRenderer.fontHeight;
        int drawX = -textWidth / 2;
        int drawY = -textHeight / 2;


        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox == 0 && oy == 0) continue;
                context.drawText(client.textRenderer, value, drawX + ox, drawY + oy, 0x000000, false);
            }
        }


        context.drawText(client.textRenderer, value, drawX, drawY, 0xFFFFFF, false);
        context.getMatrices().pop();
    }

    private static boolean isTargetItem(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == Items.EXPERIENCE_BOTTLE || stack.getItem() == Items.PAPER);
    }

    private static String getItemValue(ItemStack stack) {
        if (stack.getItem() == Items.PAPER) {
            return parseMoneyValue(stack);
        } else if (stack.getItem() == Items.EXPERIENCE_BOTTLE) {
            return parseXPValue(stack);
        }
        return "";
    }

    private static String parseMoneyValue(ItemStack stack) {
        if (!stack.hasNbt() || !stack.getNbt().contains("display")) return "";
        var displayTag = stack.getNbt().getCompound("display");
        if (!displayTag.contains("Lore")) return "";

        var lore = displayTag.getList("Lore", 8);
        for (int i = 0; i < lore.size(); i++) {
            String loreText = lore.getString(i).replaceAll("§[0-9a-fk-or]", "").trim();
            Matcher matcher = MONEY_PATTERN.matcher(loreText);
            if (matcher.find()) {
                String numberStr = matcher.group(1).replace(",", "");
                try {
                    long value = Long.parseLong(numberStr);
                    return "$" + abbreviateNumber(value);
                } catch (NumberFormatException e) {
                    return "";
                }
            }
        }
        return "";
    }

    private static String parseXPValue(ItemStack stack) {
        if (!stack.hasNbt() || !stack.getNbt().contains("display")) return "";
        var displayTag = stack.getNbt().getCompound("display");
        if (!displayTag.contains("Lore")) return "";

        var lore = displayTag.getList("Lore", 8);
        for (int i = 0; i < lore.size(); i++) {
            String loreText = lore.getString(i).replaceAll("§[0-9a-fk-or]", "").trim();
            Matcher matcher = XP_PATTERN.matcher(loreText);
            if (matcher.find()) {
                String numberStr = matcher.group(1).replace(",", "");
                try {
                    long value = Long.parseLong(numberStr);
                    return abbreviateNumber(value) + " XP";
                } catch (NumberFormatException e) {
                    return "";
                }
            }
        }
        return "";
    }

    private static String abbreviateNumber(long value) {
        if (value < 1_000) return String.valueOf(value);
        String[] units = {"", "k", "m", "b", "t", "q"};
        int exp = (int) (Math.log(value) / Math.log(1_000));
        exp = Math.min(exp, units.length - 1);
        double abbreviated = value / Math.pow(1_000, exp);
        return String.format(abbreviated % 1 == 0 ? "%.0f%s" : "%.1f%s", abbreviated, units[exp]);
    }
}