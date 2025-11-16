package chat.cosmic.client.mixin;

import chat.cosmic.client.client.AnnouncementHiderClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {


    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void filterAnnouncements(Text message, CallbackInfo ci) {
        if (AnnouncementHiderClient.hideAnnouncements) {
            String messageText = message.getString().toLowerCase();


            if (messageText.contains("new island quest")) {
                ci.cancel();

            }
        }
    }
}