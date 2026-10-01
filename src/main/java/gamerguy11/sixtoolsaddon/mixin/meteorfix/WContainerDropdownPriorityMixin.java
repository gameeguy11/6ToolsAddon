package gamerguy11.sixtoolsaddon.mixin.meteorfix;

import java.lang.reflect.Field;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import net.minecraft.client.gui.Click;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {WContainer.class},
   remap = false
)
public abstract class WContainerDropdownPriorityMixin {
   private static final Field EXPANDED_FIELD;

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void meteorfix$prioritizeDropdownClick(Click click, boolean doubled, CallbackInfoReturnable cir) {
      WDropdown<?> dropdown = findOpenDropdown((WContainer)(Object)this);
      if (dropdown != null && dropdown.mouseClicked(click, doubled)) {
         cir.setReturnValue(true);
      }

   }

   @Inject(
      method = {"mouseReleased"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void meteorfix$prioritizeDropdownRelease(Click click, CallbackInfoReturnable cir) {
      WDropdown<?> dropdown = findOpenDropdown((WContainer)(Object)this);
      if (dropdown != null && dropdown.mouseReleased(click)) {
         cir.setReturnValue(true);
      }

   }

   private static WDropdown<?> findOpenDropdown(WContainer container) {
      for(int i = container.cells.size() - 1; i >= 0; --i) {
         WDropdown<?> found = findOpenDropdown(((Cell)container.cells.get(i)).widget());
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private static WDropdown<?> findOpenDropdown(WWidget widget) {
      if (widget instanceof WDropdown<?> dropdown) {
         if (isExpanded(dropdown)) {
            return dropdown;
         }
      }

      if (widget instanceof WContainer container) {
         return findOpenDropdown(container);
      } else {
         return null;
      }
   }

   private static boolean isExpanded(WDropdown<?> dropdown) {
      if (EXPANDED_FIELD == null) {
         return false;
      } else {
         try {
            return EXPANDED_FIELD.getBoolean(dropdown);
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   static {
      Field field = null;

      try {
         field = WDropdown.class.getDeclaredField("expanded");
         field.setAccessible(true);
      } catch (Throwable var2) {
      }

      EXPANDED_FIELD = field;
   }
}
