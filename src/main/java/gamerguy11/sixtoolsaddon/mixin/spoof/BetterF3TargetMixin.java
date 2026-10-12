package gamerguy11.sixtoolsaddon.mixin.spoof;

import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "me.cominixo.betterf3.modules.TargetModule", remap = false)
public class BetterF3TargetMixin {
   @Redirect(method = "update", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2338;method_10263()I", remap = false))
   private int sixtoolsaddon$spoofX(BlockPos pos) {
      return Spoofer.spoof(pos).getX();
   }

   @Redirect(method = "update", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2338;method_10264()I", remap = false))
   private int sixtoolsaddon$spoofY(BlockPos pos) {
      return Spoofer.spoof(pos).getY();
   }

   @Redirect(method = "update", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2338;method_10260()I", remap = false))
   private int sixtoolsaddon$spoofZ(BlockPos pos) {
      return Spoofer.spoof(pos).getZ();
   }
}
