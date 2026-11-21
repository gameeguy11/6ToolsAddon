package gamerguy11.sixtoolsaddon.mixin.shulkerview;

import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import gamerguy11.sixtoolsaddon.shulkerview.ShulkerTooltip;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
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
      method = {"drawMouseoverTooltip"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void shulkerView$render(DrawContext context, int x, int y, CallbackInfo ci) {
      ShulkerView module = Modules.get().get(ShulkerView.class);
      if (module == null || !module.isActive()) return;

      DuckHandledScreen duck = (DuckHandledScreen) (Object) this;
      if (ShulkerTooltip.render((HandledScreen<?>) (Object) this, context, x, y, module, duck.shulkerView$getFocused(), duck.shulkerView$getX(), duck.shulkerView$getY())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void shulkerView$mouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (click.button() != 2) return;

      ShulkerView module = Modules.get().get(ShulkerView.class);
      if (module == null || !module.isActive() || !module.isMiddleClickPreview()) return;

      DuckHandledScreen duck = (DuckHandledScreen) (Object) this;
      if (ShulkerTooltip.openPreview(this, duck.shulkerView$getFocused())) {
         cir.setReturnValue(true);
      }
   }
}
