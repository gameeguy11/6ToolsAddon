package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.shulkerview.RenderHandler;
import gamerguy11.sixtoolsaddon.shulkerview.UpdateHandler;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.fabricmc.loader.api.FabricLoader;

public class ShulkerView extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgBackground;
   private final SettingGroup sgPosition;
   private final Setting<Boolean> compact;
   private final Setting<Boolean> bothSides;
   private final Setting<Boolean> tooltips;
   private final Setting<Boolean> shulkerBoxTooltipCompat;
   private final Setting<Boolean> stackIdentical;
   private final Setting<Integer> spacing;
   private final Setting<Integer> scale;
   private final Setting<SettingColor> backgroundColor;
   private final Setting<Boolean> backgroundColorUseTheme;
   private final Setting<Boolean> anchorRight;
   private final Setting<Integer> offsetX;
   private final Setting<Integer> offsetY;
   private final RenderHandler renderHandler;
   private final UpdateHandler updateHandler;
   private static final boolean SHULKER_BOX_TOOLTIP_LOADED = FabricLoader.getInstance().isModLoaded("shulkerboxtooltip");

   public ShulkerView() {
      super(SixToolsAddon.CATEGORY, "shulker-view", "Shows shulker box contents in a preview, right in your inventory.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgBackground = this.settings.createGroup("Background");
      this.sgPosition = this.settings.createGroup("Position");
      this.compact = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("compact")).description("Merges stacks of the same item and hides empty slots in the preview.")).defaultValue(true)).build());
      this.bothSides = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("both-sides")).description("Once previews fill up the left side of the screen, continues them on the right.")).defaultValue(true)).build());
      this.tooltips = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("tooltips")).description("Shows the normal item tooltip when hovering an item inside a preview.")).defaultValue(true)).build());
      this.shulkerBoxTooltipCompat = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("shulkerboxtooltip-compat")).description("When the ShulkerBoxTooltip mod is installed, keeps the normal item tooltip on shulker boxes so its hover preview shows up together with these previews.")).defaultValue(true)).build());
      this.stackIdentical = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("stack-identical")).description("Shows identical shulker boxes (same color and same contents) as a single preview with a count like \"3x\" in the corner.")).defaultValue(true)).build());
      this.spacing = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("spacing")).description("Empty space in pixels between the previews.")).defaultValue(4)).min(0).sliderMax(30).build());
      this.scale = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("scale")).description("Size of the previews, in tenths (10 = normal size).")).defaultValue(10)).min(1).sliderMax(20).build());
      this.backgroundColor = this.sgBackground.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("background-color")).description("Color of the preview background, including its opacity (alpha). Set alpha to 0 for no background at all.")).defaultValue(new SettingColor(16, 16, 20, 200)).build());
      this.backgroundColorUseTheme = this.sgBackground.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("background-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.anchorRight = this.sgPosition.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("anchor-right")).description("Starts drawing previews from the right edge of the screen instead of the left. With both-sides on, overflow spills to the opposite side.")).defaultValue(false)).build());
      this.offsetX = this.sgPosition.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("offset-x")).description("Extra horizontal offset in pixels, measured inward from whichever edge (left/right) previews are anchored to.")).defaultValue(0)).range(-1000, 1000).sliderRange(-200, 200).build());
      this.offsetY = this.sgPosition.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("offset-y")).description("Extra vertical offset in pixels, measured down from the top of the screen.")).defaultValue(0)).range(-1000, 1000).sliderRange(-200, 200).build());
      this.renderHandler = new RenderHandler(this);
      this.updateHandler = new UpdateHandler(this);
   }

   public boolean shouldKeepItemTooltip() {
      return SHULKER_BOX_TOOLTIP_LOADED && (Boolean)this.shulkerBoxTooltipCompat.get();
   }

   public boolean isCompact() {
      return (Boolean)this.compact.get();
   }

   public boolean isBothSides() {
      return (Boolean)this.bothSides.get();
   }

   public boolean isStackIdentical() {
      return (Boolean)this.stackIdentical.get();
   }

   public int getSpacing() {
      return (Integer)this.spacing.get();
   }

   public boolean isTooltips() {
      return (Boolean)this.tooltips.get();
   }

   public int getBackground() {
      return ThemeColorUtils.resolve((SettingColor)this.backgroundColor.get(), (Boolean)this.backgroundColorUseTheme.get()).getPacked();
   }

   public float getScale() {
      return (float)(Integer)this.scale.get() / 10.0F;
   }

   public boolean isAnchorRight() {
      return (Boolean)this.anchorRight.get();
   }

   public int getOffsetX() {
      return (Integer)this.offsetX.get();
   }

   public int getOffsetY() {
      return (Integer)this.offsetY.get();
   }

   public RenderHandler getRenderHandler() {
      return this.renderHandler;
   }

   public UpdateHandler getUpdateHandler() {
      return this.updateHandler;
   }
}
