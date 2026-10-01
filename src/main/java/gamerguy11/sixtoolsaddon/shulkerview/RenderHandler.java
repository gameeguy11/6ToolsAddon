package gamerguy11.sixtoolsaddon.shulkerview;

import gamerguy11.sixtoolsaddon.mixin.shulkerview.DuckHandledScreen;
import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.Click;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.gui.screen.Screen;
import org.joml.Math;
import org.joml.Vector2d;

public class RenderHandler {
   private static final int MARGIN = 4;
   private static final int PAD = 4;
   private static final int CELL = 18;
   private static final int HEADER_HEIGHT = 11;
   private final ShulkerView config;
   private final Vector2d clicked = new Vector2d();
   private final Map<Integer, Vector2d> offsets = new HashMap();
   private final List<Hit> hits = new ArrayList();
   private int draggingSlot = -1;
   private int height;
   private int offset;
   private int rows;
   private int cols;
   private int startX;
   private int currentY;
   private float scale;

   public RenderHandler(ShulkerView config) {
      this.config = config;
   }

   public void render(DrawContext context, double mouseX, double mouseY) {
      this.scale = this.config.getScale();
      this.hits.clear();
      boolean right = this.config.isAnchorRight();
      boolean overflowed = false;
      this.startX = right ? 4 : 4 + this.config.getOffsetX();
      this.currentY = this.config.isBothSides() ? 4 + this.config.getOffsetY() : 4 + this.config.getOffsetY() + this.offset;
      context.createNewRootLayer();
      context.getMatrices().pushMatrix();
      context.getMatrices().scale(this.scale, this.scale);

      ShulkerInfo shulkerInfo;
      for(ObjectListIterator var8 = this.config.getUpdateHandler().getShulkerList().iterator(); var8.hasNext(); this.drawShulkerInfo(context, shulkerInfo, mouseX, mouseY)) {
         shulkerInfo = (ShulkerInfo)var8.next();
         this.updateShulkerInfo(shulkerInfo);
         if ((float)this.currentY >= (float)context.getScaledWindowHeight() / this.scale && this.config.isBothSides() && !overflowed) {
            overflowed = true;
            right = !right;
            this.currentY = 4 + this.config.getOffsetY();
         }

         if (right) {
            this.startX = (int)((float)context.getScaledWindowWidth() / this.scale) - this.panelWidth(shulkerInfo) - 4 - this.config.getOffsetX();
         } else {
            this.startX = 4 + this.config.getOffsetX();
         }
      }

      context.getMatrices().popMatrix();
      this.height = this.currentY - this.offset;
      this.clicked.set((double)0.0F);
   }

   private int panelWidth(ShulkerInfo info) {
      int width = this.cols * 18 - 1 + 8;
      if (info.count() > 1) {
         width = Math.max(width, this.badgeWidth(info) + 8);
      }

      return width;
   }

   private int badgeWidth(ShulkerInfo info) {
      return MeteorClient.mc.textRenderer.getWidth(info.count() + "x") + 7;
   }

   private void drawShulkerInfo(DrawContext context, ShulkerInfo info, double mouseX, double mouseY) {
      int width = this.panelWidth(info);
      boolean stacked = info.count() > 1;
      int top = 4 + (stacked ? 11 : 0);
      int height = top + this.rows * 18 - 1 + 4;
      Vector2d moved = (Vector2d)this.offsets.get(info.slot());
      int x = this.startX + (moved == null ? 0 : (int)moved.x);
      int y = this.currentY + (moved == null ? 0 : (int)moved.y);
      if ((float)y * this.scale < (float)context.getScaledWindowHeight()) {
         this.hits.add(new Hit(info.slot(), x, y, width, height));
         this.drawPanel(context, x, y, width, height, info.color());
         if (stacked) {
            this.drawBadge(context, info, x + width - 4 - this.badgeWidth(info), y + 3);
         }

         int gridX = x + (width - (this.cols * 18 - 1)) / 2;
         int count = 0;
         int hoveredIndex = -1;

         for(ItemStack stack : info.stacks()) {
            if (info.compact() && stack.isEmpty()) {
               break;
            }

            int cellX = gridX + count % 9 * 18;
            int cellY = y + top + count / 9 * 18;
            boolean hovered = this.isHoveredItem(mouseX, mouseY, cellX, cellY);
            context.fill(cellX, cellY, cellX + 18 - 1, cellY + 18 - 1, hovered ? 1090519039 : 805306368);
            this.drawStack(context, stack, cellX + 1, cellY + 1);
            if (hovered) {
               hoveredIndex = count;
            }

            ++count;
         }

         if (count == 0 && info.compact()) {
            context.fill(gridX, y + top, gridX + 18 - 1, y + top + 18 - 1, 805306368);
            context.drawItem(info.shulker(), gridX + 1, y + top + 1);
         }

         if (hoveredIndex >= 0) {
            this.drawTooltip(context, (ItemStack)info.stacks().get(hoveredIndex), mouseX, mouseY);
         }

         if (this.clicked.lengthSquared() != (double)0.0F && this.isHovered(this.clicked.x, this.clicked.y, x, y, (float)width, (float)height)) {
            this.clickShulker(info);
         }
      }

      this.currentY += height + this.config.getSpacing();
   }

   private void drawPanel(DrawContext context, int x, int y, int w, int h, int color) {
      int rgb = color & 16777215;
      int outline = -1879048192 | rgb;
      int accent = -16777216 | rgb;
      context.fill(x + 1, y + 1, x + w - 1, y + h - 1, this.config.getBackground());
      context.fill(x + 1, y, x + w - 1, y + 1, outline);
      context.fill(x + 1, y + h - 1, x + w - 1, y + h, outline);
      context.fill(x, y + 1, x + 1, y + h - 1, outline);
      context.fill(x + w - 1, y + 1, x + w, y + h - 1, outline);
      context.fill(x + 2, y + 1, x + w - 2, y + 3, accent);
   }

