package chat.cosmic.client.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chat.cosmic.client.client.KeyBinds.KeyBinds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TrinketMod implements ModInitializer {
    private static final Pattern CHARGE_PATTERN = Pattern.compile(".*\\((\\d+)\\)");
    private static final String HUD_ID = "trinket_display";
    private static final Path CONFIG_PATH = Paths.get("config", "cosmic_trackers.properties");
    private UniversalGuiMover.HudContainer hudContainer;
    private boolean hudVisible = true;
    private static boolean modEnabled = true;
    private static boolean hudEnabled = true;

    private static final List<String> ALLOWED_TRINKETS = List.of(
            "Speed Trinket I",
            "Strength Trinket I",
            "Ender Pearl Trinket",
            "Healing Trink I",
            "Healing Trinket II",
            "Healing Trinket III"
    );

    public static void setModEnabled(boolean enabled) {
        modEnabled = enabled;
    }

    public static void setHudEnabled(boolean enabled) {
        hudEnabled = enabled;
    }

    @Override
    public void onInitialize() {
        loadConfig();
        hudEnabled = SettingsManager.getToggleSettings().getOrDefault("Trinket Display HUD", true);

        HudRenderCallback.EVENT.register(this::renderTrinkets);

        hudContainer = new UniversalGuiMover.HudContainer(10, 10, 150, 12, 1);
        UniversalGuiMover.trackHudContainer(HUD_ID, hudContainer);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBinds.getToggleTrinketHud().wasPressed()) {
                hudVisible = !hudVisible;
                SettingsManager.getToggleSettings().put("Trinket Display HUD", hudVisible);
                SettingsManager.saveSettings();
                saveConfig();
            }
        });
    }

    private void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                Properties props = new Properties();
                props.load(Files.newInputStream(CONFIG_PATH));
                hudVisible = Boolean.parseBoolean(props.getProperty("trinket.visible", "true"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            Properties props = new Properties();
            if (Files.exists(CONFIG_PATH)) {
                props.load(Files.newInputStream(CONFIG_PATH));
            }
            props.setProperty("trinket.visible", String.valueOf(hudVisible));
            Files.createDirectories(CONFIG_PATH.getParent());
            props.store(Files.newOutputStream(CONFIG_PATH), "Cosmic Trackers Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void renderTrinkets(DrawContext drawContext, float tickDelta) {
        if (!modEnabled || !hudVisible || !hudEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        List<TrinketData> trinkets = getHotbarTrinkets(client.player.getInventory());
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
            drawContext.drawText(client.textRenderer,
                    Text.literal(trinket.name + ": " + trinket.charges).formatted(Formatting.WHITE),
                    0, yOffset, 0xFFFFFF, false);
            yOffset += 10;
        }

        drawContext.getMatrices().pop();
    }

    private List<TrinketData> getHotbarTrinkets(PlayerInventory inv) {
        List<TrinketData> trinkets = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            String name = stack.getName().getString();

            boolean isAllowed = ALLOWED_TRINKETS.stream()
                    .anyMatch(allowed -> name.startsWith(allowed));
            if (!isAllowed) continue;

            Matcher matcher = CHARGE_PATTERN.matcher(name);
            if (matcher.find()) {
                int charges = Integer.parseInt(matcher.group(1));
                String cleanName = name.replaceAll("\\s*\\(\\d+\\)", "").trim();
                trinkets.add(new TrinketData(cleanName, charges));
            }
        }

        trinkets.sort(Comparator.comparingInt(t -> {
            String lowerName = t.name.toLowerCase();
            if (lowerName.contains("healing")) return 1;
            if (lowerName.contains("speed")) return 2;
            if (lowerName.contains("strength")) return 3;
            if (lowerName.contains("pearl")) {
                if (t.name.endsWith("III")) return 4;
                if (t.name.endsWith("II")) return 5;
                return 6;
            }
            return 7;
        }));

        return trinkets;
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