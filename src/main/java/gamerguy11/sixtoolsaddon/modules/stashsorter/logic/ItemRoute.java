package gamerguy11.sixtoolsaddon.modules.stashsorter.logic;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.DataComponentTypes;

public final class ItemRoute {
   private ItemRoute() {
   }

   public static String of(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         String var10000 = String.valueOf(Registries.ITEM.getId(stack.getItem()));
         return var10000 + "|" + String.valueOf(stack.getComponents());
      } else {
         return "";
      }
   }

   public static boolean isShulker(ItemStack stack) {
      boolean var10000;
      if (stack != null) {
         Item var2 = stack.getItem();
         if (var2 instanceof BlockItem) {
            BlockItem blockItem = (BlockItem)var2;
            if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
               var10000 = true;
               return var10000;
            }
         }
      }

      var10000 = false;
      return var10000;
   }

   public static boolean isShulker(Item item) {
      boolean var10000;
      if (item instanceof BlockItem blockItem) {
         if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
            var10000 = true;
            return var10000;
         }
      }

      var10000 = false;
      return var10000;
   }

   public static String shulkerContents(ItemStack stack) {
      if (!isShulker(stack)) {
         return "";
      } else {
         ContainerComponent contents = (ContainerComponent)stack.get(DataComponentTypes.CONTAINER);
         if (contents == null) {
            return "shulker#empty";
         } else {
            Map<String, Integer> itemCounts = new TreeMap();

            for(ItemStack item : contents.stream().toList()) {
               if (!item.isEmpty()) {
                  itemCounts.merge(of(item), item.getCount(), Integer::sum);
               }
            }

            return itemCounts.isEmpty() ? "shulker#empty" : "shulker#" + String.valueOf(itemCounts);
         }
      }
   }

   public static String topLevel(ItemStack stack) {
      return isShulker(stack) ? shulkerContents(stack) : of(stack);
   }

   public static Item item(String route) {
      if (route == null) {
         return null;
      } else {
         int separator = route.indexOf(124);
         if (separator <= 0) {
            return null;
         } else {
            Identifier id = Identifier.tryParse(route.substring(0, separator));
            return id != null && Registries.ITEM.containsId(id) ? (Item)Registries.ITEM.get(id) : null;
         }
      }
   }
}
