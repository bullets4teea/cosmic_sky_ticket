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
        Matcher matcher = PlayerHeadCooldownMod.getCooldownPattern().matcher(messageText);

        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            long totalCooldownMs = (minutes * 60L + seconds) * 1000L;

            PlayerHeadCooldownMod.handleServerCooldownMessage(totalCooldownMs);
        }
    }
}