   private void drawBadge(DrawContext context, ShulkerInfo info, int x, int y) {
      int var10000 = info.count();
      String label = var10000 + "x";
      int w = this.badgeWidth(info);
      int h = 10;
      int rgb = info.color() & 16777215;
      int fill = -16777216 | rgb;
      context.fill(x + 1, y, x + w - 1, y + h, fill);
      context.fill(x, y + 1, x + w, y + h - 1, fill);
      int r = rgb >> 16 & 255;
      int g = rgb >> 8 & 255;
      int b = rgb & 255;
      double luminance = (0.299 * (double)r + 0.587 * (double)g + 0.114 * (double)b) / (double)255.0F;
      int textColor = luminance > 0.6 ? -15724528 : -1;
      context.drawText(MeteorClient.mc.textRenderer, label, x + 4, y + 1, textColor, false);
   }

   public boolean mouseClick(Click click) {
      if (click.button() == 0) {
         this.clicked.set(click.x(), click.y());
         return false;
      } else {
         int hit = this.hitTest(click.x(), click.y());
         if (hit < 0) {
            return false;
         } else if (click.button() == 1) {
            this.draggingSlot = hit;
            return true;
         } else if (click.button() == 2) {
            this.offsets.clear();
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean mouseDrag(Click click, double dx, double dy) {
      if (click.button() == 1 && this.draggingSlot >= 0) {
         ((Vector2d)this.offsets.computeIfAbsent(this.draggingSlot, (k) -> new Vector2d())).add(dx / (double)this.scale, dy / (double)this.scale);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseRelease(Click click) {
      if (click.button() == 1 && this.draggingSlot >= 0) {
         this.draggingSlot = -1;
         return true;
      } else {
         return false;
      }
   }

   private int hitTest(double mouseX, double mouseY) {
      double mx = mouseX / (double)this.scale;
      double my = mouseY / (double)this.scale;

      for(int i = this.hits.size() - 1; i >= 0; --i) {
         Hit h = (Hit)this.hits.get(i);
         if (mx >= (double)h.x() && mx <= (double)(h.x() + h.w()) && my >= (double)h.y() && my <= (double)(h.y() + h.h())) {
            return h.slot();
         }
      }

      return -1;
   }

   public void mouseScroll(double x, double y, double amount) {
      if (amount != (double)0.0F) {
         Screen var8 = MeteorClient.mc.currentScreen;
         if (var8 instanceof DuckHandledScreen) {
            DuckHandledScreen screen = (DuckHandledScreen)var8;
            Slot slot = screen.shulkerView$getFocused();
            if (slot != null && slot.getStack().isOf(Items.BUNDLE)) {
               return;
            }
         }

         float f = Math.min((float)(-this.height) + (float)MeteorClient.mc.getWindow().getScaledHeight() / this.scale, 0.0F);
         this.offset = (int)MathHelper.clamp((double)this.offset + Math.ceil(amount) * (double)10.0F, (double)f, (double)0.0F);
      }
   }

   private void drawStack(DrawContext ctx, ItemStack stack, int x, int y) {
      ctx.drawItem(stack, x, y);
      if (stack.getCount() > 999) {
         String text = "%.1fk".formatted((float)stack.getCount() / 1000.0F);
         ctx.drawStackOverlay(MeteorClient.mc.textRenderer, stack, x, y, text);
      } else {
         ctx.drawStackOverlay(MeteorClient.mc.textRenderer, stack, x, y);
      }

   }

   private void drawTooltip(DrawContext ctx, ItemStack stack, double mouseX, double mouseY) {
      if (this.config.isTooltips() && !stack.isEmpty()) {
         float f = 1.0F / this.scale;
         ctx.getMatrices().pushMatrix();
         ctx.getMatrices().scale(f, f);
         ctx.drawItemTooltip(MeteorClient.mc.textRenderer, stack, (int)mouseX, (int)mouseY);
         ctx.getMatrices().popMatrix();
      }
   }

   private void clickShulker(ShulkerInfo info) {
      int id = MeteorClient.mc.player.currentScreenHandler.syncId;
      MeteorClient.mc.interactionManager.clickSlot(id, info.slot(), 0, SlotActionType.PICKUP, MeteorClient.mc.player);
      this.clicked.set((double)0.0F);
   }

   private void updateShulkerInfo(ShulkerInfo info) {
      int size = info.stacks().size();
      if (info.compact()) {
         size = 0;

         for(ItemStack s : info.stacks()) {
            if (s.isEmpty()) {
               break;
            }

            ++size;
         }

         size = Math.max(1, size);
      }

      this.rows = (int)Math.ceil((float)size / 9.0F);
      this.cols = MathHelper.clamp(size, 1, 9);
   }

   private boolean isHovered(double mx, double my, int x, int y, float w, float h) {
      mx /= (double)this.scale;
      my /= (double)this.scale;
      return mx >= (double)x && mx <= (double)((float)x + w) && my >= (double)y && my <= (double)((float)y + h);
   }

   private boolean isHoveredItem(double mouseX, double mouseY, int x, int y) {
      mouseX /= (double)this.scale;
      mouseY /= (double)this.scale;
      return mouseX >= (double)x && mouseX < (double)(x + 18 - 1) && mouseY >= (double)y && mouseY < (double)(y + 18 - 1);
   }

   private static record Hit(int slot, int x, int y, int w, int h) {
   }
}
