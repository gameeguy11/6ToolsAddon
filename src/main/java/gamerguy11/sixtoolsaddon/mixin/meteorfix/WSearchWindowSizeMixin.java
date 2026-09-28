package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.Utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the search window from growing wider/taller than the screen (or
 * narrower than is comfortable to read), without freezing it at any
 * particular size. Every call re-clamps against the window's *current*
 * real content size, so the box always tracks what's actually in it -
 * typing a more specific search correctly shrinks the window, clearing a
 * search correctly grows it back.
 */
@Mixin(value = WWidget.class, remap = false)
public abstract class WSearchWindowSizeMixin {
    private static final double MIN_WIDTH = 220;
    private static final double MIN_HEIGHT = 120;
    private static final double BOTTOM_MARGIN = 20;

    @Inject(method = "calculateSize", at = @At("RETURN"), require = 0)
    private void meteorfix$clampSearchSize(CallbackInfo ci) {
        if (!((Object) this instanceof WWindow window) || !"search".equals(window.id)) return;

        double availableHeight = Math.max(MIN_HEIGHT, Utils.getWindowHeight() - window.y - BOTTOM_MARGIN);

        window.width = Math.max(window.width, MIN_WIDTH);
        window.height = Math.min(Math.max(window.height, MIN_HEIGHT), availableHeight);
    }
}
