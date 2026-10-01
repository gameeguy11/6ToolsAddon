package gamerguy11.sixtoolsaddon.shulkerview;

import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.screen.slot.Slot;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public class UpdateHandler {
   private final ObjectList shulkerList = ObjectLists.synchronize(new ObjectArrayList());
   private final ShulkerView config;

   public UpdateHandler(ShulkerView config) {
      this.config = config;
   }

   public void tick() {
      if (MeteorClient.mc.currentScreen instanceof HandledScreen) {
         HandledScreen<?> screen = (HandledScreen)MeteorClient.mc.currentScreen;
         List<ShulkerInfo> found = new ArrayList();
         boolean stack = this.config.isStackIdentical();

         for(Slot slot : screen.getScreenHandler().slots) {
            ShulkerInfo info = ShulkerInfo.create(this.config, slot.getStack(), slot.id);
            if (info != null) {
               boolean merged = false;
               if (stack) {
                  for(int i = 0; i < found.size(); ++i) {
                     ShulkerInfo existing = (ShulkerInfo)found.get(i);
                     if (existing.sameAs(info)) {
                        found.set(i, existing.withCount(existing.count() + 1));
                        merged = true;
                        break;
                     }
                  }
               }

               if (!merged) {
                  found.add(info);
               }
            }
         }

         found.sort(Comparator.comparingInt(ShulkerInfo::totalItems).reversed().thenComparing(Comparator.comparingInt(ShulkerInfo::count).reversed()));
         this.shulkerList.clear();
         this.shulkerList.addAll(found);
      }
   }

   public ObjectList getShulkerList() {
      return this.shulkerList;
   }
}
