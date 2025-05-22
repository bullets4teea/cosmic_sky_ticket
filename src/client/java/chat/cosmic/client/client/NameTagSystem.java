package chat.cosmic.client.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class NameTagSystem implements ClientModInitializer {
    private static final String CONFIG_FILE = "cosmic-client-nametags.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NameTagSystem INSTANCE;
    private final Map<String, Boolean> tierVisibility = new HashMap<>();
    private final Map<String, Boolean> glowVisibility = new HashMap<>();

    public NameTagSystem() {
        // Initialize all tiers to false
        String[] tiers = {"basic", "elite", "legendary", "godly", "mythic", "heroic"};
        for (String tier : tiers) {
            String key = tier + "_marauder";
            tierVisibility.put(key, true);
            glowVisibility.put(key, true);
        }
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        load();
        registerCommands();
    }

    public boolean shouldShowNameTag(String mobName) {
        String lowerName = mobName.toLowerCase();
        for (Map.Entry<String, Boolean> entry : tierVisibility.entrySet()) {
            String tier = entry.getKey().replace("_", " ");
            if (lowerName.contains(tier) && !entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean shouldGlow(String mobName) {
        String lowerName = mobName.toLowerCase();
        for (Map.Entry<String, Boolean> entry : glowVisibility.entrySet()) {
            String tier = entry.getKey().replace("_", " ");
            if (lowerName.contains(tier) && !entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public void toggleTier(String tier) {
        String key = tier.toLowerCase().replace(" ", "_");
        if (tierVisibility.containsKey(key)) {
            tierVisibility.put(key, !tierVisibility.get(key));
            save();
        }
    }

    public void toggleAllTiers(boolean value) {
        tierVisibility.replaceAll((k, v) -> value);
        save();
    }

    public void toggleGlow(String tier) {
        String key = tier.toLowerCase().replace(" ", "_");
        if (glowVisibility.containsKey(key)) {
            glowVisibility.put(key, !glowVisibility.get(key));
            save();
        }
    }

    public void toggleAllGlow(boolean value) {
        glowVisibility.replaceAll((k, v) -> value);
        save();
    }

    public void load() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE).toFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                NameTagSystem loaded = GSON.fromJson(reader, NameTagSystem.class);
                this.tierVisibility.clear();
                this.tierVisibility.putAll(loaded.tierVisibility);
                this.glowVisibility.clear();
                this.glowVisibility.putAll(loaded.glowVisibility);
            } catch (IOException e) {
                System.err.println("Failed to load config: " + e.getMessage());
            }
        } else {
            save();
        }
    }

    public void save() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE).toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerNameTagCommands(dispatcher);
            registerGlowCommands(dispatcher);
        });
    }

    private void registerNameTagCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> cmd = ClientCommandManager.literal("nametag")
                .then(ClientCommandManager.literal("toggle")
                        .then(createTierNode("basic"))
                        .then(createTierNode("elite"))
                        .then(createTierNode("legendary"))
                        .then(createTierNode("godly"))
                        .then(createTierNode("mythic"))
                        .then(createTierNode("heroic")))
                .then(ClientCommandManager.literal("all")
                        .then(ClientCommandManager.literal("show")
                                .executes(ctx -> {
                                    toggleAllTiers(true);
                                    ctx.getSource().sendFeedback(Text.literal("§aAll name tags are now visible"));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("hide")
                                .executes(ctx -> {
                                    toggleAllTiers(false);
                                    ctx.getSource().sendFeedback(Text.literal("§cAll name tags are now hidden"));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("status").executes(this::showNameTagStatus));
        dispatcher.register(cmd);
    }

    private void registerGlowCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> cmd = ClientCommandManager.literal("glow")
                .then(ClientCommandManager.literal("toggle")
                        .then(createGlowTierNode("basic"))
                        .then(createGlowTierNode("elite"))
                        .then(createGlowTierNode("legendary"))
                        .then(createGlowTierNode("godly"))
                        .then(createGlowTierNode("mythic"))
                        .then(createGlowTierNode("heroic")))
                .then(ClientCommandManager.literal("all")
                        .then(ClientCommandManager.literal("on")
                                .executes(ctx -> {
                                    toggleAllGlow(true);
                                    ctx.getSource().sendFeedback(Text.literal("§aAll glow effects are now enabled"));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("off")
                                .executes(ctx -> {
                                    toggleAllGlow(false);
                                    ctx.getSource().sendFeedback(Text.literal("§cAll glow effects are now disabled"));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("status").executes(this::showGlowStatus));
        dispatcher.register(cmd);
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> createTierNode(String tier) {
        return ClientCommandManager.literal(tier).executes(ctx -> {
            toggleTier(tier + "_marauder");
            ctx.getSource().sendFeedback(Text.literal("§a" + capitalize(tier) + " name tags: " + (tierVisibility.get(tier + "_marauder") ? "§aON" : "§cOFF")));
            return 1;
        });
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> createGlowTierNode(String tier) {
        return ClientCommandManager.literal(tier).executes(ctx -> {
            toggleGlow(tier + "_marauder");
            ctx.getSource().sendFeedback(Text.literal("§b" + capitalize(tier) + " glow: " + (glowVisibility.get(tier + "_marauder") ? "§aON" : "§cOFF")));
            return 1;
        });
    }

    private int showNameTagStatus(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§6=== NameTags ==="));
        tierVisibility.forEach((k, v) -> ctx.getSource().sendFeedback(Text.literal("  §7- " + capitalize(k.replace("_", " ")) + ": " + (v ? "§aSHOWN" : "§cHIDDEN"))));
        return 1;
    }

    private int showGlowStatus(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§6=== Glow ==="));
        glowVisibility.forEach((k, v) -> ctx.getSource().sendFeedback(Text.literal("  §7- " + capitalize(k.replace("_", " ")) + ": " + (v ? "§aON" : "§cOFF"))));
        return 1;
    }

    private String capitalize(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    public static NameTagSystem getInstance() {
        if (INSTANCE == null) INSTANCE = new NameTagSystem();
        return INSTANCE;
    }
}