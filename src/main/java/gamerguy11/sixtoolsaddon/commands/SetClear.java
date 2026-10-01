package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.modules.StashMover;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class SetClear extends Command {
   public SetClear() {
      super("setclear", "Clear all StashMover area selections", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.executes((context) -> {
         StashMover module = (StashMover)Modules.get().get(StashMover.class);
         if (module != null) {
            module.clearAreas();
            this.info("§cAll StashMover areas have been cleared", new Object[0]);
            String prefix = (String)Config.get().prefix.get();
            this.info("§7Use §f" + prefix + "setinput §7and §f" + prefix + "setoutput §7to select new areas", new Object[0]);
         } else {
            this.error("StashMover module not found!", new Object[0]);
         }

         return 1;
      });
   }
}
