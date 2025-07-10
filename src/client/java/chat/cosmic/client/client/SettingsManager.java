package chat.cosmic.client.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SettingsManager {
    private static final Path CONFIG_PATH = Paths.get("config", "untitled20_settings.properties");
    private static boolean settingsOpen = false;
    private static boolean initialized = false;

    private static final Map<String, Boolean> toggleSettings = new LinkedHashMap<>();
    private static final Map<String, Float> sliderSettings = new LinkedHashMap<>();
    private static final Map<String, KeyBinding> keybindList = new LinkedHashMap<>();
    private static final List<String> commandHelp = new ArrayList<>();

    private static final Map<String, Boolean> boosterToggleSettings = new LinkedHashMap<>();
    private static final Map<String, Boolean> mobNameTagSettings = new LinkedHashMap<>();
    private static final Map<String, Boolean> mobGlowSettings = new LinkedHashMap<>();

    private static final Map<String, Integer> trinketThresholdSettings = new LinkedHashMap<>();
    private static final Map<String, Integer> trinketCriticalSettings = new LinkedHashMap<>();

    private static final Map<String, Integer> defaultThresholds = Map.of(
            "Healing", 50,
            "Speed", 6,
            "Strength", 6,
            "Ender", 7
    );

    private static final Map<String, Integer> defaultCriticalThresholds = Map.of(
            "Healing", 25,
            "Speed", 4,
            "Strength", 4,
            "Ender", 5
    );

    public static void setMuleHudEnabled(Boolean muleHud) {
    }

    public static void setCombatHudEnabled(Boolean combatHud) {
    }

    private static class ChaoticMessage {
        final String message;
        int timer;
        final UUID uuid;

        ChaoticMessage(String message, int duration) {
            this.message = message;
            this.timer = duration;
            this.uuid = UUID.randomUUID();
        }
    }

    public static void initialize() {
        if (initialized) return;
        loadSettings();

        setDefaultIfMissing("Show Notifications", true);
        setDefaultIfMissing("Show Player List", true);
        setDefaultIfMissing("XP Booster HUD", true);
        setDefaultIfMissing("Trinket Display HUD", true);
        setDefaultIfMissing("Chest Tracker HUD", true);
        setDefaultIfMissing("Curse HUD", true);
        setDefaultIfMissing("Chaotic Zone HUD", true);
        setDefaultIfMissing("Combat HUD", true);
        setDefaultIfMissing("Mule HUD", true);
        setDefaultIfMissing("Armor Durability Alerts", true);
        setDefaultIfMissing("Hide Armor", false);
        setDefaultIfMissing("Players per Column", 15f);
        setDefaultIfMissing("Damage Numbers", true);
        setDefaultIfMissing("Highlight Search", true);
        setDefaultIfMissing("Mythic Fishing HUD", true);

        setBoosterDefaultIfMissing("Island XP Booster", true);
        setBoosterDefaultIfMissing("Treasure Chance Booster", true);
        setBoosterDefaultIfMissing("Ender Pearl", true);
        setBoosterDefaultIfMissing("/feed", true);
        setBoosterDefaultIfMissing("/heal", true);
        setBoosterDefaultIfMissing("/fix", true);
        setBoosterDefaultIfMissing("/near", true);

        // Initialize mob settings
        String[] mobTiers = {"basic", "elite", "legendary", "godly", "mythic", "heroic"};
        for (String tier : mobTiers) {
            setDefaultIfMissing("Mob " + tier + " Nametag", true);
            if (!tier.equals("basic") && !tier.equals("elite")) {
                setDefaultIfMissing("Mob " + tier + " Glow", true);
            }
        }

        keybindList.put("Toggle GUI Movement", UniversalGuiMover.moveGuisKey);
        keybindList.put("Toggle Notifications", chat.cosmic.client.join.toggleNotificationsKey);
        keybindList.put("Toggle Player List", chat.cosmic.client.join.toggleGuiKey);
        keybindList.put("Scale Up", UniversalGuiMover.scaleUpKey);
        keybindList.put("Scale Down", UniversalGuiMover.scaleDownKey);
        keybindList.put("Toggle Durability Alerts", Amor.toggleKeybind);
        keybindList.put("Toggle Chest HUD", ChestTrackerMod.toggleHudKey);
        keybindList.put("Reset Chest Tracker", ChestTrackerMod.resetKey);
        keybindList.put("Start/Pause Chest Timer", ChestTrackerMod.startPauseTimerKey);
        keybindList.put("Toggle Fishing HUD", MythicTrackerMod.toggleHudKey);
        keybindList.put("Toggle Search", HighlightSearchMod.TOGGLE_SEARCH_KEY);

        commandHelp.add("/pr <name> - Red name");
        commandHelp.add("/pg <name> - Green name");
        commandHelp.add("/pdb <name> - Aqua name");
        commandHelp.add("/pw <name> - Reset color");
        commandHelp.add("/prr - Reset all colors");
        commandHelp.add("/player list ignore <name> - Toggle ignore");
        commandHelp.add("/setmaxplayers <count> - Set max players per column");
        commandHelp.add("/dur <value> - Set durability warning threshold (1-300)");
        commandHelp.add("/dursound <sound> - Set alert sound (or 'reset') does not work for something later on ");
        commandHelp.add("/chestreset - Reset chest counts and gems");
        commandHelp.add("/chesttimer reset - Reset timer to 00:00:00");
        commandHelp.add("/chesttimer start - Start/resume timer");
        commandHelp.add("/chesttimer pause - Pause timer");
        commandHelp.add("/sys - Show System Override stats for 10s");
        commandHelp.add("/fish reset - Reset all fishing counters");

        applySettings();
        initialized = true;

        try {
            Path oldConfig = Paths.get(MinecraftClient.getInstance().runDirectory.getPath(), "config", "armorvisibility.cfg");
            if (Files.exists(oldConfig)) {
                Properties prop = new Properties();
                try (InputStream is = Files.newInputStream(oldConfig)) {
                    prop.load(is);
                }
                boolean hideArmor = Boolean.parseBoolean(prop.getProperty("hideArmor", "false"));
                toggleSettings.put("Hide Armor", hideArmor);
                Files.delete(oldConfig);
                applySettings();
                saveSettings();
            }
        } catch (IOException e) {
            System.err.println("Error migrating armor config: " + e.getMessage());
        }
    }

    private static void setDefaultIfMissing(String name, boolean defaultValue) {
        if (!toggleSettings.containsKey(name)) {
            toggleSettings.put(name, defaultValue);
        }
    }

    private static void setDefaultIfMissing(String name, float defaultValue) {
        if (!sliderSettings.containsKey(name)) {
            sliderSettings.put(name, defaultValue);
        }
    }

    private static void setBoosterDefaultIfMissing(String name, boolean defaultValue) {
        if (!boosterToggleSettings.containsKey(name)) {
            boosterToggleSettings.put(name, defaultValue);
        }
    }

    public static void loadSettings() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                Properties props = new Properties();
                try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
                    props.load(is);
                }

                for (String key : props.stringPropertyNames()) {
                    if (key.startsWith("toggle_")) {
                        String settingName = key.substring(7);
                        boolean value = Boolean.parseBoolean(props.getProperty(key));
                        toggleSettings.put(settingName, value);
                    } else if (key.startsWith("slider_")) {
                        String settingName = key.substring(7);
                        float value = Float.parseFloat(props.getProperty(key));
                        sliderSettings.put(settingName, value);
                    } else if (key.startsWith("booster_")) {
                        String settingName = key.substring(8);
                        boolean value = Boolean.parseBoolean(props.getProperty(key));
                        boosterToggleSettings.put(settingName, value);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
        }
    }

    public static void saveSettings() {
        Properties props = new Properties();

        for (Map.Entry<String, Boolean> entry : toggleSettings.entrySet()) {
            props.setProperty("toggle_" + entry.getKey(), entry.getValue().toString());
        }

        for (Map.Entry<String, Float> entry : sliderSettings.entrySet()) {
            props.setProperty("slider_" + entry.getKey(), entry.getValue().toString());
        }

        for (Map.Entry<String, Boolean> entry : boosterToggleSettings.entrySet()) {
            props.setProperty("booster_" + entry.getKey(), entry.getValue().toString());
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream os = Files.newOutputStream(CONFIG_PATH)) {
                props.store(os, "Untitled20 Mod Settings");
            }
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    public static void toggleSettings() {
        settingsOpen = !settingsOpen;
        SettingsInputHandler.cancelKeybindEditing();

        if (settingsOpen) {
            Window window = MinecraftClient.getInstance().getWindow();
            SettingsRenderer.setPosition(
                    (window.getScaledWidth() - SettingsRenderer.getWidth()) / 2,
                    (window.getScaledHeight() - SettingsRenderer.getHeight()) / 2
            );
        }
    }

    public static void closeSettings() {
        settingsOpen = false;
        SettingsInputHandler.saveThresholdsIfEditing();
        SettingsInputHandler.cancelKeybindEditing();
    }

    public static boolean isSettingsOpen() {
        return settingsOpen;
    }

    public static boolean isWaitingForKey() {
        return SettingsInputHandler.isWaitingForKey();
    }

    public static void applySettings() {
        chat.cosmic.client.join.NOTIFICATIONS_ENABLED = toggleSettings.getOrDefault("Show Notifications", false);
        chat.cosmic.client.join.GUI_VISIBLE = toggleSettings.getOrDefault("Show Player List", false);
        chat.cosmic.client.join.MAX_PLAYERS_PER_COLUMN = sliderSettings.getOrDefault("Players per Column", 15f).intValue();

        XPBoosterMod.setModEnabled(toggleSettings.getOrDefault("XP Booster HUD", true));
        TrinketMod.setModEnabled(toggleSettings.getOrDefault("Trinket Display HUD", true));
        TrinketMod.setHudEnabled(toggleSettings.getOrDefault("Trinket Display HUD", true));
        ChestTrackerMod.setModEnabled(toggleSettings.getOrDefault("Chest Tracker HUD", true));
        XPBoosterMod.setColorAlertsEnabled(toggleSettings.getOrDefault("Color Alerts for Boosters", true));
        Amor.enabled = toggleSettings.getOrDefault("Armor Durability Alerts", true);
        ArmorToggleMod.hideArmor = toggleSettings.getOrDefault("Hide Armor", false);
        StatusEffectsTracker.setCombatHudEnabled(toggleSettings.getOrDefault("Combat HUD", true));
        StatusEffectsTracker.setMuleHudEnabled(toggleSettings.getOrDefault("Mule HUD", true));
        DamageDisplayMod.enabled = toggleSettings.getOrDefault("Damage Numbers", true);
        HighlightSearchMod.isSearchVisible = toggleSettings.getOrDefault("Highlight Search", true);
        MythicTrackerMod.isHudVisible = toggleSettings.getOrDefault("Mythic Fishing HUD", true);

        // Apply mob settings to NameTagSystem
        String[] mobTiers = {"basic", "elite", "legendary", "godly", "mythic", "heroic"};
        for (String tier : mobTiers) {
            String nametagKey = "Mob " + tier + " Nametag";
            String glowKey = "Mob " + tier + " Glow";

            if (toggleSettings.containsKey(nametagKey)) {
                NameTagSystem.getInstance().tierVisibility.put(tier + "_marauder",
                        toggleSettings.get(nametagKey));
            }

            if (toggleSettings.containsKey(glowKey)) {
                NameTagSystem.getInstance().glowVisibility.put(tier + "_marauder",
                        toggleSettings.get(glowKey));
            }
        }
    }

    public static boolean isBoosterEnabled(String boosterKey) {
        return boosterToggleSettings.getOrDefault(boosterKey, true);
    }

    public static Map<String, Boolean> getToggleSettings() {
        return toggleSettings;
    }

    public static Map<String, Float> getSliderSettings() {
        return sliderSettings;
    }

    public static Map<String, KeyBinding> getKeybindList() {
        return keybindList;
    }

    public static List<String> getCommandHelp() {
        return commandHelp;
    }

    public static Map<String, Boolean> getBoosterToggleSettings() {
        return boosterToggleSettings;
    }
}