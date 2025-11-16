package chat.cosmic.client.mixin;

import chat.cosmic.client.client.ChatHistorySaver;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHistoryMixin {
    
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void onChatMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        ChatHistorySaver.addChatMessage(message, signature, indicator);
    }
    
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void onChatClear(boolean clearHistory, CallbackInfo ci) {
        // Prevent chat from being cleared
        ChatHistorySaver.onChatClearAttempt();
        ci.cancel();
    }
}
