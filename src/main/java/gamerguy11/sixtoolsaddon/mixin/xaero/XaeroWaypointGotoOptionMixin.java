package gamerguy11.sixtoolsaddon.mixin.xaero;

import gamerguy11.sixtoolsaddon.efly.pathing.XaeroGotoOption;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.map.mods.gui.WaypointReader", remap = false)
public abstract class XaeroWaypointGotoOptionMixin {
   @SuppressWarnings({"rawtypes", "unchecked"})
   @Inject(method = "getRightClickOptions(Lxaero/map/mods/gui/Waypoint;Lxaero/map/gui/IRightClickableElement;)Ljava/util/ArrayList;", at = @At("RETURN"), require = 0)
   private void sixtoolsaddon$addGoto(xaero.map.mods.gui.Waypoint waypoint, xaero.map.gui.IRightClickableElement target, CallbackInfoReturnable cir) {
      Object result = cir.getReturnValue();
      if (result instanceof List list) {
         XaeroGotoOption.insertAtTop(list, target, (int) Math.floor(waypoint.getRenderX()), (int) Math.floor(waypoint.getRenderZ()));
      }
   }
}
