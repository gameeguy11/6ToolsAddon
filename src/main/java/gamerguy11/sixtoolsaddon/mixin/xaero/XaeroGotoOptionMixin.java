package gamerguy11.sixtoolsaddon.mixin.xaero;

import gamerguy11.sixtoolsaddon.efly.pathing.XaeroGotoOption;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class XaeroGotoOptionMixin {
   @SuppressWarnings({"rawtypes", "unchecked"})
   @Inject(method = "getRightClickOptions", at = @At("RETURN"), require = 0)
   private void sixtoolsaddon$addGoto(CallbackInfoReturnable cir) {
      Object result = cir.getReturnValue();
      if (result instanceof List list) {
         XaeroGotoOption.insertAtTop(list, (xaero.map.gui.IRightClickableElement)(Object)this);
      }
   }
}
