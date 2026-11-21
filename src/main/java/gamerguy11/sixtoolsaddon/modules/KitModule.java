package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.kit.Kit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;

public class KitModule extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> chatNotify = sgGeneral.add(new BoolSetting.Builder()
      .name("chat-notify")
      .description("Sends a chat message when a kit finishes sorting.")
      .defaultValue(true)
      .build());
   private final Setting<Integer> tickRate = sgGeneral.add(new IntSetting.Builder()
      .name("tick-rate")
      .description("Ticks to wait between each batch of slot moves. Higher is slower but less likely to trip anti-cheat.")
      .defaultValue(2)
      .range(1, 20)
      .sliderRange(1, 20)
      .build());
   private final Setting<Integer> frequency = sgGeneral.add(new IntSetting.Builder()
      .name("frequency")
      .description("Slot moves done per batch.")
      .defaultValue(1)
      .range(1, 3)
      .sliderRange(1, 3)
      .build());
   private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
      .name("auto-disable")
      .description("Turns the module off once the kit is arranged.")
      .defaultValue(true)
      .build());

   private final ArrayDeque<SlotMove> jobs = new ArrayDeque<>();
   private int ticks;
   private boolean isSorted = true;
   private boolean retriedThisPass;
   private Kit activeKit;

   public KitModule() {
      super(SixToolsAddon.CATEGORY, "kit", "Arranges your inventory into a saved kit. Manage kits with the .kit command.");
   }

   @Override
   public void onDeactivate() {
      ticks = 0;
      jobs.clear();
      isSorted = true;
      retriedThisPass = false;
      activeKit = null;
   }

   public void notifyInfo(String message, Object... args) {
      info(message, args);
   }

   public void notifyError(String message, Object... args) {
      error(message, args);
   }

   public boolean arrange(Kit kit) {
      if (mc.player == null || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return false;
      if (kit == null || kit.getItemsBySlot().isEmpty()) return false;
      if (!isActive()) toggle();
      activeKit = kit;
      plan(kit, true);
      return true;
   }

   private void plan(Kit kit, boolean resetRetry) {
      if (mc.player == null || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return;

      Map<Integer, Kit.Entry> wanted = new HashMap<>();
      kit.getItemsBySlot().forEach((slot, entry) -> wanted.put(Kit.toHandlerSlot(slot), entry));

      jobs.clear();
      if (resetRetry) retriedThisPass = false;
      isSorted = false;

      List<Integer> settled = new ArrayList<>();
      Map<Integer, ItemStack> pendingStacks = new HashMap<>();

      for (int to = 9; to <= 44; to++) {
         Kit.Entry want = wanted.get(to);
         if (want == null) continue;

         ItemStack current = pendingStacks.containsKey(to) ? pendingStacks.get(to) : handler.getSlot(to).getStack();
         if (want.matches(current)) {
            settled.add(to);
            continue;
         }

         for (int from = 9; from <= 44; from++) {
            if (from == to || settled.contains(from)) continue;
            ItemStack occupying = pendingStacks.containsKey(from) ? pendingStacks.get(from) : handler.getSlot(from).getStack();
            if (!want.matches(occupying)) continue;

            Kit.Entry fromWant = wanted.get(from);
            if (fromWant == null || !fromWant.matches(occupying)) {
               settled.add(to);
               if (!current.isEmpty()) {
                  pendingStacks.put(from, current);
               } else {
                  settled.add(from);
                  pendingStacks.remove(from);
               }
               jobs.addLast(new SlotMove(from, to));
               break;
            }
            settled.add(from);
         }
      }
   }

   private boolean isFullySorted(Kit kit) {
      if (mc.player == null || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return true;
      for (Map.Entry<Integer, Kit.Entry> entry : kit.getItemsBySlot().entrySet()) {
         if (!entry.getValue().matches(handler.getSlot(Kit.toHandlerSlot(entry.getKey())).getStack())) return false;
      }
      return true;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (mc.player == null) return;
      ScreenHandler handler = mc.player.currentScreenHandler;
      if (!(handler instanceof PlayerScreenHandler)) return;

      if (++ticks < tickRate.get()) return;
      ticks = 0;

      if (!jobs.isEmpty()) {
         isSorted = false;
         for (int i = 0; i < frequency.get() && !jobs.isEmpty(); i++) {
            SlotMove job = jobs.removeFirst();
            InvUtils.move().fromId(job.from()).toId(job.to());
         }
         return;
      }

      if (isSorted || activeKit == null) return;

      if (!isFullySorted(activeKit) && !retriedThisPass) {
         retriedThisPass = true;
         plan(activeKit, false);
         return;
      }

      boolean complete = isFullySorted(activeKit);
      isSorted = true;
      retriedThisPass = false;
      if (complete && chatNotify.get()) info("Kit (highlight)%s(default) sorted.", activeKit.getName());
      if (autoDisable.get() && isActive()) toggle();
   }

   private record SlotMove(int from, int to) {}
}
