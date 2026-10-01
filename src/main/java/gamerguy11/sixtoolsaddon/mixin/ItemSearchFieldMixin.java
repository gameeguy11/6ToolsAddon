package gamerguy11.sixtoolsaddon.mixin;

import gamerguy11.sixtoolsaddon.modules.ItemSearchBar;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({HandledScreen.class})
public abstract class ItemSearchFieldMixin extends Screen {
   @Shadow
   protected int x;
   @Shadow
   protected int y;
   @Unique
   private TextFieldWidget itemSearch$field;
   @Unique
   private ItemSearchBar itemSearch$module;

   protected ItemSearchFieldMixin(Text title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void itemSearch$onInit(CallbackInfo ci) {
      this.itemSearch$module = (ItemSearchBar)Modules.get().get(ItemSearchBar.class);
      if (this.itemSearch$module != null && this.itemSearch$module.isActive() && this.itemSearch$module.shouldShowSearchField()) {
         this.itemSearch$field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, this.x + this.itemSearch$module.getOffsetX(), this.y + this.itemSearch$module.getOffsetY(), this.itemSearch$module.getFieldWidth(), this.itemSearch$module.getFieldHeight(), Text.of("Search items..."));
         this.itemSearch$field.setPlaceholder(Text.of("Search items..."));
         this.itemSearch$field.setMaxLength(100);
         String currentQuery = (String)this.itemSearch$module.searchQuery.get();
         if (currentQuery != null && !currentQuery.isEmpty()) {
            this.itemSearch$field.setText(currentQuery);
         }

         this.itemSearch$field.setChangedListener((text) -> {
            if (this.itemSearch$module != null) {
               this.itemSearch$module.updateSearchQuery(text);
            }

         });
         this.itemSearch$field.setFocused(false);
         this.itemSearch$field.setEditable(true);
         this.itemSearch$field.setVisible(true);
         this.addDrawableChild(this.itemSearch$field);
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void itemSearch$onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (this.itemSearch$module != null && this.itemSearch$module.isActive() && this.itemSearch$module.shouldShowSearchField()) {
         if (this.itemSearch$field != null) {
            this.itemSearch$field.setX(this.x + this.itemSearch$module.getOffsetX());
            this.itemSearch$field.setY(this.y + this.itemSearch$module.getOffsetY());
            this.itemSearch$field.setVisible(true);
         }
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void itemSearch$onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
      if (this.itemSearch$module != null && this.itemSearch$module.isActive() && this.itemSearch$module.shouldShowSearchField()) {
         if (this.itemSearch$field != null) {
            int keyCode = input.key();
            if (keyCode == 258) {
               this.setFocused(this.itemSearch$field);
               this.itemSearch$field.setFocused(true);
               cir.setReturnValue(true);
            } else if (keyCode == 256 && this.itemSearch$field.isFocused()) {
               this.setFocused((Element)null);
               this.itemSearch$field.setFocused(false);
               cir.setReturnValue(true);
            } else {
               if (this.itemSearch$field.isFocused()) {
                  this.itemSearch$field.keyPressed(input);
                  if (keyCode != 256) {
                     cir.setReturnValue(true);
                  }
               }

            }
         }
      }
   }

   @Override
   public boolean charTyped(CharInput input) {
      if (this.itemSearch$module != null && this.itemSearch$module.isActive() && this.itemSearch$module.shouldShowSearchField()
         && this.itemSearch$field != null && this.itemSearch$field.isFocused()
         && this.itemSearch$field.charTyped(input)) {
         return true;
      }
      return super.charTyped(input);
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void itemSearch$onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (this.itemSearch$module != null && this.itemSearch$module.isActive() && this.itemSearch$module.shouldShowSearchField()) {
         if (this.itemSearch$field != null) {
            double mouseX = click.x();
            double mouseY = click.y();
            boolean clickedOnField = mouseX >= (double)this.itemSearch$field.getX() && mouseX < (double)(this.itemSearch$field.getX() + this.itemSearch$field.getWidth()) && mouseY >= (double)this.itemSearch$field.getY() && mouseY < (double)(this.itemSearch$field.getY() + this.itemSearch$field.getHeight());
            if (clickedOnField) {
               this.setFocused(this.itemSearch$field);
               this.itemSearch$field.setFocused(true);
               if (this.itemSearch$field.mouseClicked(click, doubled)) {
                  cir.setReturnValue(true);
               }
            } else if (this.getFocused() == this.itemSearch$field) {
               this.setFocused((Element)null);
               this.itemSearch$field.setFocused(false);
            }

         }
      }
   }

   @Inject(
      method = {"drawSlot"},
      at = {@At("HEAD")}
   )
   private void itemSearch$onDrawSlot(DrawContext context, Slot slot, int x, int y, CallbackInfo ci) {
      if (this.itemSearch$module != null && this.itemSearch$module.isActive()) {
         if (slot.hasStack()) {
            if (this.itemSearch$module.shouldHighlightSlot(slot.getStack())) {
               context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ((SettingColor)this.itemSearch$module.highlightColor.get()).getPacked());
            }

         }
      }
   }
}
