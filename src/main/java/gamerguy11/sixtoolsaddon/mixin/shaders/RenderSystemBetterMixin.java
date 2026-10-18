package gamerguy11.sixtoolsaddon.mixin.shaders;

import com.mojang.blaze3d.systems.RenderSystem;
import gamerguy11.sixtoolsaddon.render.BetterShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class RenderSystemBetterMixin {
   @Inject(method = "flipFrame", at = @At("TAIL"))
   private static void sixtools$flipFrame(CallbackInfo info) {
      BetterShader.flipFrame();
   }
}
