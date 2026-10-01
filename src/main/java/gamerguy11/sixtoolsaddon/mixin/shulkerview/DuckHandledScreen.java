package gamerguy11.sixtoolsaddon.mixin.shulkerview;

import net.minecraft.screen.slot.Slot;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({HandledScreen.class})
public interface DuckHandledScreen {
   @Accessor("focusedSlot")
   Slot shulkerView$getFocused();
}
