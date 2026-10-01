package gamerguy11.sixtoolsaddon.modules;

import java.util.List;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.SoundEventListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;

public class RespawnPointBlocker extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgFeedback;
   public final Setting<Boolean> blockBeds;
   public final Setting<Boolean> blockRespawnAnchors;
   public final Setting<Boolean> chatFeedback;
   public final Setting<Boolean> soundFeedback;
   public final Setting<List<SoundEvent>> feedbackSound;
   public final Setting<Integer> soundVolume;

   public RespawnPointBlocker() {
      super(SixToolsAddon.CATEGORY, "respawn-point-blocker", "Prevents setting respawn points by blocking bed and respawn anchor interactions.");
      this.sgGeneral = this.settings.createGroup("General");
      this.sgFeedback = this.settings.createGroup("Feedback");
      this.blockBeds = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("block-beds")).description("Prevents setting respawn points with beds.")).defaultValue(true)).build());
      this.blockRespawnAnchors = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("block-respawn-anchors")).description("Prevents setting respawn points with respawn anchors.")).defaultValue(true)).build());
      this.chatFeedback = this.sgFeedback.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("chat-feedback")).description("Sends a message in chat when interaction is blocked.")).defaultValue(true)).build());
      this.soundFeedback = this.sgFeedback.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("sound-feedback")).description("Plays a sound when interaction is blocked.")).defaultValue(true)).build());
      this.feedbackSound = this.sgFeedback.add(((SoundEventListSetting.Builder)((SoundEventListSetting.Builder)(new SoundEventListSetting.Builder()).name("feedback-sound")).description("Sound to play when interaction is blocked. Only the first sound in the list will be played.")).defaultValue(new SoundEvent[]{SoundEvents.ENTITY_VILLAGER_NO}).build());
      this.soundVolume = this.sgFeedback.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("sound-volume")).description("Volume of the feedback sound.")).defaultValue(100)).range(0, 200).sliderRange(0, 200).build());
   }

   @EventHandler(
      priority = 100
   )
   private void onInteractBlock(InteractBlockEvent event) {
      BlockHitResult hitResult = event.result;
      BlockPos blockPos = hitResult.getBlockPos();
      BlockState blockState = this.mc.world.getBlockState(blockPos);
      Block block = blockState.getBlock();
      if (this.isRespawnPointBlock(block)) {
         boolean shouldBlock = false;
         String blockName = "";
         if (block != Blocks.WHITE_BED && block != Blocks.ORANGE_BED && block != Blocks.MAGENTA_BED && block != Blocks.LIGHT_BLUE_BED && block != Blocks.YELLOW_BED && block != Blocks.LIME_BED && block != Blocks.PINK_BED && block != Blocks.GRAY_BED && block != Blocks.LIGHT_GRAY_BED && block != Blocks.CYAN_BED && block != Blocks.PURPLE_BED && block != Blocks.BLUE_BED && block != Blocks.BROWN_BED && block != Blocks.GREEN_BED && block != Blocks.RED_BED && block != Blocks.BLACK_BED) {
            if (block == Blocks.RESPAWN_ANCHOR && (Boolean)this.blockRespawnAnchors.get()) {
               shouldBlock = true;
               blockName = "Respawn Anchor";
            }
         } else if ((Boolean)this.blockBeds.get()) {
            shouldBlock = true;
            blockName = "Bed";
         }

         if (shouldBlock) {
            event.cancel();
         }
      }

   }

   private boolean isRespawnPointBlock(Block block) {
      return block == Blocks.WHITE_BED || block == Blocks.ORANGE_BED || block == Blocks.MAGENTA_BED || block == Blocks.LIGHT_BLUE_BED || block == Blocks.YELLOW_BED || block == Blocks.LIME_BED || block == Blocks.PINK_BED || block == Blocks.GRAY_BED || block == Blocks.LIGHT_GRAY_BED || block == Blocks.CYAN_BED || block == Blocks.PURPLE_BED || block == Blocks.BLUE_BED || block == Blocks.BROWN_BED || block == Blocks.GREEN_BED || block == Blocks.RED_BED || block == Blocks.BLACK_BED || block == Blocks.RESPAWN_ANCHOR;
   }
}
