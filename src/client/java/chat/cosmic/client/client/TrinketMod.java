package chat.cosmic.client.client;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrinketMod implements ModInitializer {

    private static final Pattern CHARGE_PATTERN = Pattern.compile(".*\\((\\d+)\\)");
    private static final String HUD_ID = "trinket_display";
    private UniversalGuiMover.HudContainer hudContainer;
    private boolean hudVisible = true;
    private KeyBinding toggleKey;

    @Override
    public void onInitialize() {
        HudRenderCallback.EVENT.register(this::renderTrinkets);

        hudContainer = new UniversalGuiMover.HudContainer(10, 10, 150, 12, 1);
        UniversalGuiMover.trackHudContainer(HUD_ID, hudContainer);

        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Trinket toggle",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_T,
                        "adv"
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                hudVisible = !hudVisible;
            }
        });
    }

    private void renderTrinkets(DrawContext drawContext, float tickDelta) {
        if (!hudVisible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        List<TrinketData> trinkets = getTrinkets(client.player.getInventory());
        if (trinkets.isEmpty()) return;

        hudContainer.lineCount = trinkets.size();

        int x = hudContainer.x;
        int y = hudContainer.y;
        float scale = UniversalGuiMover.getGlobalTextScale();

        int maxWidth = 0;
        for (TrinketData trinket : trinkets) {
            String text = trinket.name + ": " + trinket.charges;
            int width = client.textRenderer.getWidth(text);
            if (width > maxWidth) maxWidth = width;
        }

        hudContainer.baseWidth = maxWidth + 10;
        hudContainer.baseHeight = 12;

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(x, y, 0);
        drawContext.getMatrices().scale(scale, scale, 1.0f);

        int yOffset = 0;
        for (TrinketData trinket : trinkets) {
            Formatting color = getColor(trinket);
            drawContext.drawText(client.textRenderer,
                    Text.literal(trinket.name + ": " + trinket.charges).formatted(color),
                    0, yOffset, 0xFFFFFF, false);
            yOffset += 10;
        }

        drawContext.getMatrices().pop();
    }

    private List<TrinketData> getTrinkets(PlayerInventory inv) {
        List<TrinketData> trinkets = new ArrayList<>();

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            String name = stack.getName().getString();
            if (!name.toLowerCase().contains("trinket")) continue;

            Matcher matcher = CHARGE_PATTERN.matcher(name);
            if (matcher.find()) {
                int charges = Integer.parseInt(matcher.group(1));
                String cleanName = name.replaceAll("\\s*\\(\\d+\\)", "").trim();
                trinkets.add(new TrinketData(cleanName, charges));
            }
        }

        // Case-insensitive sorting with priority: Health -> Speed -> Strength
        trinkets.sort(Comparator.comparingInt(t -> {
            String lowerName = t.name.toLowerCase();
            if (lowerName.contains("health")) return 1;
            if (lowerName.contains("speed")) return 2;
            if (lowerName.contains("strength")) return 3;
            return 4;
        }));

        return trinkets;
    }

    private Formatting getColor(TrinketData trinket) {
        String lowerName = trinket.name.toLowerCase();
        int charges = trinket.charges;

        if (lowerName.contains("health")) {
            if (charges > 25) return Formatting.GOLD;
            return Formatting.RED;
        }
        else if (lowerName.contains("speed") || lowerName.contains("strength")) {
            if (charges > 7) return Formatting.GOLD;
            return Formatting.RED;
        }

        // Default color scheme for other trinkets
        if (charges > 200) return Formatting.GREEN;
        if (charges > 100) return Formatting.YELLOW;
        if (charges > 50) return Formatting.GOLD;
        return Formatting.RED;
    }

    private static class TrinketData {
        final String name;
        final int charges;

        TrinketData(String name, int charges) {
            this.name = name;
            this.charges = charges;
        }
    }
}