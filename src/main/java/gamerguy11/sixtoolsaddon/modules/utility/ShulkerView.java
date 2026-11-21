package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class ShulkerView extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<Boolean> colored = this.sgGeneral.add(new BoolSetting.Builder()
      .name("colored")
      .description("Tints the preview with the color of the shulker box.")
      .defaultValue(true)
      .build());

   private final Setting<Boolean> majorityItem = this.sgGeneral.add(new BoolSetting.Builder()
      .name("majority-item")
      .description("Draws the most common item inside a shulker box on top of its slot.")
      .defaultValue(false)
      .build());

   private final Setting<Boolean> middleClickPreview = this.sgGeneral.add(new BoolSetting.Builder()
      .name("middle-click-preview")
      .description("Middle click a shulker box to open its contents in a read-only screen. Escape goes back.")
      .defaultValue(true)
      .build());

   public ShulkerView() {
      super(SixToolsAddon.CATEGORY, "shulker-view", "Shows shulker box contents when you hover them. Hold Left Alt to lock the preview and inspect its items.");
   }

   public boolean isColored() {
      return this.colored.get();
   }

   public boolean isMajorityItem() {
      return this.majorityItem.get();
   }

   public boolean isMiddleClickPreview() {
      return this.middleClickPreview.get();
   }
}
