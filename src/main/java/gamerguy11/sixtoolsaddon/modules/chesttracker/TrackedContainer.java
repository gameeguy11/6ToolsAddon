package gamerguy11.sixtoolsaddon.modules.chesttracker;

import com.google.gson.JsonObject;
import gamerguy11.sixtoolsaddon.modules.stashsorter.logic.ItemRoute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;

public class TrackedContainer {
   private final BlockPos position;
   private final String dimension;
   private String customName;
   private final Map<String, Integer> items;
   private final Map<String, Integer> topLevelRoutes;
   private final List<ItemStack> itemStacks;
   private long lastUpdated;
   private String containerType;
   private boolean typeKnown = true;
   private Boolean doubleChest;

   public TrackedContainer(BlockPos position, String dimension, String containerType) {
      this.position = position;
      this.dimension = dimension;
      this.containerType = containerType;
      this.items = new HashMap();
      this.topLevelRoutes = new HashMap();
      this.itemStacks = new ArrayList();
      this.customName = null;
      this.lastUpdated = System.currentTimeMillis();
   }

   public void updateContents(List<ItemStack> stacks) {
      this.updateContents(stacks, stacks);
   }

   public void updateContents(List<ItemStack> stacks, List<ItemStack> topLevelStacks) {
      this.items.clear();
      this.topLevelRoutes.clear();
      this.itemStacks.clear();

      for(ItemStack stack : stacks) {
         if (stack != null && !stack.isEmpty()) {
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            this.items.put(itemId, (Integer)this.items.getOrDefault(itemId, 0) + stack.getCount());
            this.itemStacks.add(stack.copy());
         }
      }

      for(ItemStack stack : topLevelStacks) {
         if (stack != null && !stack.isEmpty()) {
            this.topLevelRoutes.merge(ItemRoute.topLevel(stack), stack.getCount(), Integer::sum);
         }
      }

      this.lastUpdated = System.currentTimeMillis();
   }

   public boolean containsItem(String itemId) {
      return this.items.containsKey(itemId);
   }

   public boolean containsItem(Item item) {
      String itemId = Registries.ITEM.getId(item).toString();
      return this.items.containsKey(itemId);
   }

   public int getItemCount(String itemId) {
      return (Integer)this.items.getOrDefault(itemId, 0);
   }

   public Map<String, Integer> getItems() {
      return new HashMap(this.items);
   }

   public Map<String, Integer> getTopLevelRouteCounts() {
      return new HashMap(this.topLevelRoutes);
   }

   public List<ItemStack> getItemStacks() {
      return new ArrayList(this.itemStacks);
   }

   public BlockPos getPosition() {
      return this.position;
   }

   public String getDimension() {
      return this.dimension;
   }

   public String getCustomName() {
      return this.customName;
   }

   public void setCustomName(String name) {
      this.customName = name;
   }

   public long getLastUpdated() {
      return this.lastUpdated;
   }

   public boolean isTypeKnown() {
      return this.typeKnown;
   }

   public void setContainerType(String containerType) {
      this.containerType = containerType;
      this.typeKnown = true;
   }

   public Boolean getDoubleChest() {
      return this.doubleChest;
   }

   public void setDoubleChest(Boolean doubleChest) {
      this.doubleChest = doubleChest;
   }

   public String getContainerType() {
      return this.containerType;
   }

   public boolean isEmpty() {
      return this.items.isEmpty();
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("x", this.position.getX());
      json.addProperty("y", this.position.getY());
      json.addProperty("z", this.position.getZ());
      json.addProperty("dimension", this.dimension);
      json.addProperty("type", this.containerType);
      json.addProperty("lastUpdated", this.lastUpdated);
      if (this.doubleChest != null) {
         json.addProperty("double", this.doubleChest);
      }

      if (this.customName != null) {
         json.addProperty("customName", this.customName);
      }

      JsonObject itemsJson = new JsonObject();

      for(Map.Entry<String, Integer> entry : this.items.entrySet()) {
         itemsJson.addProperty((String)entry.getKey(), (Number)entry.getValue());
      }

      json.add("items", itemsJson);
      JsonObject routesJson = new JsonObject();

      for(Map.Entry<String, Integer> entry : this.topLevelRoutes.entrySet()) {
         routesJson.addProperty((String)entry.getKey(), (Number)entry.getValue());
      }

      json.add("topLevelRoutes", routesJson);
      return json;
   }

   public static TrackedContainer fromJson(JsonObject json) {
      BlockPos pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
      String dimension = json.get("dimension").getAsString();
      String type = json.has("type") ? json.get("type").getAsString() : "chest";
      TrackedContainer container = new TrackedContainer(pos, dimension, type);
      container.typeKnown = json.has("type");
      if (json.has("double")) {
         container.doubleChest = json.get("double").getAsBoolean();
      }

      if (json.has("customName")) {
         container.customName = json.get("customName").getAsString();
      }

      if (json.has("lastUpdated")) {
         container.lastUpdated = json.get("lastUpdated").getAsLong();
      }

      if (json.has("items")) {
         JsonObject itemsJson = json.getAsJsonObject("items");

         for(String key : itemsJson.keySet()) {
            container.items.put(key, itemsJson.get(key).getAsInt());
         }
      }

      if (json.has("topLevelRoutes")) {
         JsonObject routesJson = json.getAsJsonObject("topLevelRoutes");

         for(String key : routesJson.keySet()) {
            container.topLevelRoutes.put(key, routesJson.get(key).getAsInt());
         }
      }

      return container;
   }

   public String getDisplayName() {
      if (this.customName != null && !this.customName.isEmpty()) {
         return this.customName;
      } else {
         Object[] var10001 = new Object[4];
         String var10004 = this.containerType.substring(0, 1).toUpperCase();
         var10001[0] = var10004 + this.containerType.substring(1);
         var10001[1] = this.position.getX();
         var10001[2] = this.position.getY();
         var10001[3] = this.position.getZ();
         return String.format("%s [%d, %d, %d]", var10001);
      }
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (!(obj instanceof TrackedContainer)) {
         return false;
      } else {
         TrackedContainer other = (TrackedContainer)obj;
         return this.position.equals(other.position) && this.dimension.equals(other.dimension);
      }
   }

   public int hashCode() {
      return this.position.hashCode() * 31 + this.dimension.hashCode();
   }
}
