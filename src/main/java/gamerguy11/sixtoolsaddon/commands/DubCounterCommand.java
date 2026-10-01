package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.client.world.ClientWorld;

public class DubCounterCommand extends Command {
   public static int lastDubs = -1;
   public static int lastNormalChests = -1;
   public static CountMode lastMode = null;
   private static final List<String> RADIUS_SUGGESTIONS = List.of("4", "8", "16", "32");

   public DubCounterCommand() {
      super("dub", "Counts how many double chests are nearby.", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.executes((ctx) -> {
         this.count(DubCounterCommand.CountMode.Loaded, 8);
         return 1;
      });
      builder.then(((LiteralArgumentBuilder)literal("rendered").executes((ctx) -> {
         this.count(DubCounterCommand.CountMode.Rendered, 8);
         return 1;
      })).then(argument("radius", IntegerArgumentType.integer(1, 32)).suggests((context, suggestionsBuilder) -> CommandSource.suggestMatching(RADIUS_SUGGESTIONS.stream(), suggestionsBuilder)).executes((ctx) -> {
         int radius = IntegerArgumentType.getInteger(ctx, "radius");
         this.count(DubCounterCommand.CountMode.Rendered, radius);
         return 1;
      })));
   }

   private void count(CountMode mode, int radius) {
      int length = mode == DubCounterCommand.CountMode.Rendered ? this.scanChunksAround(radius) : this.scanChunksAround(Integer.MAX_VALUE);
      int dubs = length / 2;
      lastDubs = dubs;
      lastNormalChests = length;
      lastMode = mode;
      ChatUtils.info("There are roughly (highlight)%s(default) (%s normal chests) %s double chests.", new Object[]{dubs, length, mode == DubCounterCommand.CountMode.Rendered ? "rendered" : "loaded"});
   }

   private int scanChunksAround(int chunkRadius) {
      ClientWorld world = mc.world;
      if (world != null && mc.player != null) {
         int playerChunkX = mc.player.getChunkPos().x;
         int playerChunkZ = mc.player.getChunkPos().z;
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
