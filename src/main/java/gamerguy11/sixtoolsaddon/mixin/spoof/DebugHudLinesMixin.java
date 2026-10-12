package gamerguy11.sixtoolsaddon.mixin.spoof;

import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.hud.DebugHud$1")
public class DebugHudLinesMixin {
   @ModifyVariable(method = "addLine", at = @At("HEAD"), argsOnly = true)
   private String sixtoolsaddon$spoofLine(String line) {
      return Spoofer.processDebugLine(line);
   }

   @ModifyVariable(method = "addPriorityLine", at = @At("HEAD"), argsOnly = true)
   private String sixtoolsaddon$spoofPriorityLine(String line) {
      return Spoofer.processDebugLine(line);
   }

   @ModifyVariable(method = "addLineToSection", at = @At("HEAD"), argsOnly = true)
   private String sixtoolsaddon$spoofSectionLine(String line) {
      return Spoofer.processDebugLine(line);
   }

   @ModifyVariable(method = "addLinesToSection", at = @At("HEAD"), argsOnly = true)
   private Collection<String> sixtoolsaddon$spoofSectionLines(Collection<String> lines) {
      List<String> result = new ArrayList<>(lines.size());
      for (String line : lines) {
         result.add(Spoofer.processDebugLine(line));
      }

      return result;
   }
}
