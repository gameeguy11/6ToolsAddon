package gamerguy11.sixtoolsaddon.baritone;

import net.minecraft.util.math.BlockPos;

public final class BaritoneBridge {
   private BaritoneBridge() {
   }

   public static boolean isPathing() {
      return BaritoneCheck.isPresent() && Impl.isPathing();
   }

   public static void cancel() {
      if (BaritoneCheck.isPresent()) {
         Impl.cancel();
      }
   }

   public static void goToBlock(BlockPos pos) {
      if (BaritoneCheck.isPresent()) {
         Impl.goToBlock(pos);
      }
   }

   public static void goNear(BlockPos pos, int range) {
      if (BaritoneCheck.isPresent()) {
         Impl.goNear(pos, range);
      }
   }

   public static void getToBlock(BlockPos pos) {
      if (BaritoneCheck.isPresent()) {
         Impl.getToBlock(pos);
      }
   }

   public static boolean[] getAllowBreakPlace() {
      if (!BaritoneCheck.isPresent()) {
         return null;
      }

      return Impl.getAllowBreakPlace();
   }

   public static void setAllowBreakPlace(boolean allowBreak, boolean allowPlace) {
      if (BaritoneCheck.isPresent()) {
         Impl.setAllowBreakPlace(allowBreak, allowPlace);
      }
   }

   private static final class Impl {
      static boolean isPathing() {
         return baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
      }

      static void cancel() {
         baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
      }

      static void goToBlock(BlockPos pos) {
         baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new baritone.api.pathing.goals.GoalBlock(pos));
      }

      static void goNear(BlockPos pos, int range) {
         baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new baritone.api.pathing.goals.GoalNear(pos, range));
      }

      static void getToBlock(BlockPos pos) {
         baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new baritone.api.pathing.goals.GoalGetToBlock(pos));
      }

      static boolean[] getAllowBreakPlace() {
         baritone.api.Settings s = baritone.api.BaritoneAPI.getSettings();
         return new boolean[]{(Boolean)s.allowBreak.value, (Boolean)s.allowPlace.value};
      }

      static void setAllowBreakPlace(boolean allowBreak, boolean allowPlace) {
         baritone.api.Settings s = baritone.api.BaritoneAPI.getSettings();
         s.allowBreak.value = allowBreak;
         s.allowPlace.value = allowPlace;
      }
   }
}
