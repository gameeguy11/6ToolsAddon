package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.modules.InventorySorterModule;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class InventoryCommand extends Command {
   public InventoryCommand() {
      super("invsorter", "Save and load exact inventory layouts.", new String[0]);
   }

   private InventorySorterModule module() {
      return (InventorySorterModule)Modules.get().get(InventorySorterModule.class);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.then(literal("save").then(argument("name", StringArgumentType.word()).suggests((context, suggestionsBuilder) -> CommandSource.suggestMatching(this.module().inventoryNames().stream(), suggestionsBuilder)).executes((ctx) -> {
         String name = StringArgumentType.getString(ctx, "name");
         InventorySorterModule module = this.module();
         boolean overwriting = module.hasInventory(name);
         if (!module.saveInventory(name)) {
            module.notifyError("Open your inventory (or have no other screen open) before saving.");
            return 1;
         } else {
            module.notifySaveResult(overwriting, name);
            return 1;
         }
      })));
      builder.then(literal("load").then(argument("name", StringArgumentType.word()).suggests((context, suggestionsBuilder) -> CommandSource.suggestMatching(this.module().inventoryNames().stream(), suggestionsBuilder)).executes((ctx) -> {
         String name = StringArgumentType.getString(ctx, "name");
         InventorySorterModule module = this.module();
         if (!module.hasInventory(name)) {
            module.notifyError("No inventory named (highlight)%s(default) is saved. Use (highlight).invsorter list(default) to see what you have.", name);
            return 1;
         } else {
            if (!module.isActive()) {
               module.toggle();
            }

            module.loadInventory(name);
            module.notifyInfo("Sorting to inventory (highlight)%s(default)...", name);
            return 1;
         }
      })));
      builder.then(literal("delete").then(argument("name", StringArgumentType.word()).suggests((context, suggestionsBuilder) -> CommandSource.suggestMatching(this.module().inventoryNames().stream(), suggestionsBuilder)).executes((ctx) -> {
         String name = StringArgumentType.getString(ctx, "name");
         InventorySorterModule module = this.module();
         if (!module.hasInventory(name)) {
            module.notifyError("No inventory named (highlight)%s(default) to delete.", name);
            return 1;
         } else {
            module.deleteInventory(name);
            module.notifyInfo("Deleted inventory (highlight)%s(default).", name);
            return 1;
         }
      })));
      builder.then(literal("clear").executes((ctx) -> {
         InventorySorterModule module = this.module();
         module.clearInventories();
         module.notifyInfo("Cleared all saved inventories.");
         return 1;
      }));
      builder.then(literal("list").executes((ctx) -> {
         InventorySorterModule module = this.module();
         List<String> names = module.inventoryNames();
         if (names.isEmpty()) {
            module.notifyInfo("No inventories saved yet. Try (highlight).invsorter save <name>(default).");
         } else {
            module.notifyInfo("Saved inventories: (highlight)%s(default).", String.join(", ", names));
         }

         return 1;
      }));
   }
}
