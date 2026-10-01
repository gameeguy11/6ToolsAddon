package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.DropItemsEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class AntiDrop extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Boolean> checkShulkers;
   private final Setting<Boolean> allItems;
   private final Setting<List<Item>> items;

   public AntiDrop() {
      super(SixToolsAddon.CATEGORY, "anti-drop", "Stops you from dropping certain items.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.checkShulkers = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("check-shulkers")).description("If to check the items inside of a shulker you are dropping.")).defaultValue(false)).build());
      this.allItems = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("all-items")).description("If to disable dropping completely.")).defaultValue(false)).build());
      this.items = this.sgGeneral.add(((ItemListSetting.Builder)((ItemListSetting.Builder)((ItemListSetting.Builder)(new ItemListSetting.Builder()).name("items")).description("The items to stop dropping.")).visible(() -> !(Boolean)this.allItems.get())).build());
   }

   @EventHandler
   private void onDrop(DropItemsEvent event) {
      if ((Boolean)this.allItems.get()) {
         event.cancel();
      } else if (((List)this.items.get()).contains(event.itemStack.getItem())) {
         event.cancel();
      } else {
         if ((Boolean)this.checkShulkers.get() && Utils.isShulker(event.itemStack.getItem())) {
            ItemStack[] itemStacks = new ItemStack[27];
            Utils.getItemsInContainerItem(event.itemStack, itemStacks);

            for(ItemStack item : itemStacks) {
               if (((List)this.items.get()).contains(item.getItem())) {
                  event.cancel();
                  return;
               }
            }
         }

      }
   }
}
