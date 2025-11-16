package chat.cosmic.client.client.playerheadcooldown.mixin;


import chat.cosmic.client.client.playerheadcooldown.PlayerHeadCooldownMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public class PlayerHeadCooldownScreenMixin {

    @Inject(
            method = "drawSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onDrawSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        PlayerHeadCooldownMod.renderInventoryCooldown(context, slot.getStack(), slot.x, slot.y);
    }
}