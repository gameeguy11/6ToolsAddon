package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ArmorHud extends HudElement {
    public static final HudElementInfo<ArmorHud> INFO = new HudElementInfo<>(SixToolsAddon.HUD_GROUP, "armor", "Displays your armor.", ArmorHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDurability = settings.createGroup("Durability");
    private final SettingGroup sgScale = settings.createGroup("Scale");
    private final SettingGroup sgBackground = settings.createGroup("Background");

    private final Setting<Orientation> orientation = sgGeneral.add(new EnumSetting.Builder<Orientation>()
            .name("orientation")
            .description("How to display armor.")
            .defaultValue(Orientation.Vertical)
            .onChanged(o -> calculateSize())
            .build()
    );

    private final Setting<Boolean> flipOrder = sgGeneral.add(new BoolSetting.Builder()
            .name("flip-order")
            .description("Flips the order of armor items.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showEmpty = sgGeneral.add(new BoolSetting.Builder()
            .name("show-empty")
            .description("Renders barrier icons for empty slots.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Durability> durability = sgDurability.add(new EnumSetting.Builder<Durability>()
            .name("durability")
            .description("How to display armor durability.")
            .defaultValue(Durability.Percentage)
            .onChanged(d -> calculateSize())
            .build()
    );

    private final Setting<SettingColor> durabilityColor = sgDurability.add(new ColorSetting.Builder()
            .name("durability-color")
            .description("Color of the text.")
            .visible(() -> durability.get() == Durability.Total || durability.get() == Durability.Percentage)
            .defaultValue(new SettingColor())
            .build()
    );

    private final Setting<Boolean> durabilityColorUseTheme = sgDurability.add(new BoolSetting.Builder()
            .name("durability-color-use-theme")
            .description("Uses Meteor's current GUI theme accent color instead of the color above.")
            .visible(() -> durability.get() == Durability.Total || durability.get() == Durability.Percentage)
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> durabilityShadow = sgDurability.add(new BoolSetting.Builder()
            .name("durability-shadow")
            .description("Text shadow.")
            .visible(() -> durability.get() == Durability.Total || durability.get() == Durability.Percentage)
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

    public ArmorHud() {
        super(INFO);

        calculateSize();
    }

    private void calculateSize() {
        boolean showsText = durability.get() == Durability.Total || durability.get() == Durability.Percentage;
        double extra = showsText ? 10 * getScale() : 0;

        switch (orientation.get()) {
            case Horizontal -> setSize((16 * 4 + 2 * 4) * getScale(), 16 * getScale() + extra);

            case Vertical -> setSize(16 * getScale(), (16 * getScale() + extra) * 4 + 2 * getScale() * 3);
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        int emptySlots = 0;

        ItemStack[] armor = flipOrder.get() ?
                new ItemStack[]{getItem(EquipmentSlot.HEAD), getItem(EquipmentSlot.CHEST), getItem(EquipmentSlot.LEGS), getItem(EquipmentSlot.FEET)} :
                new ItemStack[]{getItem(EquipmentSlot.FEET), getItem(EquipmentSlot.LEGS), getItem(EquipmentSlot.CHEST), getItem(EquipmentSlot.HEAD)};

        for (ItemStack stack : armor) {
            if (stack.isEmpty()) emptySlots++;
        }

        if (background.get() && emptySlots < 4) {
            renderer.quad(this.x, this.y, getWidth(), getHeight(), ThemeColorUtils.resolve(backgroundColor.get(), backgroundColorUseTheme.get()));
        }

        boolean showsText = durability.get() == Durability.Total || durability.get() == Durability.Percentage;
        double textExtra = showsText ? 10 * getScale() : 0;
        double iconSize = 16 * getScale();
        double rowStep = orientation.get() == Orientation.Vertical
                ? iconSize + textExtra + 2 * getScale()
                : 18 * getScale();

        double x = this.x;
        double y = this.y;

        for (int position = 0; position < 4; position++) {
            ItemStack itemStack = armor[position];

            double iconX, iconY;

            if (orientation.get() == Orientation.Vertical) {
                iconX = x;
                iconY = y + position * rowStep;
            } else {
                iconX = x + position * rowStep;
                iconY = y;
            }

            boolean damageable = itemStack.getMaxDamage() > 0;

            renderer.item(itemStack, (int) iconX, (int) iconY, getScale(), damageable && durability.get() == Durability.Bar);

            if (damageable && durability.get() != Durability.Bar && durability.get() != Durability.None) {
                String message = switch (durability.get()) {
                    case Total -> Integer.toString(itemStack.getMaxDamage() - itemStack.getDamage());
                    case Percentage ->
                            Integer.toString(Math.round(((itemStack.getMaxDamage() - itemStack.getDamage()) * 100f) / (float) itemStack.getMaxDamage())) + "%";
                    default -> "err";
                };

                double messageWidth = renderer.textWidth(message);

                double textX = iconX + (iconSize - messageWidth) / 2.0;
                double textY = iconY + iconSize + 1;

                renderer.text(message, textX, textY, ThemeColorUtils.resolve(durabilityColor.get(), durabilityColorUseTheme.get()), durabilityShadow.get());
            }
        }
    }

    private ItemStack getItem(EquipmentSlot slot) {
        if (isInEditor()) {
            return switch (slot) {
                case HEAD -> new ItemStack(Items.NETHERITE_HELMET);
                case CHEST -> new ItemStack(Items.NETHERITE_CHESTPLATE);
                case LEGS -> new ItemStack(Items.NETHERITE_LEGGINGS);
                default -> new ItemStack(Items.NETHERITE_BOOTS);
            };
        }

        ItemStack stack = mc.player.getEquippedStack(slot);
        return stack.isEmpty() && showEmpty.get() ? new ItemStack(Items.BARRIER) : stack;
    }

    private float getScale() {
        return customScale.get() ? scale.get().floatValue() : scale.getDefaultValue().floatValue();
    }

    public enum Durability {
        None,
        Bar,
        Total,
        Percentage
    }

    public enum Orientation {
        Horizontal,
        Vertical
    }
}