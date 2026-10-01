package gamerguy11.sixtoolsaddon.mixin.accessor;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({PlayerInventory.class})
public interface PlayerInventoryAccessor {
   @Accessor("selectedSlot")
   int getSelectedSlot();

   @Accessor("selectedSlot")
   void setSelectedSlot(int var1);

   @Accessor("main")
   DefaultedList getMain();
}
