package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import gamerguy11.sixtoolsaddon.meteorfix.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WWindow.class, remap = false)
public abstract class WWindowLayoutMixin {
    @Inject(method = "onCalculateWidgetPositions", at = @At("RETURN"), require = 0)
    private void meteorfix$restorePosition(CallbackInfo ci) {
        try {
            WWindow window = (WWindow) (Object) this;
            WindowPositionMemory.afterLayout(window);
            if (window.parent instanceof WContainer parent) {
                for (Cell<?> cell : parent.cells) {
                    if (cell.widget() == window) {
                        cell.x = window.x;
                        cell.y = window.y;
                        cell.width = window.width;
                        cell.height = window.height;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
