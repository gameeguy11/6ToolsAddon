package gamerguy11.sixtoolsaddon.shulkerview;

import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import gamerguy11.sixtoolsaddon.mixin.shulkerview.DuckHandledScreen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.MathHelper;
import org.joml.Math;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RenderHandler {
    private static final int MARGIN = 4;      // distance from the screen edge
    private static final int PAD = 4;         // padding inside each panel
    private static final int CELL = 18;       // slot pitch (16px item + 2px)
    private static final int HEADER_HEIGHT = 11; // extra room for the count badge on stacked previews

    private final ShulkerView config;

    private record Hit(int slot, int x, int y, int w, int h) {}

    private final Vector2d clicked = new Vector2d();
    /** Manual position offsets (in preview space) per shulker slot, set by right-click dragging. */
    private final Map<Integer, Vector2d> offsets = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private int draggingSlot = -1;
    private int height, offset;
    private int rows, cols;

    private int startX;
    private int currentY;
    private float scale;

    public RenderHandler(ShulkerView config) {
        this.config = config;
    }

    public void render(DrawContext context, double mouseX, double mouseY) {
        scale = config.getScale();
        hits.clear();

        boolean right = config.isAnchorRight();
        boolean overflowed = false;
        startX = right ? MARGIN : MARGIN + config.getOffsetX();
        currentY = config.isBothSides() ? MARGIN + config.getOffsetY() : MARGIN + config.getOffsetY() + offset;

        context.createNewRootLayer();
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        for (ShulkerInfo shulkerInfo : config.getUpdateHandler().getShulkerList()) {
            updateShulkerInfo(shulkerInfo);

            if (currentY >= context.getScaledWindowHeight() / scale && config.isBothSides() && !overflowed) {
                overflowed = true;
                right = !right;
                currentY = MARGIN + config.getOffsetY();
            }

            if (right) {
                startX = (int) (context.getScaledWindowWidth() / scale) - panelWidth(shulkerInfo) - MARGIN - config.getOffsetX();
            } else {
                startX = MARGIN + config.getOffsetX();
            }
            drawShulkerInfo(context, shulkerInfo, mouseX, mouseY);
        }
        context.getMatrices().popMatrix();

        height = currentY - offset;
        clicked.set(0);
    }

    private int panelWidth(ShulkerInfo info) {
        int width = cols * CELL - 1 + PAD * 2;
        if (info.count() > 1) width = Math.max(width, badgeWidth(info) + PAD * 2);
        return width;
    }

    private int badgeWidth(ShulkerInfo info) {
        return mc.textRenderer.getWidth(info.count() + "x") + 7;
    }

    private void drawShulkerInfo(DrawContext context, ShulkerInfo info, double mouseX, double mouseY) {
        int width = panelWidth(info);
        boolean stacked = info.count() > 1;
        int top = PAD + (stacked ? HEADER_HEIGHT : 0);
        int height = top + rows * CELL - 1 + PAD;

        Vector2d moved = offsets.get(info.slot());
        int x = startX + (moved == null ? 0 : (int) moved.x);
        int y = currentY + (moved == null ? 0 : (int) moved.y);

        if (y * scale < context.getScaledWindowHeight()) {
            hits.add(new Hit(info.slot(), x, y, width, height));
            drawPanel(context, x, y, width, height, info.color());

            if (stacked) drawBadge(context, info, x + width - PAD - badgeWidth(info), y + 3);

            // Center the slot grid horizontally when the panel is wider than the grid (narrow stacked previews).
            int gridX = x + (width - (cols * CELL - 1)) / 2;
            int count = 0;
            int hoveredIndex = -1;

            for (ItemStack stack : info.stacks()) {
                if (info.compact() && stack.isEmpty()) break;
                int cellX = gridX + (count % 9) * CELL;
                int cellY = y + top + (count / 9) * CELL;

                boolean hovered = isHoveredItem(mouseX, mouseY, cellX, cellY);
                context.fill(cellX, cellY, cellX + CELL - 1, cellY + CELL - 1, hovered ? 0x40FFFFFF : 0x30000000);
                drawStack(context, stack, cellX + 1, cellY + 1);
                if (hovered) hoveredIndex = count;
                count++;
            }

            if (count == 0 && info.compact()) {
                context.fill(gridX, y + top, gridX + CELL - 1, y + top + CELL - 1, 0x30000000);
                context.drawItem(info.shulker(), gridX + 1, y + top + 1);
            }

            // Tooltip last so nothing is drawn over it.
            if (hoveredIndex >= 0) drawTooltip(context, info.stacks().get(hoveredIndex), mouseX, mouseY);

            if (clicked.lengthSquared() != 0 && isHovered(clicked.x, clicked.y, x, y, width, height)) clickShulker(info);
        }

        currentY += height + config.getSpacing();
    }

    /** Rounded panel: translucent body, thin outline tinted with the shulker color, and an accent strip on top. */
    private void drawPanel(DrawContext context, int x, int y, int w, int h, int color) {
        int rgb = color & 0xFFFFFF;
        int outline = (0x90 << 24) | rgb;
        int accent = (0xFF << 24) | rgb;

        // Body (corners notched by 1px for a rounded look)
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, config.getBackground());

        // Outline
        context.fill(x + 1, y, x + w - 1, y + 1, outline);
        context.fill(x + 1, y + h - 1, x + w - 1, y + h, outline);
        context.fill(x, y + 1, x + 1, y + h - 1, outline);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, outline);

        // Accent strip along the top edge
        context.fill(x + 2, y + 1, x + w - 2, y + 3, accent);
    }

    /** Pill-shaped "3x" badge in the shulker's color with readable text. */
    private void drawBadge(DrawContext context, ShulkerInfo info, int x, int y) {
        String label = info.count() + "x";
        int w = badgeWidth(info);
        int h = 10;
        int rgb = info.color() & 0xFFFFFF;
        int fill = (0xFF << 24) | rgb;

        context.fill(x + 1, y, x + w - 1, y + h, fill);
        context.fill(x, y + 1, x + w, y + h - 1, fill);

        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        int textColor = luminance > 0.6 ? 0xFF101010 : 0xFFFFFFFF;

        context.drawText(mc.textRenderer, label, x + 4, y + 1, textColor, false);
    }

    /** Returns true if the click was consumed (right/middle click on a preview). */
    public boolean mouseClick(Click click) {
        if (click.button() == 0) {
            clicked.set(click.x(), click.y());
            return false;
        }

        int hit = hitTest(click.x(), click.y());
        if (hit < 0) return false;

        if (click.button() == 1) { // right click: start moving this preview
            draggingSlot = hit;
            return true;
        }
        if (click.button() == 2) { // middle click: reset all moved previews
            offsets.clear();
            return true;
        }
        return false;
    }

    public boolean mouseDrag(Click click, double dx, double dy) {
        if (click.button() != 1 || draggingSlot < 0) return false;
        offsets.computeIfAbsent(draggingSlot, k -> new Vector2d()).add(dx / scale, dy / scale);
        return true;
    }

    public boolean mouseRelease(Click click) {
        if (click.button() != 1 || draggingSlot < 0) return false;
        draggingSlot = -1;
        return true;
    }

    /** Slot of the topmost preview under the mouse, or -1. */
    private int hitTest(double mouseX, double mouseY) {
        double mx = mouseX / scale, my = mouseY / scale;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx <= h.x() + h.w() && my >= h.y() && my <= h.y() + h.h()) return h.slot();
        }
        return -1;
    }

    public void mouseScroll(double x, double y, double amount) {
        if (amount == 0) return;
        if (mc.currentScreen instanceof DuckHandledScreen screen) {
            Slot slot = screen.shulkerView$getFocused();
            if (slot != null && slot.getStack().isOf(Items.BUNDLE)) return;
        }

        float f = Math.min(-height + mc.getWindow().getScaledHeight() / scale, 0);
        this.offset = (int) MathHelper.clamp(offset + Math.ceil(amount) * 10, f, 0);
    }

    private void drawStack(DrawContext ctx, ItemStack stack, int x, int y) {
        ctx.drawItem(stack, x, y);
        if (stack.getCount() > 999) {
            String text = "%.1fk".formatted(stack.getCount() / 1000f);
            ctx.drawStackOverlay(mc.textRenderer, stack, x, y, text);
        } else {
            ctx.drawStackOverlay(mc.textRenderer, stack, x, y);
        }
    }

    private void drawTooltip(DrawContext ctx, ItemStack stack, double mouseX, double mouseY) {
        if (!config.isTooltips() || stack.isEmpty()) return;
        float f = 1f / scale;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(f, f);
        ctx.drawItemTooltip(mc.textRenderer, stack, (int) mouseX, (int) mouseY);
        ctx.getMatrices().popMatrix();
    }

    private void clickShulker(ShulkerInfo info) {
        int id = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(id, info.slot(), 0, SlotActionType.PICKUP, mc.player);
        clicked.set(0);
    }

    private void updateShulkerInfo(ShulkerInfo info) {
        int size = info.stacks().size();

        if (info.compact()) {
            size = 0;
            for (ItemStack s : info.stacks()) {
                if (s.isEmpty()) break;
                size++;
            }
            size = Math.max(1, size);
        }

        rows = (int) Math.ceil(size / 9f);
        cols = MathHelper.clamp(size, 1, 9);
    }

    private boolean isHovered(double mx, double my, int x, int y, float w, float h) {
        mx /= scale;
        my /= scale;
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean isHoveredItem(double mouseX, double mouseY, int x, int y) {
        mouseX /= scale;
        mouseY /= scale;
        return mouseX >= x && mouseX < x + CELL - 1 && mouseY >= y && mouseY < y + CELL - 1;
    }
}
