package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import net.minecraft.client.gui.Click;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
