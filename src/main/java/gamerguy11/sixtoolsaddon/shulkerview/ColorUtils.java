package gamerguy11.sixtoolsaddon.shulkerview;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.util.math.ColorHelper;

public class ColorUtils {
   private ColorUtils() {
      throw new AssertionError();
   }

   public static int getColor(ShulkerBoxBlock block) {
      return block.getColor() != null ? ColorHelper.withAlpha(255, block.getColor().getMapColor().color) : -6728784;
   }
}
