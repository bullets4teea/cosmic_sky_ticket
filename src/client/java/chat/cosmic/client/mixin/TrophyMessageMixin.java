package chat.cosmic.client.mixin;

import chat.cosmic.client.client.TrophyGuiScreen;
import chat.cosmic.client.client.TrophyTrackerMod;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class TrophyMessageMixin {

    // Intercept action bar messages (above hotbar)
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        if (message != null) {
            String messageText = message.getString();
            TrophyTrackerMod.processTrophyMessage(messageText);
        }
    }
}