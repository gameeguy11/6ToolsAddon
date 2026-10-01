package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.modules.chesttracker.ChestTrackerModule;
import gamerguy11.sixtoolsaddon.modules.chesttracker.TrackedContainer;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;

public class ItemSearchBar extends Module {
   private final WeakHashMap highlightCache = new WeakHashMap();
   private String cachedQuery = "";
   private String[] cachedSplitQueries = null;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgGUI;
   private final SettingGroup sgItemFrames;
   public final Setting<String> searchQuery;
   private final Setting<Boolean> caseSensitive;
   private final Setting<Boolean> splitQueries;
   private final Setting<Boolean> searchItemName;
   private final Setting<Boolean> searchItemType;
   private final Setting<Boolean> searchLore;
   public final Setting<SettingColor> highlightColor;
   private final Setting<Boolean> ownInventory;
   private final Setting<Boolean> showSearchField;
   private final Setting<Boolean> chestTrackerIntegration;
   private final Setting<Keybind> clickToSearchKey;
   private final Setting<Integer> fieldWidth;
   private final Setting<Integer> fieldHeight;
   private final Setting<Integer> offsetX;
   private final Setting<Integer> offsetY;
   private final Setting<Boolean> highlightItemFrames;
   private final Setting<SettingColor> frameFillColor;
   private final Setting<SettingColor> frameOutlineColor;
   private final Setting<Boolean> frameRenderFill;
   private final Setting<Boolean> frameRenderOutline;
   private final Setting<Boolean> frameRenderTracer;
   private final Setting<SettingColor> frameTracerColor;
   private ItemStack lastHoveredItem;
   private boolean middleMousePressed;
   private String currentSearchQuery;

