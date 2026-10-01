package gamerguy11.sixtoolsaddon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalGetToBlock;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.modules.chesttracker.ChestTrackerModule;
import gamerguy11.sixtoolsaddon.modules.chesttracker.TrackedContainer;
import gamerguy11.sixtoolsaddon.modules.stashsorter.data.SortArea;
import gamerguy11.sixtoolsaddon.modules.stashsorter.data.SortAreaStore;
import gamerguy11.sixtoolsaddon.modules.stashsorter.logic.ItemRoute;
import gamerguy11.sixtoolsaddon.modules.stashsorter.logic.SortPlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.registry.Registries;

public class AutoStashSorter extends Module {
   private static final String MIXED_ITEMS_ROUTE = "mixed#items";
   private static final String SHULKER_ROUTE_PREFIX = "shulker#";
   private static final String SHULKER_MISC_ROUTE = "shulker#misc";
   private final SettingGroup sgGeneral;
   private final SettingGroup sgFilter;
   private final SettingGroup sgTiming;
   private final SettingGroup sgRender;
   private final SettingGroup sgSafety;
   private final Setting<Boolean> includeBarrels;
   private final Setting<Boolean> includeTrappedChests;
   private final Setting moveShulkers;
   private final Setting minShulkersForOwnChest;
   private final Setting looseItems;
   private final Setting<Boolean> keepHotbar;
   private final Setting<List<String>> keepItems;
   private final Setting<Boolean> fallbackToEmptiest;
   private final Setting sortContainerContents;
   private final Setting<Integer> interactDelay;
   private final Setting<Integer> closeDelay;
   private final Setting transferSpeed;
   private final Setting waypointDelay;
   private final Setting<Integer> gotoTimeout;
   private final Setting safePathing;
   private final Setting pauseOnLag;
   private final Setting<Boolean> chatFeedback;
   private final Setting<Boolean> renderContainers;
   private final Setting<SettingColor> scannedColor;
   private State state;
   private int stateTimer;
   private int gotoTimer;
   private int openRetries;
   private Boolean savedAllowBreak;
   private Boolean savedAllowPlace;
   private final List<StashContainer> containers;
   private final Map<String, String> shulkerRouteGroups;
   private final SortAreaStore areaStore;
   private SortArea currentArea;
   private String loadedAreaKey;
   private String loadedTrackerKey;
   private boolean selectingArea;
   private BlockPos selectionPos1;
   private String selectionDimension;
   private String selectionServer;
   private int sourceIndex;
   private StashContainer currentSource;
   private StashContainer currentTarget;
   private Item currentCargoItem;
   private String currentCargoRoute;
   private boolean revisitSource;
   private Item currentAttemptItem;
   private String currentAttemptRoute;
   private int currentAttemptCount;

   public void onActivate() {
      if (this.mc.player != null && this.mc.world != null) {
         this.containers.clear();
         this.sourceIndex = -1;
         this.currentTarget = null;
         this.currentSource = null;
         this.currentCargoItem = null;
         this.currentCargoRoute = null;
         this.revisitSource = false;
         this.currentAttemptItem = null;
         this.currentAttemptRoute = null;
         this.currentAttemptCount = 0;
         this.stateTimer = 0;
         this.gotoTimer = 0;
         this.openRetries = 0;
         this.setSafePathing((Boolean)this.safePathing.get());
         if (this.getCurrentArea() == null) {
            this.warning("No sorting area set. Use .sorter area and select two corners.", new Object[0]);
            this.toggle();
         } else {
            this.loadTrackedContainers();
            if (this.containers.isEmpty()) {
               this.warning("No tracked chests or barrels were found in the selected area. Open them with ChestTracker first.", new Object[0]);
               this.toggle();
            } else {
               if ((Boolean)this.chatFeedback.get()) {
                  this.info("Using (highlight)%d(default) ChestTracker entries inside the selected area; untracked containers are ignored.", new Object[]{this.containers.size()});
               }

               this.startDepositPhase();
            }
         }
      } else {
         this.warning("You must be in a world to sort.", new Object[0]);
         this.toggle();
      }
   }

   public void onDeactivate() {
      if (this.mc.player != null && this.mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
         this.mc.player.closeHandledScreen();
      }

      this.cancelPathing();
      this.restorePathing();
      ChestTrackerModule tracker = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
      if (tracker != null && this.loadedTrackerKey != null) {
         tracker.getData().saveData();
      }

      this.state = AutoStashSorter.State.IDLE;
   }

