package gamerguy11.sixtoolsaddon.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;

public class StashMoverSelectionHandler {
   private static StashMoverSelectionHandler INSTANCE;
   private boolean wasSelecting = false;

   public static void init() {
      if (INSTANCE == null) {
         INSTANCE = new StashMoverSelectionHandler();
         MeteorClient.EVENT_BUS.subscribe(INSTANCE);
         System.out.println("[StashMover] Selection handler initialized and subscribed to events");
         if (MeteorClient.mc != null && MeteorClient.mc.player != null) {
            ChatUtils.info("[StashMover] Selection handler ready", new Object[0]);
         }
      } else {
         System.out.println("[StashMover] Selection handler already initialized");
      }

   }

   @EventHandler(
      priority = 200
   )
   private void onInteractBlock(InteractBlockEvent event) {
      AutoStashSorter sorter = (AutoStashSorter)Modules.get().get(AutoStashSorter.class);
      if (sorter == null || !sorter.isSelectingArea()) {
         StashMover module = (StashMover)Modules.get().get(StashMover.class);
         if (module != null) {
            if (module.isSelecting()) {
               if (event.hand == Hand.MAIN_HAND) {
                  event.cancel();
                  module.handleBlockSelectionPublic(event.result.getBlockPos());
               }
            }
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onStartBreakingBlock(StartBreakingBlockEvent event) {
      AutoStashSorter sorter = (AutoStashSorter)Modules.get().get(AutoStashSorter.class);
      if (sorter == null || !sorter.isSelectingArea()) {
         StashMover module = (StashMover)Modules.get().get(StashMover.class);
         if (module != null) {
            if (module.isSelecting()) {
               event.cancel();
               module.handleBlockSelectionPublic(event.blockPos);
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
         StashMover module = (StashMover)Modules.get().get(StashMover.class);
         if (module != null) {
            if (module.isSelecting()) {
               if (!this.wasSelecting) {
                  this.wasSelecting = true;
               }

               if (MeteorClient.mc.options.attackKey.isPressed()) {
                  MeteorClient.mc.options.attackKey.setPressed(false);
                  if (MeteorClient.mc.crosshairTarget != null && MeteorClient.mc.crosshairTarget.getType() == Type.BLOCK) {
                     BlockHitResult hit = (BlockHitResult)MeteorClient.mc.crosshairTarget;
                     BlockPos pos = hit.getBlockPos();
                     module.handleBlockSelectionPublic(pos);
                  }
               }

               if (MeteorClient.mc.options.inventoryKey.wasPressed()) {
                  module.cancelSelection();
                  ChatUtils.info("§cSelection cancelled", new Object[0]);
                  return;
               }
            } else {
               this.wasSelecting = false;
            }

         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      StashMover module = (StashMover)Modules.get().get(StashMover.class);
      if (module != null) {
         if (module.getSelectionMode() != StashMover.SelectionMode.NONE) {
            BlockPos selectionPos1 = module.getSelectionPos1();
            if (selectionPos1 != null) {
               BlockPos currentPos = MeteorClient.mc.crosshairTarget != null && MeteorClient.mc.crosshairTarget.getType() == Type.BLOCK ? ((BlockHitResult)MeteorClient.mc.crosshairTarget).getBlockPos() : MeteorClient.mc.player.getBlockPos();
               Box selectionBox = new Box((double)Math.min(selectionPos1.getX(), currentPos.getX()), (double)Math.min(selectionPos1.getY(), currentPos.getY()), (double)Math.min(selectionPos1.getZ(), currentPos.getZ()), (double)(Math.max(selectionPos1.getX(), currentPos.getX()) + 1), (double)(Math.max(selectionPos1.getY(), currentPos.getY()) + 1), (double)(Math.max(selectionPos1.getZ(), currentPos.getZ()) + 1));
               boolean isInput = module.getSelectionMode() == StashMover.SelectionMode.INPUT_FIRST || module.getSelectionMode() == StashMover.SelectionMode.INPUT_SECOND;
               SettingColor color = isInput ? new SettingColor(0, 255, 0, 100) : new SettingColor(0, 100, 255, 100);
               event.renderer.box(selectionBox, color, color, ShapeMode.Both, 0);
               Box corner1 = new Box((double)selectionPos1.getX(), (double)selectionPos1.getY(), (double)selectionPos1.getZ(), (double)(selectionPos1.getX() + 1), (double)(selectionPos1.getY() + 1), (double)(selectionPos1.getZ() + 1));
               event.renderer.box(corner1, new SettingColor(255, 255, 0, 200), new SettingColor(255, 255, 0, 100), ShapeMode.Both, 0);
            }
         }

         module.renderAreas(event);
      }
   }
}
