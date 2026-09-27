package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import meteordevelopment.orbit.EventHandler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InventorySorterModule extends Module {
    private static final Path SAVE_FILE = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("inventory-sorter")
        .resolve("inventories.json");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> chatNotify = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Sends a chat message when an inventory is saved or finishes sorting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> tickRate = sgGeneral.add(new IntSetting.Builder()
        .name("tick-rate")
        .description("Ticks to wait between each slot move. Higher is slower but less likely to trip anti-cheat.")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Turns the module off by itself once the inventory finishes sorting, instead of continuing to watch for changes.")
        .defaultValue(true)
        .build()
    );

    private final SettingGroup sgAutoLoot = settings.createGroup("Auto-Loot");

    public enum AutoLootMode {
        Off,
        Refill,
        Rekit
    }

    private final Setting<AutoLootMode> autoLootMode = sgAutoLoot.add(new EnumSetting.Builder<AutoLootMode>()
        .name("auto-loot")
        .description("Off: does nothing extra. Refill: whenever you open storage while this module is active, takes any items you're already carrying, topping up what you have. Rekit: takes items belonging to the chosen saved inventory below from any storage you open, then arranges your inventory into that layout once you close it.")
        .defaultValue(AutoLootMode.Off)
        .build()
    );

    private final Setting<String> rekitTarget = sgAutoLoot.add(new StringSetting.Builder()
        .name("rekit-inventory")
        .description("Name of the saved inventory (see the inventories list) to pull items for and arrange into, when auto-loot is set to Rekit.")
        .defaultValue("")
        .visible(() -> autoLootMode.get() == AutoLootMode.Rekit)
        .build()
    );

    private record SlotMove(int from, int to) {}

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HashMap<String, HashMap<Integer, Item>> inventories = new HashMap<>();
    private final ArrayDeque<SlotMove> jobs = new ArrayDeque<>();

    private int ticks = 0;
    private boolean isSorted = true;
    private boolean retriedThisPass = false;
    private String activeInventoryKey = null;

    private final ArrayDeque<Integer> lootJobs = new ArrayDeque<>();
    private ScreenHandler lastSeenHandler = null;
    private boolean pendingRekitSort = false;
    private boolean autoLootSort = false;

    public InventorySorterModule() {
        super(SixToolsAddon.CATEGORY, "inventory-sorter", "Auto-sorts your inventory back into a saved inventory layout.");

        loadFromDisk();
    }

    @Override
    public void onActivate() {
        loadFromDisk();
    }

    @Override
    public void onDeactivate() {
        ticks = 0;
        jobs.clear();
        isSorted = true;
        retriedThisPass = false;

        lootJobs.clear();
        lastSeenHandler = null;
        pendingRekitSort = false;
        autoLootSort = false;
    }

    public void notifyInfo(String message, Object... args) {
        info(message, args);
    }

    public void notifyError(String message, Object... args) {
        error(message, args);
    }

    public void notifySaveResult(boolean overwritten, String name) {
        if (!chatNotify.get()) return;
        info(overwritten ? "Overwrote inventory (highlight)%s(default)." : "Saved inventory (highlight)%s(default).", name);
    }

    public boolean hasInventory(String name) {
        return inventories.containsKey(name);
    }

    public List<String> inventoryNames() {
        return new ArrayList<>(inventories.keySet());
    }

    public void deleteInventory(String name) {
        inventories.remove(name);
        saveToDisk();
    }

    public void clearInventories() {
        inventories.clear();
        saveToDisk();
    }

    public boolean saveInventory(String name) {
        if (mc.player == null) return false;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return false;

        HashMap<Integer, Item> snapshot = new HashMap<>();
        for (int slot = PlayerScreenHandler.EQUIPMENT_START; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty() && !stack.isOf(Items.AIR)) {
                snapshot.put(slot, stack.getItem());
            }
        }

        inventories.put(name, snapshot);
        saveToDisk();

        return true;
    }

    public void loadInventory(String name) {
        loadInventory(name, true);
    }

    private void loadInventory(String name, boolean resetRetry) {
        if (mc.player == null) return;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return;

        HashMap<Integer, Item> inventory = inventories.get(name);
        if (inventory == null || inventory.isEmpty()) {
            error("No inventory named (highlight)%s(default) is saved.", name);
            return;
        }

        jobs.clear();
        activeInventoryKey = name;
        if (resetRetry) retriedThisPass = false;
        isSorted = false;

        List<Integer> settled = new ArrayList<>();
        HashMap<Integer, ItemStack> pendingStacks = new HashMap<>();

        for (int to = PlayerScreenHandler.EQUIPMENT_START; to < handler.slots.size(); to++) {
            Item wanted = inventory.get(to);
            if (wanted == null) continue;

            ItemStack current = pendingStacks.containsKey(to) ? pendingStacks.get(to) : handler.getSlot(to).getStack();
            if (current.isOf(wanted)) {
                settled.add(to);
                continue;
            }

            for (int from = PlayerScreenHandler.EQUIPMENT_START; from < handler.slots.size(); from++) {
                if (from == to || settled.contains(from)) continue;

                ItemStack occupying = pendingStacks.containsKey(from)
                    ? pendingStacks.get(from)
                    : handler.getSlot(from).getStack();

                if (!occupying.isOf(wanted)) continue;

                if (inventory.get(from) != null && occupying.isOf(inventory.get(from))) {
                    settled.add(from);
                    continue;
                }

                if (!current.isEmpty()) {
                    settled.add(to);
                    pendingStacks.put(from, current);
                } else {
                    settled.add(to);
                    settled.add(from);
                    pendingStacks.remove(from);
                }

                jobs.addLast(new SlotMove(from, to));
                break;
            }
        }
    }

    private boolean isFullySorted(String name) {
        if (inventories.isEmpty() || name == null) return true;
        if (mc.player == null) return true;
        if (!inventories.containsKey(name)) return true;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return true;

        HashMap<Integer, Item> inventory = inventories.get(name);
        for (int slot = PlayerScreenHandler.EQUIPMENT_START; slot < handler.slots.size(); slot++) {
            Item wanted = inventory.get(slot);
            if (wanted == null) continue;
            if (!handler.getSlot(slot).getStack().isOf(wanted)) return false;
        }

        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        ScreenHandler handler = mc.player.currentScreenHandler;

        if (handler != lastSeenHandler) {
            lastSeenHandler = handler;

            if (isContainerHandler(handler) && autoLootMode.get() != AutoLootMode.Off) {
                queueAutoLoot(handler);
            } else if (handler instanceof PlayerScreenHandler && pendingRekitSort) {
                pendingRekitSort = false;
                autoLootSort = true;
                loadInventory(rekitTarget.get());
            }
        }

        ticks++;
        if (ticks < tickRate.get()) return;
        ticks = 0;

        if (!lootJobs.isEmpty()) {
            if (isContainerHandler(handler)) {
                InvUtils.shiftClick().slotId(lootJobs.removeFirst());
                if (lootJobs.isEmpty()) onLootDrained();
            } else {

                lootJobs.clear();
            }
            return;
        }

        if (!(handler instanceof PlayerScreenHandler)) return;

        if (!jobs.isEmpty()) {
            isSorted = false;
            SlotMove job = jobs.removeFirst();
            InvUtils.move().fromId(job.from()).toId(job.to());
            return;
        }

        if (isSorted) return;

        if (!isFullySorted(activeInventoryKey)) {
            if (!retriedThisPass) {

                retriedThisPass = true;
                loadInventory(activeInventoryKey, false);
                return;
            }

            isSorted = true;
            retriedThisPass = false;
            if (autoLootSort) autoLootSort = false;
            else autoDisableIfEnabled();
            return;
        }

        isSorted = true;
        retriedThisPass = false;
        if (chatNotify.get()) info("Inventory (highlight)%s(default) sorted.", activeInventoryKey);
        if (autoLootSort) autoLootSort = false;
        else autoDisableIfEnabled();
    }

    private boolean isContainerHandler(ScreenHandler handler) {
        return handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler;
    }

    private void queueAutoLoot(ScreenHandler handler) {
        lootJobs.clear();

        Set<Item> wanted = autoLootMode.get() == AutoLootMode.Rekit ? rekitWantedItems() : refillWantedItems();
        if (wanted == null || wanted.isEmpty()) return;

        for (Slot slot : handler.slots) {

            if (slot.inventory instanceof PlayerInventory) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !wanted.contains(stack.getItem())) continue;

            lootJobs.addLast(slot.id);
        }
    }

    private Set<Item> refillWantedItems() {
        Set<Item> wanted = new HashSet<>();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) wanted.add(stack.getItem());
        }

        return wanted;
    }

    private Set<Item> rekitWantedItems() {
        if (rekitTarget.get().isBlank()) {
            error("Set a (highlight)rekit-inventory(default) name in the module settings.");
            return null;
        }

        HashMap<Integer, Item> kit = inventories.get(rekitTarget.get());
        if (kit == null || kit.isEmpty()) {
            error("No saved inventory named (highlight)%s(default) to rekit from.", rekitTarget.get());
            return null;
        }

        return new HashSet<>(kit.values());
    }

    private void onLootDrained() {
        if (autoLootMode.get() == AutoLootMode.Rekit) {
            pendingRekitSort = true;
            if (chatNotify.get()) info("Grabbed items for (highlight)%s(default), arranging once you close this.", rekitTarget.get());
        } else if (chatNotify.get()) {
            info("Refill complete.");
        }
    }

    private void autoDisableIfEnabled() {
        if (autoDisable.get() && isActive()) toggle();
    }

    private void loadFromDisk() {
        inventories.clear();

        if (!Files.exists(SAVE_FILE)) return;

        try (BufferedReader reader = Files.newBufferedReader(SAVE_FILE, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<HashMap<String, HashMap<Integer, String>>>() {}.getType();
            HashMap<String, HashMap<Integer, String>> raw = gson.fromJson(reader, type);
            if (raw == null) return;

            for (Map.Entry<String, HashMap<Integer, String>> entry : raw.entrySet()) {
                HashMap<Integer, Item> itemMap = new HashMap<>();
                for (Map.Entry<Integer, String> itemEntry : entry.getValue().entrySet()) {
                    Identifier id = Identifier.of(itemEntry.getValue());

                    if (!Registries.ITEM.containsId(id)) {

                        SixToolsAddon.LOG.warn("Inventory '{}': unknown item id '{}' for slot {}, skipping.", entry.getKey(), itemEntry.getValue(), itemEntry.getKey());
                        continue;
                    }

                    itemMap.put(itemEntry.getKey(), Registries.ITEM.get(id));
                }
                inventories.put(entry.getKey(), itemMap);
            }
        } catch (IOException e) {
            SixToolsAddon.LOG.error("Failed to read inventories.json", e);
            ChatUtils.error("Failed to read saved inventories, check logs.");
        }
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());

            HashMap<String, HashMap<Integer, String>> raw = new HashMap<>();
            for (Map.Entry<String, HashMap<Integer, Item>> entry : inventories.entrySet()) {
                HashMap<Integer, String> nameMap = new HashMap<>();
                for (Map.Entry<Integer, Item> itemEntry : entry.getValue().entrySet()) {
                    nameMap.put(itemEntry.getKey(), Registries.ITEM.getId(itemEntry.getValue()).toString());
                }
                raw.put(entry.getKey(), nameMap);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(SAVE_FILE, StandardCharsets.UTF_8)) {
                gson.toJson(raw, writer);
            }
        } catch (IOException e) {
            SixToolsAddon.LOG.error("Failed to write inventories.json", e);
            ChatUtils.error("Failed to save inventories, check logs.");
        }
    }
}
