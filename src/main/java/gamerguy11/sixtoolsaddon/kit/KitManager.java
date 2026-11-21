package gamerguy11.sixtoolsaddon.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class KitManager {
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("6tools").resolve("kits.json");
   private static final Path OLD_FILE = FabricLoader.getInstance().getConfigDir().resolve("inventory-sorter").resolve("inventories.json");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   public static final KitManager INSTANCE = new KitManager();

   private final List<Kit> kits = new ArrayList<>();
   private String currentName;

   private KitManager() {
      load();
   }

   public List<Kit> all() {
      return kits;
   }

   public List<String> names() {
      List<String> names = new ArrayList<>();
      for (Kit kit : kits) names.add(kit.getName());
      return names;
   }

   public Kit find(String name) {
      if (name == null) return null;
      for (Kit kit : kits) if (kit.getName().equalsIgnoreCase(name)) return kit;
      return null;
   }

   public Kit current() {
      return find(currentName);
   }

   public void setCurrent(String name) {
      Kit kit = find(name);
      currentName = kit != null ? kit.getName() : name;
      save();
   }

   public boolean remove(String name) {
      Kit kit = find(name);
      if (kit == null) return false;
      kits.remove(kit);
      if (currentName != null && currentName.equalsIgnoreCase(kit.getName())) currentName = null;
      save();
      return true;
   }

   public boolean rename(String name, String newName) {
      Kit kit = find(name);
      if (kit == null) return false;
      Kit renamed = new Kit(newName);
      renamed.getItemsBySlot().putAll(kit.getItemsBySlot());
      boolean wasCurrent = kit == current();
      kits.remove(kit);
      Kit clash = find(newName);
      if (clash != null) kits.remove(clash);
      kits.add(renamed);
      if (wasCurrent) currentName = renamed.getName();
      save();
      return true;
   }

   public Kit capture(String name, PlayerEntity player) {
      Kit old = find(name);
      if (old != null) kits.remove(old);
      Kit kit = new Kit(name);
      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (stack.isEmpty()) continue;
         kit.getItemsBySlot().put(slot, new Kit.Entry(Registries.ITEM.getId(stack.getItem()).toString(), potionSignature(stack)));
      }
      kits.add(kit);
      currentName = kit.getName();
      save();
      return kit;
   }

   public static String potionSignature(ItemStack stack) {
      PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
      if (contents == null) return null;
      List<String> signatures = new ArrayList<>();
      for (StatusEffectInstance effect : contents.getEffects()) {
         signatures.add(effect.getTranslationKey() + " " + (effect.getAmplifier() + 1));
      }
      Collections.sort(signatures);
      return String.join("", signatures);
   }

   private void load() {
      kits.clear();
      currentName = null;
      try {
         if (Files.exists(FILE)) {
            try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
               fromJson(JsonParser.parseReader(reader));
            }
         } else if (Files.exists(OLD_FILE)) {
            importOld();
            save();
         }
      } catch (Exception e) {
         SixToolsAddon.LOG.error("Failed to read kits.json", e);
      }
   }

   private void fromJson(JsonElement json) {
      if (!json.isJsonObject()) return;
      JsonObject root = json.getAsJsonObject();
      if (root.has("current") && root.get("current").isJsonPrimitive()) currentName = root.get("current").getAsString();
      for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
         if (!entry.getValue().isJsonArray()) continue;
         Kit kit = new Kit(entry.getKey());
         for (JsonElement element : entry.getValue().getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject slotObject = element.getAsJsonObject();
            if (!slotObject.has("slot") || !slotObject.has("item") || !slotObject.get("item").isJsonObject()) continue;
            JsonObject item = slotObject.getAsJsonObject("item");
            if (!item.has("id")) continue;
            String data = item.has("data") ? item.get("data").getAsString() : null;
            kit.getItemsBySlot().put(slotObject.get("slot").getAsInt(), new Kit.Entry(item.get("id").getAsString(), data));
         }
         kits.add(kit);
      }
   }

   private void importOld() throws IOException {
      try (BufferedReader reader = Files.newBufferedReader(OLD_FILE, StandardCharsets.UTF_8)) {
         JsonElement json = JsonParser.parseReader(reader);
         if (!json.isJsonObject()) return;
         for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            Kit kit = new Kit(entry.getKey());
            for (Map.Entry<String, JsonElement> slot : entry.getValue().getAsJsonObject().entrySet()) {
               int handlerSlot = Integer.parseInt(slot.getKey());
               int index = handlerSlot >= 36 && handlerSlot <= 44 ? handlerSlot - 36 : handlerSlot >= 9 && handlerSlot <= 35 ? handlerSlot : -1;
               if (index >= 0) kit.getItemsBySlot().put(index, new Kit.Entry(slot.getValue().getAsString(), null));
            }
            kits.add(kit);
         }
      }
   }

   public void save() {
      JsonObject root = new JsonObject();
      if (currentName != null) root.addProperty("current", currentName);
      for (Kit kit : kits) {
         JsonArray items = new JsonArray();
         kit.getItemsBySlot().forEach((slot, entry) -> {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.itemId());
            if (entry.potionSignature() != null) item.addProperty("data", entry.potionSignature());
            JsonObject slotObject = new JsonObject();
            slotObject.addProperty("slot", slot);
            slotObject.add("item", item);
            items.add(slotObject);
         });
         root.add(kit.getName(), items);
      }
      try {
         Files.createDirectories(FILE.getParent());
         try (BufferedWriter writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
         }
      } catch (IOException e) {
         SixToolsAddon.LOG.error("Failed to write kits.json", e);
      }
   }
}
