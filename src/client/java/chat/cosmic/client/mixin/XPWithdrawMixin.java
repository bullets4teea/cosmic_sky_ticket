package chat.cosmic.client.mixin;

import chat.cosmic.client.client.xpwithdraw;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Minecraft's outgoing command sending to intercept /xpbottle and /xpb.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class XPWithdrawMixin {

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onSendChatCommand(String command, CallbackInfo ci) {
        if (xpwithdraw.handleOutgoingCommand(command)) {
            ci.cancel(); // cancel the original command send
        }
    }
}
