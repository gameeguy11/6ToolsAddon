package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   value = {WWindow.class},
   remap = false
)
public interface WWindowDragStateAccessor {
   @Accessor("moved")
   boolean meteorfix$getMoved();

   @Accessor("moved")
   void meteorfix$setMoved(boolean var1);

   @Accessor("movedX")
   void meteorfix$setMovedX(double var1);

   @Accessor("movedY")
   void meteorfix$setMovedY(double var1);
}
