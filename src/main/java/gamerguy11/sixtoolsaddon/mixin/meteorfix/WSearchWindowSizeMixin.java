package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.Utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {WWidget.class},
   remap = false
)
public abstract class WSearchWindowSizeMixin {
   private static final double MIN_WIDTH = (double)220.0F;
   private static final double MIN_HEIGHT = (double)120.0F;
   private static final double BOTTOM_MARGIN = (double)20.0F;

   @Inject(
      method = {"calculateSize"},
      at = {@At("RETURN")},
      require = 0
   )
   private void meteorfix$clampSearchSize(CallbackInfo ci) {
      if ((Object)this instanceof WWindow window) {
         if ("search".equals(window.id)) {
            double availableHeight = Math.max((double)120.0F, (double)Utils.getWindowHeight() - window.y - (double)20.0F);
            window.width = Math.max(window.width, (double)220.0F);
            window.height = Math.min(Math.max(window.height, (double)120.0F), availableHeight);
            return;
         }
      }

   }
}
