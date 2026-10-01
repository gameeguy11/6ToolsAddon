package gamerguy11.sixtoolsaddon.modules.chesttracker;

import gamerguy11.sixtoolsaddon.modules.ItemSearchBar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.Click;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;

public class ChestTrackerScreen extends Screen {
   private final ChestTrackerModule module;
   private final ChestTrackerDataV2 data;
   private TextFieldWidget searchField;
   private String searchQuery = "";
   private List<ItemEntry> allItems = new ArrayList();
   private List<ItemEntry> filteredItems = new ArrayList();
   private int scrollOffset = 0;
   private int maxScroll = 0;
   private static final int ITEM_SIZE = 18;
   private static final int ITEMS_PER_ROW = 16;
   private static final int PADDING = 10;
   private static final int TOP_PADDING = 70;
   private static final int BOTTOM_PADDING = 35;
   private static final int SCROLLBAR_WIDTH = 8;
   private static final int MAX_PANEL_HEIGHT = 600;
   private static final int MIN_VISIBLE_ROWS = 5;
   private ButtonWidget clearSearchButton;
   private ButtonWidget sortButton;
   private SortMode currentSortMode;
   private boolean isDraggingScrollbar;
   private int scrollbarDragStartY;
   private int scrollbarDragStartOffset;
   private int cachedStartX;
   private int cachedStartY;
   private int cachedMaxY;
   private int cachedTotalRows;
   private int cachedVisibleHeight;

   public ChestTrackerScreen(ChestTrackerModule module) {
      super(Text.literal("Chest Tracker"));
      this.currentSortMode = ChestTrackerScreen.SortMode.COUNT_DESC;
      this.isDraggingScrollbar = false;
      this.scrollbarDragStartY = 0;
      this.scrollbarDragStartOffset = 0;
      this.module = module;
      this.data = module.getData();
   }

   protected void init() {
      super.init();
      ItemSearchBar itemSearchBar = (ItemSearchBar)Modules.get().get(ItemSearchBar.class);
      String initialSearch = "";
      if (itemSearchBar != null && itemSearchBar.isActive()) {
         initialSearch = (String)itemSearchBar.searchQuery.get();
      }

      this.searchField = new TextFieldWidget(this.textRenderer, this.width / 2 - 110, 20, 200, 20, Text.literal("Search items..."));
      this.searchField.setMaxLength(50);
      this.searchField.setPlaceholder(Text.literal("Search items..."));
      this.searchField.setChangedListener(this::onSearchChanged);
      if (!initialSearch.isEmpty()) {
         this.searchField.setText(initialSearch);
         this.searchQuery = initialSearch;
      }

      this.addSelectableChild(this.searchField);
      this.clearSearchButton = ButtonWidget.builder(Text.literal("§cx"), (button) -> {
         this.searchField.setText("");
         this.searchQuery = "";
         this.filterItems();
         if (itemSearchBar != null && itemSearchBar.isActive()) {
            itemSearchBar.updateSearchQuery("");
         }

      }).dimensions(this.width / 2 + 95, 20, 20, 20).build();
      this.addDrawableChild(this.clearSearchButton);
      this.sortButton = ButtonWidget.builder(Text.literal("Sort: " + this.currentSortMode.getDisplayName()), (button) -> {
         this.currentSortMode = this.currentSortMode.next();
         button.setMessage(Text.literal("Sort: " + this.currentSortMode.getDisplayName()));
         this.sortItems();
         this.filterItems();
      }).dimensions(this.width / 2 - 220, 20, 100, 20).build();
      this.addDrawableChild(this.sortButton);
      this.loadItems();
      this.filterItems();
   }

   private void loadItems() {
      this.allItems = new ArrayList();
      Map<String, Integer> itemCounts = new HashMap();
      String currentDim = this.getCurrentDimension();

      for(TrackedContainer container : this.data.getAllContainers(currentDim)) {
         for(Map.Entry<String, Integer> entry : container.getItems().entrySet()) {
            itemCounts.merge((String)entry.getKey(), (Integer)entry.getValue(), Integer::sum);
         }
      }

      for(Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
         Identifier id = Identifier.tryParse((String)entry.getKey());
         if (id != null && Registries.ITEM.containsId(id)) {
            Item item = (Item)Registries.ITEM.get(id);
            this.allItems.add(new ItemEntry(item, (Integer)entry.getValue()));
         }
      }

      this.sortItems();
   }

