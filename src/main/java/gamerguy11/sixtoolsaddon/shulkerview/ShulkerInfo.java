package gamerguy11.sixtoolsaddon.shulkerview;

import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemConvertible;
import net.minecraft.block.Block;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.DataComponentTypes;

public record ShulkerInfo(ItemStack shulker, boolean compact, int color, int slot, List<ItemStack> stacks, int count) {
   public static ShulkerInfo create(ShulkerView config, ItemStack stack, int slot) {
      ShulkerBoxBlock block = getBlock(stack);
      if (block == null) {
         return null;
      } else {
         List<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
         ContainerComponent component = (ContainerComponent)stack.getComponents().get(DataComponentTypes.CONTAINER);
         boolean compact = config.isCompact();
         if (component != null) {
            Item unstackable = null;
            List<ItemStack> list = component.stream().toList();

            for(int i = 0; i < list.size(); ++i) {
               ItemStack item = (ItemStack)list.get(i);
               items.set(i, item);
               if (item.getMaxCount() == 1) {
                  if (unstackable != null && !item.getItem().equals(unstackable)) {
                     compact = false;
                  }

                  unstackable = item.getItem();
               }
            }
         }

         if (compact) {
            shrinkToCompact(items);
         }

         return new ShulkerInfo(stack, compact, ColorUtils.getColor(block), slot, items, 1);
      }
   }

   public int totalItems() {
      int total = 0;

      for(ItemStack item : this.stacks) {
         if (!item.isEmpty()) {
            total += item.getCount();
         }
      }

      return total;
   }

   public ShulkerInfo withCount(int count) {
      return new ShulkerInfo(this.shulker, this.compact, this.color, this.slot, this.stacks, count);
   }

   public boolean sameAs(ShulkerInfo other) {
      if (!this.shulker.isOf(other.shulker.getItem())) {
         return false;
      } else if (this.compact != other.compact) {
         return false;
      } else if (this.compact) {
         return totals(this.stacks).equals(totals(other.stacks));
      } else if (this.stacks.size() != other.stacks.size()) {
         return false;
      } else {
         for(int i = 0; i < this.stacks.size(); ++i) {
            if (!ItemStack.areEqual((ItemStack)this.stacks.get(i), (ItemStack)other.stacks.get(i))) {
               return false;
            }
         }

         return true;
      }
   }

   private static Map<Item, Integer> totals(List<ItemStack> items) {
      Map<Item, Integer> map = new HashMap();

      for(ItemStack item : items) {
         if (!item.isEmpty()) {
            map.merge(item.getItem(), item.getCount(), Integer::sum);
         }
      }

      return map;
   }

   private static void shrinkToCompact(List<ItemStack> items) {
      Map<Item, Integer> map = new HashMap();

      for(ItemStack item : items) {
         if (!item.isEmpty()) {
            map.merge(item.getItem(), item.getCount(), Integer::sum);
         }
      }

      for(int i = 0; i < items.size(); ++i) {
         items.set(i, ItemStack.EMPTY);
      }

      int k = 0;

      for(Map.Entry<Item, Integer> entry : map.entrySet()) {
         items.set(k++, new ItemStack((ItemConvertible)entry.getKey(), (Integer)entry.getValue()));
      }

   }

   private static ShulkerBoxBlock getBlock(ItemStack stack) {
      Item var3 = stack.getItem();
      if (var3 instanceof BlockItem b) {
         Block var4 = b.getBlock();
         if (var4 instanceof ShulkerBoxBlock shulker) {
            return shulker;
         }
      }

      return null;
   }

   public static boolean isShulkerBox(ItemStack stack) {
      return getBlock(stack) != null;
   }
}
