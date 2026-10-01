package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.modules.StashMover;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class SetInput extends Command {
   public SetInput() {
      super("setinput", "Start input area selection for StashMover module", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.executes((context) -> {
         if (mc.player == null) {
            this.error("Player is null!", new Object[0]);
            return 0;
         } else {
            StashMover module = (StashMover)Modules.get().get(StashMover.class);
            if (module != null) {
               if (module.isSelecting()) {
                  module.cancelSelection();
                  this.info("Previous selection cancelled", new Object[0]);
               }

               module.startInputSelection();
               this.info("§aInput area selection started!", new Object[0]);
               this.info("§eLeft-click the first corner block", new Object[0]);
               this.info("§7Press §cESC §7to cancel selection", new Object[0]);
            } else {
               this.error("StashMover module not found!", new Object[0]);
            }

            return 1;
         }
      });
   }
}
