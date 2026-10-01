package gamerguy11.sixtoolsaddon.mixin.respawn;

import gamerguy11.sixtoolsaddon.modules.RespawnPointBlocker;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ClientPlayerInteractionManager.class})
public class RespawnPointBlockerMixin {
   @Inject(
      method = {"interactBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable cir) {
      RespawnPointBlocker module = (RespawnPointBlocker)Modules.get().get(RespawnPointBlocker.class);
      if (module.isActive()) {
         BlockPos blockPos = hitResult.getBlockPos();
         BlockState blockState = MeteorClient.mc.world.getBlockState(blockPos);
         Block block = blockState.getBlock();
         if (this.isRespawnPointBlock(block)) {
            boolean shouldBlock = false;
            if (this.isBed(block) && (Boolean)module.blockBeds.get()) {
               shouldBlock = true;
            } else if (block == Blocks.RESPAWN_ANCHOR && (Boolean)module.blockRespawnAnchors.get()) {
               shouldBlock = true;
            }

            if (shouldBlock) {
               cir.setReturnValue(ActionResult.FAIL);
               this.provideFeedback(module, block);
            }
         }

      }
   }

   private boolean isRespawnPointBlock(Block block) {
      return this.isBed(block) || block == Blocks.RESPAWN_ANCHOR;
   }

   private boolean isBed(Block block) {
      return block == Blocks.WHITE_BED || block == Blocks.ORANGE_BED || block == Blocks.MAGENTA_BED || block == Blocks.LIGHT_BLUE_BED || block == Blocks.YELLOW_BED || block == Blocks.LIME_BED || block == Blocks.PINK_BED || block == Blocks.GRAY_BED || block == Blocks.LIGHT_GRAY_BED || block == Blocks.CYAN_BED || block == Blocks.PURPLE_BED || block == Blocks.BLUE_BED || block == Blocks.BROWN_BED || block == Blocks.GREEN_BED || block == Blocks.RED_BED || block == Blocks.BLACK_BED;
   }

   private void provideFeedback(RespawnPointBlocker module, Block block) {
      String blockName = this.isBed(block) ? "Bed" : "Respawn Anchor";
      if ((Boolean)module.chatFeedback.get()) {
         module.info("Blocked %s interaction", new Object[]{blockName});
      }

      if ((Boolean)module.soundFeedback.get() && !((List)module.feedbackSound.get()).isEmpty() && MeteorClient.mc.player != null) {
         SoundEvent sound = (SoundEvent)((List)module.feedbackSound.get()).get(0);
         float volume = (float)(Integer)module.soundVolume.get() / 100.0F;
         MeteorClient.mc.player.playSound(sound, volume, 1.0F);
      }

   }
}
