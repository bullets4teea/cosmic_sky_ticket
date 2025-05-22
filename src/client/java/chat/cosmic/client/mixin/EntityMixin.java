package chat.cosmic.client.mixin;

import chat.cosmic.client.client.NameTagSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void forceClientGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof MobEntity mob && !(self instanceof PlayerEntity)) {
            String customName = mob.getCustomName() != null ? mob.getCustomName().getString() : "";
            if (!customName.isEmpty() && customName.contains("Marauder")) {
                cir.setReturnValue(NameTagSystem.getInstance().shouldGlow(customName));
            }
        }
    }
}