   public ItemSearchBar() {
      super(SixToolsAddon.CATEGORY, "ItemSearchBar", "Search and highlight items in inventory and containers.");
      this.sgGeneral = this.settings.createGroup("General");
      this.sgGUI = this.settings.createGroup("GUI Settings");
      this.sgItemFrames = this.settings.createGroup("Item Frame ESP");
      this.searchQuery = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("search-query")).description("Search query to match item names. Use commas to separate multiple search terms.")).defaultValue("")).build());
      this.caseSensitive = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("case-sensitive")).description("Whether the search should be case sensitive.")).defaultValue(false)).build());
      this.splitQueries = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("split-queries")).description("Split search queries by commas. Disable to treat commas literally.")).defaultValue(true)).build());
      this.searchItemName = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("search-item-name")).description("Search in item display names.")).defaultValue(true)).build());
      this.searchItemType = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("search-item-type")).description("Search in item type names.")).defaultValue(true)).build());
      this.searchLore = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("search-lore")).description("Search in item lore/tooltip text.")).defaultValue(false)).build());
      this.highlightColor = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("highlight-color")).description("Color to highlight matching items.")).defaultValue(new SettingColor(255, 255, 0, 100)).build());
      this.ownInventory = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("inventory-highlight")).description("Highlight items in player inventory.")).defaultValue(true)).build());
      this.showSearchField = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-search-field")).description("Show search input field on top of container windows.")).defaultValue(true)).build());
      this.chestTrackerIntegration = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("chest-tracker-integration")).description("Automatically search in ChestTracker when searching items.")).defaultValue(true)).build());
      this.clickToSearchKey = this.sgGeneral.add(((KeybindSetting.Builder)((KeybindSetting.Builder)((KeybindSetting.Builder)((KeybindSetting.Builder)(new KeybindSetting.Builder()).name("click-to-search-key")).description("Key/button to click an item to search for it.")).defaultValue(Keybind.fromButton(2))).visible(() -> (Boolean)this.chestTrackerIntegration.get())).build());
      this.fieldWidth = this.sgGUI.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("field-width")).description("Width of the search field.")).defaultValue(160)).min(80).max(300).sliderMin(80).sliderMax(300).build());
      this.fieldHeight = this.sgGUI.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("field-height")).description("Height of the search field.")).defaultValue(12)).min(8).max(20).sliderMin(8).sliderMax(20).build());
      this.offsetX = this.sgGUI.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("offset-x")).description("Horizontal offset from container edge.")).defaultValue(8)).min(-100).max(100).sliderMin(-100).sliderMax(100).build());
      this.offsetY = this.sgGUI.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("offset-y")).description("Vertical offset from container top (negative = above container).")).defaultValue(-18)).min(-50).max(50).sliderMin(-50).sliderMax(50).build());
      this.highlightItemFrames = this.sgItemFrames.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("highlight-item-frames")).description("Highlight item frames containing matching items in the world.")).defaultValue(true)).build());
      SettingGroup var10001 = this.sgItemFrames;
      ColorSetting.Builder var10002 = ((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("fill-color")).description("Fill color for item frame highlight.")).defaultValue(new SettingColor(255, 255, 0, 50));
      Setting<Boolean> var10003 = this.highlightItemFrames;
      Objects.requireNonNull(var10003);
      this.frameFillColor = var10001.add(((ColorSetting.Builder)var10002.visible(var10003::get)).build());
      var10001 = this.sgItemFrames;
      var10002 = ((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("outline-color")).description("Outline color for item frame highlight.")).defaultValue(new SettingColor(255, 255, 0, 255));
      var10003 = this.highlightItemFrames;
      Objects.requireNonNull(var10003);
      this.frameOutlineColor = var10001.add(((ColorSetting.Builder)var10002.visible(var10003::get)).build());
      var10001 = this.sgItemFrames;
      BoolSetting.Builder var6 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("render-fill")).description("Render fill of item frame highlight.")).defaultValue(true);
      var10003 = this.highlightItemFrames;
      Objects.requireNonNull(var10003);
      this.frameRenderFill = var10001.add(((BoolSetting.Builder)var6.visible(var10003::get)).build());
      var10001 = this.sgItemFrames;
      var6 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("render-outline")).description("Render outline of item frame highlight.")).defaultValue(true);
      var10003 = this.highlightItemFrames;
      Objects.requireNonNull(var10003);
      this.frameRenderOutline = var10001.add(((BoolSetting.Builder)var6.visible(var10003::get)).build());
      var10001 = this.sgItemFrames;
      var6 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("tracers")).description("Draw tracers to matching item frames.")).defaultValue(false);
      var10003 = this.highlightItemFrames;
      Objects.requireNonNull(var10003);
      this.frameRenderTracer = var10001.add(((BoolSetting.Builder)var6.visible(var10003::get)).build());
      this.frameTracerColor = this.sgItemFrames.add(((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("tracer-color")).description("Color of tracers to item frames.")).defaultValue(new SettingColor(255, 255, 0, 125)).visible(() -> (Boolean)this.highlightItemFrames.get() && (Boolean)this.frameRenderTracer.get())).build());
      this.lastHoveredItem = null;
      this.middleMousePressed = false;
      this.currentSearchQuery = "";
   }

   public void onActivate() {
      if (!((String)this.searchQuery.get()).isEmpty() && (Boolean)this.chestTrackerIntegration.get()) {
         this.updateChestTrackerSearch((String)this.searchQuery.get());
      }

   }

   public void onDeactivate() {
      if ((Boolean)this.chestTrackerIntegration.get()) {
         ChestTrackerModule chestTracker = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
         if (chestTracker != null && chestTracker.isActive()) {
            chestTracker.searchItem((Item)null);
         }
      }

      this.lastHoveredItem = null;
      this.middleMousePressed = false;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if ((Boolean)this.chestTrackerIntegration.get() && !((String)this.searchQuery.get()).equals(this.currentSearchQuery)) {
         this.updateSearchQuery((String)this.searchQuery.get());
      }

      if ((Boolean)this.chestTrackerIntegration.get() && ((Keybind)this.clickToSearchKey.get()).isSet()) {
         if (this.mc.currentScreen != null) {
            Screen var3 = this.mc.currentScreen;
            if (var3 instanceof HandledScreen) {
               HandledScreen<?> screen = (HandledScreen)var3;
               boolean var11 = ((Keybind)this.clickToSearchKey.get()).isPressed();
               ItemStack hoveredStack = null;
               if (screen.getScreenHandler() != null) {
                  double mouseX = this.mc.mouse.getX() * (double)this.mc.getWindow().getScaledWidth() / (double)this.mc.getWindow().getWidth();
                  double mouseY = this.mc.mouse.getY() * (double)this.mc.getWindow().getScaledHeight() / (double)this.mc.getWindow().getHeight();

                  for(Slot slot : screen.getScreenHandler().slots) {
                     if (this.isPointInSlot(screen, slot, mouseX, mouseY) && slot.hasStack()) {
                        hoveredStack = slot.getStack();
                        break;
                     }
                  }
               }

               if (hoveredStack != null) {
                  if (var11 && !this.middleMousePressed) {
                     this.middleMousePressed = true;
                     this.lastHoveredItem = hoveredStack;
                  } else if (!var11 && this.middleMousePressed && this.lastHoveredItem != null) {
                     this.middleMousePressed = false;
                     String itemName = this.lastHoveredItem.getName().getString();
                     this.updateSearchQuery(itemName);
                     this.info("Searching for: " + itemName, new Object[0]);
                     this.lastHoveredItem = null;
                  }
               }

               if (!var11) {
                  this.middleMousePressed = false;
               }

               return;
            }
         }

         this.lastHoveredItem = null;
      }
   }

   private boolean isPointInSlot(HandledScreen screen, Slot slot, double pointX, double pointY) {
      int x = (screen.width - 176) / 2;
      int y = (screen.height - 166) / 2;
      if (!(screen instanceof GenericContainerScreen) && screen instanceof InventoryScreen) {
         x = (screen.width - 176) / 2;
         y = (screen.height - 166) / 2;
      }

      int slotX = x + slot.x;
      int slotY = y + slot.y;
      return pointX >= (double)slotX && pointX < (double)(slotX + 16) && pointY >= (double)slotY && pointY < (double)(slotY + 16);
   }

   private boolean shouldIgnoreCurrentScreenHandler(ClientPlayerEntity player) {
      if (this.mc.currentScreen == null) {
         return true;
      } else if (player.currentScreenHandler == null) {
         return true;
      } else {
         ScreenHandler handler = player.currentScreenHandler;
         if (handler instanceof PlayerScreenHandler) {
            return !(Boolean)this.ownInventory.get();
         } else {
            return !(handler instanceof AbstractFurnaceScreenHandler) && !(handler instanceof GenericContainerScreenHandler) && !(handler instanceof Generic3x3ContainerScreenHandler) && !(handler instanceof ShulkerBoxScreenHandler) && !(handler instanceof HopperScreenHandler) && !(handler instanceof HorseScreenHandler);
         }
      }
   }

   private boolean matchesSearchQuery(String text, String query) {
      return (Boolean)this.caseSensitive.get() ? text.contains(query) : text.toLowerCase().contains(query.toLowerCase());
   }

   public void updateSearchQuery(String query) {
      this.currentSearchQuery = query;
      if (!((String)this.searchQuery.get()).equals(query)) {
         this.searchQuery.set(query);
      }

      this.highlightCache.clear();
      this.cachedQuery = "";
      this.cachedSplitQueries = null;
      if ((Boolean)this.chestTrackerIntegration.get()) {
         this.updateChestTrackerSearch(query);
      }

   }

   private void updateChestTrackerSearch(String query) {
      ChestTrackerModule chestTracker = (ChestTrackerModule)Modules.get().get(ChestTrackerModule.class);
      if (chestTracker != null && chestTracker.isActive()) {
         if (query != null && !query.trim().isEmpty()) {
            Item searchItem = null;
            String searchQuery = query.trim().toLowerCase();
            if ((Boolean)this.splitQueries.get() && searchQuery.contains(",")) {
               String[] queries = searchQuery.split(",");
               if (queries.length > 0) {
                  searchQuery = queries[0].trim();
               }
            }

            for(Item item : Registries.ITEM) {
               String itemName = item.getDefaultStack().getName().getString().toLowerCase();
               if (itemName.equals(searchQuery)) {
                  searchItem = item;
                  break;
               }
            }

            if (searchItem == null) {
               for(Item item : Registries.ITEM) {
                  String itemName = item.getDefaultStack().getName().getString().toLowerCase();
                  String translationKey = item.getTranslationKey().toLowerCase();
                  String simplifiedKey = translationKey.replace("item.minecraft.", "").replace("block.minecraft.", "").replace("_", " ");
                  if (itemName.contains(searchQuery) || simplifiedKey.contains(searchQuery) || translationKey.contains(searchQuery)) {
                     searchItem = item;
                     break;
                  }
               }
            }

            chestTracker.searchItem(searchItem);
            if (searchItem != null) {
               String itemDisplayName = searchItem.getDefaultStack().getName().getString();
               this.info("ChestTracker: Searching for §e" + itemDisplayName, new Object[0]);
               List<TrackedContainer> results = chestTracker.getData().searchItem(searchItem);
               if (!results.isEmpty()) {
                  Item finalSearchItem = searchItem;
                  int totalCount = results.stream().mapToInt((c) -> c.getItemCount(Registries.ITEM.getId(finalSearchItem).toString())).sum();
                  this.info("Found §a" + totalCount + "§r items in §e" + results.size() + "§r containers", new Object[0]);
               }
            } else {
               this.info("ChestTracker: No item found matching \"" + query + "\"", new Object[0]);
            }
         } else {
            chestTracker.searchItem((Item)null);
            this.info("ChestTracker search cleared", new Object[0]);
         }

      }
   }

   public boolean shouldShowSearchField() {
      return (Boolean)this.showSearchField.get();
   }

   public int getFieldWidth() {
      return (Integer)this.fieldWidth.get();
   }

   public int getFieldHeight() {
      return (Integer)this.fieldHeight.get();
   }

   public int getOffsetX() {
      return (Integer)this.offsetX.get();
   }

   public int getOffsetY() {
      return (Integer)this.offsetY.get();
   }

   public boolean shouldHighlightSlot(ItemStack stack) {
      if (this.mc.player == null) {
         return false;
      } else if (!stack.isEmpty() && !this.shouldIgnoreCurrentScreenHandler(this.mc.player)) {
         String query = !this.currentSearchQuery.isEmpty() ? this.currentSearchQuery.trim() : ((String)this.searchQuery.get()).trim();
         if (query.isEmpty()) {
            return false;
         } else {
            Boolean cached = (Boolean)this.highlightCache.get(stack);
            if (cached != null) {
               return cached;
            } else {
               if (!query.equals(this.cachedQuery)) {
                  this.cachedQuery = query;
                  if ((Boolean)this.splitQueries.get() && query.contains(",")) {
                     this.cachedSplitQueries = query.split(",");
                  } else {
                     this.cachedSplitQueries = null;
                  }
               }

               boolean result = this.computeHighlight(stack, query);
               this.highlightCache.put(stack, result);
               return result;
            }
         }
      } else {
         return false;
      }
   }

   private boolean computeHighlight(ItemStack stack, String query) {
      if (Utils.hasItems(stack)) {
         ItemStack[] stacks = new ItemStack[27];
         Utils.getItemsInContainerItem(stack, stacks);

         for(ItemStack s : stacks) {
            if (s != null && !s.isEmpty() && this.matchesItemDirect(s, query)) {
               return true;
            }
         }
      }

      return this.matchesItemDirect(stack, query);
   }

   private boolean matchesItemDirect(ItemStack stack, String query) {
      if (this.cachedSplitQueries != null) {
         for(String q : this.cachedSplitQueries) {
            q = q.trim();
            if (!q.isEmpty() && this.matchesItem(stack, q)) {
               return true;
            }
         }

         return false;
      } else {
         return this.matchesItem(stack, query);
      }
   }

   private boolean matchesItem(ItemStack stack, String query) {
      if ((Boolean)this.searchItemName.get()) {
         String displayName = stack.getName().getString();
         if (this.matchesSearchQuery(displayName, query)) {
            return true;
         }
      }

      if ((Boolean)this.searchItemType.get()) {
         String typeName = stack.getItem().getDefaultStack().getName().getString();
         if (this.matchesSearchQuery(typeName, query)) {
            return true;
         }
      }

      if ((Boolean)this.searchLore.get()) {
         String tooltip = stack.getComponents().toString();
         if (this.matchesSearchQuery(tooltip, query)) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world != null && this.mc.player != null) {
         if ((Boolean)this.highlightItemFrames.get()) {
            String query = !this.currentSearchQuery.isEmpty() ? this.currentSearchQuery.trim() : ((String)this.searchQuery.get()).trim();
            if (!query.isEmpty()) {
               ShapeMode shapeMode = this.getShapeMode((Boolean)this.frameRenderFill.get(), (Boolean)this.frameRenderOutline.get());
               if (shapeMode != null) {
                  Color fillColor = new Color((Color)this.frameFillColor.get());
                  Color outlineColor = new Color((Color)this.frameOutlineColor.get());

                  for(Entity entity : this.mc.world.getEntities()) {
                     if (entity instanceof ItemFrameEntity) {
                        ItemFrameEntity frame = (ItemFrameEntity)entity;
                        ItemStack heldStack = frame.getHeldItemStack();
                        if (!heldStack.isEmpty() && this.matchesItemForFrame(heldStack, query)) {
                           Box box = frame.getBoundingBox();
                           event.renderer.box(box, fillColor, outlineColor, shapeMode, 0);
                           if ((Boolean)this.frameRenderTracer.get()) {
                              event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, box.getCenter().x, box.getCenter().y, box.getCenter().z, (Color)this.frameTracerColor.get());
                           }
                        }
                     }
                  }

               }
            }
         }
      }
   }

   private ShapeMode getShapeMode(boolean renderFill, boolean renderOutline) {
      if (renderFill && renderOutline) {
         return ShapeMode.Both;
      } else if (renderFill) {
         return ShapeMode.Sides;
      } else {
         return renderOutline ? ShapeMode.Lines : null;
      }
   }

   private boolean matchesItemForFrame(ItemStack stack, String query) {
      if (!query.equals(this.cachedQuery)) {
         this.cachedQuery = query;
         if ((Boolean)this.splitQueries.get() && query.contains(",")) {
            this.cachedSplitQueries = query.split(",");
         } else {
            this.cachedSplitQueries = null;
         }
      }

      if (this.cachedSplitQueries != null) {
         for(String q : this.cachedSplitQueries) {
            q = q.trim();
            if (!q.isEmpty() && this.matchesItem(stack, q)) {
               return true;
            }
         }

         return false;
      } else {
         return this.matchesItem(stack, query);
      }
   }
}
