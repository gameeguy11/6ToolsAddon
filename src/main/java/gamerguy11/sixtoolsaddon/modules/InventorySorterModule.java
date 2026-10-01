package gamerguy11.sixtoolsaddon.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public class InventorySorterModule extends Module {
   private static final Path SAVE_FILE = FabricLoader.getInstance().getConfigDir().resolve("inventory-sorter").resolve("inventories.json");
   private final SettingGroup sgGeneral;
   private final Setting<Boolean> chatNotify;
   private final Setting<Integer> tickRate;
   private final Setting<Boolean> autoDisable;
   private final SettingGroup sgAutoLoot;
   private final Setting<AutoLootMode> autoLootMode;
   private final Setting<String> rekitTarget;
   private final Gson gson;
   private final HashMap<String, HashMap<Integer, Item>> inventories;
   private final ArrayDeque<SlotMove> jobs;
   private int ticks;
   private boolean isSorted;
   private boolean retriedThisPass;
   private String activeInventoryKey;
   private final ArrayDeque<Integer> lootJobs;
   private ScreenHandler lastSeenHandler;
   private boolean pendingRekitSort;
   private boolean autoLootSort;

   public InventorySorterModule() {
      super(SixToolsAddon.CATEGORY, "inventory-sorter", "Auto-sorts your inventory back into a saved inventory layout.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.chatNotify = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("chat-notify")).description("Sends a chat message when an inventory is saved or finishes sorting.")).defaultValue(true)).build());
      this.tickRate = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("tick-rate")).description("Ticks to wait between each slot move. Higher is slower but less likely to trip anti-cheat.")).defaultValue(2)).range(1, 20).sliderRange(1, 20).build());
      this.autoDisable = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("auto-disable")).description("Turns the module off by itself once the inventory finishes sorting, instead of continuing to watch for changes.")).defaultValue(true)).build());
      this.sgAutoLoot = this.settings.createGroup("Auto-Loot");
      this.autoLootMode = this.sgAutoLoot.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("auto-loot")).description("Off: does nothing extra. Refill: whenever you open storage while this module is active, takes any items you're already carrying, topping up what you have. Rekit: takes items belonging to the chosen saved inventory below from any storage you open, then arranges your inventory into that layout once you close it.")).defaultValue(InventorySorterModule.AutoLootMode.Off)).build());
      this.rekitTarget = this.sgAutoLoot.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("rekit-inventory")).description("Name of the saved inventory (see the inventories list) to pull items for and arrange into, when auto-loot is set to Rekit.")).defaultValue("")).visible(() -> this.autoLootMode.get() == InventorySorterModule.AutoLootMode.Rekit)).build());
      this.gson = (new GsonBuilder()).setPrettyPrinting().create();
      this.inventories = new HashMap();
      this.jobs = new ArrayDeque();
      this.ticks = 0;
      this.isSorted = true;
      this.retriedThisPass = false;
      this.activeInventoryKey = null;
      this.lootJobs = new ArrayDeque();
      this.lastSeenHandler = null;
      this.pendingRekitSort = false;
      this.autoLootSort = false;
      this.loadFromDisk();
   }

   public void onActivate() {
      this.loadFromDisk();
   }

   public void onDeactivate() {
      this.ticks = 0;
      this.jobs.clear();
      this.isSorted = true;
      this.retriedThisPass = false;
      this.lootJobs.clear();
      this.lastSeenHandler = null;
      this.pendingRekitSort = false;
      this.autoLootSort = false;
   }

   public void notifyInfo(String message, Object... args) {
      this.info(message, args);
   }

   public void notifyError(String message, Object... args) {
      this.error(message, args);
   }

   public void notifySaveResult(boolean overwritten, String name) {
      if ((Boolean)this.chatNotify.get()) {
         this.info(overwritten ? "Overwrote inventory (highlight)%s(default)." : "Saved inventory (highlight)%s(default).", new Object[]{name});
      }
   }

   public boolean hasInventory(String name) {
      return this.inventories.containsKey(name);
   }

   public List<String> inventoryNames() {
      return new ArrayList(this.inventories.keySet());
   }

   public void deleteInventory(String name) {
      this.inventories.remove(name);
      this.saveToDisk();
   }

   public void clearInventories() {
      this.inventories.clear();
      this.saveToDisk();
   }

   public boolean saveInventory(String name) {
      if (this.mc.player == null) {
         return false;
      } else {
         ScreenHandler var3 = this.mc.player.currentScreenHandler;
         if (var3 instanceof PlayerScreenHandler) {
            PlayerScreenHandler handler = (PlayerScreenHandler)var3;
            HashMap var6 = new HashMap();

            for(int slot = 5; slot < handler.slots.size(); ++slot) {
               ItemStack stack = handler.getSlot(slot).getStack();
               if (!stack.isEmpty() && !stack.isOf(Items.AIR)) {
                  var6.put(slot, stack.getItem());
               }
            }

            this.inventories.put(name, var6);
            this.saveToDisk();
            return true;
         } else {
            return false;
         }
      }
   }

   public void loadInventory(String name) {
      this.loadInventory(name, true);
   }

   private void loadInventory(String name, boolean resetRetry) {
      if (this.mc.player != null) {
         ScreenHandler var4 = this.mc.player.currentScreenHandler;
         if (var4 instanceof PlayerScreenHandler) {
            PlayerScreenHandler handler = (PlayerScreenHandler)var4;
            HashMap<Integer, Item> inventory = (HashMap)this.inventories.get(name);
            if (inventory != null && !inventory.isEmpty()) {
               this.jobs.clear();
               this.activeInventoryKey = name;
               if (resetRetry) {
                  this.retriedThisPass = false;
               }

               this.isSorted = false;
               List<Integer> settled = new ArrayList();
               HashMap<Integer, ItemStack> pendingStacks = new HashMap();

               for(int to = 5; to < handler.slots.size(); ++to) {
                  Item wanted = (Item)inventory.get(to);
                  if (wanted != null) {
                     ItemStack current = pendingStacks.containsKey(to) ? (ItemStack)pendingStacks.get(to) : handler.getSlot(to).getStack();
                     if (current.isOf(wanted)) {
                        settled.add(to);
                     } else {
                        for(int from = 5; from < handler.slots.size(); ++from) {
                           if (from != to && !settled.contains(from)) {
                              ItemStack occupying = pendingStacks.containsKey(from) ? (ItemStack)pendingStacks.get(from) : handler.getSlot(from).getStack();
                              if (occupying.isOf(wanted)) {
                                 if (inventory.get(from) == null || !occupying.isOf((Item)inventory.get(from))) {
                                    if (!current.isEmpty()) {
                                       settled.add(to);
                                       pendingStacks.put(from, current);
                                    } else {
                                       settled.add(to);
                                       settled.add(from);
                                       pendingStacks.remove(from);
                                    }

                                    this.jobs.addLast(new SlotMove(from, to));
                                    break;
                                 }

                                 settled.add(from);
                              }
                           }
                        }
                     }
                  }
               }

            } else {
               this.error("No inventory named (highlight)%s(default) is saved.", new Object[]{name});
            }
         }
      }
   }

   private boolean isFullySorted(String name) {
      if (!this.inventories.isEmpty() && name != null) {
         if (this.mc.player == null) {
            return true;
         } else if (!this.inventories.containsKey(name)) {
            return true;
         } else {
            ScreenHandler var3 = this.mc.player.currentScreenHandler;
            if (var3 instanceof PlayerScreenHandler) {
               PlayerScreenHandler handler = (PlayerScreenHandler)var3;
               HashMap var6 = (HashMap)this.inventories.get(name);

               for(int slot = 5; slot < handler.slots.size(); ++slot) {
                  Item wanted = (Item)var6.get(slot);
                  if (wanted != null && !handler.getSlot(slot).getStack().isOf(wanted)) {
                     return false;
                  }
               }

               return true;
            } else {
               return true;
            }
         }
      } else {
         return true;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null) {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (handler != this.lastSeenHandler) {
            this.lastSeenHandler = handler;
            if (this.isContainerHandler(handler) && this.autoLootMode.get() != InventorySorterModule.AutoLootMode.Off) {
               this.queueAutoLoot(handler);
            } else if (handler instanceof PlayerScreenHandler && this.pendingRekitSort) {
               this.pendingRekitSort = false;
               this.autoLootSort = true;
               this.loadInventory((String)this.rekitTarget.get());
            }
         }

         ++this.ticks;
         if (this.ticks >= (Integer)this.tickRate.get()) {
            this.ticks = 0;
            if (!this.lootJobs.isEmpty()) {
               if (this.isContainerHandler(handler)) {
                  InvUtils.shiftClick().slotId((Integer)this.lootJobs.removeFirst());
                  if (this.lootJobs.isEmpty()) {
                     this.onLootDrained();
                  }
               } else {
                  this.lootJobs.clear();
               }

            } else if (handler instanceof PlayerScreenHandler) {
               if (!this.jobs.isEmpty()) {
                  this.isSorted = false;
                  SlotMove job = (SlotMove)this.jobs.removeFirst();
                  InvUtils.move().fromId(job.from()).toId(job.to());
               } else if (!this.isSorted) {
                  if (!this.isFullySorted(this.activeInventoryKey)) {
                     if (!this.retriedThisPass) {
                        this.retriedThisPass = true;
                        this.loadInventory(this.activeInventoryKey, false);
                     } else {
                        this.isSorted = true;
                        this.retriedThisPass = false;
                        if (this.autoLootSort) {
                           this.autoLootSort = false;
                        } else {
                           this.autoDisableIfEnabled();
                        }

                     }
                  } else {
                     this.isSorted = true;
                     this.retriedThisPass = false;
                     if ((Boolean)this.chatNotify.get()) {
                        this.info("Inventory (highlight)%s(default) sorted.", new Object[]{this.activeInventoryKey});
                     }

                     if (this.autoLootSort) {
                        this.autoLootSort = false;
                     } else {
                        this.autoDisableIfEnabled();
                     }

                  }
               }
            }
         }
      }
   }

   private boolean isContainerHandler(ScreenHandler handler) {
      return handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler;
   }

   private void queueAutoLoot(ScreenHandler handler) {
      this.lootJobs.clear();
      Set<Item> wanted = this.autoLootMode.get() == InventorySorterModule.AutoLootMode.Rekit ? this.rekitWantedItems() : this.refillWantedItems();
      if (wanted != null && !wanted.isEmpty()) {
         for(Slot slot : handler.slots) {
            if (!(slot.inventory instanceof PlayerInventory)) {
               ItemStack stack = slot.getStack();
               if (!stack.isEmpty() && wanted.contains(stack.getItem())) {
                  this.lootJobs.addLast(slot.id);
               }
            }
         }

      }
   }

   private Set<Item> refillWantedItems() {
      Set<Item> wanted = new HashSet();

      for(int i = 0; i < 36; ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (!stack.isEmpty()) {
            wanted.add(stack.getItem());
         }
      }

      return wanted;
   }

   private Set<Item> rekitWantedItems() {
      if (((String)this.rekitTarget.get()).isBlank()) {
         this.error("Set a (highlight)rekit-inventory(default) name in the module settings.", new Object[0]);
         return null;
      } else {
         HashMap<Integer, Item> kit = (HashMap)this.inventories.get(this.rekitTarget.get());
         if (kit != null && !kit.isEmpty()) {
            return new HashSet(kit.values());
         } else {
            this.error("No saved inventory named (highlight)%s(default) to rekit from.", new Object[]{this.rekitTarget.get()});
            return null;
         }
      }
   }

   private void onLootDrained() {
      if (this.autoLootMode.get() == InventorySorterModule.AutoLootMode.Rekit) {
         this.pendingRekitSort = true;
         if ((Boolean)this.chatNotify.get()) {
            this.info("Grabbed items for (highlight)%s(default), arranging once you close this.", new Object[]{this.rekitTarget.get()});
         }
      } else if ((Boolean)this.chatNotify.get()) {
         this.info("Refill complete.", new Object[0]);
      }

   }

   private void autoDisableIfEnabled() {
      if ((Boolean)this.autoDisable.get() && this.isActive()) {
         this.toggle();
      }

   }

   private void loadFromDisk() {
      this.inventories.clear();
      if (Files.exists(SAVE_FILE, new LinkOption[0])) {
         try {
            BufferedReader reader = Files.newBufferedReader(SAVE_FILE, StandardCharsets.UTF_8);

            label79: {
               try {
                  Type type = new TypeToken<HashMap<String, HashMap<Integer, String>>>() {}.getType();
                  HashMap<String, HashMap<Integer, String>> raw = (HashMap)this.gson.fromJson(reader, type);
                  if (raw == null) {
                     break label79;
                  }

                  for(Map.Entry entry : raw.entrySet()) {
                     HashMap<Integer, Item> itemMap = new HashMap();

                     for(Map.Entry itemEntry : ((HashMap<Integer, String>)entry.getValue()).entrySet()) {
                        Identifier id = Identifier.of((String)itemEntry.getValue());
                        if (!Registries.ITEM.containsId(id)) {
                           SixToolsAddon.LOG.warn("Inventory '{}': unknown item id '{}' for slot {}, skipping.", new Object[]{entry.getKey(), itemEntry.getValue(), itemEntry.getKey()});
                        } else {
                           itemMap.put((Integer)itemEntry.getKey(), (Item)Registries.ITEM.get(id));
                        }
                     }

                     this.inventories.put((String)entry.getKey(), itemMap);
                  }
               } catch (Throwable var11) {
                  if (reader != null) {
                     try {
                        reader.close();
                     } catch (Throwable var10) {
                        var11.addSuppressed(var10);
                     }
                  }

                  throw var11;
               }

               if (reader != null) {
                  reader.close();
               }

               return;
            }

            if (reader != null) {
               reader.close();
            }

         } catch (IOException e) {
            SixToolsAddon.LOG.error("Failed to read inventories.json", e);
            ChatUtils.error("Failed to read saved inventories, check logs.", new Object[0]);
         }
      }
   }

   private void saveToDisk() {
      try {
         Files.createDirectories(SAVE_FILE.getParent());
         HashMap<String, HashMap<Integer, String>> raw = new HashMap();

         for(Map.Entry entry : this.inventories.entrySet()) {
            HashMap<Integer, String> nameMap = new HashMap();

            for(Map.Entry itemEntry : ((HashMap<Integer, Item>)entry.getValue()).entrySet()) {
               nameMap.put((Integer)itemEntry.getKey(), Registries.ITEM.getId((Item)itemEntry.getValue()).toString());
            }

            raw.put((String)entry.getKey(), nameMap);
         }

         BufferedWriter writer = Files.newBufferedWriter(SAVE_FILE, StandardCharsets.UTF_8);

         try {
            this.gson.toJson(raw, writer);
         } catch (Throwable var8) {
            if (writer != null) {
               try {
                  writer.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (writer != null) {
            writer.close();
         }
      } catch (IOException e) {
         SixToolsAddon.LOG.error("Failed to write inventories.json", e);
         ChatUtils.error("Failed to save inventories, check logs.", new Object[0]);
      }

   }

   public static enum AutoLootMode {
      Off,
      Refill,
      Rekit;

      private static AutoLootMode[] $values() {
         return new AutoLootMode[]{Off, Refill, Rekit};
      }
   }

   private static record SlotMove(int from, int to) {
   }
}
