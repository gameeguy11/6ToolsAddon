package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import java.util.Objects;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ArmorHud extends HudElement {
   public static final HudElementInfo INFO;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgDurability;
   private final SettingGroup sgScale;
   private final SettingGroup sgBackground;
   private final Setting<Orientation> orientation;
   private final Setting<Boolean> flipOrder;
   private final Setting<Boolean> showEmpty;
   private final Setting<Durability> durability;
   private final Setting<SettingColor> durabilityColor;
   private final Setting<Boolean> durabilityColorUseTheme;
   private final Setting<Boolean> durabilityShadow;
   private final Setting<Boolean> customScale;
   private final Setting<Double> scale;
   private final Setting<Boolean> background;
   private final Setting<SettingColor> backgroundColor;
   private final Setting<Boolean> backgroundColorUseTheme;

   public ArmorHud() {
      super(INFO);
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgDurability = this.settings.createGroup("Durability");
      this.sgScale = this.settings.createGroup("Scale");
      this.sgBackground = this.settings.createGroup("Background");
      this.orientation = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("orientation")).description("How to display armor.")).defaultValue(ArmorHud.Orientation.Vertical)).onChanged((o) -> this.calculateSize())).build());
      this.flipOrder = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("flip-order")).description("Flips the order of armor items.")).defaultValue(true)).build());
      this.showEmpty = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-empty")).description("Renders barrier icons for empty slots.")).defaultValue(false)).build());
      this.durability = this.sgDurability.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("durability")).description("How to display armor durability.")).defaultValue(ArmorHud.Durability.Percentage)).onChanged((d) -> this.calculateSize())).build());
      this.durabilityColor = this.sgDurability.add(((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("durability-color")).description("Color of the text.")).visible(() -> this.durability.get() == ArmorHud.Durability.Total || this.durability.get() == ArmorHud.Durability.Percentage)).defaultValue(new SettingColor()).build());
      this.durabilityColorUseTheme = this.sgDurability.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("durability-color-use-theme")).description("Uses Meteor's current GUI theme accent color instead of the color above.")).visible(() -> this.durability.get() == ArmorHud.Durability.Total || this.durability.get() == ArmorHud.Durability.Percentage)).defaultValue(false)).build());
      this.durabilityShadow = this.sgDurability.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("durability-shadow")).description("Text shadow.")).visible(() -> this.durability.get() == ArmorHud.Durability.Total || this.durability.get() == ArmorHud.Durability.Percentage)).defaultValue(true)).build());
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
      boolean showsText = this.durability.get() == ArmorHud.Durability.Total || this.durability.get() == ArmorHud.Durability.Percentage;
      double extra = showsText ? (double)(10.0F * this.getScale()) : (double)0.0F;
      switch (((Orientation)this.orientation.get()).ordinal()) {
         case 0 -> this.setSize((double)(72.0F * this.getScale()), (double)(16.0F * this.getScale()) + extra);
         case 1 -> this.setSize((double)(16.0F * this.getScale()), ((double)(16.0F * this.getScale()) + extra) * (double)4.0F + (double)(2.0F * this.getScale() * 3.0F));
      }

   }

   public void render(HudRenderer renderer) {
      int emptySlots = 0;
      ItemStack[] armor = (Boolean)this.flipOrder.get() ? new ItemStack[]{this.getItem(EquipmentSlot.HEAD), this.getItem(EquipmentSlot.CHEST), this.getItem(EquipmentSlot.LEGS), this.getItem(EquipmentSlot.FEET)} : new ItemStack[]{this.getItem(EquipmentSlot.FEET), this.getItem(EquipmentSlot.LEGS), this.getItem(EquipmentSlot.CHEST), this.getItem(EquipmentSlot.HEAD)};

      for(ItemStack stack : armor) {
         if (stack.isEmpty()) {
            ++emptySlots;
         }
      }

      if ((Boolean)this.background.get() && emptySlots < 4) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), ThemeColorUtils.resolve((SettingColor)this.backgroundColor.get(), (Boolean)this.backgroundColorUseTheme.get()));
      }

      boolean showsText = this.durability.get() == ArmorHud.Durability.Total || this.durability.get() == ArmorHud.Durability.Percentage;
      double textExtra = showsText ? (double)(10.0F * this.getScale()) : (double)0.0F;
      double iconSize = (double)(16.0F * this.getScale());
      double rowStep = this.orientation.get() == ArmorHud.Orientation.Vertical ? iconSize + textExtra + (double)(2.0F * this.getScale()) : (double)(18.0F * this.getScale());
      double x = (double)this.x;
      double y = (double)this.y;

      for(int position = 0; position < 4; ++position) {
         ItemStack itemStack = armor[position];
         double iconX;
         double iconY;
         if (this.orientation.get() == ArmorHud.Orientation.Vertical) {
            iconX = x;
            iconY = y + (double)position * rowStep;
         } else {
            iconX = x + (double)position * rowStep;
            iconY = y;
         }

         boolean damageable = itemStack.getMaxDamage() > 0;
         renderer.item(itemStack, (int)iconX, (int)iconY, this.getScale(), damageable && this.durability.get() == ArmorHud.Durability.Bar);
         if (damageable && this.durability.get() != ArmorHud.Durability.Bar && this.durability.get() != ArmorHud.Durability.None) {
            String var10000;
            switch (((Durability)this.durability.get()).ordinal()) {
               case 2 -> var10000 = Integer.toString(itemStack.getMaxDamage() - itemStack.getDamage());
               case 3 -> var10000 = Integer.toString(Math.round((float)(itemStack.getMaxDamage() - itemStack.getDamage()) * 100.0F / (float)itemStack.getMaxDamage())) + "%";
               default -> var10000 = "err";
            }

            String message = var10000;
            double messageWidth = renderer.textWidth(message);
            double textX = iconX + (iconSize - messageWidth) / (double)2.0F;
            double textY = iconY + iconSize + (double)1.0F;
            renderer.text(message, textX, textY, ThemeColorUtils.resolve((SettingColor)this.durabilityColor.get(), (Boolean)this.durabilityColorUseTheme.get()), (Boolean)this.durabilityShadow.get());
         }
      }

   }

   private ItemStack getItem(EquipmentSlot slot) {
      if (this.isInEditor()) {
         ItemStack var10000;
         switch (slot) {
            case HEAD -> var10000 = new ItemStack(Items.NETHERITE_HELMET);
            case CHEST -> var10000 = new ItemStack(Items.NETHERITE_CHESTPLATE);
            case LEGS -> var10000 = new ItemStack(Items.NETHERITE_LEGGINGS);
            default -> var10000 = new ItemStack(Items.NETHERITE_BOOTS);
         }

         return var10000;
      } else {
         ItemStack stack = MeteorClient.mc.player.getEquippedStack(slot);
         return stack.isEmpty() && (Boolean)this.showEmpty.get() ? new ItemStack(Items.BARRIER) : stack;
      }
   }

   private float getScale() {
      return (Boolean)this.customScale.get() ? ((Double)this.scale.get()).floatValue() : ((Double)this.scale.getDefaultValue()).floatValue();
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "armor", "Displays your armor.", ArmorHud::new);
   }

   public static enum Durability {
      None,
      Bar,
      Total,
      Percentage;

      private static Durability[] $values() {
         return new Durability[]{None, Bar, Total, Percentage};
      }
   }

   public static enum Orientation {
      Horizontal,
      Vertical;

      private static Orientation[] $values() {
         return new Orientation[]{Horizontal, Vertical};
      }
   }
}
