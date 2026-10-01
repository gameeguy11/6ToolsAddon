package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.commands.DubCounterCommand;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.client.world.ClientWorld;

public class DubCounter extends Module {
   public SettingGroup sgGeneral;
   private final Setting<CountMode> countMode;
   private final Setting<Integer> radius;
   private final Setting<Boolean> chatFeedback;
   public int lastDubs;
   public int lastNormalChests;
   public CountMode lastMode;

   public DubCounter() {
      super(SixToolsAddon.CATEGORY, "dub-counter", "Counts how many double chests are nearby.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.countMode = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("count-mode")).description("The way the chests are counted.")).defaultValue(DubCounter.CountMode.Loaded)).build());
      this.radius = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("radius")).description("Chunk radius to scan around you when count-mode is Rendered.")).defaultValue(8)).min(1).sliderMax(32).visible(() -> this.countMode.get() == DubCounter.CountMode.Rendered)).build());
      this.chatFeedback = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("chat-feedback")).description("Prints the result in chat as well as updating the HUD element.")).defaultValue(true)).build());
      this.lastDubs = -1;
      this.lastNormalChests = -1;
      this.lastMode = null;
   }

   public void onActivate() {
      this.count();
      this.toggle();
   }

   private void count() {
      int length = this.countMode.get() == DubCounter.CountMode.Rendered ? this.countRendered() : this.countLoaded();
      int dubs = length / 2;
      this.lastDubs = dubs;
      this.lastNormalChests = length;
      this.lastMode = (CountMode)this.countMode.get();
      DubCounterCommand.lastDubs = dubs;
      DubCounterCommand.lastNormalChests = length;
      DubCounterCommand.lastMode = DubCounterCommand.CountMode.valueOf(((CountMode)this.countMode.get()).name());
      if ((Boolean)this.chatFeedback.get()) {
         this.info("There are roughly (highlight)%s(default) (%s normal chests) %s double chests.", new Object[]{dubs, length, this.countMode.get() == DubCounter.CountMode.Rendered ? "rendered" : "loaded"});
      }

   }

   private int countLoaded() {
      return this.mc.world == null ? 0 : this.scanChunksAround(Integer.MAX_VALUE);
   }

   private int countRendered() {
      return this.mc.world == null ? 0 : this.scanChunksAround((Integer)this.radius.get());
   }

   private int scanChunksAround(int chunkRadius) {
      ClientWorld world = this.mc.world;
      if (world != null && this.mc.player != null) {
         int playerChunkX = this.mc.player.getChunkPos().x;
         int playerChunkZ = this.mc.player.getChunkPos().z;
         int count = 0;
         int scanRadius = chunkRadius == Integer.MAX_VALUE ? 32 : chunkRadius;

         for(int dx = -scanRadius; dx <= scanRadius; ++dx) {
            for(int dz = -scanRadius; dz <= scanRadius; ++dz) {
               WorldChunk chunk = world.getChunk(playerChunkX + dx, playerChunkZ + dz);
               if (chunk != null) {
                  for(BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                     if (blockEntity instanceof ChestBlockEntity) {
                        ++count;
                     }
                  }
               }
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   public static enum CountMode {
      Rendered,
      Loaded;

      private static CountMode[] $values() {
         return new CountMode[]{Rendered, Loaded};
      }
   }
}
