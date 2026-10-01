package gamerguy11.sixtoolsaddon.mixin.shulkerview;

import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CreativeInventoryScreen.class})
public class MixinCreativeInventoryScreen {
   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")}
   )
   private void shulkerView$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable cir) {
      ShulkerView module = (ShulkerView)Modules.get().get(ShulkerView.class);
      if (module != null && module.isActive()) {
         module.getRenderHandler().mouseScroll(mouseX, mouseY, verticalAmount);
      }
   }
}
