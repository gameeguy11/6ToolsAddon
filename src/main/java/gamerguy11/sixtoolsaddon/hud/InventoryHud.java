package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class InventoryHud extends HudElement {
    public static final HudElementInfo<InventoryHud> INFO = new HudElementInfo<>(SixToolsAddon.HUD_GROUP, "inventory", "Displays your inventory.", InventoryHud::new);

    private static final int COLUMNS = 9;
    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_SIZE = 27;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScale = settings.createGroup("Scale");
    private final SettingGroup sgBackground = settings.createGroup("Background");

    private final Setting<Boolean> inventoryOnly = sgGeneral.add(new BoolSetting.Builder()
            .name("inventory-only")
            .description("Only shows the main inventory. When disabled, the hotbar is shown above the inventory as well.")
            .defaultValue(false)
            .onChanged(b -> calculateSize())
            .build()
    );

    private final Setting<Boolean> showEmpty = sgGeneral.add(new BoolSetting.Builder()
            .name("show-empty")
            .description("Renders barrier icons for empty slots.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> showCount = sgGeneral.add(new BoolSetting.Builder()
            .name("show-count")
            .description("Displays the stack count on top of each item.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> customScale = sgScale.add(new BoolSetting.Builder()
            .name("custom-scale")
            .description("Applies a custom scale to this hud element.")
            .defaultValue(false)
            .onChanged(b -> calculateSize())
            .build()
    );

    private final Setting<Double> scale = sgScale.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Custom scale.")
            .visible(customScale::get)
            .defaultValue(2.0)
            .onChanged(d -> calculateSize())
            .min(0.5)
            .sliderRange(0.5, 3)
            .build()
    );

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
            .name("background")
            .description("Displays background.")
            .defaultValue(false)
            .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Color used for the background.")
            .visible(background::get)
            .defaultValue(new SettingColor(25, 25, 25, 50))
            .build()
    );

    private final Setting<Boolean> backgroundColorUseTheme = sgBackground.add(new BoolSetting.Builder()
            .name("background-color-use-theme")
            .description("Uses Meteor's current GUI theme accent color instead of the color above.")
            .visible(background::get)
            .defaultValue(false)
            .build()
    );

    public InventoryHud() {
        super(INFO);

        calculateSize();
    }

    private void calculateSize() {
        int rows = inventoryOnly.get() ? 3 : 4;
        double gap = inventoryOnly.get() ? 0 : 2 * getScale();

        setSize(
                COLUMNS * 18 * getScale(),
                rows * 18 * getScale() + gap
        );
    }

    @Override
    public void render(HudRenderer renderer) {
        if (background.get()) {
            renderer.quad(this.x, this.y, getWidth(), getHeight(), ThemeColorUtils.resolve(backgroundColor.get(), backgroundColorUseTheme.get()));
        }

        double slotSize = 18 * getScale();
        double iconInset = 1 * getScale();

        double x = this.x;
        double y = this.y;

        if (!inventoryOnly.get()) {
            for (int i = 0; i < HOTBAR_SIZE; i++) {
                ItemStack stack = getSlot(i);
                double slotX = x + i * slotSize;
                renderSlot(renderer, stack, slotX + iconInset, y + iconInset);
            }

            y += slotSize + 2 * getScale();
        }

        for (int i = 0; i < MAIN_SIZE; i++) {
            ItemStack stack = getSlot(HOTBAR_SIZE + i);

            int column = i % COLUMNS;
            int row = i / COLUMNS;

            double slotX = x + column * slotSize;
            double slotY = y + row * slotSize;

            renderSlot(renderer, stack, slotX + iconInset, slotY + iconInset);
        }
    }

    private void renderSlot(HudRenderer renderer, ItemStack stack, double iconX, double iconY) {
        boolean drawCount = showCount.get() && !stack.isEmpty() && stack.getCount() > 1;
        String countOverlay = drawCount ? Integer.toString(stack.getCount()) : null;

        renderer.item(stack, (int) iconX, (int) iconY, getScale(), drawCount, countOverlay);
    }

    private ItemStack getSlot(int index) {
        if (isInEditor()) {

            return index % 5 == 0 ? new ItemStack(Items.DIAMOND) : ItemStack.EMPTY;
        }

        ItemStack stack = mc.player.getInventory().getStack(index);
        return stack.isEmpty() && showEmpty.get() ? new ItemStack(Items.BARRIER) : stack;
    }

    private float getScale() {
        return customScale.get() ? scale.get().floatValue() : scale.getDefaultValue().floatValue();
    }
}
