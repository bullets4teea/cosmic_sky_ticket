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

            // Determine which slot this pet is in
            int slot = -1;
            if (hand == Hand.MAIN_HAND) {
                slot = user.getInventory().selectedSlot;
            } else {
                // For offhand, it's slot 40
                slot = 40;
            }

            // Track pet usage immediately when clicked
            PlayerHeadCooldownMod.trackPetUsage(stack, slot);

            // Check if pet is on cooldown
            if (PlayerHeadCooldownMod.isOnCooldown(stack)) {
                if (world.isClient) {
                    long remaining = PlayerHeadCooldownMod.getRemainingCooldown(stack);
                    String timeText = PlayerHeadCooldownMod.formatTime(remaining);
                    user.sendMessage(Text.literal("§c" + petName + " is on cooldown! Time remaining: §e" + timeText), true);
                }
                cir.setReturnValue(TypedActionResult.pass(stack));
                return;
            }

            // Check if same pet type is already active
            if (ActivePetEffectsHud.isPetTypeActive(petName)) {
                if (world.isClient) {
                    user.sendMessage(Text.literal("§cYou already have an active " + petName + "! Wait for it to finish."), true);
                }
                cir.setReturnValue(TypedActionResult.pass(stack));
                return;
            }

            // Start cooldown and track the specific slot
            if (world.isClient) {
                PlayerHeadCooldownMod.startCooldown(stack, slot);
            }
        }
    }
}