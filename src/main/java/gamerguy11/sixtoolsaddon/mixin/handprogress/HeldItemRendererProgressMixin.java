package gamerguy11.sixtoolsaddon.mixin.handprogress;

import gamerguy11.sixtoolsaddon.modules.visual.HandProgress;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererProgressMixin {
   @Shadow
   private float equipProgressMainHand;
   @Shadow
   private float lastEquipProgressMainHand;
   @Shadow
   private float equipProgressOffHand;
   @Shadow
   private float lastEquipProgressOffHand;

   @Inject(method = "updateHeldItems", at = @At("TAIL"))
   private void sixtools$handProgress(CallbackInfo ci) {
      HandProgress module = Modules.get().get(HandProgress.class);
      if (module == null || !module.isActive()) {
         return;
      }

      if (module.modifyMainhand.get()) {
         float progress = module.mainhandProgress.get().floatValue();
         this.equipProgressMainHand = progress;
         this.lastEquipProgressMainHand = progress;
      }

      if (module.modifyOffhand.get()) {
         float progress = module.offhandProgress.get().floatValue();
         this.equipProgressOffHand = progress;
         this.lastEquipProgressOffHand = progress;
      }
   }
}
