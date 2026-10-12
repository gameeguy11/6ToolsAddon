package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.debug.SyncDebug;
import java.nio.file.Path;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

public class SyncDebugCommand extends Command {
   public SyncDebugCommand() {
      super("syncdebug", "Writes sixtools-debug.txt describing how Mio and RusherHack store friends.", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.executes((ctx) -> {
         try {
            Path file = SyncDebug.run(mc.runDirectory.toPath(), SixToolsAddon.class.getClassLoader());
            ChatUtils.info("Debug report written to (highlight)%s(default). Send me that file.", new Object[]{file});
         } catch (Exception e) {
            SixToolsAddon.LOG.warn("syncdebug failed", e);
            ChatUtils.error("Debug failed: %s", new Object[]{e.toString()});
         }

         return 1;
      });
   }
}