   private void loadTrackedContainers() {
      ChestTrackerModule tracker = this.getChestTracker();
      if (tracker != null) {
         String dimension = this.currentDimension();
         List<TrackedContainer> indexed = SortPlanner.indexedContainers(tracker.getData().getAllContainers(dimension), this.getCurrentArea(), dimension, this.mc.player.getBlockPos(), (Boolean)this.includeBarrels.get(), (Boolean)this.includeTrappedChests.get());
         this.shulkerRouteGroups.clear();
         Map<String, Integer> shulkerTotals = new HashMap();

         for(TrackedContainer tracked : indexed) {
            for(Map.Entry route : tracked.getTopLevelRouteCounts().entrySet()) {
               String normalized = this.routeFromTracker((String)route.getKey());
               if (this.isShulkerRoute(normalized)) {
                  shulkerTotals.merge(normalized, (Integer)route.getValue(), Integer::sum);
               }
            }
         }

         for(Map.Entry route : shulkerTotals.entrySet()) {
            String destination = !"shulker#empty".equals(route.getKey()) && (Integer)route.getValue() < (Integer)this.minShulkersForOwnChest.get() ? "shulker#misc" : (String)route.getKey();
            this.shulkerRouteGroups.put((String)route.getKey(), destination);
         }

         for(TrackedContainer tracked : indexed) {
            int slots = "barrel".equals(tracked.getContainerType()) ? 27 : 54;
            StashContainer container = new StashContainer(tracked.getPosition(), slots);

            for(Map.Entry entry : tracked.getItems().entrySet()) {
               Identifier id = Identifier.tryParse((String)entry.getKey());
               if (id != null && (Integer)entry.getValue() > 0) {
                  Item item = (Item)Registries.ITEM.get(id);
                  if (item != Items.AIR) {
                     int count = (Integer)entry.getValue();
                     container.counts.put(item, count);
                  }
               }
            }

            for(Map.Entry route : tracked.getTopLevelRouteCounts().entrySet()) {
               String plannedRoute = this.routeFromTracker((String)route.getKey());
               if (plannedRoute != null) {
                  plannedRoute = (String)this.shulkerRouteGroups.getOrDefault(plannedRoute, plannedRoute);
                  container.routeCounts.merge(plannedRoute, (Integer)route.getValue(), Integer::sum);
               }
            }

            container.freeSlots = slots;
            container.snapshotLoaded = true;
            this.containers.add(container);
         }

      }
   }

   public List<BlockPos> getTrackedContainerPositionsInArea() {
      if (this.mc.player != null && this.mc.world != null) {
         SortArea area = this.getCurrentArea();
         ChestTrackerModule tracker = this.getChestTracker();
         return area != null && tracker != null ? SortPlanner.indexedContainers(tracker.getData().getAllContainers(this.currentDimension()), area, this.currentDimension(), this.mc.player.getBlockPos(), (Boolean)this.includeBarrels.get(), (Boolean)this.includeTrappedChests.get()).stream().map(TrackedContainer::getPosition).toList() : List.of();
      } else {
         return List.of();
      }
   }

   private ChestTrackerModule getChestTracker() {
      ChestTrackerModule tracker = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
      if (tracker == null) {
         return null;
      } else {
         if (!tracker.isActive()) {
            String key = areaKey(this.currentServer(), this.currentDimension());
            if (!key.equals(this.loadedTrackerKey)) {
               tracker.getData().loadData();
               this.loadedTrackerKey = key;
            }
         }

         return tracker;
      }
   }

   public void startAreaSelection() {
      if (this.isActive()) {
         this.warning("Disable the sorter before changing its area.", new Object[0]);
      } else if (this.mc.player != null && this.mc.world != null) {
         StashMover stashMover = (StashMover)Modules.get().get(StashMover.class);
         if (stashMover != null && stashMover.isSelecting()) {
            stashMover.cancelSelection();
         }

         this.selectingArea = true;
         this.selectionPos1 = null;
         this.selectionDimension = this.currentDimension();
         this.selectionServer = this.currentServer();
         this.info("Left-click two corners of the sorting area. Only tracked containers inside it will be used.", new Object[0]);
      } else {
         this.warning("You must be in a world to select a sorting area.", new Object[0]);
      }
   }

   public void handleAreaSelection(BlockPos pos) {
      if (this.selectingArea && pos != null && this.mc.world != null) {
         if (this.selectionDimension.equals(this.currentDimension()) && this.selectionServer.equals(this.currentServer())) {
            if (this.selectionPos1 == null) {
               this.selectionPos1 = pos.toImmutable();
               this.info("First corner set at %s. Select the opposite corner.", new Object[]{pos.toShortString()});
            } else {
               this.currentArea = new SortArea(this.selectionDimension, this.selectionPos1, pos);
               this.areaStore.save(this.selectionServer, this.selectionDimension, this.currentArea);
               this.loadedAreaKey = areaKey(this.selectionServer, this.selectionDimension);
               this.selectingArea = false;
               this.selectionPos1 = null;
               this.info("Sorting area saved: %s (%s blocks).", new Object[]{this.currentArea.corners(), this.currentArea.footprint()});
            }
         } else {
            this.cancelAreaSelection();
            this.warning("World changed during area selection; selection cancelled.", new Object[0]);
         }
      }
   }

   public void cancelAreaSelection() {
      this.selectingArea = false;
      this.selectionPos1 = null;
   }

   public void clearSortArea() {
      if (this.mc.world != null) {
         String server = this.currentServer();
         String dimension = this.currentDimension();
         this.areaStore.clear(server, dimension);
         this.currentArea = null;
         this.loadedAreaKey = areaKey(server, dimension);
      }
   }

   public boolean isSelectingArea() {
      return this.selectingArea;
   }

   public BlockPos getSelectionPos1() {
      return this.selectionPos1;
   }

