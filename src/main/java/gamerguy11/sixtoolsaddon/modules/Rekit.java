package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.kit.Kit;
import gamerguy11.sixtoolsaddon.kit.KitManager;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
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
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;

public class Rekit extends Module {
   public enum Source {
      Kit,
      Carried
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Source> source = sgGeneral.add(new EnumSetting.Builder<Source>()
      .name("source")
      .description("Kit: takes only what the kit is missing. Carried: takes any items you are already carrying, topping up what you have.")
      .defaultValue(Source.Kit)
      .build());
   private final Setting<String> kitName = sgGeneral.add(new StringSetting.Builder()
      .name("kit")
      .description("Kit to refill. Leave empty to use the kit selected with .kit load.")
      .defaultValue("")
      .visible(() -> source.get() == Source.Kit)
      .build());
   private final Setting<Boolean> arrangeAfter = sgGeneral.add(new BoolSetting.Builder()
      .name("arrange-after")
      .description("Arranges your inventory into the kit after you close the container.")
      .defaultValue(true)
      .visible(() -> source.get() == Source.Kit)
      .build());
   private final Setting<Integer> tickRate = sgGeneral.add(new IntSetting.Builder()
      .name("tick-rate")
      .description("Ticks to wait between each item taken.")
      .defaultValue(2)
      .range(1, 20)
      .sliderRange(1, 20)
      .build());
   private final Setting<Boolean> chatNotify = sgGeneral.add(new BoolSetting.Builder()
      .name("chat-notify")
      .description("Sends a chat message when it finishes taking items.")
      .defaultValue(true)
      .build());

   private final ArrayDeque<Integer> lootJobs = new ArrayDeque<>();
   private ScreenHandler lastSeenHandler;
   private Kit pendingArrange;
   private int ticks;

   public Rekit() {
      super(SixToolsAddon.CATEGORY, "rekit", "Refills your kit from any container you open, then arranges your inventory into it.");
   }

   @Override
   public void onDeactivate() {
      lootJobs.clear();
      lastSeenHandler = null;
      pendingArrange = null;
      ticks = 0;
   }

   private Kit targetKit() {
      String name = kitName.get();
      return name.isBlank() ? KitManager.INSTANCE.current() : KitManager.INSTANCE.find(name);
   }

   private static boolean isContainer(ScreenHandler handler) {
      return handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (mc.player == null) return;
      ScreenHandler handler = mc.player.currentScreenHandler;

      if (handler != lastSeenHandler) {
         lastSeenHandler = handler;
         if (isContainer(handler)) {
            queueLoot(handler);
         } else if (handler instanceof PlayerScreenHandler && pendingArrange != null) {
            Kit kit = pendingArrange;
            pendingArrange = null;
            Modules.get().get(KitModule.class).arrange(kit);
         }
      }

      if (lootJobs.isEmpty() || ++ticks < tickRate.get()) return;
      ticks = 0;

      if (!isContainer(handler)) {
         lootJobs.clear();
         return;
      }
      InvUtils.shiftClick().slotId(lootJobs.removeFirst());
      if (lootJobs.isEmpty()) onLootDrained();
   }

   private void queueLoot(ScreenHandler handler) {
      lootJobs.clear();
      pendingArrange = null;

      if (source.get() == Source.Carried) {
         Set<Item> carried = new HashSet<>();
         for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) carried.add(stack.getItem());
         }
         for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && carried.contains(stack.getItem())) lootJobs.addLast(slot.id);
         }
         return;
      }

      Kit kit = targetKit();
      if (kit == null || kit.getItemsBySlot().isEmpty()) {
         error("No kit to refill. Save one with (highlight).kit save <name>(default) or set the kit setting.");
         return;
      }

      Map<String, Integer> capacity = new HashMap<>();
      for (Kit.Entry entry : kit.getItemsBySlot().values()) {
         capacity.merge(entry.key(), entry.item().getMaxCount(), Integer::sum);
      }
      Map<String, Integer> have = new HashMap<>();
      for (int i = 0; i < 36; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (stack.isEmpty()) continue;
         for (Kit.Entry entry : kit.getItemsBySlot().values()) {
            if (entry.matches(stack)) {
               have.merge(entry.key(), stack.getCount(), Integer::sum);
               break;
            }
         }
      }

      for (Slot slot : handler.slots) {
         if (slot.inventory instanceof PlayerInventory) continue;
         ItemStack stack = slot.getStack();
         if (stack.isEmpty()) continue;
         for (Kit.Entry entry : kit.getItemsBySlot().values()) {
            if (!entry.matches(stack)) continue;
            String key = entry.key();
            if (have.getOrDefault(key, 0) < capacity.getOrDefault(key, 0)) {
               lootJobs.addLast(slot.id);
               have.merge(key, stack.getCount(), Integer::sum);
            }
            break;
         }
      }

      if (!lootJobs.isEmpty() && arrangeAfter.get()) pendingArrange = kit;
   }

   private void onLootDrained() {
      if (!chatNotify.get()) return;
      if (pendingArrange != null) info("Grabbed items for (highlight)%s(default), arranging once you close this.", pendingArrange.getName());
      else info("Refill complete.");
   }
}
