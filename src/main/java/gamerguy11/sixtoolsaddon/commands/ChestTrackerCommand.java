package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.modules.chesttracker.ChestTrackerModule;
import gamerguy11.sixtoolsaddon.modules.chesttracker.TrackedContainer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public class ChestTrackerCommand extends Command {
   public ChestTrackerCommand() {
      super("chesttracker", "Search for items in tracked containers.", new String[]{"ct", "track"});
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.then(((LiteralArgumentBuilder)literal("search").then(literal("hand").executes((context) -> {
         ChestTrackerModule module = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
         if (module != null && module.isActive()) {
            if (mc.player == null) {
               this.error("§cNot in-game!", new Object[0]);
               return 1;
            } else {
               ItemStack held = mc.player.getMainHandStack();
               if (held.isEmpty()) {
                  this.error("§cHand empty!", new Object[0]);
                  return 1;
               } else {
                  this.searchAndDisplay(module, held.getItem());
                  return 1;
               }
            }
         } else {
            this.error("§cModule off!", new Object[0]);
            return 1;
         }
      }))).then(argument("item", StringArgumentType.greedyString()).executes((context) -> {
         ChestTrackerModule module = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
         if (module != null && module.isActive()) {
            String itemName = StringArgumentType.getString(context, "item");
            Item item = this.findItem(itemName);
            if (item == null) {
               this.error("§cUnknown: " + itemName, new Object[0]);
               return 1;
            } else {
               this.searchAndDisplay(module, item);
               return 1;
            }
         } else {
            this.error("§cModule off!", new Object[0]);
            return 1;
         }
      })));
      builder.then(((LiteralArgumentBuilder)literal("clear").then(literal("all").executes((context) -> {
         ChestTrackerModule module = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
         if (module != null && module.isActive()) {
            module.getData().clearAll();
            this.info("§aCleared all", new Object[0]);
            return 1;
         } else {
            this.error("§cModule off!", new Object[0]);
            return 1;
         }
      }))).then(literal("dimension").executes((context) -> {
         ChestTrackerModule module = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
         if (module != null && module.isActive()) {
            module.getData().clearCurrentDimension();
            this.info("§aCleared dimension", new Object[0]);
            return 1;
         } else {
            this.error("§cModule off!", new Object[0]);
            return 1;
         }
      })));
      builder.then(literal("export").executes((context) -> {
         ChestTrackerModule module = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
         if (module != null && module.isActive()) {
            try {
               SimpleDateFormat var10000 = new SimpleDateFormat("yyyyMMdd_HHmmss");
               Date var10001 = new Date();
               String fn = "export_" + var10000.format(var10001) + ".json";
               module.getData().exportData(fn);
               this.info("§aExported: §f" + fn, new Object[0]);
            } catch (Exception e) {
               this.error("§cFailed: " + e.getMessage(), new Object[0]);
            }

            return 1;
         } else {
            this.error("§cModule off!", new Object[0]);
            return 1;
         }
      }));
      builder.then(literal("nearby").then(argument("radius", IntegerArgumentType.integer(1, 128)).executes((context) -> {
         ChestTrackerModule module = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
         if (module != null && module.isActive()) {
            if (mc.player == null) {
               this.error("§cNot in-game!", new Object[0]);
               return 1;
            } else {
               int r = IntegerArgumentType.getInteger(context, "radius");
               List<TrackedContainer> all = module.getData().getAllContainers();
               int found = 0;
               this.info("§e§lNearby (<" + r + "m):", new Object[0]);

               for(TrackedContainer c : all) {
                  BlockPos p = c.getPosition();
                  double d = Math.sqrt(mc.player.squaredDistanceTo((double)p.getX() + (double)0.5F, (double)p.getY() + (double)0.5F, (double)p.getZ() + (double)0.5F));
                  if (d <= (double)r) {
                     String col = d <= module.getRenderDistance() ? "§a" : "§7";
                     this.info(String.format("%s[%d,%d,%d] §e%.0fm §b%d§7items%s", col, p.getX(), p.getY(), p.getZ(), d, c.getItems().size(), c.isEmpty() ? " §c✗" : ""), new Object[0]);
                     ++found;
                  }
               }

               this.info(found > 0 ? "§a" + found + " found" : "§cNone found", new Object[0]);
               return 1;
            }
         } else {
            this.error("§cModule off!", new Object[0]);
            return 1;
         }
      })));
   }

   private void searchAndDisplay(ChestTrackerModule module, Item item) {
      List<TrackedContainer> results = module.getData().searchItem(item);
      String name = item.getName().getString();
      if (results.isEmpty()) {
         this.info("§cNone found: §f" + name, new Object[0]);
      } else {
         String itemId = Registries.ITEM.getId(item).toString();
         int total = 0;
         int near = 0;
         double rd = module.getRenderDistance();
         double rdSq = rd * rd;

         for(TrackedContainer c : results) {
            total += c.getItemCount(itemId);
            if (mc.player != null) {
               BlockPos p = c.getPosition();
               if (mc.player.squaredDistanceTo((double)p.getX() + (double)0.5F, (double)p.getY() + (double)0.5F, (double)p.getZ() + (double)0.5F) <= rdSq) {
                  ++near;
               }
            }
         }

         this.info(String.format("§a%,d §f%s §7in §e%d §7box%s", total, name, results.size(), results.size() > 1 ? "es" : ""), new Object[0]);
         if (near < results.size()) {
            this.info(String.format("§7Lit: §e%d §7Far: §c%d", near, results.size() - near), new Object[0]);
         }

         module.searchItem(item);
      }
   }

   private Item findItem(String query) {
      query = query.toLowerCase().replace(" ", "_");
      Identifier id = Identifier.tryParse("minecraft:" + query);
      if (id != null && Registries.ITEM.containsId(id)) {
         return (Item)Registries.ITEM.get(id);
      } else {
         for(Identifier itemId : Registries.ITEM.getIds()) {
            String path = itemId.getPath();
            if (path.contains(query)) {
               return (Item)Registries.ITEM.get(itemId);
            }
         }

         return null;
      }
   }
}
