package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import net.minecraft.client.gui.Click;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WDropdown.onMouseClicked() only reports a click as "used" when it lands
 * exactly on the dropdown header or on one of its open option widgets. If a
 * click is anywhere else inside the popup's visual area (a gap between
 * items, the popup's own border, a stale mouseOver flag, etc.) the method
 * returns false and the surrounding WContainer keeps walking its cell list,
 * handing that same click to whatever widget is really sitting underneath
 * the popup on screen.
 *
 * This treats an open dropdown as modal: while it was expanded at the start
 * of the click, the click is always swallowed, regardless of exactly what
 * the original logic decided. That's what stops the click from ever
 * reaching the widget underneath.
 */
@Mixin(value = WDropdown.class, remap = false)
public abstract class WDropdownClickThroughMixin {

    @Shadow
    protected boolean expanded;

    @Unique
    private boolean meteorfix$wasExpanded;

    @Inject(method = "onMouseClicked", at = @At("HEAD"))
    private void meteorfix$captureExpanded(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        meteorfix$wasExpanded = expanded;
    }

    @Inject(method = "onMouseClicked", at = @At("RETURN"), cancellable = true)
    private void meteorfix$swallowWhileOpen(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (meteorfix$wasExpanded && !cir.getReturnValueZ()) {
            cir.setReturnValue(true);
        }
    }
}
