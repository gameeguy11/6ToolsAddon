package gamerguy11.sixtoolsaddon.modules.stashsorter.logic;

import gamerguy11.sixtoolsaddon.modules.chesttracker.TrackedContainer;
import gamerguy11.sixtoolsaddon.modules.stashsorter.data.SortArea;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class SortPlanner {
   private SortPlanner() {
   }

   public static List<TrackedContainer> indexedContainers(List<TrackedContainer> tracked, SortArea area, String dimension, BlockPos origin, boolean includeBarrels, boolean includeTrappedChests) {
      if (area != null && dimension.equals(area.dimension)) {
         Set<BlockPos> seen = new HashSet();
         List<TrackedContainer> candidates = new ArrayList();

         for(TrackedContainer container : tracked) {
            BlockPos pos = container.getPosition();
            if (area.contains(pos) && seen.add(pos) && isSupportedType(container.getContainerType(), includeBarrels, includeTrappedChests)) {
               candidates.add(container);
            }
         }

         candidates.sort(Comparator.<TrackedContainer>comparingDouble((containerx) -> origin.getSquaredDistance(containerx.getPosition())).thenComparing(TrackedContainer::getPosition));
         return candidates;
      } else {
         return List.of();
      }
   }

   private static boolean isSupportedType(String type, boolean includeBarrels, boolean includeTrappedChests) {
      if (type == null) {
         return false;
      } else {
         String normalized = type.toLowerCase(Locale.ROOT);
         if (normalized.equals("barrel")) {
            return includeBarrels;
         } else if (normalized.equals("trapped_chest")) {
            return includeTrappedChests;
         } else {
            return normalized.equals("chest") || normalized.equals("copper_chest") || normalized.equals("double_chest");
         }
      }
   }
}
