package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import net.minecraft.client.gui.Click;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Belt-and-braces companion to WDropdownClickThroughMixin.
 *
 * A WContainer normally hands a click to its cells in add order, which is
 * also (roughly) back-to-front draw order. An open dropdown's popup is
 * drawn as a floating overlay on top of everything else regardless of when
 * it was added, so if it happens to sit earlier in a cell list than the
 * widget it visually overlaps, that widget would still be asked first.
 *
 * Before doing normal iteration, this looks for any dropdown in the
 * container that is currently expanded and gives it first refusal on the
 * click/release. WDropdownClickThroughMixin guarantees such a dropdown
 * consumes the event, so nothing underneath it ever sees the click.
 */
@Mixin(value = WContainer.class, remap = false)
public abstract class WContainerDropdownPriorityMixin {

    private static final Field EXPANDED_FIELD;

    static {
        Field field = null;
        try {
            field = WDropdown.class.getDeclaredField("expanded");
            field.setAccessible(true);
        } catch (Throwable ignored) {
        }
        EXPANDED_FIELD = field;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void meteorfix$prioritizeDropdownClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        WDropdown<?> dropdown = findOpenDropdown((WContainer) (Object) this);
        if (dropdown != null && dropdown.mouseClicked(click, doubled)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void meteorfix$prioritizeDropdownRelease(Click click, CallbackInfoReturnable<Boolean> cir) {
        WDropdown<?> dropdown = findOpenDropdown((WContainer) (Object) this);
        if (dropdown != null && dropdown.mouseReleased(click)) {
            cir.setReturnValue(true);
        }
    }

    private static WDropdown<?> findOpenDropdown(WContainer container) {
        for (int i = container.cells.size() - 1; i >= 0; i--) {
            WDropdown<?> found = findOpenDropdown(container.cells.get(i).widget());
            if (found != null) return found;
        }
        return null;
    }

    private static WDropdown<?> findOpenDropdown(WWidget widget) {
        if (widget instanceof WDropdown<?> dropdown && isExpanded(dropdown)) return dropdown;
        if (widget instanceof WContainer container) return findOpenDropdown(container);
        return null;
    }

    private static boolean isExpanded(WDropdown<?> dropdown) {
        if (EXPANDED_FIELD == null) return false;
        try {
            return EXPANDED_FIELD.getBoolean(dropdown);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
