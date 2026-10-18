package gamerguy11.sixtoolsaddon.mixin.swing;

import gamerguy11.sixtoolsaddon.modules.visual.SwingSpeed;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererSwingRotationMixin {
   @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
   private void sixtools$swingRotation(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
      SwingSpeed module = Modules.get().get(SwingSpeed.class);
      if (module == null || !module.isActive()) {
         return;
      }

      ci.cancel();
      int i = arm == Arm.RIGHT ? 1 : -1;
      float f = MathHelper.sin(swingProgress * swingProgress * 3.1415927F);
      float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * 3.1415927F);
      if (module.rotationY.get()) {
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45.0F + f * -20.0F)));
      }

      if (module.rotationZ.get()) {
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -20.0F));
      }

      if (module.rotationX.get()) {
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
      }

      if (module.rotationY.get()) {
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45.0F));
      }
   }
}
