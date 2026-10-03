package gamerguy11.sixtoolsaddon.mixin.splash;

import gamerguy11.sixtoolsaddon.splash.CustomSplashes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public class TitleScreenSplashClickMixin {
   @Shadow
   private SplashTextRenderer splashText;

   @Inject(
      method = {"render"},
      at = {@At("HEAD")}
   )
   private void sixtoolsaddon$rotateSplash(CallbackInfo ci) {
      if (CustomSplashes.due()) {
         CustomSplashes.Splash splash = CustomSplashes.pick();
         SplashTextRenderer renderer = new SplashTextRenderer(CustomSplashes.toText(splash.text()));
         CustomSplashes.bind(renderer, splash);
         this.splashText = renderer;
      }
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sixtoolsaddon$clickSplashLink(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      CustomSplashes.Splash splash = CustomSplashes.splashFor(this.splashText);
      if (splash == null || splash.url() == null || click.button() != 0) {
         return;
      }

      Screen screen = (Screen)(Object)this;
      MinecraftClient client = MinecraftClient.getInstance();
      int textWidth = client.textRenderer.getWidth(CustomSplashes.toText(splash.text()));
      double scale = 1.8D * 100.0D / (textWidth + 32);
      double angle = Math.toRadians(20.0D);
      double dx = click.x() - (screen.width / 2.0D + 123.0D);
      double dy = click.y() - 69.0D;
      double localX = (dx * Math.cos(angle) - dy * Math.sin(angle)) / scale;
      double localY = (dx * Math.sin(angle) + dy * Math.cos(angle)) / scale;

      if (Math.abs(localX) <= textWidth / 2.0D + 2.0D && localY >= -11.0D && localY <= 4.0D) {
         ConfirmLinkScreen.open(screen, splash.url());
         cir.setReturnValue(true);
      }
   }
}
