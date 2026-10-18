package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class HandProgress extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Boolean> modifyMainhand = this.sgGeneral.add(new BoolSetting.Builder().name("modify-mainhand").description("Modifies the mainhand's equip progress.").defaultValue(true).build());
   public final Setting<Double> mainhandProgress = this.sgGeneral.add(new DoubleSetting.Builder().name("mainhand-progress").description("Mainhand progress.").defaultValue(1.0).min(-1.0).max(1.0).sliderRange(-1.0, 1.0).visible(() -> this.modifyMainhand.get()).build());
   public final Setting<Boolean> modifyOffhand = this.sgGeneral.add(new BoolSetting.Builder().name("modify-offhand").description("Modifies the offhand's equip progress.").defaultValue(true).build());
   public final Setting<Double> offhandProgress = this.sgGeneral.add(new DoubleSetting.Builder().name("offhand-progress").description("Offhand progress.").defaultValue(1.0).min(-1.0).max(1.0).sliderRange(-1.0, 1.0).visible(() -> this.modifyOffhand.get()).build());

   public HandProgress() {
      super(SixToolsAddon.CATEGORY, "hand-progress", "Modifies the equip progress of the items you hold.");
   }
}
