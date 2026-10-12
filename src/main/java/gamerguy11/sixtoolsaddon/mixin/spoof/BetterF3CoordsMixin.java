package gamerguy11.sixtoolsaddon.mixin.spoof;

import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "me.cominixo.betterf3.modules.CoordsModule", remap = false)
public class BetterF3CoordsMixin {
   @Redirect(method = "update", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1297;method_23317()D", ordinal = 0, remap = false))
   private double sixtoolsaddon$spoofX(Entity entity) {
      return Spoofer.spoofX(entity.getX());
   }

   @Redirect(method = "update", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1297;method_23318()D", ordinal = 0, remap = false))
   private double sixtoolsaddon$spoofY(Entity entity) {
      return Spoofer.spoofY(entity.getY());
   }

   @Redirect(method = "update", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1297;method_23321()D", ordinal = 0, remap = false))
   private double sixtoolsaddon$spoofZ(Entity entity) {
      return Spoofer.spoofZ(entity.getZ());
   }

   @Redirect(method = "update", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1297;method_24515()Lnet/minecraft/class_2338;", ordinal = 0, remap = false))
   private BlockPos sixtoolsaddon$spoofBlockPos(Entity entity) {
      return Spoofer.spoof(entity.getBlockPos());
   }
}
