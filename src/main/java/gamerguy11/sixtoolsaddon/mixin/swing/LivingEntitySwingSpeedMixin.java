package gamerguy11.sixtoolsaddon.mixin.swing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gamerguy11.sixtoolsaddon.modules.visual.SwingSpeed;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LivingEntity.class})
public class LivingEntitySwingSpeedMixin {
   @ModifyExpressionValue(
      method = {"getHandSwingDuration"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/component/type/SwingAnimationComponent;duration()I"
      )}
   )
   private int sixtoolsaddon$swingDuration(int original) {
      if ((Object)this != MinecraftClient.getInstance().player) {
         return original;
      }

      SwingSpeed module = Modules.get().get(SwingSpeed.class);
      if (module == null || !module.isActive()) {
         return original;
      }

      return module.getSwingDuration(original);
   }
}
