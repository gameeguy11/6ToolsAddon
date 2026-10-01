package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

public class MapDuplicator extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgLimits;
   private final Setting<Boolean> showStatus;
   private final Setting<Boolean> silentCrafting;
   private final Setting<Integer> craftingLoops;
   private final Setting<Integer> maxClicksPerSecond;
   private final Setting<Integer> clickDelay;
   private int tickCounter;
   private boolean isCrafting;
   private int mapsToDuplicate;
   private int emptyMapsAvailable;
   private List<CraftingTask> craftingQueue;
   private int currentTaskIndex;
   private int craftingStep;

   public MapDuplicator() {
      super(SixToolsAddon.CATEGORY, "Map Copier", "Automatically duplicates all maps using inventory crafting.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgLimits = this.settings.getDefaultGroup();
      this.showStatus = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-status")).description("Show status messages in chat.")).defaultValue(true)).build());
      this.silentCrafting = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("silent-crafting")).description("Allow crafting without opening inventory (may not work on all servers).")).defaultValue(false)).build());
      this.craftingLoops = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("crafting-loops")).description("Number of times to duplicate each map stack.")).defaultValue(1)).min(1).max(64).sliderMin(1).sliderMax(64).build());
      this.maxClicksPerSecond = this.sgLimits.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("max-clicks-per-second")).description("Maximum clicks per second to avoid server kicks.")).defaultValue(50)).min(1).max(100).build());
      this.clickDelay = this.sgLimits.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("click-delay")).description("Delay between clicks in ticks.")).defaultValue(2)).min(1).max(10).build());
      this.tickCounter = 0;
      this.isCrafting = false;
      this.mapsToDuplicate = 0;
      this.emptyMapsAvailable = 0;
      this.craftingQueue = new ArrayList();
      this.currentTaskIndex = 0;
      this.craftingStep = 0;
   }

   public void onActivate() {
      this.tickCounter = 0;
      this.isCrafting = false;
      this.currentTaskIndex = 0;
      this.craftingStep = 0;
      this.craftingQueue.clear();
      if (!(Boolean)this.silentCrafting.get()) {
         this.mc.execute(() -> {
            if (this.mc.player != null) {
               this.mc.setScreen(new InventoryScreen(this.mc.player));
            }

         });
      }

   }

   public void onDeactivate() {
      this.isCrafting = false;
      this.craftingQueue.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.world != null) {
         ++this.tickCounter;
         if (!this.isCrafting) {
            if (!(Boolean)this.silentCrafting.get() && !(this.mc.player.currentScreenHandler instanceof PlayerScreenHandler) && !(this.mc.player.currentScreenHandler instanceof CraftingScreenHandler)) {
               return;
            }

            this.analyzeInventory();
            int totalEmptyMapsNeeded = this.mapsToDuplicate * (Integer)this.craftingLoops.get();
            if (this.mapsToDuplicate == 0 || this.emptyMapsAvailable < totalEmptyMapsNeeded) {
               if ((Boolean)this.showStatus.get()) {
                  if (this.mapsToDuplicate == 0) {
                     this.info("No maps to duplicate found.", new Object[0]);
                  } else {
                     this.error("Insufficient empty maps! Need " + totalEmptyMapsNeeded + " (" + this.mapsToDuplicate + " stacks × " + String.valueOf(this.craftingLoops.get()) + ") but only have " + this.emptyMapsAvailable + ".", new Object[0]);
                  }
               }

               this.toggle();
               return;
            }

            this.startCrafting();
         }

         if (this.isCrafting && this.tickCounter >= (Integer)this.clickDelay.get()) {
            this.tickCounter = 0;
            this.processCrafting();
         }

      }
   }

   private void analyzeInventory() {
      this.mapsToDuplicate = 0;
      this.emptyMapsAvailable = 0;
      this.craftingQueue.clear();

      for(int i = 0; i < 9; ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.FILLED_MAP && stack.getCount() > 0) {
            ++this.mapsToDuplicate;

            for(int loop = 0; loop < (Integer)this.craftingLoops.get(); ++loop) {
               this.craftingQueue.add(new CraftingTask(SlotUtils.indexToId(i), stack, loop + 1));
            }
         }
      }

      for(int i = 9; i < this.mc.player.getInventory().size(); ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.FILLED_MAP && stack.getCount() > 0) {
            ++this.mapsToDuplicate;

            for(int loop = 0; loop < (Integer)this.craftingLoops.get(); ++loop) {
               this.craftingQueue.add(new CraftingTask(SlotUtils.indexToId(i), stack, loop + 1));
            }
         }
      }

      for(int i = 0; i < 9; ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.MAP) {
            this.emptyMapsAvailable += stack.getCount();
         }
      }

      for(int i = 9; i < this.mc.player.getInventory().size(); ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.MAP) {
            this.emptyMapsAvailable += stack.getCount();
         }
      }

      if (this.mc.player.currentScreenHandler instanceof PlayerScreenHandler || this.mc.player.currentScreenHandler instanceof CraftingScreenHandler) {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (handler instanceof PlayerScreenHandler) {
            for(int i = 1; i <= 4; ++i) {
               try {
                  if (i < handler.slots.size()) {
                     ItemStack stack = handler.getSlot(i).getStack();
                     if (stack.getItem() == Items.MAP) {
                        this.emptyMapsAvailable += stack.getCount();
                     }
                  }
               } catch (Exception var5) {
               }
            }
         } else if (handler instanceof CraftingScreenHandler) {
            for(int i = 1; i <= 9; ++i) {
               try {
                  if (i < handler.slots.size()) {
                     ItemStack stack = handler.getSlot(i).getStack();
                     if (stack.getItem() == Items.MAP) {
                        this.emptyMapsAvailable += stack.getCount();
                     }
                  }
               } catch (Exception var4) {
               }
            }
         }
      }

      int totalEmptyMapsNeeded = this.mapsToDuplicate * (Integer)this.craftingLoops.get();
      if (this.emptyMapsAvailable >= totalEmptyMapsNeeded) {
         if ((Boolean)this.showStatus.get()) {
            this.info("Found " + this.mapsToDuplicate + " filled map stacks and " + this.emptyMapsAvailable + " empty maps. Will duplicate " + this.mapsToDuplicate + " stacks " + String.valueOf(this.craftingLoops.get()) + " times each.", new Object[0]);
         }

      }
   }

   private void startCrafting() {
      if (this.craftingQueue.isEmpty()) {
         if ((Boolean)this.showStatus.get()) {
            this.error("Cannot start crafting: no maps to duplicate.", new Object[0]);
         }

      } else {
         int totalEmptyMapsNeeded = this.mapsToDuplicate * (Integer)this.craftingLoops.get();
         if (this.emptyMapsAvailable < totalEmptyMapsNeeded) {
            if ((Boolean)this.showStatus.get()) {
               this.error("Cannot start crafting: insufficient empty maps. Need " + totalEmptyMapsNeeded + " but only have " + this.emptyMapsAvailable + ".", new Object[0]);
            }

         } else {
            this.currentTaskIndex = 0;
            this.craftingStep = 0;
            this.isCrafting = true;
            if ((Boolean)this.showStatus.get()) {
               int var10001 = this.craftingQueue.size();
               this.info("Starting map duplication process for " + var10001 + " crafting tasks (" + this.mapsToDuplicate + " stacks × " + String.valueOf(this.craftingLoops.get()) + " loops)...", new Object[0]);
            }

         }
      }
   }

   private void processCrafting() {
      if (this.isCrafting && this.currentTaskIndex < this.craftingQueue.size()) {
         if (!(Boolean)this.silentCrafting.get() && !(this.mc.player.currentScreenHandler instanceof PlayerScreenHandler) && !(this.mc.player.currentScreenHandler instanceof CraftingScreenHandler)) {
            if ((Boolean)this.showStatus.get()) {
               this.error("Please open your inventory or a crafting table to continue duplication.", new Object[0]);
            }

            this.finishCrafting();
         } else {
            this.performCraftingStep();
         }
      } else {
         this.finishCrafting();
      }
   }

   private void performCraftingStep() {
      if (this.currentTaskIndex >= this.craftingQueue.size()) {
         this.finishCrafting();
      } else {
         CraftingTask currentTask = (CraftingTask)this.craftingQueue.get(this.currentTaskIndex);
         switch (this.craftingStep) {
            case 0:
               if (this.isValidSlot(currentTask.mapSlot)) {
                  InvUtils.click().slotId(currentTask.mapSlot);
                  if (this.isValidSlot(1)) {
                     InvUtils.click().slotId(1);
                  }

                  this.craftingStep = 1;
               } else {
                  this.info("Invalid source slot: " + currentTask.mapSlot, new Object[0]);
                  this.finishCrafting();
               }
               break;
            case 1:
               int emptyMapSlot = this.findNextEmptyMap();
               if (emptyMapSlot != -1) {
                  if (this.isValidSlot(emptyMapSlot)) {
                     InvUtils.click().slotId(emptyMapSlot);
                     if (this.isValidSlot(2)) {
                        InvUtils.click().slotId(2);
                     }

                     this.craftingStep = 2;
                  } else {
                     this.info("Invalid empty map slot: " + emptyMapSlot, new Object[0]);
                     this.finishCrafting();
                  }
               } else {
                  this.info("No empty maps available", new Object[0]);
                  this.finishCrafting();
               }
               break;
            case 2:
               if (this.isValidSlot(0)) {
                  InvUtils.click().slotId(0);
                  this.craftingStep = 3;
               } else {
                  this.info("Invalid output slot", new Object[0]);
                  this.finishCrafting();
               }
               break;
            case 3:
               if (this.isValidSlot(currentTask.mapSlot)) {
                  InvUtils.click().slotId(currentTask.mapSlot);
                  this.craftingStep = 4;
               } else {
                  this.info("Invalid target slot: " + currentTask.mapSlot, new Object[0]);
                  this.finishCrafting();
               }
               break;
            case 4:
               if (this.isValidSlot(1) && this.isValidSlot(currentTask.mapSlot)) {
                  InvUtils.shiftClick().slotId(1);
               }

               this.craftingStep = 5;
               break;
            case 5:
               CraftingTask nextTask = this.currentTaskIndex + 1 < this.craftingQueue.size() ? (CraftingTask)this.craftingQueue.get(this.currentTaskIndex + 1) : null;
               if ((nextTask == null || nextTask.mapSlot != currentTask.mapSlot) && (Boolean)this.showStatus.get()) {
                  this.info("Map stack completed all " + String.valueOf(this.craftingLoops.get()) + " loop(s)!", new Object[0]);
               }

               ++this.currentTaskIndex;
               this.craftingStep = 0;
         }

      }
   }

   private boolean isValidSlot(int slotId) {
      try {
         if (this.mc.player != null && this.mc.player.currentScreenHandler != null) {
            ScreenHandler handler = this.mc.player.currentScreenHandler;
            if (slotId >= 0 && slotId < handler.slots.size()) {
               return handler.getSlot(slotId) != null;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } catch (Exception var3) {
         return false;
      }
   }

   private void clearCraftingGrid() {
      try {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (handler instanceof PlayerScreenHandler || handler instanceof CraftingScreenHandler) {
            int startSlot = handler instanceof PlayerScreenHandler ? 1 : 1;
            int endSlot = handler instanceof PlayerScreenHandler ? 4 : 9;

            for(int slot = startSlot; slot <= endSlot; ++slot) {
               if (this.isValidSlot(slot) && handler.getSlot(slot).hasStack()) {
                  InvUtils.click().slotId(slot);
                  int emptySlot = this.findEmptyInventorySlot();
                  if (emptySlot >= 0) {
                     int emptySlotId = SlotUtils.indexToId(emptySlot);
                     if (this.isValidSlot(emptySlotId)) {
                        InvUtils.click().slotId(emptySlotId);
                     }
                  }
               }
            }

            if ((Boolean)this.showStatus.get()) {
               this.info("Crafting grid cleared - all items moved to inventory", new Object[0]);
            }
         }
      } catch (Exception e) {
         if ((Boolean)this.showStatus.get()) {
            this.error("Error clearing crafting grid: " + e.getMessage(), new Object[0]);
         }
      }

   }

   private int findEmptyInventorySlot() {
      for(int i = 0; i < this.mc.player.getInventory().size(); ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   private int findNextEmptyMap() {
      for(int i = 0; i < 9; ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.MAP) {
            return SlotUtils.indexToId(i);
         }
      }

      for(int i = 9; i < 36; ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.MAP) {
            return SlotUtils.indexToId(i);
         }
      }

      ScreenHandler handler = this.mc.player.currentScreenHandler;
      if (handler instanceof PlayerScreenHandler) {
         for(int i = 1; i <= 4; ++i) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.getItem() == Items.MAP) {
               return i;
            }
         }
      } else if (handler instanceof CraftingScreenHandler) {
         for(int i = 1; i <= 9; ++i) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.getItem() == Items.MAP) {
               return i;
            }
         }
      }

      return -1;
   }

   private void finishCrafting() {
      this.clearCraftingGrid();
      this.isCrafting = false;
      if ((Boolean)this.showStatus.get()) {
         this.info("Map duplication completed! Duplicated " + this.currentTaskIndex + " maps.", new Object[0]);
      }

      this.toggle();
   }

   public String getInfoString() {
      return this.mapsToDuplicate + "/" + this.emptyMapsAvailable;
   }

   private static class CraftingTask {
      public final int mapSlot;
      public final ItemStack mapStack;
      public final int loop;

      public CraftingTask(int mapSlot, ItemStack mapStack, int loop) {
         this.mapSlot = mapSlot;
         this.mapStack = mapStack;
         this.loop = loop;
      }
   }
}
