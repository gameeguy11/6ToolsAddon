package gamerguy11.sixtoolsaddon.mixin.spoof;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public class XaeroWorldMapMixin {
   @ModifyArg(
      method = "method_25394",
      at = @At(
         value = "INVOKE",
         target = "Lxaero/map/graphics/MapRenderHelper;drawCenteredStringWithBackground(Lnet/minecraft/class_332;Lnet/minecraft/class_327;Ljava/lang/String;IIIFFFF)V",
         ordinal = 0
      ),
      index = 2,
      require = 1
   )
   private String sixtoolsaddon$cursorCoordinates(String line) {
      return Spoofer.processMapLine(line);
   }

   @ModifyExpressionValue(
      method = "getRightClickOptions",
      at = @At(
         value = "INVOKE",
         target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
         ordinal = 2
      ),
      require = 1
   )
   private String sixtoolsaddon$rightClickCoordinates(String line) {
      return Spoofer.processMapLine(line);
   }
}
