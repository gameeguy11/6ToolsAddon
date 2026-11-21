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

   public static boolean isReady() {
      if (!BaritoneCheck.isPresent()) {
         return false;
      }

      try {
         return Impl.isReady();
      } catch (Throwable ignored) {
         return false;
      }
   }

   public static boolean isGoalActive(BlockPos pos) {
      if (!BaritoneCheck.isPresent()) {
         return false;
      }

      try {
         return Impl.isGoalActive(pos);
      } catch (Throwable ignored) {
         return false;
      }
   }

   public static void cancelGoal(BlockPos pos) {
      if (!BaritoneCheck.isPresent()) {
         return;
      }

      try {
         Impl.cancelGoal(pos);
      } catch (Throwable ignored) {
         gamerguy11.sixtoolsaddon.utils.Swallowed.log("BaritoneBridge:cancelGoal", ignored);
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

   public static Boolean getFreeLook() {
      if (!BaritoneCheck.isPresent()) {
         return null;
      }

      return Impl.getFreeLook();
   }

   public static void setFreeLook(boolean value) {
      if (BaritoneCheck.isPresent()) {
         Impl.setFreeLook(value);
      }
   }

   private static final class Impl {
      static Boolean getFreeLook() {
         return (Boolean)baritone.api.BaritoneAPI.getSettings().freeLook.value;
      }

      static void setFreeLook(boolean value) {
         baritone.api.BaritoneAPI.getSettings().freeLook.value = value;
      }

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

      static boolean isReady() {
         baritone.api.utils.IPlayerContext ctx = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext();
         return ctx.player() != null && ctx.world() != null;
      }

      private static BlockPos goalPos(baritone.api.pathing.goals.Goal goal) {
         if (goal instanceof baritone.api.pathing.goals.GoalBlock block) {
            return block.getGoalPos();
         }

         if (goal instanceof baritone.api.pathing.goals.GoalNear near) {
            return near.getGoalPos();
         }

         return null;
      }

      static boolean isGoalActive(BlockPos pos) {
         baritone.api.process.ICustomGoalProcess process = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
         return process.isActive() && pos.equals(goalPos(process.getGoal()));
      }

      static void cancelGoal(BlockPos pos) {
         baritone.api.IBaritone bar = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
         baritone.api.process.ICustomGoalProcess process = bar.getCustomGoalProcess();
         if (!process.isActive() || !pos.equals(goalPos(process.getGoal()))) {
            return;
         }

         process.onLostControl();
         bar.getPathingBehavior().cancelEverything();
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