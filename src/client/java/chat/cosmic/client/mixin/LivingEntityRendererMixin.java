package chat.cosmic.client.mixin;

import chat.cosmic.client.client.NameTagSystem;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hideMobNameTags(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof MobEntity && !(entity instanceof PlayerEntity)) {
            String customName = entity.getCustomName() != null ? entity.getCustomName().getString() : "";
            if (!customName.isEmpty() && customName.contains("Marauder")) {
                cir.setReturnValue(NameTagSystem.getInstance().shouldShowNameTag(customName));
            }
        }
    }
}