package chat.cosmic.client.client.playerheadcooldown;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class PetManager {
    private static final Map<String, Long> petCooldowns = new HashMap<>();

    static {
        // Initialize all pets with their cooldown durations in milliseconds
        petCooldowns.put("Loot Llama Pet", 24L * 60 * 60 * 1000); // 24 hours
        petCooldowns.put("Battle Pig Pet", 60L * 60 * 1000); // 60 minutes
        petCooldowns.put("Miner Matt Pet", 10L * 60 * 1000); // 10 minutes
        petCooldowns.put("Slayer Sam Pet", 15L * 60 * 1000); // 15 minutes
        petCooldowns.put("Chaos Cow Pet", 60L * 60 * 1000); // 60 minutes
        petCooldowns.put("Blacksmith Brandon Pet", 20L * 60 * 1000); // 20 minutes
        petCooldowns.put("Fisherman Fred Pet", 10L * 60 * 1000); // 10 minutes
        petCooldowns.put("Alchemist Alex Pet", 8L * 60 * 1000); // 8 minutes
        petCooldowns.put("Blood Sheep Pet", 60L * 60 * 1000);; // 60 minutes
        petCooldowns.put("Merchant Pet", 15L * 60 * 1000); // 15 minutes
        petCooldowns.put("Dire Wolf Pet", 15L * 60 * 1000); // 15 minutes
        petCooldowns.put("Void Chicken Pet", 60L * 60 * 1000); // 60 minutes
        petCooldowns.put("Barry Bee Pet", 3L * 60 * 60 * 1000);
    }

    public static boolean isPet(ItemStack stack) {
        if (stack.getItem() != Items.PLAYER_HEAD) {
            return false;
        }

        String petName = getPetName(stack);
        return petName != null && petCooldowns.containsKey(petName);
    }

    public static String getPetName(ItemStack stack) {
        if (!stack.hasCustomName() && !stack.hasNbt()) {
            return null;
        }

        String displayName = null;

        if (stack.hasCustomName()) {
            Text displayNameText = stack.getName();
            displayName = displayNameText.getString();
        } else if (stack.hasNbt()) {
            NbtCompound nbt = stack.getNbt();
            if (nbt != null && nbt.contains("display")) {
                NbtCompound display = nbt.getCompound("display");
                if (display.contains("Name")) {
                    displayName = display.getString("Name");
                }
            }
        }

        if (displayName != null) {
            for (String petName : petCooldowns.keySet()) {
                if (displayName.contains(petName) && displayName.matches(".*\\[LVL [1-9]0?\\].*")) {
                    return petName;
                }
            }
        }

        if (stack.hasNbt()) {
            NbtCompound nbt = stack.getNbt();
            if (nbt.contains("petType")) {
                String petType = nbt.getString("petType");
                return getPetNameFromType(petType);
            }
        }

        return null;
    }

    private static String getPetNameFromType(String petType) {
        switch (petType) {
            case "LOOT_LLAMA": return "Loot Llama Pet";
            case "BATTLE_PIG": return "Battle Pig Pet";
            case "MINER_MATT": return "Miner Matt Pet";
            case "SLAYER_SAM": return "Slayer Sam Pet";
            case "CHAOS_COW": return "Chaos Cow Pet";
            case "BLACKSMITH_BRANDON": return "Blacksmith Brandon Pet";
            case "FISHERMAN_FRED": return "Fisherman Fred Pet";
            case "ALCHEMIST_ALEX": return "Alchemist Alex Pet";
            case "BLOOD_SHEEP": return "Blood Sheep Pet";
            case "MERCHANT": return "Merchant Pet";
            case "DIRE_WOLF": return "Dire Wolf Pet";
            case "VOID_CHICKEN": return "Void Chicken Pet";
            default: return null;
        }
    }

    public static long getCooldownDuration(String petName) {
        return petCooldowns.getOrDefault(petName, 0L);
    }

    public static long getCooldownDuration(ItemStack stack) {
        String petName = getPetName(stack);
        return petName != null ? petCooldowns.getOrDefault(petName, 0L) : 0L;
    }
}

