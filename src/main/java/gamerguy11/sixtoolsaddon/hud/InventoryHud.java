package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import java.util.Objects;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class InventoryHud extends HudElement {
   public static final HudElementInfo INFO;
   private static final int COLUMNS = 9;
   private static final int HOTBAR_SIZE = 9;
   private static final int MAIN_SIZE = 27;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgScale;
   private final SettingGroup sgBackground;
   private final Setting<Boolean> inventoryOnly;
   private final Setting<Boolean> showEmpty;
   private final Setting<Boolean> showCount;
   private final Setting<Boolean> customScale;
   private final Setting<Double> scale;
   private final Setting<Boolean> background;
   private final Setting<SettingColor> backgroundColor;
   private final Setting<Boolean> backgroundColorUseTheme;

   public InventoryHud() {
      super(INFO);
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgScale = this.settings.createGroup("Scale");
      this.sgBackground = this.settings.createGroup("Background");
      this.inventoryOnly = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("inventory-only")).description("Only shows the main inventory. When disabled, the hotbar is shown above the inventory as well.")).defaultValue(false)).onChanged((b) -> this.calculateSize())).build());
      this.showEmpty = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-empty")).description("Renders barrier icons for empty slots.")).defaultValue(false)).build());
      this.showCount = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-count")).description("Displays the stack count on top of each item.")).defaultValue(true)).build());
      this.customScale = this.sgScale.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("custom-scale")).description("Applies a custom scale to this hud element.")).defaultValue(false)).onChanged((b) -> this.calculateSize())).build());
      SettingGroup var10001 = this.sgScale;
      DoubleSetting.Builder var10002 = (DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("scale")).description("Custom scale.");
      Setting<Boolean> var10003 = this.customScale;
      Objects.requireNonNull(var10003);
      this.scale = var10001.add(((DoubleSetting.Builder)((DoubleSetting.Builder)var10002.visible(var10003::get)).defaultValue((double)2.0F).onChanged((d) -> this.calculateSize())).min((double)0.5F).sliderRange((double)0.5F, (double)3.0F).build());
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
      int rows = (Boolean)this.inventoryOnly.get() ? 3 : 4;
      double gap = (Boolean)this.inventoryOnly.get() ? (double)0.0F : (double)(2.0F * this.getScale());
      this.setSize((double)(162.0F * this.getScale()), (double)((float)(rows * 18) * this.getScale()) + gap);
   }

   public void render(HudRenderer renderer) {
      if ((Boolean)this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), ThemeColorUtils.resolve((SettingColor)this.backgroundColor.get(), (Boolean)this.backgroundColorUseTheme.get()));
      }

      double slotSize = (double)(18.0F * this.getScale());
      double iconInset = (double)(1.0F * this.getScale());
      double x = (double)this.x;
      double y = (double)this.y;
      if (!(Boolean)this.inventoryOnly.get()) {
         for(int i = 0; i < 9; ++i) {
            ItemStack stack = this.getSlot(i);
            double slotX = x + (double)i * slotSize;
            this.renderSlot(renderer, stack, slotX + iconInset, y + iconInset);
         }

         y += slotSize + (double)(2.0F * this.getScale());
      }

      for(int i = 0; i < 27; ++i) {
         ItemStack stack = this.getSlot(9 + i);
         int column = i % 9;
         int row = i / 9;
         double slotX = x + (double)column * slotSize;
         double slotY = y + (double)row * slotSize;
         this.renderSlot(renderer, stack, slotX + iconInset, slotY + iconInset);
      }

   }

   private void renderSlot(HudRenderer renderer, ItemStack stack, double iconX, double iconY) {
      boolean drawCount = (Boolean)this.showCount.get() && !stack.isEmpty() && stack.getCount() > 1;
      String countOverlay = drawCount ? Integer.toString(stack.getCount()) : null;
      renderer.item(stack, (int)iconX, (int)iconY, this.getScale(), drawCount, countOverlay);
   }

   private ItemStack getSlot(int index) {
      if (this.isInEditor()) {
         return index % 5 == 0 ? new ItemStack(Items.DIAMOND) : ItemStack.EMPTY;
      } else {
         ItemStack stack = MeteorClient.mc.player.getInventory().getStack(index);
         return stack.isEmpty() && (Boolean)this.showEmpty.get() ? new ItemStack(Items.BARRIER) : stack;
      }
   }

   private float getScale() {
      return (Boolean)this.customScale.get() ? ((Double)this.scale.get()).floatValue() : ((Double)this.scale.getDefaultValue()).floatValue();
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "inventory", "Displays your inventory.", InventoryHud::new);
   }
}