   private void sortItems() {
      switch (this.currentSortMode.ordinal()) {
         case 0 -> this.allItems.sort((a, b) -> Integer.compare(b.count, a.count));
         case 1 -> this.allItems.sort((a, b) -> Integer.compare(a.count, b.count));
         case 2 -> this.allItems.sort((a, b) -> a.item.getName().getString().compareToIgnoreCase(b.item.getName().getString()));
         case 3 -> this.allItems.sort((a, b) -> b.item.getName().getString().compareToIgnoreCase(a.item.getName().getString()));
      }

   }

   private void filterItems() {
      if (this.allItems == null) {
         this.allItems = new ArrayList();
      }

      if (this.searchQuery.isEmpty()) {
         this.filteredItems = new ArrayList(this.allItems);
      } else {
         String query = this.searchQuery.toLowerCase();
         this.filteredItems = (List)this.allItems.stream().filter((entry) -> entry.item.getName().getString().toLowerCase().contains(query)).collect(Collectors.toList());
      }

      int rows = (int)Math.ceil((double)this.filteredItems.size() / (double)16.0F);
      int contentHeight = Math.max(rows * 18, 90);
      int maxPanelHeight = Math.min(600, this.height - 70 - 35);
      int actualPanelHeight = Math.min(contentHeight, maxPanelHeight);
      int visibleRows = actualPanelHeight / 18;
      this.maxScroll = Math.max(0, rows - visibleRows);
      this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll);
   }

   private void onSearchChanged(String query) {
      this.searchQuery = query;
      this.filterItems();
      ItemSearchBar itemSearchBar = (ItemSearchBar)Modules.get().get(ItemSearchBar.class);
      if (itemSearchBar != null && itemSearchBar.isActive()) {
         itemSearchBar.updateSearchQuery(query);
      }

   }

   private void updateCachedBounds() {
      this.cachedStartX = this.width / 2 - 144;
      this.cachedStartY = 70;
      this.cachedTotalRows = (int)Math.ceil((double)this.filteredItems.size() / (double)16.0F);
      int contentHeight = Math.max(this.cachedTotalRows * 18, 90);
      int maxPanelHeight = Math.min(600, this.height - 70 - 35);
      this.cachedVisibleHeight = Math.min(contentHeight, maxPanelHeight);
      this.cachedMaxY = 70 + this.cachedVisibleHeight;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.updateCachedBounds();
      context.fill(0, 0, this.width, this.height, -268435456);
      int panelWidth = 308;
      int panelX = this.width / 2 - panelWidth / 2;
      int panelY = 65;
      int totalRows = (int)Math.ceil((double)this.filteredItems.size() / (double)16.0F);
      int contentHeight = Math.max(totalRows * 18, 90);
      int maxPanelHeight = Math.min(600, this.height - 70 - 35);
      int panelContentHeight = Math.min(contentHeight, maxPanelHeight);
      int panelBottom = panelY + panelContentHeight + 15;
      context.fill(panelX, panelY, panelX + panelWidth, panelBottom, -15066598);
      context.fill(panelX, panelY, panelX + panelWidth, panelY + 2, -11184811);
      context.fill(panelX, panelY, panelX + 2, panelBottom, -11184811);
      context.fill(panelX + panelWidth - 2, panelY, panelX + panelWidth, panelBottom, -14013910);
      context.fill(panelX, panelBottom - 2, panelX + panelWidth, panelBottom, -14013910);
      String currentDim = this.getCurrentDimension();
      String dimName = currentDim.contains("overworld") ? "Overworld" : (currentDim.contains("nether") ? "Nether" : (currentDim.contains("end") ? "End" : currentDim));
      context.drawCenteredTextWithShadow(this.textRenderer, "§l§eChest Tracker §r§7- " + dimName, this.width / 2, 8, 16777215);
      this.searchField.render(context, mouseX, mouseY, delta);
      this.clearSearchButton.visible = !this.searchQuery.isEmpty();
      this.clearSearchButton.active = !this.searchQuery.isEmpty();
      this.renderItemGrid(context, mouseX, mouseY);
      this.renderScrollbar(context, mouseX, mouseY);
      super.render(context, mouseX, mouseY, delta);
      this.renderTooltip(context, mouseX, mouseY);
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   private void renderItemGrid(DrawContext context, int mouseX, int mouseY) {
      int index = this.scrollOffset * 16;
      int maxIndex = this.filteredItems.size();
      int panelWidth = 308;
      int panelX = this.width / 2 - panelWidth / 2;
      context.enableScissor(panelX + 10, 70, panelX + panelWidth - 10, this.cachedMaxY);
      int visibleRows = this.cachedVisibleHeight / 18 + 2;
      int maxRow = Math.min(visibleRows, this.cachedTotalRows - this.scrollOffset);

      for(int row = 0; row < maxRow; ++row) {
         for(int col = 0; col < 16 && index < maxIndex; ++col) {
            ItemEntry entry = (ItemEntry)this.filteredItems.get(index);
            int x = this.cachedStartX + col * 18;
            int y = this.cachedStartY + row * 18;
            if (y + 18 > 70 && y < this.cachedMaxY) {
               boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
               context.fill(x, y, x + 18, y + 18, -12961222);
               if (hovered) {
                  context.fill(x, y, x + 18, y + 1, -16711936);
                  context.fill(x, y, x + 1, y + 18, -16711936);
                  context.fill(x + 18 - 1, y, x + 18, y + 18, -16711936);
                  context.fill(x, y + 18 - 1, x + 18, y + 18, -16711936);
               } else {
                  context.fill(x, y, x + 18, y + 1, -11184811);
                  context.fill(x, y, x + 1, y + 18, -11184811);
                  context.fill(x + 18 - 1, y, x + 18, y + 18, -14013910);
                  context.fill(x, y + 18 - 1, x + 18, y + 18, -14013910);
               }

               context.drawItem(new ItemStack(entry.item), x + 1, y + 1);
               ++index;
            } else {
               ++index;
            }
         }

         if (index >= maxIndex) {
            break;
         }
      }

      context.disableScissor();
      String itemCountText;
      if (this.searchQuery.isEmpty()) {
         itemCountText = String.format("§e%d §7unique items tracked", this.filteredItems.size());
      } else {
         itemCountText = String.format("§e%d §7items found (filtered from §e%d§7 total)", this.filteredItems.size(), this.allItems.size());
      }

      int countTextWidth = this.textRenderer.getWidth(itemCountText);
      int countX = this.width / 2 - countTextWidth / 2;
      int countY = 52;
      context.fill(countX - 4, countY - 2, countX + countTextWidth + 4, countY + 10, -587202560);
      context.drawText(this.textRenderer, itemCountText, countX, countY, -22016, false);
   }

   private String formatNumber(int number) {
      if (number >= 1000000) {
         return String.format("%.1fM", (double)number / (double)1000000.0F);
      } else {
         return number >= 1000 ? String.format("%.1fK", (double)number / (double)1000.0F) : String.valueOf(number);
      }
   }

   private void renderScrollbar(DrawContext context, int mouseX, int mouseY) {
      if (this.maxScroll > 0) {
         int panelWidth = 308;
         int scrollbarX = this.width / 2 + panelWidth / 2 + 5;
         int scrollbarY = 70;
         int scrollbarHeight = this.cachedVisibleHeight;
         context.fill(scrollbarX, scrollbarY, scrollbarX + 8, scrollbarY + scrollbarHeight, -14013910);
         int visibleRows = scrollbarHeight / 18;
         int thumbHeight = Math.max(20, (int)((double)visibleRows / (double)this.cachedTotalRows * (double)scrollbarHeight));
         int scrollableHeight = scrollbarHeight - thumbHeight;
         int thumbY = scrollbarY + (this.maxScroll > 0 ? (int)((double)this.scrollOffset / (double)this.maxScroll * (double)scrollableHeight) : 0);
         boolean hovered = mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
         int thumbColor = this.isDraggingScrollbar ? -16711936 : (hovered ? -16724992 : -16742400);
         context.fill(scrollbarX + 1, thumbY, scrollbarX + 8 - 1, thumbY + thumbHeight, thumbColor);
         context.fill(scrollbarX + 1, thumbY, scrollbarX + 8 - 1, thumbY + 1, -16711936);
         context.fill(scrollbarX + 1, thumbY + thumbHeight - 1, scrollbarX + 8 - 1, thumbY + thumbHeight, -16755456);
      }
   }

   private void renderTooltip(DrawContext context, int mouseX, int mouseY) {
      int index = this.scrollOffset * 16;
      int maxIndex = this.filteredItems.size();
      int visibleRows = this.cachedVisibleHeight / 18 + 2;
      int maxRow = Math.min(visibleRows, this.cachedTotalRows - this.scrollOffset);

      for(int row = 0; row < maxRow; ++row) {
         for(int col = 0; col < 16; ++col) {
            if (index >= maxIndex) {
               return;
            }

            int x = this.cachedStartX + col * 18;
            int y = this.cachedStartY + row * 18;
            if (y + 18 > 70 && y < this.cachedMaxY) {
               if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                  ItemEntry entry = (ItemEntry)this.filteredItems.get(index);
                  List<TrackedContainer> containers = this.data.searchItem(entry.item);
                  int withinRange = 0;
                  double renderDist = this.module.getRenderDistance();
                  if (this.client != null && this.client.player != null) {
                     for(TrackedContainer container : containers) {
                        BlockPos pos = container.getPosition();
                        double distSq = this.client.player.squaredDistanceTo((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)0.5F, (double)pos.getZ() + (double)0.5F);
                        if (distSq <= renderDist * renderDist) {
                           ++withinRange;
                        }
                     }
                  }

                  List<Text> tooltip = new ArrayList();
                  tooltip.add(Text.literal("§f§l" + entry.item.getName().getString()));
                  tooltip.add(Text.literal(""));
                  String var10001 = this.formatCountFull(entry.count);
                  tooltip.add(Text.literal("§7Total Amount: §a" + var10001));
                  tooltip.add(Text.literal("§7Found in: §e" + containers.size() + " §7container(s)"));
                  if (withinRange > 0 && withinRange < containers.size()) {
                     tooltip.add(Text.literal("§7Will highlight: §e" + withinRange + " §7nearby"));
                     tooltip.add(Text.literal("§8(Increase render distance for more)"));
                  } else if (withinRange == 0) {
                     tooltip.add(Text.literal("§cAll containers are far away!"));
                     tooltip.add(Text.literal("§8(Increase render distance in settings)"));
                  }

                  tooltip.add(Text.literal(""));
                  tooltip.add(Text.literal("§e§l» Click to Highlight All Within Range «"));
                  ItemSearchBar itemSearchBar = (ItemSearchBar)Modules.get().get(ItemSearchBar.class);
                  if (itemSearchBar != null && itemSearchBar.isActive()) {
                     tooltip.add(Text.literal("§7(Also searches in ItemSearchBar)"));
                  }

                  context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
                  return;
               }

               ++index;
            } else {
               ++index;
            }
         }
      }

   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      if (this.maxScroll > 0 && button == 0) {
         int panelWidth = 308;
         int scrollbarX = this.width / 2 + panelWidth / 2 + 5;
         int scrollbarY = 70;
         int totalRows = (int)Math.ceil((double)this.filteredItems.size() / (double)16.0F);
         int contentHeight = Math.max(totalRows * 18, 90);
         int maxPanelHeight = Math.min(600, this.height - 70 - 35);
         int scrollbarHeight = Math.min(contentHeight, maxPanelHeight);
         if (mouseX >= (double)scrollbarX && mouseX <= (double)(scrollbarX + 8) && mouseY >= (double)scrollbarY && mouseY <= (double)(scrollbarY + scrollbarHeight)) {
            int visibleRows = scrollbarHeight / 18;
            int thumbHeight = Math.max(20, (int)((double)visibleRows / (double)totalRows * (double)scrollbarHeight));
            int scrollableHeight = scrollbarHeight - thumbHeight;
            int thumbY = scrollbarY + (this.maxScroll > 0 ? (int)((double)this.scrollOffset / (double)this.maxScroll * (double)scrollableHeight) : 0);
            if (mouseY >= (double)thumbY && mouseY <= (double)(thumbY + thumbHeight)) {
               this.isDraggingScrollbar = true;
               this.scrollbarDragStartY = (int)mouseY;
               this.scrollbarDragStartOffset = this.scrollOffset;
               return true;
            }

            double clickRatio = (mouseY - (double)scrollbarY) / (double)scrollableHeight;
            this.scrollOffset = (int)(clickRatio * (double)this.maxScroll);
            this.scrollOffset = Math.max(0, Math.min(this.maxScroll, this.scrollOffset));
            return true;
         }
      }

      int index = this.scrollOffset * 16;
      int maxIndex = this.filteredItems.size();
      int visibleRows = this.cachedVisibleHeight / 18 + 2;
      int maxRow = Math.min(visibleRows, this.cachedTotalRows - this.scrollOffset);

      for(int row = 0; row < maxRow; ++row) {
         for(int col = 0; col < 16 && index < maxIndex; ++col) {
            int x = this.cachedStartX + col * 18;
            int y = this.cachedStartY + row * 18;
            if (y + 18 > 70 && y < this.cachedMaxY) {
               if (mouseX >= (double)x && mouseX < (double)(x + 18) && mouseY >= (double)y && mouseY < (double)(y + 18)) {
                  ItemEntry entry = (ItemEntry)this.filteredItems.get(index);
                  this.onItemClicked(entry);
                  return true;
               }

               ++index;
            } else {
               ++index;
            }
         }
      }

      return super.mouseClicked(click, doubled);
   }

   private void onItemClicked(ItemEntry entry) {
      List<TrackedContainer> results = this.data.searchItem(entry.item);
      this.module.searchItem(entry.item);
      ItemSearchBar itemSearchBar = (ItemSearchBar)Modules.get().get(ItemSearchBar.class);
      if (itemSearchBar != null && itemSearchBar.isActive()) {
         String itemName = entry.item.getName().getString();
         itemSearchBar.updateSearchQuery(itemName);
         this.searchField.setText(itemName);
         this.searchQuery = itemName;
         this.filterItems();
      }

      int withinRange = 0;
      if (this.client != null && this.client.player != null) {
         double renderDist = this.module.getRenderDistance();

         for(TrackedContainer container : results) {
            BlockPos pos = container.getPosition();
            double distSq = this.client.player.squaredDistanceTo((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)0.5F, (double)pos.getZ() + (double)0.5F);
            if (distSq <= renderDist * renderDist) {
               ++withinRange;
            }
         }
      }

      if (this.client != null && this.client.player != null) {
         String msg = withinRange < results.size() ? String.format("§aLit: §e%d§7/§f%d §7(%d far)", withinRange, results.size(), results.size() - withinRange) : String.format("§aLit: §e%d §7boxes", results.size());
         this.client.player.sendMessage(Text.literal(msg), false);
      }

      this.close();
   }

   public boolean mouseDragged(Click click, double deltaX, double deltaY) {
      double mouseX = click.x();
      double mouseY = click.y();
      if (this.isDraggingScrollbar && this.maxScroll > 0) {
         int totalRows = (int)Math.ceil((double)this.filteredItems.size() / (double)16.0F);
         int contentHeight = Math.max(totalRows * 18, 90);
         int maxPanelHeight = Math.min(600, this.height - 70 - 35);
         int scrollbarHeight = Math.min(contentHeight, maxPanelHeight);
         int visibleRows = scrollbarHeight / 18;
         int thumbHeight = Math.max(20, (int)((double)visibleRows / (double)totalRows * (double)scrollbarHeight));
         int scrollableHeight = scrollbarHeight - thumbHeight;
         int dragDelta = (int)mouseY - this.scrollbarDragStartY;
         double scrollRatio = (double)dragDelta / (double)scrollableHeight;
         int newOffset = this.scrollbarDragStartOffset + (int)(scrollRatio * (double)this.maxScroll);
         this.scrollOffset = Math.max(0, Math.min(this.maxScroll, newOffset));
         return true;
      } else {
         return super.mouseDragged(click, deltaX, deltaY);
      }
   }

   public boolean mouseReleased(Click click) {
      int button = click.button();
      if (this.isDraggingScrollbar && button == 0) {
         this.isDraggingScrollbar = false;
         return true;
      } else {
         return super.mouseReleased(click);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (verticalAmount > (double)0.0F) {
         this.scrollOffset = Math.max(0, this.scrollOffset - 1);
      } else if (verticalAmount < (double)0.0F) {
         this.scrollOffset = Math.min(this.maxScroll, this.scrollOffset + 1);
      }

      return true;
   }

   public boolean shouldPause() {
      return false;
   }

   private String formatCount(int count) {
      if (count >= 1000000) {
         return String.format("%.1fM", (double)count / (double)1000000.0F);
      } else {
         return count >= 1000 ? String.format("%.1fK", (double)count / (double)1000.0F) : String.valueOf(count);
      }
   }

   private String formatCountFull(int count) {
      return String.format("%,d", count);
   }

   private String getCurrentDimension() {
      return this.client != null && this.client.world != null ? this.client.world.getRegistryKey().getValue().toString() : "unknown";
   }

   private static class ItemEntry {
      final Item item;
      final int count;

      ItemEntry(Item item, int count) {
         this.item = item;
         this.count = count;
      }
   }

   private static enum SortMode {
      COUNT_DESC("Count ↓"),
      COUNT_ASC("Count ↑"),
      NAME_ASC("Name A-Z"),
      NAME_DESC("Name Z-A");

      private final String displayName;

      private SortMode(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public SortMode next() {
         SortMode[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }

      private static SortMode[] $values() {
         return new SortMode[]{COUNT_DESC, COUNT_ASC, NAME_ASC, NAME_DESC};
      }
   }
}
