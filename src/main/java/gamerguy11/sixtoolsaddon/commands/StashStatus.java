package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.modules.StashMover;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class StashStatus extends Command {
   public StashStatus() {
      super("stashstatus", "Check StashMover areas and configuration", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.executes((context) -> {
         if (mc.player == null) {
            return 0;
         } else {
            StashMover module = (StashMover)Modules.get().get(StashMover.class);
            if (module != null) {
               String prefix = (String)Config.get().prefix.get();
               this.info("§6=== StashMover Status ===", new Object[0]);
               if (module.isActive()) {
                  this.info("§aModule: §fACTIVE", new Object[0]);
                  this.info("§7State: §f" + String.valueOf(module.getCurrentState()), new Object[0]);
                  this.info("§7Items moved: §f" + module.getItemsTransferred(), new Object[0]);
                  this.info("§7Containers processed: §f" + module.getContainersProcessed(), new Object[0]);
               } else {
                  this.info("§cModule: §fINACTIVE", new Object[0]);
                  this.info("§7Use §f" + prefix + "stash-mover §7to activate", new Object[0]);
               }

               this.info("", new Object[0]);
               boolean hasInput = module.hasInputArea();
               boolean hasOutput = module.hasOutputArea();
               if (hasInput) {
                  this.info("§aInput Area: §fSET", new Object[0]);
                  this.info("§7  Containers: §f" + module.getInputContainerCount(), new Object[0]);
               } else {
                  this.info("§cInput Area: §fNOT SET", new Object[0]);
                  this.info("§7  Use §f" + prefix + "setinput §7to select", new Object[0]);
               }

               if (hasOutput) {
                  this.info("§bOutput Area: §fSET", new Object[0]);
                  this.info("§7  Containers: §f" + module.getOutputContainerCount(), new Object[0]);
               } else {
                  this.info("§cOutput Area: §fNOT SET", new Object[0]);
                  this.info("§7  Use §f" + prefix + "setoutput §7to select", new Object[0]);
               }

               if (hasInput && hasOutput) {
                  this.info("", new Object[0]);
                  this.info("§aReady to use! §7Enable module to start.", new Object[0]);
               }
            } else {
               this.error("StashMover module not found!", new Object[0]);
            }

            return 1;
         }
      });
   }
}
