package gamerguy11.sixtoolsaddon.modules.chesttracker;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalGetToBlock;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.HopperBlock;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.BarrelBlock;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

public class ChestTrackerModule extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgAutoOpen;
   private final SettingGroup sgRender;
   private final SettingGroup sgFilter;
   private final SettingGroup sgAdvanced;
   private final Setting<Keybind> browserKey;
   private final Setting<Boolean> autoOpenEnabled;
   private final Setting<Boolean> removeDestroyed;
   private final Map<BlockPos, Integer> missingChecks = new HashMap<>();
   private int cleanupTimer;
   private final Setting<Boolean> silentMode;
   private final Setting<Double> autoOpenRange;
   private final Setting<Integer> autoOpenDelay;
   private final Setting<Integer> autoOpenCloseDelay;
   private final Setting<Boolean> autoOpenUseBaritone;
   private final Setting<Double> autoOpenSearchRadius;
   private final Setting<Boolean> renderTracked;
   private final Setting<Boolean> renderSearchResults;
   private final Setting<Integer> renderDistance;
   private final Setting<ShapeMode> shapeMode;
   private final Setting<SettingColor> trackedColor;
   private final Setting<SettingColor> searchColor;
   private final Setting<SettingColor> searchLineColor;
   private final Setting<Boolean> trackChests;
   private final Setting<Boolean> trackBarrels;
   private final Setting<Boolean> trackShulkers;
   private final Setting<Boolean> trackEnderChests;
   private final Setting<Boolean> trackHoppers;
   private final Setting<Boolean> trackDispensers;
   private final Setting<Boolean> trackCopperChests;
   private final Setting<Boolean> debugMode;
   private final ChestTrackerDataV2 data;
   private Item currentSearchItem;
   private BlockPos lastInteractedBlock;
   private boolean awaiting;
   private int awaitingTicks;
   private int tickCounter;
   private BlockPos[] currentOpenPositions;
   private List<TrackedContainer> renderCache;
   private BlockPos baritoneGoalPos;
   private long lastRenderCacheUpdate;
   private boolean shouldAutoClose;
   private boolean silentPending;
   private int ticksUntilClose;
   private static final int AWAITING_TIMEOUT = 40;
   private final Map<BlockPos, Integer> blockedContainers;
   private static final int BLOCKED_COOLDOWN_TICKS = 100;
   private static final int MAX_FAILED_ATTEMPTS = 3;

   public ChestTrackerModule() {
      super(SixToolsAddon.CATEGORY, "chest-tracker", "Track items in containers.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgAutoOpen = this.settings.createGroup("Auto-Open");
      this.sgRender = this.settings.createGroup("Render");
      this.sgFilter = this.settings.createGroup("Filter");
      this.sgAdvanced = this.settings.createGroup("Advanced");
      this.browserKey = this.sgGeneral.add(((KeybindSetting.Builder)((KeybindSetting.Builder)((KeybindSetting.Builder)(new KeybindSetting.Builder()).name("browser-keybind")).description("Open container browser GUI.")).defaultValue(Keybind.fromKey(89))).action(() -> {
         if (this.mc.currentScreen == null) {
            this.mc.setScreen(new ChestTrackerScreen(this));
         }

      }).build());
      this.removeDestroyed = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("remove-destroyed")).description("Forgets tracked containers (and their items) once the block is broken, replaced by another kind of container, or a double chest loses or gains its other half.")).defaultValue(true)).build());
      this.autoOpenEnabled = this.sgAutoOpen.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("auto-open")).description("Automatically open nearby containers.")).defaultValue(false)).build());
      SettingGroup var10001 = this.sgAutoOpen;
      BoolSetting.Builder var10002 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("silent-mode")).description("Auto-opened containers are read in the background: the container screen never appears and your hand doesn't swing. Manually opened containers are unaffected.")).defaultValue(true);
      Setting<Boolean> var10003 = this.autoOpenEnabled;
      Objects.requireNonNull(var10003);
      this.silentMode = var10001.add(((BoolSetting.Builder)var10002.visible(var10003::get)).build());
      var10001 = this.sgAutoOpen;
      DoubleSetting.Builder var5 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("auto-open-range")).description("Range to search for containers to auto-open.")).defaultValue((double)4.0F).min((double)1.0F).max((double)6.0F).sliderRange((double)1.0F, (double)6.0F).decimalPlaces(1);
      var10003 = this.autoOpenEnabled;
      Objects.requireNonNull(var10003);
      this.autoOpenRange = var10001.add(((DoubleSetting.Builder)var5.visible(var10003::get)).build());
      var10001 = this.sgAutoOpen;
      IntSetting.Builder var6 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("auto-open-delay")).description("Delay in ticks between opening containers.")).defaultValue(5)).min(1).max(20).sliderRange(1, 20);
      var10003 = this.autoOpenEnabled;
      Objects.requireNonNull(var10003);
      this.autoOpenDelay = var10001.add(((IntSetting.Builder)var6.visible(var10003::get)).build());
      var10001 = this.sgAutoOpen;
      var6 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("auto-close-delay")).description("Delay in ticks before closing container after opening (allows server to send all contents).")).defaultValue(5)).min(0).max(40).sliderRange(0, 40);
      var10003 = this.autoOpenEnabled;
      Objects.requireNonNull(var10003);
      this.autoOpenCloseDelay = var10001.add(((IntSetting.Builder)var6.visible(var10003::get)).build());
      var10001 = this.sgAutoOpen;
      BoolSetting.Builder var8 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("use-baritone")).description("Use Baritone to walk toward untracked containers outside interaction range.")).defaultValue(false);
      var10003 = this.autoOpenEnabled;
      Objects.requireNonNull(var10003);
      this.autoOpenUseBaritone = var10001.add(((BoolSetting.Builder)var8.visible(var10003::get)).build());
      this.autoOpenSearchRadius = this.sgAutoOpen.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("baritone-search-radius")).description("Radius to search for untracked containers to walk to.")).defaultValue((double)32.0F).min((double)4.0F).max((double)128.0F).sliderRange((double)4.0F, (double)128.0F).decimalPlaces(0).visible(() -> (Boolean)this.autoOpenEnabled.get() && (Boolean)this.autoOpenUseBaritone.get())).build());
      this.renderTracked = this.sgRender.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("render-tracked")).description("Render all tracked containers.")).defaultValue(true)).build());
      this.renderSearchResults = this.sgRender.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("render-search-results")).description("Render search results.")).defaultValue(true)).build());
      this.renderDistance = this.sgRender.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("render-distance")).description("Maximum render distance.")).defaultValue(128)).min(8).max(2048).sliderRange(8, 2048).build());
      this.shapeMode = this.sgRender.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("shape-mode")).description("Render shape mode.")).defaultValue(ShapeMode.Both)).build());
      this.trackedColor = this.sgRender.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("tracked-color")).description("Color for tracked containers.")).defaultValue(new SettingColor(255, 255, 0, 75)).build());
      this.searchColor = this.sgRender.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("search-color")).description("Color for search results.")).defaultValue(new SettingColor(0, 255, 0, 100)).build());
      this.searchLineColor = this.sgRender.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("search-line-color")).description("Line color for search results.")).defaultValue(new SettingColor(0, 255, 0, 255)).build());
      this.trackChests = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("track-chests")).description("Track chests and trapped chests.")).defaultValue(true)).build());
      this.trackBarrels = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("track-barrels")).description("Track barrels.")).defaultValue(true)).build());
      this.trackShulkers = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("track-shulkers")).description("Track shulker boxes.")).defaultValue(true)).build());
      this.trackEnderChests = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("track-ender-chests")).description("Track ender chests.")).defaultValue(false)).build());
      this.trackHoppers = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("track-hoppers")).description("Track hoppers.")).defaultValue(true)).build());
      this.trackDispensers = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("track-dispensers")).description("Track dispensers and droppers.")).defaultValue(true)).build());
      this.trackCopperChests = this.sgFilter.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("track-copper-chests")).description("Track copper chests (for modded servers).")).defaultValue(true)).build());
      this.debugMode = this.sgAdvanced.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("debug")).description("Show debug messages in chat.")).defaultValue(false)).build());
      this.lastInteractedBlock = null;
      this.awaiting = false;
      this.awaitingTicks = 0;
      this.tickCounter = 0;
      this.currentOpenPositions = new BlockPos[2];
      this.renderCache = new ArrayList();
      this.baritoneGoalPos = null;
      this.lastRenderCacheUpdate = 0L;
      this.shouldAutoClose = false;
      this.silentPending = false;
      this.ticksUntilClose = 0;
      this.blockedContainers = new HashMap();
      this.data = new ChestTrackerDataV2();
   }

   public void onActivate() {
      this.data.loadData();
      int count = this.data.getTotalContainerCount();
      if (count > 0 && (Boolean)this.debugMode.get()) {
         this.info("Loaded " + count + " containers", new Object[0]);
      }

      this.resetState();
      this.setupBlockInteractionTracking();
   }

   public void onDeactivate() {
      this.cancelBaritoneGoal();
      this.data.saveData();
   }

   private void resetState() {
      this.lastInteractedBlock = null;
      this.currentSearchItem = null;
      this.awaiting = false;
      this.awaitingTicks = 0;
      this.tickCounter = 0;
      this.currentOpenPositions = new BlockPos[2];
      this.renderCache.clear();
      this.shouldAutoClose = false;
      this.silentPending = false;
      this.ticksUntilClose = 0;
      this.blockedContainers.clear();
      this.missingChecks.clear();
      this.cleanupTimer = 0;
   }

   private void setupBlockInteractionTracking() {
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
         if (!this.isActive()) {
            return ActionResult.PASS;
         } else if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
         } else if (this.mc.player != player) {
            return ActionResult.PASS;
         } else {
            BlockPos pos = hitResult.getBlockPos();
            if (this.mc.world == null) {
               return ActionResult.PASS;
            } else {
               Block block = this.mc.world.getBlockState(pos).getBlock();
               if (this.isTrackableContainer(block)) {
                  this.lastInteractedBlock = pos.toImmutable();
               }

               return ActionResult.PASS;
            }
         }
      });
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.removeDestroyed.get()) {
         ++this.cleanupTimer;
         if (this.cleanupTimer >= 20) {
            this.cleanupTimer = 0;
            this.removeDestroyedContainers();
         }
      }

      if (!this.awaiting) {
         this.silentPending = false;
      }

      if (!this.blockedContainers.isEmpty()) {
         Iterator<Map.Entry<BlockPos, Integer>> it = this.blockedContainers.entrySet().iterator();

         while(it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = (Map.Entry)it.next();
            int ticksRemaining = (Integer)entry.getValue() - 1;
            if (ticksRemaining <= 0) {
               it.remove();
               if ((Boolean)this.debugMode.get()) {
                  this.info("Removed " + ((BlockPos)entry.getKey()).toShortString() + " from blocked list (cooldown expired)", new Object[0]);
               }
            } else {
               entry.setValue(ticksRemaining);
            }
         }
      }

      if (this.shouldAutoClose) {
         if (!this.isInContainerScreen()) {
            this.shouldAutoClose = false;
            this.ticksUntilClose = 0;
            if ((Boolean)this.debugMode.get()) {
               this.info("Container closed manually, cancelling auto-close", new Object[0]);
            }
         } else if (this.ticksUntilClose > 0) {
            --this.ticksUntilClose;
            if (this.ticksUntilClose % 5 == 0 && (Boolean)this.debugMode.get()) {
               this.info("Auto-close countdown: " + this.ticksUntilClose + " ticks remaining", new Object[0]);
            }

            if (this.ticksUntilClose == 0) {
               this.shouldAutoClose = false;
               if (this.mc.player != null) {
                  this.mc.player.closeHandledScreen();
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Auto-closed container after " + String.valueOf(this.autoOpenCloseDelay.get()) + " tick delay", new Object[0]);
                  }
               }
            }
         }
      }

      if (this.awaiting) {
         if (this.isInContainerScreen()) {
            ++this.awaitingTicks;
            if (this.awaitingTicks > 5) {
               if ((Boolean)this.debugMode.get()) {
                  this.info("InventoryEvent didn't fire - manually processing container", new Object[0]);
               }

               ScreenHandler handler = this.mc.player.currentScreenHandler;
               if (handler != null && this.currentOpenPositions[0] != null) {
                  BlockPos trackPos = this.currentOpenPositions[0];
                  this.awaiting = false;
                  this.awaitingTicks = 0;
                  this.blockedContainers.remove(trackPos);
                  if (this.currentOpenPositions[1] != null) {
                     this.blockedContainers.remove(this.currentOpenPositions[1]);
                  }

                  List<ItemStack> items = new ArrayList();
                  List<ItemStack> topLevelItems = new ArrayList();
                  int containerSlots = handler.slots.size() - 36;

                  for(int i = 0; i < containerSlots && i < handler.slots.size(); ++i) {
                     Slot slot = (Slot)handler.slots.get(i);
                     ItemStack stack = slot.getStack();
                     if (!stack.isEmpty()) {
                        items.add(stack.copy());
                        topLevelItems.add(stack.copy());
                        if (Utils.hasItems(stack)) {
                           ItemStack[] nestedItems = new ItemStack[27];
                           Utils.getItemsInContainerItem(stack, nestedItems);

                           for(ItemStack nestedItem : nestedItems) {
                              if (nestedItem != null && !nestedItem.isEmpty()) {
                                 items.add(nestedItem.copy());
                              }
                           }
                        }
                     }
                  }

                  String currentDim = this.getCurrentDimension();
                  String containerType = this.getContainerType(trackPos);
                  this.data.trackContainer(trackPos, currentDim, containerType, items, topLevelItems);
                  this.markShape(trackPos, currentDim);
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Manually tracked " + containerType + " (" + items.size() + " items)", new Object[0]);
                  }

                  int closeDelay = (Integer)this.autoOpenCloseDelay.get();
                  if (closeDelay == 0) {
                     this.mc.player.closeHandledScreen();
                     if ((Boolean)this.debugMode.get()) {
                        this.info("Closed immediately (0 tick delay)", new Object[0]);
                     }
                  } else {
                     this.shouldAutoClose = true;
                     this.ticksUntilClose = closeDelay;
                     if ((Boolean)this.debugMode.get()) {
                        this.info("Set shouldAutoClose=true, ticksUntilClose=" + closeDelay, new Object[0]);
                     }
                  }

                  this.currentOpenPositions = new BlockPos[2];
               } else {
                  this.awaiting = false;
                  this.awaitingTicks = 0;
                  this.currentOpenPositions = new BlockPos[2];
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Reset awaiting flag (can't process inventory)", new Object[0]);
                  }
               }
            }
         } else {
            ++this.awaitingTicks;
            if (this.awaitingTicks > 40) {
               if (this.currentOpenPositions[0] != null) {
                  this.blockedContainers.put(this.currentOpenPositions[0], 100);
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Container at " + this.currentOpenPositions[0].toShortString() + " failed to open (timeout), adding to blocked list", new Object[0]);
                  }
               }

               this.awaiting = false;
               this.awaitingTicks = 0;
               this.currentOpenPositions = new BlockPos[2];
               if ((Boolean)this.debugMode.get()) {
                  this.info("Reset awaiting flag (timeout - container never opened or closed without inventory event)", new Object[0]);
               }
            }
         }
      }

      if (this.isInContainerScreen()) {
         this.cancelBaritoneGoal();
      } else {
         if (!(Boolean)this.autoOpenEnabled.get() || !(Boolean)this.autoOpenUseBaritone.get()) {
            this.cancelBaritoneGoal();
            if (!(Boolean)this.autoOpenEnabled.get()) {
               return;
            }
         }

         if (this.awaiting) {
            this.cancelBaritoneGoal();
         } else if (this.tickCounter < (Integer)this.autoOpenDelay.get()) {
            ++this.tickCounter;
         } else {
            this.tickCounter = 0;
            int range = (int)Math.ceil((Double)this.autoOpenRange.get());
            BlockPos playerPos = this.mc.player.getBlockPos();
            String currentDim = this.getCurrentDimension();

            for(int x = -range; x <= range; ++x) {
               for(int y = -range; y <= range; ++y) {
                  for(int z = -range; z <= range; ++z) {
                     BlockPos blockPos = playerPos.add(x, y, z);
                     double distSq = this.mc.player.squaredDistanceTo((double)blockPos.getX() + (double)0.5F, (double)blockPos.getY() + (double)0.5F, (double)blockPos.getZ() + (double)0.5F);
                     double maxDistSq = (Double)this.autoOpenRange.get() * (Double)this.autoOpenRange.get();
                     if (!(distSq > maxDistSq)) {
                        BlockState blockState = this.mc.world.getBlockState(blockPos);
                        Block block = blockState.getBlock();
                        if (this.isTrackableContainer(block)) {
                           boolean isAlreadyTracked = this.data.getContainer(blockPos, currentDim) != null;
                           if (!isAlreadyTracked && block instanceof ChestBlock) {
                              ChestType chestType = (ChestType)blockState.get(ChestBlock.CHEST_TYPE);
                              if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                                 Direction facing = (Direction)blockState.get(ChestBlock.FACING);
                                 BlockPos otherHalf = blockPos.offset(chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise());
                                 if (this.data.getContainer(otherHalf, currentDim) != null) {
                                    isAlreadyTracked = true;
                                 }
                              }
                           }

                           if (!this.blockedContainers.containsKey(blockPos) && !isAlreadyTracked) {
                              Vec3d vec = new Vec3d((double)blockPos.getX() + (double)0.5F, (double)blockPos.getY() + (double)0.5F, (double)blockPos.getZ() + (double)0.5F);
                              BlockHitResult hitResult = new BlockHitResult(vec, Direction.UP, blockPos, false);
                              ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
                              if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
                                 this.cancelBaritoneGoal();
                                 this.blockedContainers.remove(blockPos);
                                 this.awaiting = true;
                                 this.silentPending = (Boolean)this.silentMode.get();
                                 this.awaitingTicks = 0;
                                 this.currentOpenPositions[0] = blockPos.toImmutable();
                                 this.currentOpenPositions[1] = null;
                                 if (block instanceof ChestBlock) {
                                    ChestType chestType = (ChestType)blockState.get(ChestBlock.CHEST_TYPE);
                                    if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                                       Direction facing = (Direction)blockState.get(ChestBlock.FACING);
                                       BlockPos otherPos = blockPos.offset(chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise());
                                       this.currentOpenPositions[1] = otherPos;
                                    }
                                 }

                                 if (!(Boolean)this.silentMode.get()) {
                                    this.mc.player.swingHand(Hand.MAIN_HAND);
                                 }

                                 if ((Boolean)this.debugMode.get()) {
                                    this.info("Auto-opening container at " + blockPos.toShortString(), new Object[0]);
                                 }

                                 return;
                              }

                              if (result == ActionResult.FAIL) {
                                 this.blockedContainers.put(blockPos.toImmutable(), 100);
                                 if ((Boolean)this.debugMode.get()) {
                                    this.info("Container at " + blockPos.toShortString() + " is blocked, adding to cooldown list", new Object[0]);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if ((Boolean)this.autoOpenUseBaritone.get()) {
               this.walkToNearestUntrackedContainer(playerPos, currentDim);
            }

         }
      }
   }

   private void walkToNearestUntrackedContainer(BlockPos playerPos, String currentDim) {
      int searchRange = (int)Math.ceil((Double)this.autoOpenSearchRadius.get());
      double searchMaxDistSq = (Double)this.autoOpenSearchRadius.get() * (Double)this.autoOpenSearchRadius.get();
      BlockPos nearest = null;
      double nearestDistSq = Double.MAX_VALUE;

      for(int x = -searchRange; x <= searchRange; ++x) {
         for(int y = -searchRange; y <= searchRange; ++y) {
            for(int z = -searchRange; z <= searchRange; ++z) {
               BlockPos blockPos = playerPos.add(x, y, z);
               double distSq = this.mc.player.squaredDistanceTo((double)blockPos.getX() + (double)0.5F, (double)blockPos.getY() + (double)0.5F, (double)blockPos.getZ() + (double)0.5F);
               if (!(distSq > searchMaxDistSq) && !this.blockedContainers.containsKey(blockPos)) {
                  BlockState blockState = this.mc.world.getBlockState(blockPos);
                  Block block = blockState.getBlock();
                  if (this.isTrackableContainer(block) && this.data.getContainer(blockPos, currentDim) == null && distSq < nearestDistSq) {
                     nearestDistSq = distSq;
                     nearest = blockPos.toImmutable();
                  }
               }
            }
         }
      }

      if (nearest == null) {
         this.cancelBaritoneGoal();
      } else {
         if (this.baritoneGoalPos != null && this.baritoneGoalPos.equals(nearest)) {
            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
               GoalGetToBlock goal = new GoalGetToBlock(nearest);
               BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            }
         } else {
            this.baritoneGoalPos = nearest;
            GoalGetToBlock goal = new GoalGetToBlock(nearest);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            if ((Boolean)this.debugMode.get()) {
               this.info("Baritone walking to untracked container at " + nearest.toShortString(), new Object[0]);
            }
         }

      }
   }

   private void cancelBaritoneGoal() {
      if (this.baritoneGoalPos != null) {
         if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
         }

         this.baritoneGoalPos = null;
      }
   }

   @EventHandler
   private void onInventory(InventoryEvent event) {
      if (this.isActive()) {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (handler != null) {
            BlockPos trackPos = this.currentOpenPositions[0];
            if (trackPos == null) {
               trackPos = this.lastInteractedBlock;
            }

            if (trackPos == null) {
               this.awaiting = false;
               this.awaitingTicks = 0;
            } else {
               boolean wasAutoOpened = this.awaiting;
               this.awaiting = false;
               this.awaitingTicks = 0;
               this.blockedContainers.remove(trackPos);
               if (this.currentOpenPositions[1] != null) {
                  this.blockedContainers.remove(this.currentOpenPositions[1]);
               }

               if ((Boolean)this.debugMode.get()) {
                  this.info("Inventory event fired - wasAutoOpened: " + wasAutoOpened, new Object[0]);
               }

               List<ItemStack> items = new ArrayList();
               List<ItemStack> topLevelItems = new ArrayList();
               int containerSlots = handler.slots.size() - 36;

               for(int i = 0; i < containerSlots && i < handler.slots.size(); ++i) {
                  Slot slot = (Slot)handler.slots.get(i);
                  ItemStack stack = slot.getStack();
                  if (!stack.isEmpty()) {
                     items.add(stack.copy());
                     topLevelItems.add(stack.copy());
                     if (Utils.hasItems(stack)) {
                        ItemStack[] nestedItems = new ItemStack[27];
                        Utils.getItemsInContainerItem(stack, nestedItems);

                        for(ItemStack nestedItem : nestedItems) {
                           if (nestedItem != null && !nestedItem.isEmpty()) {
                              items.add(nestedItem.copy());
                           }
                        }
                     }
                  }
               }

               String currentDim = this.getCurrentDimension();
               String containerType = this.getContainerType(trackPos);
               this.data.trackContainer(trackPos, currentDim, containerType, items, topLevelItems);
               this.markShape(trackPos, currentDim);
               if ((Boolean)this.debugMode.get()) {
                  this.info("Tracked " + containerType + " (" + items.size() + " items)", new Object[0]);
               }

               if (wasAutoOpened) {
                  int closeDelay = (Integer)this.autoOpenCloseDelay.get();
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Scheduling auto-close with delay: " + closeDelay + " ticks", new Object[0]);
                  }

                  if (closeDelay == 0) {
                     this.mc.player.closeHandledScreen();
                     if ((Boolean)this.debugMode.get()) {
                        this.info("Closed immediately (0 tick delay)", new Object[0]);
                     }
                  } else {
                     this.shouldAutoClose = true;
                     this.ticksUntilClose = closeDelay;
                     if ((Boolean)this.debugMode.get()) {
                        this.info("Set shouldAutoClose=true, ticksUntilClose=" + closeDelay, new Object[0]);
                     }
                  }
               }

               this.lastInteractedBlock = null;
               this.currentOpenPositions = new BlockPos[2];
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         if ((Boolean)this.renderTracked.get() || (Boolean)this.renderSearchResults.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - this.lastRenderCacheUpdate > 1000L) {
               String currentDim = this.getCurrentDimension();
               this.renderCache = new ArrayList(this.data.getAllContainers(currentDim));
               this.lastRenderCacheUpdate = currentTime;
            }

            double maxDist = (double)(Integer)this.renderDistance.get();
            double maxDistSq = maxDist * maxDist;

            for(TrackedContainer container : this.renderCache) {
               BlockPos pos = container.getPosition();
               double distSq = this.mc.player.squaredDistanceTo((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)0.5F, (double)pos.getZ() + (double)0.5F);
               if (!(distSq > maxDistSq)) {
                  boolean isSearchResult = this.currentSearchItem != null && container.containsItem(this.currentSearchItem);
                  boolean shouldRender = false;
                  SettingColor sideCol = null;
                  SettingColor lineCol = null;
                  if (isSearchResult && (Boolean)this.renderSearchResults.get()) {
                     shouldRender = true;
                     sideCol = (SettingColor)this.searchColor.get();
                     lineCol = (SettingColor)this.searchLineColor.get();
                  } else if ((Boolean)this.renderTracked.get()) {
                     shouldRender = true;
                     sideCol = (SettingColor)this.trackedColor.get();
                     lineCol = (SettingColor)this.trackedColor.get();
                  }

                  if (shouldRender) {
                     event.renderer.box(pos, sideCol, lineCol, (ShapeMode)this.shapeMode.get(), 0);
                     BlockPos otherHalf = this.findDoubleChestOtherHalf(pos);
                     if (otherHalf != null) {
                        event.renderer.box(otherHalf, sideCol, lineCol, (ShapeMode)this.shapeMode.get(), 0);
                     }
                  }
               }
            }

         }
      }
   }

   private BlockPos findDoubleChestOtherHalf(BlockPos pos) {
      if (this.mc.world == null) {
         return null;
      } else {
         BlockState state = this.mc.world.getBlockState(pos);
         Block block = state.getBlock();
         if (!(block instanceof ChestBlock) && !(block instanceof TrappedChestBlock)) {
            return null;
         } else {
            try {
               if (state.contains(ChestBlock.CHEST_TYPE)) {
                  ChestType chestType = (ChestType)state.get(ChestBlock.CHEST_TYPE);
                  if (chestType == ChestType.SINGLE) {
                     return null;
                  }

                  if (state.contains(ChestBlock.FACING)) {
                     Direction facing = (Direction)state.get(ChestBlock.FACING);
                     BlockPos otherPos = chestType == ChestType.LEFT ? pos.offset(facing.rotateYClockwise()) : pos.offset(facing.rotateYCounterclockwise());
                     BlockState otherState = this.mc.world.getBlockState(otherPos);
                     if (otherState.getBlock().getClass() == block.getClass()) {
                        return otherPos;
                     }
                  }
               }
            } catch (Exception var8) {
            }

            return null;
         }
      }
   }

   public WWidget getWidget(GuiTheme theme) {
      WTable table = theme.table();
      WButton openBrowser = (WButton)table.add(theme.button("Open Browser (" + String.valueOf(this.browserKey.get()) + ")")).expandX().widget();
      openBrowser.action = () -> this.mc.setScreen(new ChestTrackerScreen(this));
      table.row();
      WButton searchHeld = (WButton)table.add(theme.button("Search Held Item")).expandX().widget();
      searchHeld.action = this::searchHeldItem;
      table.row();
      WButton clearSearch = (WButton)table.add(theme.button("Clear Search")).expandX().widget();
      clearSearch.action = () -> {
         this.currentSearchItem = null;
         if ((Boolean)this.debugMode.get()) {
            this.info("Search cleared", new Object[0]);
         }

      };
      table.row();
      WButton saveData = (WButton)table.add(theme.button("Save Data")).expandX().widget();
      saveData.action = () -> this.data.saveData();
      table.row();
      WButton clearAll = (WButton)table.add(theme.button("Clear All Data")).expandX().widget();
      clearAll.action = () -> {
         this.data.clearAll();
         if ((Boolean)this.debugMode.get()) {
            this.info("All data cleared", new Object[0]);
         }

      };
      return table;
   }

   private void searchHeldItem() {
      if (this.mc.player != null) {
         ItemStack held = this.mc.player.getMainHandStack();
         if (held.isEmpty()) {
            if ((Boolean)this.debugMode.get()) {
               this.warning("No item in hand", new Object[0]);
            }

         } else {
            this.currentSearchItem = held.getItem();
            List<TrackedContainer> results = this.data.searchItem(this.currentSearchItem);
            if ((Boolean)this.debugMode.get()) {
               this.info("Found " + results.size() + " containers with " + this.currentSearchItem.getName().getString(), new Object[0]);
            }

         }
      }
   }

   private boolean isContainerBlock(Block block) {
      return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock || block instanceof EnderChestBlock || block instanceof HopperBlock || block instanceof DispenserBlock;
   }

   private Boolean chestShape(BlockPos pos) {
      if (this.mc.world == null) {
         return null;
      }

      BlockState state = this.mc.world.getBlockState(pos);
      if (state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE)) {
         return state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE;
      }

      return null;
   }

   private void markShape(BlockPos pos, String dimension) {
      TrackedContainer container = this.data.getContainer(pos, dimension);
      if (container != null) {
         container.setDoubleChest(this.chestShape(pos));
      }
   }

   private boolean isOutdated(TrackedContainer container, BlockPos pos) {
      if (container.isTypeKnown() && !container.getContainerType().equals(this.getContainerType(pos))) {
         return true;
      }

      Boolean wasDouble = container.getDoubleChest();
      Boolean isDouble = this.chestShape(pos);
      return wasDouble != null && isDouble != null && !wasDouble.equals(isDouble);
   }

   private void removeDestroyedContainers() {
      if (this.mc.world == null || this.mc.player == null) {
         return;
      }

      String dim = this.getCurrentDimension();
      int removed = 0;

      for (TrackedContainer container : this.data.getAllContainers(dim)) {
         BlockPos pos = container.getPosition();
         if (!this.mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            this.missingChecks.remove(pos);
            continue;
         }

         if (this.isContainerBlock(this.mc.world.getBlockState(pos).getBlock()) && !this.isOutdated(container, pos)) {
            this.missingChecks.remove(pos);
            continue;
         }

         int misses = this.missingChecks.merge(pos, 1, Integer::sum);
         if (misses >= 2) {
            this.missingChecks.remove(pos);
            if (this.data.removeContainer(pos, dim)) {
               ++removed;
            }
         }
      }

      if (removed > 0) {
         this.lastRenderCacheUpdate = 0L;
         if (this.debugMode.get()) {
            this.info("Removed " + removed + " destroyed container(s) from tracking", new Object[0]);
         }
      }
   }

   private boolean isTrackableContainer(Block block) {
      if (!(block instanceof ChestBlock) && !(block instanceof TrappedChestBlock)) {
         if (block instanceof BarrelBlock) {
            return (Boolean)this.trackBarrels.get();
         } else if (block instanceof ShulkerBoxBlock) {
            return (Boolean)this.trackShulkers.get();
         } else if (block instanceof EnderChestBlock) {
            return (Boolean)this.trackEnderChests.get();
         } else if (block instanceof HopperBlock) {
            return (Boolean)this.trackHoppers.get();
         } else {
            return !(block instanceof DispenserBlock) && !(block instanceof DropperBlock) ? false : (Boolean)this.trackDispensers.get();
         }
      } else {
         return block != Blocks.COPPER_CHEST && block != Blocks.EXPOSED_COPPER_CHEST && block != Blocks.WEATHERED_COPPER_CHEST && block != Blocks.OXIDIZED_COPPER_CHEST && block != Blocks.WAXED_COPPER_CHEST && block != Blocks.WAXED_EXPOSED_COPPER_CHEST && block != Blocks.WAXED_WEATHERED_COPPER_CHEST && block != Blocks.WAXED_OXIDIZED_COPPER_CHEST ? (Boolean)this.trackChests.get() : (Boolean)this.trackCopperChests.get();
      }
   }

   private String getContainerType(BlockPos pos) {
      if (this.mc.world == null) {
         return "container";
      } else {
         Block block = this.mc.world.getBlockState(pos).getBlock();
         if (block != Blocks.COPPER_CHEST && block != Blocks.EXPOSED_COPPER_CHEST && block != Blocks.WEATHERED_COPPER_CHEST && block != Blocks.OXIDIZED_COPPER_CHEST && block != Blocks.WAXED_COPPER_CHEST && block != Blocks.WAXED_EXPOSED_COPPER_CHEST && block != Blocks.WAXED_WEATHERED_COPPER_CHEST && block != Blocks.WAXED_OXIDIZED_COPPER_CHEST) {
            if (block instanceof TrappedChestBlock) {
               return "trapped_chest";
            } else if (block instanceof ChestBlock) {
               return "chest";
            } else if (block instanceof BarrelBlock) {
               return "barrel";
            } else if (block instanceof ShulkerBoxBlock) {
               return "shulker_box";
            } else if (block instanceof EnderChestBlock) {
               return "ender_chest";
            } else if (block instanceof HopperBlock) {
               return "hopper";
            } else if (block instanceof DispenserBlock) {
               return "dispenser";
            } else {
               return block instanceof DropperBlock ? "dropper" : "container";
            }
         } else {
            return "copper_chest";
         }
      }
   }

   private boolean isInContainerScreen() {
      if (this.mc.player == null) {
         return false;
      } else if (this.mc.currentScreen == null && !(Boolean)this.silentMode.get()) {
         return false;
      } else {
         return this.mc.player.currentScreenHandler != this.mc.player.playerScreenHandler;
      }
   }

   public boolean shouldSuppressScreen(Screen screen) {
      if (this.isActive() && (Boolean)this.silentMode.get() && this.silentPending) {
         return screen instanceof HandledScreen && !(screen instanceof InventoryScreen) && !(screen instanceof CreativeInventoryScreen);
      } else {
         return false;
      }
   }

   private String getCurrentDimension() {
      return this.mc.world == null ? "unknown" : this.mc.world.getRegistryKey().getValue().toString();
   }

   public Item getCurrentSearchItem() {
      return this.currentSearchItem;
   }

   public void setCurrentSearchItem(Item item) {
      this.currentSearchItem = item;
   }

   public ChestTrackerDataV2 getData() {
      return this.data;
   }

   public void refreshTrackedContainer(BlockPos pos, ScreenHandler handler) {
      if (pos != null && handler != null && this.mc.world != null) {
         List<ItemStack> contents = new ArrayList();
         List<ItemStack> topLevelContents = new ArrayList();
         int containerSlots = Math.max(0, handler.slots.size() - 36);

         for(int slotIndex = 0; slotIndex < containerSlots; ++slotIndex) {
            ItemStack stack = ((Slot)handler.slots.get(slotIndex)).getStack();
            if (!stack.isEmpty()) {
               topLevelContents.add(stack.copy());
               contents.add(stack.copy());
               if (Utils.hasItems(stack)) {
                  ItemStack[] nestedItems = new ItemStack[27];
                  Utils.getItemsInContainerItem(stack, nestedItems);

                  for(ItemStack nestedItem : nestedItems) {
                     if (nestedItem != null && !nestedItem.isEmpty()) {
                        contents.add(nestedItem.copy());
                     }
                  }
               }
            }
         }

         this.data.trackContainer(pos, this.getCurrentDimension(), this.getContainerType(pos), contents, topLevelContents);
         this.markShape(pos, this.getCurrentDimension());
      }
   }

   public void searchItem(Item item) {
      this.currentSearchItem = item;
   }

   public double getRenderDistance() {
      return (double)(Integer)this.renderDistance.get();
   }
}
