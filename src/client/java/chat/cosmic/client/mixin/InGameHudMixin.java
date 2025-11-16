package chat.cosmic.client.mixin;

import chat.cosmic.client.client.AnnouncementHiderClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    // Prevent setting title only if it contains "new island quest"
    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true, require = 0)
    private void preventSetTitle(Text title, CallbackInfo ci) {
        if (AnnouncementHiderClient.hideAnnouncements && title != null) {
            String titleText = title.getString().toLowerCase();
            if (titleText.contains("new island quest")) {
                ci.cancel();
            }
        }
    }



    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true, require = 0)
    private void preventSetSubtitle(Text subtitle, CallbackInfo ci) {
        if (AnnouncementHiderClient.hideAnnouncements && subtitle != null) {
            String subtitleText = subtitle.getString().toLowerCase();
            if (subtitleText.contains("new island quest")) {
                ci.cancel();
            }
        }
    }


}