package gamerguy11.sixtoolsaddon.mixin.splash;

import gamerguy11.sixtoolsaddon.splash.CustomSplashes;
import net.minecraft.text.Text;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SplashTextResourceSupplier.class})
public class SplashTextResourceSupplierMixin {
   @Inject(
      method = {"get"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void sixtoolsaddon$overrideSplash(CallbackInfoReturnable<SplashTextRenderer> cir) {
      String custom = CustomSplashes.pick();
      if (custom != null) {
         cir.setReturnValue(new SplashTextRenderer(Text.literal(custom)));
      }

   }
}
