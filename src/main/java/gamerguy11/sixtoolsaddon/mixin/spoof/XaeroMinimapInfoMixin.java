package gamerguy11.sixtoolsaddon.mixin.spoof;

import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "xaero.hud.minimap.info.BuiltInInfoDisplays", remap = false)
public class XaeroMinimapInfoMixin {
   @ModifyVariable(method = "lambda$static$0", at = @At("HEAD"), argsOnly = true, require = 1)
   private static BlockPos sixtoolsaddon$coordinates(BlockPos pos) {
      return Spoofer.spoof(pos);
   }

   @ModifyVariable(method = "lambda$static$2", at = @At("HEAD"), argsOnly = true, require = 1)
   private static BlockPos sixtoolsaddon$overworldCoordinates(BlockPos pos) {
      return Spoofer.spoof(pos);
   }

   @ModifyVariable(method = "lambda$static$3", at = @At("HEAD"), argsOnly = true, require = 1)
   private static BlockPos sixtoolsaddon$chunkCoordinates(BlockPos pos) {
      return Spoofer.spoof(pos);
   }
}
