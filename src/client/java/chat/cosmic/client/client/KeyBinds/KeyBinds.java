package chat.cosmic.client.client.KeyBinds;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class KeyBinds {

    public static KeyBinding toggleGUIMovement;
    public static KeyBinding scaleUp;
    public static KeyBinding scaleDown;


    public static KeyBinding toggleNotifications;
    public static KeyBinding togglePlayerList;


    public static KeyBinding toggleDurabilityAlerts;
    public static KeyBinding toggleArmorVisibility;


    public static KeyBinding toggleDamageDisplay;


    public static KeyBinding toggleFishingHud;


    public static KeyBinding trinket_hud_toggle;


    public static KeyBinding Chest_tracker_timer_reset;
    public static KeyBinding Chest_tracker_start_pause_timer;
    public static KeyBinding Chest_tracker_toggle_hud;


    public static KeyBinding TrophyTracker_hud;


    public static KeyBinding toggleSearch;


    public static KeyBinding toggleBossBars;


    public static KeyBinding toggleSkyBlockTracker;

    public static void registerKeyBinds() {

        toggleGUIMovement = registerKeyBinding(
                "Move GUIs",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "Universal GUI Mover"
        );

        scaleUp = registerKeyBinding(
                "Scale Up",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,
                "Universal GUI Mover"
        );

        scaleDown = registerKeyBinding(
                "Scale Down",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                "Universal GUI Mover"
        );


        toggleNotifications = registerKeyBinding(
                "Toggle Notifications Join/Leave",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                "adv"
        );

        togglePlayerList = registerKeyBinding(
                "Toggle Player List",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "adv"
        );


        toggleDurabilityAlerts = registerKeyBinding(
                "Toggle Durability Alerts",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "adv"
        );

        toggleArmorVisibility = registerKeyBinding(
                "Toggle Mob Armor Visibility",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "adv"
        );


        toggleDamageDisplay = registerKeyBinding(
                "Toggle Damage Display",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "adv"
        );


        toggleBossBars = registerKeyBinding(
                "Toggle Globe Boosters",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "Island"
        );


        trinket_hud_toggle = registerKeyBinding(
                "Toggle Trinket HUD",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "adv"
        );


        Chest_tracker_timer_reset = registerKeyBinding(
                "Chest Tracker Reset",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "adv"
        );

        Chest_tracker_start_pause_timer = registerKeyBinding(
                "Chest Timer Start/Pause",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "adv"
        );

        Chest_tracker_toggle_hud = registerKeyBinding(
                "Chest Tracker Toggle HUD",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "adv"
        );


        TrophyTracker_hud = registerKeyBinding(
                "Trophy Tracker",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "Island"
        );


        toggleSearch = registerKeyBinding(
                "Toggle Search Bar",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "Island"
        );


        toggleSkyBlockTracker = registerKeyBinding(
                "Toggle SkyBlock Progress Tracker",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "Island"
        );
    }

    private static KeyBinding registerKeyBinding(String name, InputUtil.Type type, int key, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, type, key, category));
    }


    public static KeyBinding getToggleGUIMovement() {
        return toggleGUIMovement;
    }

    public static KeyBinding getScaleUp() {
        return scaleUp;
    }

    public static KeyBinding getScaleDown() {
        return scaleDown;
    }

    public static KeyBinding getToggleNotifications() {
        return toggleNotifications;
    }

    public static KeyBinding getTogglePlayerList() {
        return togglePlayerList;
    }

    public static KeyBinding getToggleDurabilityAlerts() {
        return toggleDurabilityAlerts;
    }

    public static KeyBinding getToggleArmorVisibility() {
        return toggleArmorVisibility;
    }

    public static KeyBinding getToggleDamageDisplay() {
        return toggleDamageDisplay;
    }

    public static KeyBinding getToggleFishingHud() {
        return toggleFishingHud;
    }

    public static KeyBinding getToggleTrinketHud() {
        return trinket_hud_toggle;
    }

    public static KeyBinding getChestTrackerReset() {
        return Chest_tracker_timer_reset;
    }

    public static KeyBinding getChestTrackerStartPause() {
        return Chest_tracker_start_pause_timer;
    }

    public static KeyBinding getChestTrackerToggleHud() {
        return Chest_tracker_toggle_hud;
    }

    public static KeyBinding getTrophyTracker() {
        return TrophyTracker_hud;
    }

    public static KeyBinding getToggleSearch() {
        return toggleSearch;
    }

    public static KeyBinding getToggleBossBars() {
        return toggleBossBars;
    }

    public static KeyBinding getToggleSkyBlockTracker() {
        return toggleSkyBlockTracker;
    }
}