package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.modules.stashsorter.data.SortArea;
import java.util.List;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;

public final class AutoStashSorterSelectionHandler {
   private static final SettingColor AREA_FILL = new SettingColor(0, 180, 255, 24);
   private static final SettingColor AREA_LINE = new SettingColor(0, 210, 255, 210);
   private static final SettingColor PREVIEW_FILL = new SettingColor(0, 255, 110, 28);
   private static final SettingColor PREVIEW_LINE = new SettingColor(0, 255, 110, 230);
   private static final SettingColor TRACKED_FILL = new SettingColor(255, 190, 0, 36);
   private static final SettingColor TRACKED_LINE = new SettingColor(255, 205, 35, 220);
   private static AutoStashSorterSelectionHandler instance;
   private List<BlockPos> trackedPositions = List.of();
   private String cachedAreaKey = "";
   private long nextTrackedRefresh;
   private boolean selectionClickArmed = true;

   private AutoStashSorterSelectionHandler() {
   }

   public static void init() {
      if (instance == null) {
         instance = new AutoStashSorterSelectionHandler();
         MeteorClient.EVENT_BUS.subscribe(instance);
      }

   }

   @EventHandler(
      priority = 200
   )
   private void onStartBreakingBlock(StartBreakingBlockEvent event) {
      AutoStashSorter module = sorter();
      if (module != null && module.isSelectingArea()) {
         event.cancel();
         if (this.selectionClickArmed) {
            this.selectionClickArmed = false;
            module.handleAreaSelection(event.blockPos);
         }

      }
   }

   @EventHandler(
      priority = 200
   )
   private void onInteractBlock(InteractBlockEvent event) {
      AutoStashSorter module = sorter();
      if (module != null && module.isSelectingArea() && event.hand == Hand.MAIN_HAND) {
         event.cancel();
         if (this.selectionClickArmed) {
            this.selectionClickArmed = false;
            module.handleAreaSelection(event.result.getBlockPos());
         }

      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      AutoStashSorter module = sorter();
      if (module != null) {
         if (!module.isSelectingArea()) {
            this.selectionClickArmed = true;
         } else {
            if (!MeteorClient.mc.options.attackKey.isPressed() && !MeteorClient.mc.options.useKey.isPressed()) {
               this.selectionClickArmed = true;
            }

            if (MeteorClient.mc.options.inventoryKey.wasPressed()) {
               module.cancelAreaSelection();
               this.selectionClickArmed = true;
               ChatUtils.info("Area selection cancelled", new Object[0]);
            }

         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
         AutoStashSorter module = sorter();
         if (module != null) {
            int minY = MeteorClient.mc.world.getBottomY();
            int maxY = minY + MeteorClient.mc.world.getHeight() - 1;
            SortArea area = module.getCurrentArea();
            if (area != null) {
               draw(event, area.box(minY, maxY), AREA_FILL, AREA_LINE);
            }

            String areaKey = area == null ? "" : area.dimension + ":" + area.corners();
            long now = System.currentTimeMillis();
            if (!areaKey.equals(this.cachedAreaKey) || now >= this.nextTrackedRefresh) {
               this.cachedAreaKey = areaKey;
               this.nextTrackedRefresh = now + 1000L;
               this.trackedPositions = area == null ? List.of() : module.getTrackedContainerPositionsInArea();
            }

            for(BlockPos pos : this.trackedPositions) {
               Box trackedBox = (new Box(pos)).expand(0.025);
               draw(event, trackedBox, TRACKED_FILL, TRACKED_LINE);
            }

            if (module.isSelectingArea() && module.getSelectionPos1() != null) {
               BlockPos first;
               BlockPos var10000;
               label38: {
                  first = module.getSelectionPos1();
                  HitResult var12 = MeteorClient.mc.crosshairTarget;
                  if (var12 instanceof BlockHitResult) {
                     BlockHitResult hit = (BlockHitResult)var12;
                     if (MeteorClient.mc.crosshairTarget.getType() == Type.BLOCK) {
                        var10000 = hit.getBlockPos();
                        break label38;
                     }
                  }

                  var10000 = first;
               }

               BlockPos second = var10000;
               SortArea preview = new SortArea(MeteorClient.mc.world.getRegistryKey().getValue().toString(), first, second);
               draw(event, preview.box(minY, maxY), PREVIEW_FILL, PREVIEW_LINE);
            }
         }
      }
   }

   private static void draw(Render3DEvent event, Box box, SettingColor fill, SettingColor line) {
      event.renderer.box(box, fill, line, ShapeMode.Both, 0);
   }

   private static AutoStashSorter sorter() {
      return (AutoStashSorter)Modules.get().get(AutoStashSorter.class);
   }
}
