package chat.cosmic.client.client.playerheadcooldown.mixin;
import chat.cosmic.client.client.playerheadcooldown.PlayerHeadCooldownMod;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;

@Mixin(ChatHud.class)
public class ChatMessageMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onChatMessage(Text message, CallbackInfo ci) {
        String messageText = message.getString();


        if (messageText.contains("Your blessing has prevented your pet's ability cooldown!")) {

            int startIdx = messageText.indexOf('(');
            int endIdx = messageText.indexOf(')');
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                String petAbilityName = messageText.substring(startIdx + 1, endIdx);
                String petName = mapAbilityNameToPetName(petAbilityName);
                if (petName != null) {
                    PlayerHeadCooldownMod.cancelPetCooldown(petName);
                    System.out.println("Blessing prevented cooldown for: " + petName);
                }
            }
            return;
        }

        if (messageText.startsWith("PET:")) {
            String petName = extractPetNameFromMessage(messageText);
            if (petName != null) {
                PlayerHeadCooldownMod.handlePetActivationMessage(petName);
            }
        }

        Matcher matcher = PlayerHeadCooldownMod.getCooldownPattern().matcher(messageText);
        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            long totalCooldownMs = (minutes * 60L + seconds) * 1000L;
            PlayerHeadCooldownMod.handleServerCooldownMessage(totalCooldownMs);
        }
    }
    
    private String mapAbilityNameToPetName(String abilityName) {

        if (abilityName.contains("Man's Best Friend")) return "Dire Wolf Pet";
        if (abilityName.contains("Mining Mastery")) return "Miner Matt Pet";
        if (abilityName.contains("Harvest Helper")) return "Farmer Bob Pet";
        if (abilityName.contains("Battle Fury")) return "Battle Pig Pet";
        if (abilityName.contains("Slayer's Edge")) return "Slayer Sam Pet";
        if (abilityName.contains("Chaos Strike")) return "Chaos Cow Pet";
        if (abilityName.contains("Forge Master")) return "Blacksmith Brandon Pet";
        if (abilityName.contains("Fishing Fortune")) return "Fisherman Fred Pet";
        if (abilityName.contains("Potion Master")) return "Alchemist Alex Pet";
        if (abilityName.contains("Blood Sacrifice")) return "Blood Sheep Pet";
        if (abilityName.contains("Merchant's Luck")) return "Merchant Pet";
        if (abilityName.contains("Void Teleport")) return "Void Chicken Pet";
        if (abilityName.contains("Loot Multiplier")) return "Loot Llama Pet";
        if (abilityName.contains("Bee Swarm")) return "Barry Bee Pet";
        if (abilityName.contains("Quest Master")) return "Quester Quincy Pet";
        
        return null;
    }

    private String extractPetNameFromMessage(String message) {
        try {

            String content = message.substring(5).trim();


            int bracketIndex = content.indexOf('[');
            if (bracketIndex > 0) {

                String petNamePart = content.substring(0, bracketIndex).trim();


                return mapToInternalPetName(petNamePart);
            }
        } catch (Exception e) {

            return extractPetNameFallback(message);
        }

        return null;
    }

    private String mapToInternalPetName(String petNameFromMessage) {


        if (petNameFromMessage.contains("Dire Wolf")) return "Dire Wolf Pet";
        if (petNameFromMessage.contains("Miner Matt")) return "Miner Matt Pet";
        if (petNameFromMessage.contains("Farmer Bob")) return "Farmer Bob Pet";
        if (petNameFromMessage.contains("Battle Pig")) return "Battle Pig Pet";
        if (petNameFromMessage.contains("Slayer Sam")) return "Slayer Sam Pet";
        if (petNameFromMessage.contains("Chaos Cow")) return "Chaos Cow Pet";
        if (petNameFromMessage.contains("Blacksmith Brandon")) return "Blacksmith Brandon Pet";
        if (petNameFromMessage.contains("Fisherman Fred")) return "Fisherman Fred Pet";
        if (petNameFromMessage.contains("Alchemist Alex")) return "Alchemist Alex Pet";
        if (petNameFromMessage.contains("Blood Sheep")) return "Blood Sheep Pet";
        if (petNameFromMessage.contains("Merchant")) return "Merchant Pet";
        if (petNameFromMessage.contains("Void Chicken")) return "Void Chicken Pet";
        if (petNameFromMessage.contains("Loot Llama")) return "Loot Llama Pet";
        if (petNameFromMessage.contains("Barry Bee")) return "Barry Bee Pet";


        return petNameFromMessage + " Pet";
    }

    private String extractPetNameFallback(String message) {

        String[] knownPets = {
                "Dire Wolf", "Miner Matt", "Farmer Bob", "Battle Pig",
                "Slayer Sam", "Chaos Cow", "Blacksmith Brandon", "Fisherman Fred",
                "Alchemist Alex", "Blood Sheep", "Merchant", "Void Chicken",
                "Loot Llama", "Barry Bee"
        };

        for (String pet : knownPets) {
            if (message.contains(pet)) {
                return pet + " Pet";
            }
        }

        return null;
    }
}