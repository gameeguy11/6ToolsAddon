package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class PvPNeccessaryHud extends HudElement {
   public static final HudElementInfo INFO;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgScale;
   private final SettingGroup sgBackground;
   private final Setting<List<Item>> items;
   public final Setting<Integer> margin;
   public final Setting<Boolean> customScale;
   public final Setting<Double> scale;
   public final Setting<Boolean> background;
   public final Setting<SettingColor> backgroundColor;
   public final Setting<Boolean> backgroundColorUseTheme;

   private PvPNeccessaryHud() {
      super(INFO);
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgScale = this.settings.createGroup("Scale");
      this.sgBackground = this.settings.createGroup("Background");
      this.items = this.sgGeneral.add(((ItemListSetting.Builder)((ItemListSetting.Builder)(new ItemListSetting.Builder()).name("items")).description("Items to display.")).defaultValue(new Item[]{Items.TOTEM_OF_UNDYING, Items.ENDER_PEARL, Items.END_CRYSTAL, Items.OBSIDIAN}).build());
      this.margin = this.sgScale.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("margin")).description("Space between items.")).defaultValue(0)).onChanged((aInt) -> this.calculateSize())).min(0).sliderRange(0, 10).build());
      this.customScale = this.sgScale.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("custom-scale")).description("Applies a custom scale to this HUD element.")).defaultValue(false)).onChanged((aBoolean) -> this.calculateSize())).build());
      SettingGroup var10001 = this.sgScale;
      DoubleSetting.Builder var10002 = (DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("scale")).description("Custom scale.");
      Setting<Boolean> var10003 = this.customScale;
      Objects.requireNonNull(var10003);
      this.scale = var10001.add(((DoubleSetting.Builder)((DoubleSetting.Builder)var10002.visible(var10003::get)).defaultValue((double)2.0F).onChanged((aDouble) -> this.calculateSize())).min((double)0.5F).sliderRange((double)0.5F, (double)3.0F).build());
      this.background = this.sgBackground.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("background")).description("Displays background.")).defaultValue(false)).build());
      var10001 = this.sgBackground;
      ColorSetting.Builder var3 = (ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("background-color")).description("Color used for the background.");
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundColor = var10001.add(((ColorSetting.Builder)var3.visible(var10003::get)).defaultValue(new SettingColor(25, 25, 25, 50)).build());
      var10001 = this.sgBackground;
      BoolSetting.Builder var4 = (BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("background-color-use-theme")).description("Uses Meteor's current GUI theme accent color instead of the color above.");
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundColorUseTheme = var10001.add(((BoolSetting.Builder)((BoolSetting.Builder)var4.visible(var10003::get)).defaultValue(false)).build());
      this.calculateSize();
   }

   private void calculateSize() {
      float currentScale = this.getScale();
      int count = this.items.get().size();
      double slotSize = 18.0D * currentScale;
      double width = count == 0 ? 0.0D : slotSize * count + (double)(this.margin.get() * (count - 1));
      this.setSize(width, slotSize);
   }

   public void render(HudRenderer renderer) {
      this.calculateSize();

      if (this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), ThemeColorUtils.resolve((SettingColor)this.backgroundColor.get(), (Boolean)this.backgroundColorUseTheme.get()));
      }

      double slotSize = 18.0D * this.getScale();
      double iconInset = 1.0D * this.getScale();
      List<Item> list = this.items.get();

      for(int i = 0; i < list.size(); ++i) {
         Item item = list.get(i);
         int count = this.isInEditor() ? 64 : InvUtils.find(new Item[]{item}).count();
         double slotX = (double)this.x + i * (slotSize + (double)this.margin.get());
         boolean drawCount = count > 0;
         String countOverlay = drawCount ? Integer.toString(count) : null;
         renderer.item(new ItemStack(item, 1), (int)(slotX + iconInset), (int)((double)this.y + iconInset), this.getScale(), drawCount, countOverlay);
      }

   }

   private float getScale() {
      return (Boolean)this.customScale.get() ? ((Double)this.scale.get()).floatValue() : ((Double)this.scale.getDefaultValue()).floatValue();
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "pvp-neccessary", "Displays selected PvP items and their inventory counts.", PvPNeccessaryHud::new);
   }
}