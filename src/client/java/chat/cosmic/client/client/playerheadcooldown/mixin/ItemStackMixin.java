package chat.cosmic.client.client.playerheadcooldown.mixin;

import chat.cosmic.client.client.playerheadcooldown.ActivePetEffectsHud;
import chat.cosmic.client.client.playerheadcooldown.PlayerHeadCooldownMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onItemUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = (ItemStack) (Object) this;

        if (PlayerHeadCooldownMod.isPet(stack)) {
            String petName = PlayerHeadCooldownMod.getPetName(stack);

            if (PlayerHeadCooldownMod.isOnCooldown(stack)) {
                if (world.isClient) {
                    long remaining = PlayerHeadCooldownMod.getRemainingCooldown(stack);
                    String timeText = PlayerHeadCooldownMod.formatTime(remaining);
                    user.sendMessage(Text.literal("§c" + petName + " is on cooldown! Time remaining: §e" + timeText), true);
                }
                cir.setReturnValue(TypedActionResult.pass(stack));
                return;
            }

            int slotIndex = -1;
            for (int i = 0; i < user.getInventory().size(); i++) {
                if (user.getInventory().getStack(i) == stack) {
                    slotIndex = i;
                    break;
                }
            }

            PlayerHeadCooldownMod.startCooldown(stack, slotIndex);

            int petLevel = ActivePetEffectsHud.getPetLevel(stack);
            ActivePetEffectsHud.activatePetEffect(petName, petLevel);

            if (world.isClient) {
                user.sendMessage(Text.literal("§6" + petName + " activated! Cooldown started."), true);
            }

            cir.setReturnValue(TypedActionResult.success(stack, world.isClient));
        }
    }
}