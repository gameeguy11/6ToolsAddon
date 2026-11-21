package gamerguy11.sixtoolsaddon.kit;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class Kit {
   private final String name;
   private final Map<Integer, Entry> slots = new TreeMap<>();

   public Kit(String name) {
      this.name = name;
   }

   public String getName() {
      return name;
   }

   public Map<Integer, Entry> getItemsBySlot() {
      return slots;
   }

   public static int toHandlerSlot(int inventoryIndex) {
      return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
   }

   public record Entry(String itemId, String potionSignature) {
      public boolean matches(ItemStack stack) {
         if (stack.isEmpty()) return false;
         if (!Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) return false;
         return Objects.equals(KitManager.potionSignature(stack), potionSignature);
      }

      public Item item() {
         return Registries.ITEM.get(Identifier.of(itemId));
      }

      public String key() {
         return itemId + "|" + (potionSignature == null ? "" : potionSignature);
      }
   }
}
