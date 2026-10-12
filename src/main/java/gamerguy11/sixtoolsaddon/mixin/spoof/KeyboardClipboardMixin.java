package gamerguy11.sixtoolsaddon.mixin.spoof;

import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Keyboard.class)
public class KeyboardClipboardMixin {
   @ModifyVariable(method = "setClipboard", at = @At("HEAD"), argsOnly = true)
   private String sixtoolsaddon$spoofClipboard(String text) {
      return Spoofer.processClipboard(text);
   }
}
