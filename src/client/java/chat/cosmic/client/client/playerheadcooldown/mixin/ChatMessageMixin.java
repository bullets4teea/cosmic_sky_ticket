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