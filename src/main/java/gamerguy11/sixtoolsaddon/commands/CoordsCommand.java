package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;

public class CoordsCommand extends Command {
   public CoordsCommand() {
      super("coords", "Copies your current coordinates to the clipboard.", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.executes((ctx) -> {
         if (mc.player == null) {
            ChatUtils.error("You must be in-game to use this command.", new Object[0]);
            return 1;
         } else {
            BlockPos pos = mc.player.getBlockPos();
            String dimension = mc.world.getRegistryKey().getValue().toString();
            int var10000 = pos.getX();
            String coords = var10000 + " " + pos.getY() + " " + pos.getZ();
            mc.keyboard.setClipboard(coords);
            ChatUtils.info("Copied (highlight)%s(default) to your clipboard. (%s)", new Object[]{coords, dimension});
            return 1;
         }
      });
      builder.then(literal("raw").executes((ctx) -> {
         if (mc.player == null) {
            ChatUtils.error("You must be in-game to use this command.", new Object[0]);
            return 1;
         } else {
            BlockPos pos = mc.player.getBlockPos();
            int var10000 = pos.getX();
            String coords = var10000 + "," + pos.getY() + "," + pos.getZ();
            mc.keyboard.setClipboard(coords);
            ChatUtils.info("Copied (highlight)%s(default) to your clipboard.", new Object[]{coords});
            return 1;
         }
      }));
   }
}
