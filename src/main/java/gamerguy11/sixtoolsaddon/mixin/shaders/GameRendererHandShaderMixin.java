package gamerguy11.sixtoolsaddon.mixin.shaders;

import gamerguy11.sixtoolsaddon.render.BetterShader;
import gamerguy11.sixtoolsaddon.render.HandShaderRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandShaderMixin {
   @Inject(method = "renderWorld", at = @At("TAIL"))
   private void sixtools$flushHandShader(RenderTickCounter tickCounter, CallbackInfo ci) {
      BetterShader shader = BetterShader.getIfCreated();
      if (shader != null) {
         HandShaderRenderer.flush(shader);
      }
   }
}
