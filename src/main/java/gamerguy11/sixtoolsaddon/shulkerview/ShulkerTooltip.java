package gamerguy11.sixtoolsaddon.shulkerview;

import gamerguy11.sixtoolsaddon.gui.ShulkerPreviewScreen;
import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ShulkerTooltip {
    private static final int SLOTS = 27;
    private static final int COLUMNS = 9;
    private static final int CELL = 17;
    private static final int GRID_WIDTH = COLUMNS * CELL + 2;
    private static final int GRID_HEIGHT = 3 * CELL + 2;
    private static final int PADDING = 4;
    private static final int TITLE_HEIGHT = 12;
    private static final float PREVIEW_SCALE = 0.625F;
    private static final float PREVIEW_OFFSET_X = 11.0F;

    private static ItemStack lockedStack;
    private static int lockedX;
    private static int lockedY;

    private ShulkerTooltip() {
    }

    public static ShulkerBoxBlock getBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item) {
            Block block = item.getBlock();
            if (block instanceof ShulkerBoxBlock shulker) return shulker;
        }
        return null;
    }

    public static List<ItemStack> contents(ItemStack stack) {
        if (stack == null || getBlock(stack) == null) return null;
        ContainerComponent component = stack.get(DataComponentTypes.CONTAINER);
        if (component == null) return null;

        List<ItemStack> items = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) items.add(ItemStack.EMPTY);

        List<ItemStack> source = component.stream().toList();
        boolean any = false;
        for (int i = 0; i < Math.min(source.size(), SLOTS); i++) {
            ItemStack item = source.get(i);
            items.set(i, item);
            if (!item.isEmpty()) any = true;
        }
        return any ? items : null;
    }

    private static boolean altDown() {
        return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_ALT);
    }

    private static ItemStack majorityItem(List<ItemStack> items) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack item : items) {
            if (!item.isEmpty()) counts.merge(item.getItem(), 1, Integer::sum);
        }
        Item best = null;
        int bestCount = 0;
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best == null ? null : new ItemStack(best);
    }

    private static int tint(int color) {
        return (0xBF << 24) | (color & 0xFFFFFF);
    }

    private static int darken(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.55F);
        int g = (int) (((color >> 8) & 0xFF) * 0.55F);
        int b = (int) ((color & 0xFF) * 0.55F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static boolean render(HandledScreen<?> screen, DrawContext context, int mouseX, int mouseY, ShulkerView module, Slot focused, int guiX, int guiY) {
        if (module.isMajorityItem()) drawMajorityItems(screen, context, guiX, guiY);

        boolean alt = altDown();
        if (!alt) lockedStack = null;

        ItemStack stack;
        int width;
        int height;
        int px;
        int py;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        if (alt && lockedStack != null) {
            stack = lockedStack;
            px = lockedX;
            py = lockedY;
        } else {
            stack = focused == null ? null : focused.getStack();
            if (contents(stack) == null) {
                lockedStack = null;
                return false;
            }
            width = Math.max(GRID_WIDTH, textRenderer.getWidth(stack.getName())) + PADDING * 2;
            height = GRID_HEIGHT + TITLE_HEIGHT + PADDING * 2;
            px = mouseX + 12;
            if (px + width > screen.width - 4) px = mouseX - 12 - width;
            if (px < 4) px = 4;
            py = mouseY - 12;
            if (py + height > screen.height - 4) py = screen.height - 4 - height;
            if (py < 4) py = 4;
            lockedStack = stack;
            lockedX = px;
            lockedY = py;
        }

        List<ItemStack> items = contents(stack);
        if (items == null) return false;

        width = Math.max(GRID_WIDTH, textRenderer.getWidth(stack.getName())) + PADDING * 2;
        height = GRID_HEIGHT + TITLE_HEIGHT + PADDING * 2;

        ShulkerBoxBlock block = getBlock(stack);
        int base = block == null ? 0xFF99_3399 : ColorUtils.getColor(block);
        int background = module.isColored() ? tint(base) : 0xF0100010;
        int border = module.isColored() ? darken(base) : 0xFF28004B;

        context.fill(px, py, px + width, py + height, border);
        context.fill(px + 1, py + 1, px + width - 1, py + height - 1, background);

        context.drawText(textRenderer, stack.getName(), px + PADDING, py + PADDING, 0xFFFFFFFF, true);

        int gridX = px + PADDING;
        int gridY = py + PADDING + TITLE_HEIGHT;
        ItemStack hovered = null;

        for (int i = 0; i < items.size(); i++) {
            int cellX = gridX + (i % COLUMNS) * CELL;
            int cellY = gridY + (i / COLUMNS) * CELL;
            context.fill(cellX + 1, cellY + 1, cellX + CELL, cellY + CELL, 0x55000000);

            ItemStack item = items.get(i);
            if (item.isEmpty()) continue;

            context.drawItem(item, cellX + 1, cellY + 1);
            context.drawStackOverlay(textRenderer, item, cellX + 1, cellY + 1);

            if (hovered == null && mouseX >= cellX + 1 && mouseX < cellX + CELL && mouseY >= cellY + 1 && mouseY < cellY + CELL) {
                hovered = item;
            }
        }

        if (hovered != null) context.drawItemTooltip(textRenderer, hovered, mouseX, mouseY);
        return true;
    }

    private static void drawMajorityItems(HandledScreen<?> screen, DrawContext context, int guiX, int guiY) {
        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (getBlock(stack) == null) continue;
            List<ItemStack> items = contents(stack);
            if (items == null) continue;
            ItemStack preview = majorityItem(items);
            if (preview == null) continue;

            context.getMatrices().pushMatrix();
            context.getMatrices().scale(PREVIEW_SCALE, PREVIEW_SCALE);
            context.getMatrices().translate(PREVIEW_OFFSET_X, 0.0F);
            context.drawItem(preview, (int) ((guiX + slot.x) / PREVIEW_SCALE), (int) ((guiY + slot.y) / PREVIEW_SCALE));
            context.getMatrices().popMatrix();
        }
    }

    public static boolean openPreview(Screen parent, Slot focused) {
        ItemStack target = altDown() && lockedStack != null ? lockedStack : (focused == null ? null : focused.getStack());
        List<ItemStack> items = contents(target);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (items == null || mc.player == null) return false;

        ShulkerBoxScreenHandler handler = new ShulkerBoxScreenHandler(1337, mc.player.getInventory(), new SimpleInventory(items.toArray(new ItemStack[0])));
        Text title = target.getName();
        mc.setScreen(new ShulkerPreviewScreen(handler, mc.player.getInventory(), title, parent));
        return true;
    }
}
