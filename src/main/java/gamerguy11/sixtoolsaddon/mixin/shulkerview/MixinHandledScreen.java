package gamerguy11.sixtoolsaddon.mixin.shulkerview;

import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import gamerguy11.sixtoolsaddon.shulkerview.ShulkerInfo;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.Click;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({HandledScreen.class})
public abstract class MixinHandledScreen extends Screen {
   protected MixinHandledScreen(Text title) {
      super(title);
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void shulkerView$tick(CallbackInfo ci) {
      ShulkerView module = (ShulkerView)Modules.get().get(ShulkerView.class);
      if (module != null && module.isActive()) {
         module.getUpdateHandler().tick();
      }
   }

   @Inject(
      method = {"drawMouseoverTooltip"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void shulkerView$render(DrawContext context, int x, int y, CallbackInfo ci) {
      ShulkerView module = (ShulkerView)Modules.get().get(ShulkerView.class);
      if (module != null && module.isActive()) {
         module.getRenderHandler().render(context, (double)x, (double)y);
         if (!module.shouldKeepItemTooltip()) {
            Slot focused = ((DuckHandledScreen)(Object)this).shulkerView$getFocused();
            if (focused != null) {
               ItemStack stack = focused.getStack();
               if (ShulkerInfo.isShulkerBox(stack)) {
                  ci.cancel();
               }
            }

         }
      }
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void shulkerView$mouseClicked(Click click, boolean doubled, CallbackInfoReturnable cir) {
      ShulkerView module = (ShulkerView)Modules.get().get(ShulkerView.class);
      if (module != null && module.isActive()) {
         if (module.getRenderHandler().mouseClick(click)) {
            cir.setReturnValue(true);
         }

      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void shulkerView$mouseDragged(Click click, double offsetX, double offsetY, CallbackInfoReturnable cir) {
      ShulkerView module = (ShulkerView)Modules.get().get(ShulkerView.class);
      if (module != null && module.isActive()) {
         if (module.getRenderHandler().mouseDrag(click, offsetX, offsetY)) {
            cir.setReturnValue(true);
         }

      }
   }

   @Inject(
      method = {"mouseReleased"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void shulkerView$mouseReleased(Click click, CallbackInfoReturnable cir) {
      ShulkerView module = (ShulkerView)Modules.get().get(ShulkerView.class);
      if (module != null && module.isActive()) {
         if (module.getRenderHandler().mouseRelease(click)) {
            cir.setReturnValue(true);
         }

      }
   }

   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")}
   )
   private void shulkerView$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable cir) {
      ShulkerView module = (ShulkerView)Modules.get().get(ShulkerView.class);
      if (module != null && module.isActive()) {
         module.getRenderHandler().mouseScroll(mouseX, mouseY, verticalAmount);
      }
   }
}