   public SortArea getCurrentArea() {
      if (this.mc.world == null) {
         return null;
      } else {
         String server = this.currentServer();
         String dimension = this.currentDimension();
         String key = areaKey(server, dimension);
         if (!key.equals(this.loadedAreaKey)) {
            this.currentArea = this.areaStore.load(server, dimension);
            this.loadedAreaKey = key;
         }

         return this.currentArea;
      }
   }

   private String currentDimension() {
      return this.mc.world.getRegistryKey().getValue().toString();
   }

   private String currentServer() {
      ServerInfo server = this.mc.getCurrentServerEntry();
      return server == null ? "singleplayer" : server.address;
   }

   private static String areaKey(String server, String dimension) {
      return server + "|" + dimension;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (!(Boolean)this.pauseOnLag.get() || !(TickRate.INSTANCE.getTimeSinceLastTick() > 2.0F)) {
            if (this.stateTimer > 0) {
               --this.stateTimer;
            } else {
               switch (this.state.ordinal()) {
                  case 1:
                     this.handleGotoSource();
                     break;
                  case 2:
                     this.handleOpen(this.currentSource.pos, AutoStashSorter.State.WITHDRAW);
                     break;
                  case 3:
                     this.handleWithdraw();
                     break;
                  case 4:
                     this.handleCloseSource();
                     break;
                  case 5:
                     this.handleGotoDeposit();
                     break;
                  case 6:
                     this.handleOpen(this.currentTarget.pos, AutoStashSorter.State.DEPOSIT);
                     break;
                  case 7:
                     this.handleDeposit();
                     break;
                  case 8:
                     this.handleVerifyDeposit();
                     break;
                  case 9:
                     this.handleCloseDeposit();
                     break;
                  case 10:
                     if ((Boolean)this.chatFeedback.get()) {
                        this.info("Sorting done.", new Object[0]);
                     }

                     this.toggle();
               }

            }
         }
      }
   }

   private void handleGotoDeposit() {
      BlockPos target = this.currentTarget.pos;
      double dist = Math.sqrt(this.mc.player.getBlockPos().getSquaredDistance(target));
      if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing() && this.gotoTimer == 0) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(target));
      }

      ++this.gotoTimer;
      if (dist <= (double)4.0F) {
         this.cancelPathing();
         this.gotoTimer = 0;
         this.stateTimer = (Integer)this.waypointDelay.get();
         this.state = AutoStashSorter.State.OPEN_DEPOSIT;
      } else {
         if (this.gotoTimer > (Integer)this.gotoTimeout.get()) {
            this.cancelPathing();
            this.gotoTimer = 0;
            if ((Boolean)this.chatFeedback.get()) {
               this.warning("Timed out walking to a container, skipping it.", new Object[0]);
            }

            if (this.currentCargoItem != null) {
               this.currentTarget.blockedItems.add(this.currentCargoItem);
            }

            this.advanceDeposit();
         }

      }
   }

   private void handleGotoSource() {
      BlockPos target = this.currentSource.pos;
      double dist = Math.sqrt(this.mc.player.getBlockPos().getSquaredDistance(target));
      if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing() && this.gotoTimer == 0) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(target));
      }

      ++this.gotoTimer;
      if (dist <= (double)4.0F) {
         this.cancelPathing();
         this.gotoTimer = 0;
         this.stateTimer = (Integer)this.waypointDelay.get();
         this.state = AutoStashSorter.State.OPEN_SOURCE;
      } else {
         if (this.gotoTimer > (Integer)this.gotoTimeout.get()) {
            this.cancelPathing();
            this.gotoTimer = 0;
            if ((Boolean)this.chatFeedback.get()) {
               this.warning("Timed out walking to a source container; skipping it.", new Object[0]);
            }

            ++this.sourceIndex;
            this.startNextSource();
         }

      }
   }

   private void handleWithdraw() {
      ScreenHandler var2 = this.mc.player.currentScreenHandler;
      if (!(var2 instanceof GenericContainerScreenHandler handler)) {
         if (++this.openRetries < 3) {
            this.state = AutoStashSorter.State.OPEN_SOURCE;
            this.stateTimer = (Integer)this.closeDelay.get();
         } else {
            if ((Boolean)this.chatFeedback.get()) {
               this.warning("Couldn't open a source container; skipping it.", new Object[0]);
            }

            ++this.sourceIndex;
            this.startNextSource();
         }

      } else {
         this.openRetries = 0;
         this.readContainer(this.currentSource, handler);
         int clickLimit = Math.min((Integer)this.transferSpeed.get(), this.freeCargoSlots());
         if (clickLimit == 0) {
            if ((Boolean)this.chatFeedback.get()) {
               this.warning("No free inventory slots for more items; stopping the container sweep.", new Object[0]);
            }

            this.sourceIndex = this.containers.size();
            this.state = AutoStashSorter.State.CLOSE_SOURCE;
         } else {
            List<Integer> sourceSlots = new ArrayList();
            Set<String> attemptedRoutes = new HashSet();

            for(int i = 0; i < this.currentSource.totalSlots && i < handler.slots.size(); ++i) {
               ItemStack stack = handler.getSlot(i).getStack();
               if (!stack.isEmpty() && !this.isKept(stack.getItem())) {
                  String plannedRoute = this.routeFor(stack);
                  if (plannedRoute != null && !this.currentSource.blockedRoutes.contains(plannedRoute)) {
                     StashContainer target = this.findBestContainer(stack, this.currentSource);
                     if (target != null && target != this.currentSource) {
                        sourceSlots.add(i);
                        attemptedRoutes.add(plannedRoute);
                     }
                  }
               }
            }

            Map<String, Integer> cargoBefore = this.cargoRouteCounts();
            int clicks = 0;

            for(int slotIndex : sourceSlots) {
               if (clicks >= clickLimit) {
                  break;
               }

               this.mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(slotIndex).id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
               ++clicks;
            }

            Map<String, Integer> cargoAfter = this.cargoRouteCounts();
            Set<String> movedRoutes = new HashSet();

            for(Map.Entry entry : cargoAfter.entrySet()) {
               if ((Integer)entry.getValue() > (Integer)cargoBefore.getOrDefault(entry.getKey(), 0)) {
                  movedRoutes.add((String)entry.getKey());
               }
            }

            for(String route : attemptedRoutes) {
               if (!movedRoutes.contains(route)) {
                  this.currentSource.blockedRoutes.add(route);
               }
            }

            this.revisitSource = !movedRoutes.isEmpty();
            if (clicks > 0) {
               this.readContainer(this.currentSource, handler);
               this.stateTimer = (Integer)this.interactDelay.get();
            }

            this.state = AutoStashSorter.State.CLOSE_SOURCE;
         }
      }
   }

   private int freeCargoSlots() {
      int empty = 0;
      int start = (Boolean)this.keepHotbar.get() ? 9 : 0;

      for(int slot = start; slot < 36; ++slot) {
         if (this.mc.player.getInventory().getStack(slot).isEmpty()) {
            ++empty;
         }
      }

      return empty;
   }

   private Map<String, Integer> cargoRouteCounts() {
      Map<String, Integer> counts = new HashMap();
      int start = (Boolean)this.keepHotbar.get() ? 9 : 0;

      for(int slot = start; slot < 36; ++slot) {
         ItemStack stack = this.mc.player.getInventory().getStack(slot);
         String route = this.routeFor(stack);
         if (route != null) {
            counts.merge(route, stack.getCount(), Integer::sum);
         }
      }

      return counts;
   }

   private void handleCloseSource() {
      if (this.mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
         this.mc.player.closeHandledScreen();
      }

      this.stateTimer = (Integer)this.closeDelay.get();
      if (!this.advanceToNextItem()) {
         if (this.revisitSource) {
            this.revisitSource = false;
         } else {
            ++this.sourceIndex;
         }

         this.startNextSource();
      }

   }

   private void startSourceSweep() {
      if (!(Boolean)this.sortContainerContents.get()) {
         this.state = AutoStashSorter.State.DONE;
      } else {
         this.sourceIndex = 0;
         this.startNextSource();
      }
   }

   private void startNextSource() {
      while(this.sourceIndex < this.containers.size()) {
         this.currentSource = (StashContainer)this.containers.get(this.sourceIndex);
         if (this.currentSource.snapshotLoaded && this.needsConsolidation(this.currentSource)) {
            this.gotoTimer = 0;
            this.openRetries = 0;
            this.state = AutoStashSorter.State.GOTO_SOURCE;
            return;
         }

         ++this.sourceIndex;
      }

      this.currentSource = null;
      this.state = AutoStashSorter.State.DONE;
   }

   private boolean needsConsolidation(StashContainer source) {
      if (source.routeCounts.isEmpty()) {
         return !source.counts.isEmpty();
      } else {
         for(String route : source.routeCounts.keySet()) {
            Item item = this.itemForRoute(route);
            if ((item == null || !this.isKept(item)) && !source.blockedRoutes.contains(route)) {
               StashContainer target = this.findBestRoute(route, item, source);
               if (target != null) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private StashContainer findBestContainer(ItemStack stack, StashContainer excluded) {
      String route = this.routeFor(stack);
      return route == null ? null : this.findBestRoute(route, stack.getItem(), excluded);
   }

   private StashContainer findBestRoute(String route, Item item, StashContainer excluded) {
      StashContainer best = null;
      int bestCount = 0;
      double bestDist = Double.MAX_VALUE;

      for(StashContainer container : this.containers) {
         if (container != excluded && container.snapshotLoaded && container.freeSlots > 0 && !container.blockedRoutes.contains(route) && (this.isShulkerRoute(route) || !this.containsShulkerRoute(container))) {
            int count = (Integer)container.routeCounts.getOrDefault(route, 0);
            if (count > 0) {
               double distance = this.mc.player.getBlockPos().getSquaredDistance(container.pos);
               if (count > bestCount || count == bestCount && distance < bestDist) {
                  best = container;
                  bestCount = count;
                  bestDist = distance;
               }
            }
         }
      }

      if (best != null) {
         return best;
      } else if (!(Boolean)this.fallbackToEmptiest.get()) {
         return null;
      } else {
         StashContainer emptiest = null;
         int mostFree = 0;

         for(StashContainer container : this.containers) {
            if (container != excluded && container.snapshotLoaded && !container.blockedRoutes.contains(route) && (this.isShulkerRoute(route) || !this.containsShulkerRoute(container)) && container.freeSlots > mostFree && (item == null || !container.blockedItems.contains(item))) {
               mostFree = container.freeSlots;
               emptiest = container;
            }
         }

         return emptiest;
      }
   }

   private boolean containsShulkerRoute(StashContainer container) {
      return container.routeCounts.keySet().stream().anyMatch(this::isShulkerRoute);
   }

   private boolean isShulkerRoute(String route) {
      return route != null && route.startsWith("shulker#");
   }

   private void handleOpen(BlockPos pos, State nextState) {
      Vec3d eyePos = this.mc.player.getEyePos();
      Direction face = this.bestFace(pos, eyePos);
      Vec3d hitVec = this.faceHitVector(pos, face);
      double yaw = Rotations.getYaw(hitVec);
      double pitch = Rotations.getPitch(hitVec);
      this.mc.player.setYaw((float)yaw);
      this.mc.player.setPitch((float)pitch);
      this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
      BlockHitResult hit = new BlockHitResult(hitVec, face, pos, false);
      ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hit);
      if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
         this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hit);
      }

      this.stateTimer = (Integer)this.interactDelay.get();
      this.state = nextState;
   }

   private void startDepositPhase() {
      if ((Boolean)this.chatFeedback.get()) {
         this.info("Planning transfers from ChestTracker data...", new Object[0]);
      }

      this.currentCargoItem = null;
      if (!this.advanceToNextItem()) {
         this.startSourceSweep();
      }

   }

   private boolean advanceToNextItem() {
      int start = (Boolean)this.keepHotbar.get() ? 9 : 0;

      for(int slot = start; slot < 36; ++slot) {
         ItemStack stack = this.mc.player.getInventory().getStack(slot);
         if (!stack.isEmpty() && !this.isKept(stack.getItem())) {
            String route = this.routeFor(stack);
            if (route != null) {
               StashContainer target = this.findBestRoute(route, stack.getItem(), this.currentSource);
               if (target != null) {
                  this.currentCargoItem = stack.getItem();
                  this.currentCargoRoute = route;
                  this.currentTarget = target;
                  this.openRetries = 0;
                  this.gotoTimer = 0;
                  this.state = AutoStashSorter.State.GOTO_DEPOSIT;
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean isKept(Item item) {
      String id = Registries.ITEM.getId(item).toString();

      for(String kept : (List<String>)this.keepItems.get()) {
         if (kept.equalsIgnoreCase(id)) {
            return true;
         }
      }

      return false;
   }

   private String routeFor(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         if (ItemRoute.isShulker(stack)) {
            if (!(Boolean)this.moveShulkers.get()) {
               return null;
            } else {
               String route = ItemRoute.shulkerContents(stack);
               return (String)this.shulkerRouteGroups.getOrDefault(route, route);
            }
         } else {
            String var10000;
            switch (((LooseItems)this.looseItems.get()).ordinal()) {
               case 0 -> var10000 = "mixed#items";
               case 1 -> var10000 = ItemRoute.of(stack);
               case 2 -> var10000 = null;
               default -> throw new MatchException((String)null, (Throwable)null);
            }

            return var10000;
         }
      } else {
         return null;
      }
   }

   private String routeFromTracker(String storedRoute) {
      if (storedRoute == null) {
         return null;
      } else if (storedRoute.startsWith("shulker#")) {
         return (Boolean)this.moveShulkers.get() ? storedRoute : null;
      } else {
         Item item = ItemRoute.item(storedRoute);
         if (item == null) {
            return null;
         } else if (ItemRoute.isShulker(item)) {
            return (Boolean)this.moveShulkers.get() ? "shulker#" + storedRoute : null;
         } else {
            String var10000;
            switch (((LooseItems)this.looseItems.get()).ordinal()) {
               case 0 -> var10000 = "mixed#items";
               case 1 -> var10000 = storedRoute;
               case 2 -> var10000 = null;
               default -> throw new MatchException((String)null, (Throwable)null);
            }

            return var10000;
         }
      }
   }

   private Item itemForRoute(String route) {
      if ("mixed#items".equals(route)) {
         return null;
      } else {
         String itemRoute = route != null && route.startsWith("shulker#") ? route.substring("shulker#".length()) : route;
         return ItemRoute.item(itemRoute);
      }
   }

   private List<Integer> cargoSlots(String route) {
      List<Integer> slots = new ArrayList();
      int start = (Boolean)this.keepHotbar.get() ? 9 : 0;

      for(int slot = start; slot < 36; ++slot) {
         ItemStack stack = this.mc.player.getInventory().getStack(slot);
         if (!stack.isEmpty() && route.equals(this.routeFor(stack))) {
            slots.add(slot);
         }
      }

      return slots;
   }

   private int cargoCount(String route) {
      int count = 0;

      for(int slot : this.cargoSlots(route)) {
         count += this.mc.player.getInventory().getStack(slot).getCount();
      }

      return count;
   }

   private void handleDeposit() {
      ScreenHandler var2 = this.mc.player.currentScreenHandler;
      if (!(var2 instanceof GenericContainerScreenHandler handler)) {
         if (++this.openRetries < 3) {
            this.state = AutoStashSorter.State.OPEN_DEPOSIT;
            this.stateTimer = (Integer)this.closeDelay.get();
         } else {
            if (this.currentCargoRoute != null) {
               this.currentTarget.blockedRoutes.add(this.currentCargoRoute);
            }

            if ((Boolean)this.chatFeedback.get()) {
               this.warning("Couldn't open the destination; trying another container.", new Object[0]);
            }

            this.state = AutoStashSorter.State.CLOSE_DEPOSIT;
         }

      } else {
         this.openRetries = 0;
         this.readContainer(this.currentTarget, handler);
         if (this.currentCargoRoute != null && !this.cargoSlots(this.currentCargoRoute).isEmpty() && !this.currentTarget.blockedRoutes.contains(this.currentCargoRoute)) {
            if (!this.isShulkerRoute(this.currentCargoRoute) && this.containsShulkerRoute(this.currentTarget)) {
               this.currentTarget.blockedRoutes.add(this.currentCargoRoute);
               this.state = AutoStashSorter.State.CLOSE_DEPOSIT;
            } else if (this.looseItems.get() == AutoStashSorter.LooseItems.OWN_CHESTS && !this.isShulkerRoute(this.currentCargoRoute) && this.currentTarget.counts.containsKey(this.currentCargoItem) && !this.currentTarget.routeCounts.containsKey(this.currentCargoRoute)) {
               this.currentTarget.blockedRoutes.add(this.currentCargoRoute);
               if ((Boolean)this.chatFeedback.get()) {
                  this.warning("Destination has a different stack variant; trying another tracked container.", new Object[0]);
               }

               this.state = AutoStashSorter.State.CLOSE_DEPOSIT;
            } else if (this.currentTarget.freeSlots <= 0) {
               this.currentTarget.blockedRoutes.add(this.currentCargoRoute);
               this.state = AutoStashSorter.State.CLOSE_DEPOSIT;
            } else {
               this.currentAttemptItem = this.currentCargoItem;
               this.currentAttemptRoute = this.currentCargoRoute;
               this.currentAttemptCount = this.cargoCount(this.currentAttemptRoute);
               int clicks = 0;

               for(int inventorySlot : this.cargoSlots(this.currentCargoRoute)) {
                  if (clicks >= (Integer)this.transferSpeed.get()) {
                     break;
                  }

                  int screenSlot = this.currentTarget.totalSlots + this.playerInvOffset(inventorySlot);
                  if (screenSlot < handler.slots.size()) {
                     Slot slot = handler.getSlot(screenSlot);
                     this.mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                     ++clicks;
                  }
               }

               if (clicks == 0) {
                  this.currentTarget.blockedRoutes.add(this.currentCargoRoute);
                  this.state = AutoStashSorter.State.CLOSE_DEPOSIT;
               } else {
                  this.state = AutoStashSorter.State.VERIFY_DEPOSIT;
                  this.stateTimer = (Integer)this.interactDelay.get();
               }
            }
         } else {
            this.state = AutoStashSorter.State.CLOSE_DEPOSIT;
         }
      }
   }

   private void handleVerifyDeposit() {
      ScreenHandler var2 = this.mc.player.currentScreenHandler;
      if (!(var2 instanceof GenericContainerScreenHandler handler)) {
         this.currentTarget.blockedRoutes.add(this.currentAttemptRoute);
         this.state = AutoStashSorter.State.CLOSE_DEPOSIT;
      } else {
         int var3 = this.cargoCount(this.currentAttemptRoute);
         this.readContainer(this.currentTarget, handler);
         if (!this.currentTarget.routeCounts.containsKey(this.currentAttemptRoute) && this.currentTarget.counts.containsKey(this.currentAttemptItem)) {
            this.currentTarget.blockedRoutes.add(this.currentAttemptRoute);
         } else if (var3 >= this.currentAttemptCount) {
            this.currentTarget.blockedRoutes.add(this.currentAttemptRoute);
            if ((Boolean)this.chatFeedback.get()) {
               this.warning("Container refused %s; trying another destination.", new Object[]{Registries.ITEM.getId(this.currentAttemptItem)});
            }
         } else if (var3 > 0 && this.currentTarget.freeSlots <= 0) {
            this.currentTarget.blockedRoutes.add(this.currentAttemptRoute);
         }

         this.state = var3 > 0 && !this.currentTarget.blockedRoutes.contains(this.currentAttemptRoute) ? AutoStashSorter.State.DEPOSIT : AutoStashSorter.State.CLOSE_DEPOSIT;
      }
   }

   private void readContainer(StashContainer container, GenericContainerScreenHandler handler) {
      container.counts.clear();
      int containerSlots = Math.max(0, handler.slots.size() - 36);
      if (containerSlots > 0) {
         container.totalSlots = containerSlots;
      }

      int free = 0;
      container.routeCounts.clear();

      for(int i = 0; i < container.totalSlots && i < containerSlots; ++i) {
         ItemStack stack = handler.getSlot(i).getStack();
         if (stack.isEmpty()) {
            ++free;
         } else {
            container.counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            String route = this.routeFor(stack);
            if (route != null) {
               container.routeCounts.merge(route, stack.getCount(), Integer::sum);
            }
         }
      }

      container.freeSlots = free;
      container.snapshotLoaded = true;
      ChestTrackerModule tracker = this.getChestTracker();
      if (tracker != null) {
         tracker.refreshTrackedContainer(container.pos, handler);
      }

   }

   private int playerInvOffset(int playerInvIndex) {
      return playerInvIndex < 9 ? 27 + playerInvIndex : playerInvIndex - 9;
   }

   private void handleCloseDeposit() {
      if (this.mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
         this.mc.player.closeHandledScreen();
      }

      this.stateTimer = (Integer)this.closeDelay.get();
      this.advanceDeposit();
   }

   private void advanceDeposit() {
      if (!this.advanceToNextItem()) {
         if (this.currentSource == null) {
            this.startSourceSweep();
         } else {
            if (this.revisitSource) {
               this.revisitSource = false;
            }

            this.startNextSource();
         }
      }

   }

   private void cancelPathing() {
      if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
      }

   }

   private void setSafePathing(boolean enabled) {
      try {
         Settings settings = BaritoneAPI.getSettings();
         if (!enabled) {
            this.restorePathing();
            return;
         }

         if (this.savedAllowBreak == null) {
            this.savedAllowBreak = (Boolean)settings.allowBreak.value;
            this.savedAllowPlace = (Boolean)settings.allowPlace.value;
         }

         settings.allowBreak.value = false;
         settings.allowPlace.value = false;
      } catch (RuntimeException var3) {
      }

   }

   private void restorePathing() {
      if (this.savedAllowBreak != null && this.savedAllowPlace != null) {
         try {
            Settings settings = BaritoneAPI.getSettings();
            settings.allowBreak.value = this.savedAllowBreak;
            settings.allowPlace.value = this.savedAllowPlace;
         } catch (RuntimeException var2) {
         }

         this.savedAllowBreak = null;
         this.savedAllowPlace = null;
      }
   }

   private Direction bestFace(BlockPos pos, Vec3d eyePos) {
      Vec3d center = Vec3d.ofCenter(pos);
      Vec3d toPlayer = eyePos.subtract(center).normalize();
      Direction best = Direction.UP;
      double bestDot = Double.NEGATIVE_INFINITY;

      for(Direction face : Direction.values()) {
         Vec3d normal = Vec3d.of(face.getVector());
         double dot = toPlayer.dotProduct(normal);
         if (dot > bestDot) {
            bestDot = dot;
            best = face;
         }
      }

      return best == Direction.DOWN ? Direction.UP : best;
   }

   private Vec3d faceHitVector(BlockPos pos, Direction face) {
      double x = (double)pos.getX() + (double)0.5F;
      double y = (double)pos.getY() + (double)0.5F;
      double z = (double)pos.getZ() + (double)0.5F;
      switch (face) {
         case UP -> y = (double)pos.getY() + (double)1.0F;
         case DOWN -> y = (double)pos.getY();
         case NORTH -> z = (double)pos.getZ();
         case SOUTH -> z = (double)pos.getZ() + (double)1.0F;
         case WEST -> x = (double)pos.getX();
         case EAST -> x = (double)pos.getX() + (double)1.0F;
      }

      return new Vec3d(x, y, z);
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      SortArea area = this.getCurrentArea();
      if (area != null) {
         int minY = this.mc.world.getBottomY();
         int maxY = minY + this.mc.world.getHeight() - 1;
         SettingColor areaColor = new SettingColor(0, 180, 255, 45);
         event.renderer.box(area.box(minY, maxY), areaColor, areaColor, ShapeMode.Lines, 0);
      }

      if ((Boolean)this.renderContainers.get()) {
         for(StashContainer c : this.containers) {
            if (c.snapshotLoaded) {
               Box box = (new Box(c.pos)).expand(0.02);
               event.renderer.box(box, (Color)this.scannedColor.get(), (Color)this.scannedColor.get(), ShapeMode.Both, 0);
            }
         }

      }
   }

   public AutoStashSorter() {
      super(SixToolsAddon.CATEGORY, "auto-stash-sorter", "Sorts tracked containers inside a selected area, using ChestTracker's saved index.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgFilter = this.settings.createGroup("Filtering");
      this.sgTiming = this.settings.createGroup("Timing");
      this.sgRender = this.settings.createGroup("Render");
      this.sgSafety = this.settings.createGroup("Safety");
      this.includeBarrels = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("include-barrels")).description("Include tracked barrels in the selected area.")).defaultValue(true)).build());
      this.includeTrappedChests = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("include-trapped-chests")).description("Include tracked trapped chests in the selected area.")).defaultValue(true)).build());
      this.moveShulkers = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("move-shulkers")).description("Sort sealed shulker boxes into their own content-based routes.")).defaultValue(true)).build());
      SettingGroup var10001 = this.sgGeneral;
      IntSetting.Builder var10002 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("min-shulkers-for-own-chest")).description("Minimum boxes of one content route to keep a dedicated shulker destination; smaller groups share the misc route.")).defaultValue(4)).min(1).sliderRange(1, 16);
      Setting<Boolean> var10003 = this.moveShulkers;
      Objects.requireNonNull(var10003);
      this.minShulkersForOwnChest = var10001.add(((IntSetting.Builder)var10002.visible(var10003::get)).build());
      this.looseItems = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("loose-items")).description("Send loose items to one mixed chest, give each stack variant its own route, or leave them alone.")).defaultValue(AutoStashSorter.LooseItems.MIXED_CHEST)).build());
      this.keepHotbar = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("keep-hotbar")).description("Don't touch your hotbar (slots 1-9), only sort your main inventory.")).defaultValue(true)).build());
      this.keepItems = this.sgFilter.add(((StringListSetting.Builder)((StringListSetting.Builder)(new StringListSetting.Builder()).name("keep-items")).description("Item IDs (e.g. minecraft:ender_pearl) to never put in a chest.")).build());
      this.fallbackToEmptiest = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("fallback-to-emptiest-chest")).description("If no tracked container already has this item, use the tracked container with the most available space.")).defaultValue(true)).build());
      this.sortContainerContents = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("sort-container-contents")).description("Move items from tracked sources into their best matching tracked destination.")).defaultValue(true)).build());
      this.interactDelay = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("interact-delay")).description("Ticks to wait after opening a container before reading/depositing.")).defaultValue(4)).min(1).sliderRange(1, 20).build());
      this.closeDelay = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("close-delay")).description("Ticks to wait after closing a container before moving on.")).defaultValue(3)).min(1).sliderRange(1, 20).build());
      this.transferSpeed = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("transfer-speed")).description("Maximum item stacks to quick-move per transfer batch.")).defaultValue(12)).min(1).sliderRange(1, 32).build());
      this.waypointDelay = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("waypoint-delay")).description("Ticks to wait after reaching a source or destination before interacting.")).defaultValue(20)).min(0).sliderRange(0, 60).build());
      this.gotoTimeout = this.sgTiming.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("goto-timeout")).description("Max ticks to spend walking to one container before giving up on it.")).defaultValue(200)).min(20).sliderRange(20, 600).build());
      this.safePathing = this.sgSafety.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("safe-pathing")).description("Prevent Baritone from breaking or placing blocks while sorting.")).defaultValue(true)).onChanged((value) -> {
         if (this.isActive()) {
            this.setSafePathing(value);
         }

      })).build());
      this.pauseOnLag = this.sgSafety.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("pause-on-lag")).description("Pause the sorter and its timeouts while the server is lagging.")).defaultValue(true)).build());
      this.chatFeedback = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("chat-feedback")).description("Print progress messages.")).defaultValue(true)).build());
      this.renderContainers = this.sgRender.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("render-containers")).description("Highlight containers opened during the current run.")).defaultValue(true)).build());
      var10001 = this.sgRender;
      ColorSetting.Builder var2 = ((ColorSetting.Builder)(new ColorSetting.Builder()).name("scanned-color")).defaultValue(new SettingColor(0, 200, 255, 80));
      var10003 = this.renderContainers;
      Objects.requireNonNull(var10003);
      this.scannedColor = var10001.add(((ColorSetting.Builder)var2.visible(var10003::get)).build());
      this.state = AutoStashSorter.State.IDLE;
      this.stateTimer = 0;
      this.gotoTimer = 0;
      this.openRetries = 0;
      this.containers = new ArrayList();
      this.shulkerRouteGroups = new HashMap();
      this.areaStore = new SortAreaStore();
      this.sourceIndex = -1;
   }

   private static enum LooseItems {
      MIXED_CHEST,
      OWN_CHESTS,
      IGNORE;

      private static LooseItems[] $values() {
         return new LooseItems[]{MIXED_CHEST, OWN_CHESTS, IGNORE};
      }
   }

   private static enum State {
      IDLE,
      GOTO_SOURCE,
      OPEN_SOURCE,
      WITHDRAW,
      CLOSE_SOURCE,
      GOTO_DEPOSIT,
      OPEN_DEPOSIT,
      DEPOSIT,
      VERIFY_DEPOSIT,
      CLOSE_DEPOSIT,
      DONE;

      private static State[] $values() {
         return new State[]{IDLE, GOTO_SOURCE, OPEN_SOURCE, WITHDRAW, CLOSE_SOURCE, GOTO_DEPOSIT, OPEN_DEPOSIT, DEPOSIT, VERIFY_DEPOSIT, CLOSE_DEPOSIT, DONE};
      }
   }

   private static class StashContainer {
      final BlockPos pos;
      int totalSlots;
      final Map<Item, Integer> counts = new HashMap();
      final Map<String, Integer> routeCounts = new HashMap();
      final Set<Item> blockedItems = new HashSet();
      final Set<String> blockedRoutes = new HashSet();
      int freeSlots;
      boolean snapshotLoaded;

      StashContainer(BlockPos pos, int totalSlots) {
         this.pos = pos;
         this.totalSlots = totalSlots;
      }
   }
}
