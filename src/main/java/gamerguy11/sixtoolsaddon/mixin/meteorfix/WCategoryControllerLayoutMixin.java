package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"meteordevelopment/meteorclient/gui/screens/ModulesScreen$WCategoryController"},
   remap = false
)
public abstract class WCategoryControllerLayoutMixin {
   @Inject(
      method = {"onCalculateWidgetPositions"},
      at = {@At("TAIL")},
      require = 0
   )
   private void meteorfix$preserveWindowCells(CallbackInfo ci) {
      for(Cell cell : ((WContainer)(Object)this).cells) {
         WWidget var5 = cell.widget();
         if (var5 instanceof WWindow window) {
            cell.x = window.x;
            cell.y = window.y;
            cell.width = window.width;
            cell.height = window.height;
         }
      }

   }
}
