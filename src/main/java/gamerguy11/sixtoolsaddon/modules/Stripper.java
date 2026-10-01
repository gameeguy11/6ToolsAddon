package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.mixin.accessor.PlayerInventoryAccessor;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

public class Stripper extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Integer> axeSlot;
   private final Setting<Integer> stripDelay;
   private final Setting<Integer> breakDelay;
   private final Setting<Integer> placeDelay;
   private final Setting<Integer> rotationTime;
   private final Setting<Boolean> autoMine;
   private State state;
   private BlockPos targetPos;
   private BlockPos workingPos;
   private int tickTimer;
   private int rotationTimer;
   private boolean firstLogDetected;
   private static final Set<Block> LOGS;
   private static final Set<Block> STRIPPED_LOGS;

   public Stripper() {
      super(SixToolsAddon.CATEGORY, "stripper", "Strips and breaks logs after you place the first one");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.axeSlot = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("axe-slot")).description("Hotbar slot for axe (1-9)")).defaultValue(1)).range(1, 9).sliderRange(1, 9).build());
      this.stripDelay = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("strip-delay")).description("Ticks to wait before stripping")).defaultValue(0)).range(0, 40).sliderRange(0, 40).build());
      this.breakDelay = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("break-delay")).description("Ticks to wait before breaking")).defaultValue(0)).range(0, 40).sliderRange(0, 40).build());
      this.placeDelay = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("place-delay")).description("Ticks to wait before placing next log")).defaultValue(0)).range(0, 40).sliderRange(0, 40).build());
      this.rotationTime = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("rotation-time")).description("Ticks to hold rotation before action")).defaultValue(0)).range(0, 20).sliderRange(0, 20).build());
      this.autoMine = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("auto-mine")).description("Automatically mine the stripped log")).defaultValue(true)).build());
      this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
      this.targetPos = null;
      this.workingPos = null;
      this.tickTimer = 0;
      this.rotationTimer = 0;
      this.firstLogDetected = false;
   }

   public void onActivate() {
      this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
      this.targetPos = null;
      this.workingPos = null;
      this.tickTimer = 0;
      this.rotationTimer = 0;
      this.firstLogDetected = false;
      this.info("Place a log to set the working position", new Object[0]);
   }

   public void onDeactivate() {
      this.targetPos = null;
      this.workingPos = null;
      this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
      this.firstLogDetected = false;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.tickTimer > 0) {
            --this.tickTimer;
         } else {
            switch (this.state.ordinal()) {
               case 0: {
                  BlockPos playerPos = this.mc.player.getBlockPos();

                  for(int x = -3; x <= 3; ++x) {
                     for(int y = -1; y <= 2; ++y) {
                        for(int z = -3; z <= 3; ++z) {
                           BlockPos checkPos = playerPos.add(x, y, z);
                           Block block = this.mc.world.getBlockState(checkPos).getBlock();
                           if (LOGS.contains(block) && !this.firstLogDetected) {
                              this.workingPos = checkPos;
                              this.targetPos = checkPos;
                              this.firstLogDetected = true;
                              this.info("Working position set", new Object[0]);
                              this.state = Stripper.State.ROTATING_TO_STRIP;
                              this.rotationTimer = (Integer)this.rotationTime.get();
                              return;
                           }
                        }
                     }
                  }
                  break;
               }
               case 1: {
                  if (this.workingPos == null) {
                     this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                     return;
                  }

                  Vec3d target = this.workingPos.toCenterPos();
                  Rotations.rotate((double)this.getYaw(target), (double)this.getPitch(target));
                  --this.rotationTimer;
                  if (this.rotationTimer <= 0) {
                     this.state = Stripper.State.PLACING_LOG;
                  }
                  break;
               }
               case 2: {
                  if (this.workingPos == null) {
                     this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                     return;
                  }

                  int logSlot = this.findLogInInventory();
                  if (logSlot == -1) {
                     this.error("No logs in inventory", new Object[0]);
                     this.toggle();
                     return;
                  }

                  ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(logSlot);
                  BlockPos placeAgainst = this.workingPos.down();
                  Vec3d target = placeAgainst.toCenterPos().add((double)0.0F, (double)0.5F, (double)0.0F);
                  Rotations.rotate((double)this.getYaw(target), (double)this.getPitch(target));
                  BlockHitResult hitResult = new BlockHitResult(target, Direction.UP, placeAgainst, false);
                  this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
                  this.state = Stripper.State.WAIT_AFTER_PLACE;
                  this.tickTimer = (Integer)this.placeDelay.get();
                  this.targetPos = this.workingPos;
                  break;
               }
               case 3: {
                  if (this.workingPos != null && LOGS.contains(this.mc.world.getBlockState(this.workingPos).getBlock())) {
                     this.state = Stripper.State.ROTATING_TO_STRIP;
                     this.rotationTimer = (Integer)this.rotationTime.get();
                  } else {
                     this.state = Stripper.State.ROTATING_TO_PLACE;
                     this.rotationTimer = (Integer)this.rotationTime.get();
                  }
                  break;
               }
               case 4: {
                  if (this.targetPos == null || this.workingPos == null) {
                     this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                     return;
                  }

                  Vec3d target = this.targetPos.toCenterPos();
                  Rotations.rotate((double)this.getYaw(target), (double)this.getPitch(target));
                  --this.rotationTimer;
                  if (this.rotationTimer <= 0) {
                     this.state = Stripper.State.STRIPPING;
                  }
                  break;
               }
               case 5: {
                  if (this.targetPos == null || this.workingPos == null) {
                     this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                     return;
                  }

                  int slot = (Integer)this.axeSlot.get() - 1;
                  ItemStack stack = this.mc.player.getInventory().getStack(slot);
                  if (stack.isEmpty() || !(stack.getItem() instanceof AxeItem)) {
                     this.error("No axe in slot " + String.valueOf(this.axeSlot.get()), new Object[0]);
                     this.toggle();
                     return;
                  }

                  ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(slot);
                  Vec3d target = this.targetPos.toCenterPos();
                  Rotations.rotate((double)this.getYaw(target), (double)this.getPitch(target));
                  BlockHitResult hitResult = new BlockHitResult(target, Direction.UP, this.targetPos, false);
                  this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
                  this.state = Stripper.State.WAIT_AFTER_STRIP;
                  this.tickTimer = (Integer)this.stripDelay.get();
                  break;
               }
               case 6: {
                  if (this.targetPos != null && STRIPPED_LOGS.contains(this.mc.world.getBlockState(this.targetPos).getBlock())) {
                     if ((Boolean)this.autoMine.get()) {
                        this.state = Stripper.State.ROTATING_TO_BREAK;
                        this.rotationTimer = (Integer)this.rotationTime.get();
                     } else {
                        this.state = Stripper.State.WAIT_BEFORE_NEXT;
                        this.tickTimer = (Integer)this.breakDelay.get();
                     }
                  } else if (this.targetPos != null && LOGS.contains(this.mc.world.getBlockState(this.targetPos).getBlock())) {
                     this.state = Stripper.State.ROTATING_TO_STRIP;
                     this.rotationTimer = (Integer)this.rotationTime.get();
                  } else {
                     this.state = Stripper.State.WAIT_BEFORE_NEXT;
                     this.tickTimer = (Integer)this.breakDelay.get();
                  }
                  break;
               }
               case 7: {
                  if (this.targetPos == null || this.workingPos == null) {
                     this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                     return;
                  }

                  Vec3d target = this.targetPos.toCenterPos();
                  Rotations.rotate((double)this.getYaw(target), (double)this.getPitch(target));
                  --this.rotationTimer;
                  if (this.rotationTimer <= 0) {
                     this.state = Stripper.State.BREAKING;
                  }
                  break;
               }
               case 8: {
                  if (this.targetPos == null || this.workingPos == null) {
                     this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                     return;
                  }

                  int slot = (Integer)this.axeSlot.get() - 1;
                  ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(slot);
                  Vec3d target = this.targetPos.toCenterPos();
                  Rotations.rotate((double)this.getYaw(target), (double)this.getPitch(target));
                  this.mc.interactionManager.updateBlockBreakingProgress(this.targetPos, Direction.UP);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
                  this.state = Stripper.State.WAIT_AFTER_BREAK;
                  this.tickTimer = 2;
                  break;
               }
               case 9: {
                  if (this.targetPos != null && !this.mc.world.getBlockState(this.targetPos).isAir()) {
                     this.state = Stripper.State.BREAKING;
                     this.tickTimer = 1;
                  } else {
                     this.state = Stripper.State.WAIT_BEFORE_NEXT;
                     this.tickTimer = (Integer)this.breakDelay.get();
                  }
                  break;
               }
               case 10: {
                  if (this.findLogInInventory() != -1) {
                     this.state = Stripper.State.ROTATING_TO_PLACE;
                     this.rotationTimer = (Integer)this.rotationTime.get();
                  } else {
                     this.info("No more logs in inventory", new Object[0]);
                     this.toggle();
                  }
               }
            }

         }
      }
   }

   private float getYaw(Vec3d target) {
      Vec3d playerPos = this.mc.player.getEyePos();
      double deltaX = target.x - playerPos.x;
      double deltaZ = target.z - playerPos.z;
      return (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
   }

   private float getPitch(Vec3d target) {
      Vec3d playerPos = this.mc.player.getEyePos();
      double deltaX = target.x - playerPos.x;
      double deltaY = target.y - playerPos.y;
      double deltaZ = target.z - playerPos.z;
      double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      return (float)(-Math.toDegrees(Math.atan2(deltaY, horizontalDistance)));
   }

   private int findLogInInventory() {
      for(int i = 0; i < 9; ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (!stack.isEmpty()) {
            Block block = Block.getBlockFromItem(stack.getItem());
            if (LOGS.contains(block)) {
               return i;
            }
         }
      }

      return -1;
   }

   public String getInfoString() {
      return this.state.toString().replace("_", " ");
   }

   static {
      LOGS = Set.of(Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG, Blocks.CRIMSON_STEM, Blocks.WARPED_STEM, Blocks.OAK_WOOD, Blocks.SPRUCE_WOOD, Blocks.BIRCH_WOOD, Blocks.JUNGLE_WOOD, Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD, Blocks.MANGROVE_WOOD, Blocks.CHERRY_WOOD, Blocks.CRIMSON_HYPHAE, Blocks.WARPED_HYPHAE);
      STRIPPED_LOGS = Set.of(Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_CRIMSON_STEM, Blocks.STRIPPED_WARPED_STEM, Blocks.STRIPPED_OAK_WOOD, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STRIPPED_BIRCH_WOOD, Blocks.STRIPPED_JUNGLE_WOOD, Blocks.STRIPPED_ACACIA_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD, Blocks.STRIPPED_MANGROVE_WOOD, Blocks.STRIPPED_CHERRY_WOOD, Blocks.STRIPPED_CRIMSON_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE);
   }

   private static enum State {
      WAITING_FOR_FIRST_LOG,
      ROTATING_TO_PLACE,
      PLACING_LOG,
      WAIT_AFTER_PLACE,
      ROTATING_TO_STRIP,
      STRIPPING,
      WAIT_AFTER_STRIP,
      ROTATING_TO_BREAK,
      BREAKING,
      WAIT_AFTER_BREAK,
      WAIT_BEFORE_NEXT;

      private static State[] $values() {
         return new State[]{WAITING_FOR_FIRST_LOG, ROTATING_TO_PLACE, PLACING_LOG, WAIT_AFTER_PLACE, ROTATING_TO_STRIP, STRIPPING, WAIT_AFTER_STRIP, ROTATING_TO_BREAK, BREAKING, WAIT_AFTER_BREAK, WAIT_BEFORE_NEXT};
      }
   }
}
