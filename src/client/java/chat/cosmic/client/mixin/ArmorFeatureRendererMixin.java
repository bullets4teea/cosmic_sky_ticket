package chat.cosmic.client.mixin;

import chat.cosmic.client.client.ArmorToggleMod;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin<T extends LivingEntity> {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                               T entity, float limbAngle, float limbDistance, float tickDelta,
                               float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (ArmorToggleMod.hideArmor && !(entity instanceof PlayerEntity)) {
            ci.cancel();
        }
    }
}