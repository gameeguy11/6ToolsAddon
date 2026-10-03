package gamerguy11.sixtoolsaddon.mixin.splash;

import gamerguy11.sixtoolsaddon.splash.CustomSplashes;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {SplashTextResourceSupplier.class}, priority = 500)
public class SplashTextResourceSupplierMixin {
   @Inject(
      method = {"get"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sixtoolsaddon$overrideSplash(CallbackInfoReturnable<SplashTextRenderer> cir) {
      CustomSplashes.Splash splash = CustomSplashes.pick();
      SplashTextRenderer renderer = new SplashTextRenderer(CustomSplashes.toText(splash.text()));
      CustomSplashes.bind(renderer, splash);
      cir.setReturnValue(renderer);
   }
}
