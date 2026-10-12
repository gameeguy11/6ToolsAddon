package gamerguy11.sixtoolsaddon.mixin.spoof;

import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "me.cominixo.betterf3.modules.ChunksModule", remap = false)
public class BetterF3ChunksMixin {
   @Redirect(method = "update", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1297;method_24515()Lnet/minecraft/class_2338;", remap = false))
   private BlockPos sixtoolsaddon$spoofBlockPos(Entity entity) {
      return Spoofer.spoof(entity.getBlockPos());
   }
}
