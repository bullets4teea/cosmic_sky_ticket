package chat.cosmic.client.mixin;

import chat.cosmic.client.client.HighlightSearchMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow protected int x;
    @Shadow protected int y;

    @Unique private TextFieldWidget searchField;
    @Unique private static String persistentLastSearch = "";
    @Unique private static int searchBarX = -1;
    @Unique private static int searchBarY = -1;
    @Unique private boolean isDragging = false;
    @Unique private int dragOffsetX;
    @Unique private int dragOffsetY;
    @Unique private long lastClickTime = 0;
    @Unique private boolean isGrayedOut = false;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addSearchBox(CallbackInfo ci) {
        if (!HighlightSearchMod.isSearchVisible) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;

        if (searchBarX == -1 || searchBarY == -1) {
            searchBarX = screen.width / 2 - 85;
            searchBarY = screen.height / 2 + 120;
        }

        this.searchField = new TextFieldWidget(
                this.textRenderer,
                searchBarX, searchBarY,
                170, 20,
                Text.translatable("item.search")
        );

        this.addSelectableChild(this.searchField);
        this.searchField.setMaxLength(50);
        this.searchField.setText(persistentLastSearch);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!HighlightSearchMod.isSearchVisible || this.searchField == null) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        this.searchField.setX(searchBarX);
        this.searchField.setY(searchBarY);
        this.searchField.render(context, mouseX, mouseY, delta);

        String currentSearch = this.searchField.getText().toLowerCase();
        if (!currentSearch.equals(persistentLastSearch)) {
            persistentLastSearch = currentSearch;
        }

        for (Slot slot : screen.getScreenHandler().slots) {
            int xPos = slot.x + this.x;
            int yPos = slot.y + this.y;

            if (slot.hasStack()) {
                ItemStack stack = slot.getStack();
                boolean matches = itemMatchesSearch(stack, currentSearch);

                if (matches && !currentSearch.isEmpty()) {
                    context.fill(xPos, yPos, xPos + 16, yPos + 16, 0x60FFFF00);
                }

                if (!matches && !currentSearch.isEmpty() && isGrayedOut) {
                    context.fill(xPos, yPos, xPos + 16, yPos + 16, 0x80444444);
                }
            } else {
                if (isGrayedOut) {
                    context.fill(xPos, yPos, xPos + 16, yPos + 16, 0x80444444);
                }
            }
        }
    }

    @Unique
    private boolean itemMatchesSearch(ItemStack stack, String searchTerm) {
        if (searchTerm.isEmpty()) return false;

        if (stack.getName().getString().toLowerCase().contains(searchTerm)) {
            return true;
        }

        List<Text> tooltip = stack.getTooltip(null, TooltipContext.Default.ADVANCED);
        for (int i = 1; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString().toLowerCase();

            if (line.contains("durability") || line.matches(".*\\d+\\s*/\\s*\\d+.*")) {
                continue;
            }

            if (line.contains(searchTerm)) {
                return true;
            }
        }

        return false;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.searchField == null) return;

        if (button == 0) {
            HandledScreen<?> screen = (HandledScreen<?>) (Object) this;

            if (this.searchField.isMouseOver(mouseX, mouseY)) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < 250) {
                    isGrayedOut = !isGrayedOut;
                }
                lastClickTime = currentTime;

                isDragging = true;
                dragOffsetX = (int) mouseX - searchBarX;
                dragOffsetY = (int) mouseY - searchBarY;
                return;
            }

            for (Slot slot : screen.getScreenHandler().slots) {
                int xPos = slot.x + this.x;
                int yPos = slot.y + this.y;

                if (mouseX >= xPos && mouseX <= xPos + 16 && mouseY >= yPos && mouseY <= yPos + 16) {
                    this.searchField.setFocused(false);
                    return;
                }
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0) {
            isDragging = false;
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (isDragging && button == 0 && this.searchField != null) {
            searchBarX = (int) mouseX - dragOffsetX;
            searchBarY = (int) mouseY - dragOffsetY;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!HighlightSearchMod.isSearchVisible || this.searchField == null) return;

        if (this.searchField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.client.setScreen(null);
                cir.setReturnValue(true);
            }
            if (keyCode == GLFW.GLFW_KEY_E) {
                cir.setReturnValue(true);
            }
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_R) {
            HighlightSearchMod.isSearchVisible = !HighlightSearchMod.isSearchVisible;
            cir.setReturnValue(true);
        }
    }
}
