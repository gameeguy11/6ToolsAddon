package gamerguy11.sixtoolsaddon.mixin;

import gamerguy11.sixtoolsaddon.modules.ItemSearchBar;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class ItemSearchFieldMixin extends Screen {

    protected ItemSearchFieldMixin(Text title) {
        super(title);
    }

    @Shadow protected int x;
    @Shadow protected int y;

    @Unique private TextFieldWidget itemSearch$field;
    @Unique private ItemSearchBar itemSearch$module;

    @Inject(method = "init", at = @At("TAIL"))
    private void itemSearch$onInit(CallbackInfo ci) {
        itemSearch$module = Modules.get().get(ItemSearchBar.class);
        if (itemSearch$module == null || !itemSearch$module.isActive() || !itemSearch$module.shouldShowSearchField()) return;

        itemSearch$field = new TextFieldWidget(
            MinecraftClient.getInstance().textRenderer,
            this.x + itemSearch$module.getOffsetX(),
            this.y + itemSearch$module.getOffsetY(),
            itemSearch$module.getFieldWidth(),
            itemSearch$module.getFieldHeight(),
            Text.of("Search items...")
        );
        itemSearch$field.setPlaceholder(Text.of("Search items..."));
        itemSearch$field.setMaxLength(100);

        String currentQuery = itemSearch$module.searchQuery.get();
        if (currentQuery != null && !currentQuery.isEmpty()) {
            itemSearch$field.setText(currentQuery);
        }

        itemSearch$field.setChangedListener(text -> {
            if (itemSearch$module != null) {
                itemSearch$module.updateSearchQuery(text);
            }
        });
        itemSearch$field.setFocused(false);
        itemSearch$field.setEditable(true);
        itemSearch$field.setVisible(true);

        this.addDrawableChild(itemSearch$field);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void itemSearch$onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (itemSearch$module == null || !itemSearch$module.isActive() || !itemSearch$module.shouldShowSearchField()) return;
        if (itemSearch$field == null) return;

        itemSearch$field.setX(this.x + itemSearch$module.getOffsetX());
        itemSearch$field.setY(this.y + itemSearch$module.getOffsetY());
        itemSearch$field.setVisible(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void itemSearch$onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (itemSearch$module == null || !itemSearch$module.isActive() || !itemSearch$module.shouldShowSearchField()) return;
        if (itemSearch$field == null) return;

        int keyCode = input.key();

        if (keyCode == 258) {
            this.setFocused(itemSearch$field);
            itemSearch$field.setFocused(true);
            cir.setReturnValue(true);
            return;
        }
        if (keyCode == 256 && itemSearch$field.isFocused()) {
            this.setFocused(null);
            itemSearch$field.setFocused(false);
            cir.setReturnValue(true);
            return;
        }
        if (itemSearch$field.isFocused()) {
            itemSearch$field.keyPressed(input);
            if (keyCode != 256) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void itemSearch$onCharTyped(CharInput input, CallbackInfoReturnable<Boolean> cir) {
        if (itemSearch$module == null || !itemSearch$module.isActive() || !itemSearch$module.shouldShowSearchField()) return;
        if (itemSearch$field == null || !itemSearch$field.isFocused()) return;

        if (itemSearch$field.charTyped(input)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void itemSearch$onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (itemSearch$module == null || !itemSearch$module.isActive() || !itemSearch$module.shouldShowSearchField()) return;
        if (itemSearch$field == null) return;

        double mouseX = click.x();
        double mouseY = click.y();
        boolean clickedOnField = mouseX >= itemSearch$field.getX() &&
                                  mouseX < itemSearch$field.getX() + itemSearch$field.getWidth() &&
                                  mouseY >= itemSearch$field.getY() &&
                                  mouseY < itemSearch$field.getY() + itemSearch$field.getHeight();

        if (clickedOnField) {
            this.setFocused(itemSearch$field);
            itemSearch$field.setFocused(true);
            if (itemSearch$field.mouseClicked(click, doubled)) {
                cir.setReturnValue(true);
            }
        } else if (this.getFocused() == itemSearch$field) {
            this.setFocused(null);
            itemSearch$field.setFocused(false);
        }
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void itemSearch$onDrawSlot(DrawContext context, Slot slot, int x, int y, CallbackInfo ci) {
        if (itemSearch$module == null || !itemSearch$module.isActive()) return;
        if (!slot.hasStack()) return;

        if (itemSearch$module.shouldHighlightSlot(slot.getStack())) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16,
                itemSearch$module.highlightColor.get().getPacked());
        }
    }
}
