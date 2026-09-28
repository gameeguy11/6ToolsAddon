package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.shulkerview.RenderHandler;
import gamerguy11.sixtoolsaddon.shulkerview.UpdateHandler;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.fabricmc.loader.api.FabricLoader;

public class ShulkerView extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");
    private final SettingGroup sgPosition = settings.createGroup("Position");

    private final Setting<Boolean> compact = sgGeneral.add(new BoolSetting.Builder()
        .name("compact")
        .description("Merges stacks of the same item and hides empty slots in the preview.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bothSides = sgGeneral.add(new BoolSetting.Builder()
        .name("both-sides")
        .description("Once previews fill up the left side of the screen, continues them on the right.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tooltips = sgGeneral.add(new BoolSetting.Builder()
        .name("tooltips")
        .description("Shows the normal item tooltip when hovering an item inside a preview.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shulkerBoxTooltipCompat = sgGeneral.add(new BoolSetting.Builder()
        .name("shulkerboxtooltip-compat")
        .description("When the ShulkerBoxTooltip mod is installed, keeps the normal item tooltip on shulker boxes so its hover preview shows up together with these previews.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stackIdentical = sgGeneral.add(new BoolSetting.Builder()
        .name("stack-identical")
        .description("Shows identical shulker boxes (same color and same contents) as a single preview with a count like \"3x\" in the corner.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> spacing = sgGeneral.add(new IntSetting.Builder()
        .name("spacing")
        .description("Empty space in pixels between the previews.")
        .defaultValue(4)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> scale = sgGeneral.add(new IntSetting.Builder()
        .name("scale")
        .description("Size of the previews, in tenths (10 = normal size).")
        .defaultValue(10)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of the preview background, including its opacity (alpha). Set alpha to 0 for no background at all.")
        .defaultValue(new SettingColor(16, 16, 20, 200))
        .build()
    );

    private final Setting<Boolean> backgroundColorUseTheme = sgBackground.add(new BoolSetting.Builder()
        .name("background-use-theme")
        .description("Use the current Meteor theme accent color.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> anchorRight = sgPosition.add(new BoolSetting.Builder()
        .name("anchor-right")
        .description("Starts drawing previews from the right edge of the screen instead of the left. With both-sides on, overflow spills to the opposite side.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> offsetX = sgPosition.add(new IntSetting.Builder()
        .name("offset-x")
        .description("Extra horizontal offset in pixels, measured inward from whichever edge (left/right) previews are anchored to.")
        .defaultValue(0)
        .range(-1000, 1000)
        .sliderRange(-200, 200)
        .build()
    );

    private final Setting<Integer> offsetY = sgPosition.add(new IntSetting.Builder()
        .name("offset-y")
        .description("Extra vertical offset in pixels, measured down from the top of the screen.")
        .defaultValue(0)
        .range(-1000, 1000)
        .sliderRange(-200, 200)
        .build()
    );

    private final RenderHandler renderHandler = new RenderHandler(this);
    private final UpdateHandler updateHandler = new UpdateHandler(this);

    public ShulkerView() {
        super(SixToolsAddon.CATEGORY, "shulker-view", "Shows shulker box contents in a preview, right in your inventory.");
    }

    private static final boolean SHULKER_BOX_TOOLTIP_LOADED = FabricLoader.getInstance().isModLoaded("shulkerboxtooltip");

    /** True if the vanilla tooltip of a hovered shulker box should be left alone so ShulkerBoxTooltip can draw its preview. */
    public boolean shouldKeepItemTooltip() {
        return SHULKER_BOX_TOOLTIP_LOADED && shulkerBoxTooltipCompat.get();
    }

    public boolean isCompact() {
        return compact.get();
    }

    public boolean isBothSides() {
        return bothSides.get();
    }

    public boolean isStackIdentical() {
        return stackIdentical.get();
    }

    public int getSpacing() {
        return spacing.get();
    }

    public boolean isTooltips() {
        return tooltips.get();
    }

    public int getBackground() {
        return ThemeColorUtils.resolve(backgroundColor.get(), backgroundColorUseTheme.get()).getPacked();
    }

    public float getScale() {
        return scale.get() / 10f;
    }

    public boolean isAnchorRight() {
        return anchorRight.get();
    }

    public int getOffsetX() {
        return offsetX.get();
    }

    public int getOffsetY() {
        return offsetY.get();
    }

    public RenderHandler getRenderHandler() {
        return renderHandler;
    }

    public UpdateHandler getUpdateHandler() {
        return updateHandler;
    }
}
