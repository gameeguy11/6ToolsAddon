package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.modules.AutoStashSorter;
import gamerguy11.sixtoolsaddon.modules.stashsorter.data.SortArea;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class StashSorterCommand extends Command {
   public StashSorterCommand() {
      super("sorter", "Select and manage the tracked-container sorting area.", new String[]{"stashsorter"});
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.then(literal("area").executes((context) -> {
         AutoStashSorter module = this.module();
         if (mc.player != null && module != null) {
            module.startAreaSelection();
            return 1;
         } else {
            this.error("Join a world before selecting an area.", new Object[0]);
            return 1;
         }
      }));
      builder.then(literal("clear").executes((context) -> {
         AutoStashSorter module = this.module();
         if (mc.world != null && module != null) {
            module.clearSortArea();
            this.info("Sorting area cleared for this server and dimension.", new Object[0]);
            return 1;
         } else {
            this.error("Join a world first.", new Object[0]);
            return 1;
         }
      }));
      builder.then(literal("cancel").executes((context) -> {
         AutoStashSorter module = this.module();
         if (module != null && module.isSelectingArea()) {
            module.cancelAreaSelection();
            this.info("Area selection cancelled.", new Object[0]);
         } else {
            this.error("No area selection is active.", new Object[0]);
         }

         return 1;
      }));
      builder.then(literal("status").executes((context) -> {
         AutoStashSorter module = this.module();
         SortArea area = module == null ? null : module.getCurrentArea();
         if (area == null) {
            this.info("No sorting area set. Use .sorter area.", new Object[0]);
         } else {
            this.info("Sorting area: %s (%s blocks).", new Object[]{area.corners(), area.footprint()});
            this.info("ChestTracker entries in this area: %d.", new Object[]{module.getTrackedContainerPositionsInArea().size()});
         }

         return 1;
      }));
   }

   private AutoStashSorter module() {
      return (AutoStashSorter)Modules.get().get(AutoStashSorter.class);
   }
}
