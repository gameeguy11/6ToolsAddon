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
   private final Setting<SettingColor> textColor;
   private final Setting<Boolean> textColorUseTheme;
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
      this.textColor = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("text-color")).description("Color of the item count text.")).defaultValue(new SettingColor(255, 255, 255, 255)).build());
      this.textColorUseTheme = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("text-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
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
      int count = ((List)this.items.get()).size();
      this.setSize((double)(23.0F * currentScale * (float)count), (double)(17.0F * currentScale + 20.0F));
   }

   public void render(HudRenderer renderer) {
      this.calculateSize();
      int itemsLength = ((List)this.items.get()).size();
      int scaleOffset = (int)(this.getScale() * 10.0F);
      int intScale = (int)this.getScale();

      for(int i = 0; i < itemsLength; ++i) {
         Item item = (Item)((List)this.items.get()).get(i);
         ItemStack itemStack = new ItemStack(item, InvUtils.find(new Item[]{item}).count());
         int offset = i == 0 ? 0 : i * 50 * scaleOffset / (20 - (Integer)this.margin.get());
         int textXOffset = 6 * intScale;
         int textYOffset = 17 * intScale;
         if (itemStack.getCount() > 100) {
            textXOffset -= 6 * intScale;
         } else if (itemStack.getCount() > 10) {
            textXOffset -= 2 * intScale;
         }

         int finalTextXOffset = textXOffset;
         renderer.post(() -> {
            this.renderItem(renderer, itemStack, this.x + offset, this.y);
            this.renderText(renderer, itemStack, (double)(this.x + offset + finalTextXOffset), (double)(this.y + textYOffset));
         });
      }

      if ((Boolean)this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), ThemeColorUtils.resolve((SettingColor)this.backgroundColor.get(), (Boolean)this.backgroundColorUseTheme.get()));
      }

   }

   private void renderItem(HudRenderer renderer, ItemStack itemStack, int x, int y) {
      boolean resetToZero = false;
      if (itemStack.isEmpty()) {
         itemStack.setCount(1);
         resetToZero = true;
      }

      renderer.item(itemStack, x, y, this.getScale(), false);
      if (resetToZero) {
         itemStack.setCount(0);
      }

   }

   private void renderText(HudRenderer renderer, ItemStack itemStack, double x, double y) {
      boolean resetToZero = false;
      if (itemStack.isEmpty()) {
         itemStack.setCount(1);
         resetToZero = true;
      }

      renderer.text(Integer.toString(itemStack.getCount()), x, y, ThemeColorUtils.resolve((SettingColor)this.textColor.get(), (Boolean)this.textColorUseTheme.get()), true, (double)(this.getScale() / 2.0F));
      if (resetToZero) {
         itemStack.setCount(0);
      }

   }

   private float getScale() {
      return (Boolean)this.customScale.get() ? ((Double)this.scale.get()).floatValue() : ((Double)this.scale.getDefaultValue()).floatValue();
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "pvp-neccessary", "Displays selected PvP items and their inventory counts.", PvPNeccessaryHud::new);
   }
}
