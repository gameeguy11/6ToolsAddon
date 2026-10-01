package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import gamerguy11.sixtoolsaddon.meteorfix.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
   value = {WWindow.class},
   remap = false
)
public abstract class WWindowClampMixin {
   @Redirect(
      method = {"onCalculateWidgetPositions"},
      at = @At(
   value = "FIELD",
   target = "Lmeteordevelopment/meteorclient/gui/widgets/containers/WWindow;height:D",
   opcode = 180
),
      require = 0
   )
   private double meteorfix$useVisibleHeightForClamp(WWindow window) {
      try {
         return WindowPositionMemory.effectiveHeight(window);
      } catch (Throwable var3) {
         return window.height;
      }
   }
}
