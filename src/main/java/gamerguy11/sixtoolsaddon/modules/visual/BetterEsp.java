package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class BetterEsp extends Module {
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgRender = this.settings.getDefaultGroup();
   private final Setting<Boolean> players = this.sgTargets.add(new BoolSetting.Builder().name("players").description("Renders the ESP on player entities.").defaultValue(true).build());
   private final Setting<Boolean> hostiles = this.sgTargets.add(new BoolSetting.Builder().name("hostiles").description("Renders the ESP on hostile entities.").defaultValue(true).build());
   private final Setting<Boolean> animals = this.sgTargets.add(new BoolSetting.Builder().name("animals").description("Renders the ESP on animal entities.").defaultValue(true).build());
   private final Setting<Boolean> ambient = this.sgTargets.add(new BoolSetting.Builder().name("ambient").description("Renders the ESP on ambient entities.").defaultValue(false).build());
   private final Setting<Boolean> invisibles = this.sgTargets.add(new BoolSetting.Builder().name("invisibles").description("Renders the ESP on invisible entities.").defaultValue(true).build());
   private final Setting<Boolean> items = this.sgTargets.add(new BoolSetting.Builder().name("items").description("Renders the ESP on item entities.").defaultValue(true).build());
   private final Setting<Boolean> others = this.sgTargets.add(new BoolSetting.Builder().name("others").description("Renders the ESP on miscellaneous entities.").defaultValue(false).build());
   private final Setting<ShapeMode> mode = this.sgRender.add(new EnumSetting.Builder<ShapeMode>().name("mode").description("Fill, outline or both.").defaultValue(ShapeMode.Both).build());
   private final Setting<SettingColor> fillColor = this.sgRender.add(new ColorSetting.Builder().name("fill-color").description("Fill color.").defaultValue(new SettingColor(255, 255, 255, 40)).visible(() -> this.mode.get() != ShapeMode.Lines).build());
   private final Setting<SettingColor> outlineColor = this.sgRender.add(new ColorSetting.Builder().name("outline-color").description("Outline color.").defaultValue(new SettingColor(255, 255, 255, 255)).visible(() -> this.mode.get() != ShapeMode.Sides).build());

   public BetterEsp() {
      super(SixToolsAddon.CATEGORY, "better-esp", "Renders a box ESP around the entities you selected.");
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.world == null) {
         return;
      }

      for (Entity entity : this.mc.world.getEntities()) {
         if (entity == this.mc.player || !this.isValid(entity)) {
            continue;
         }

         double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX());
         double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY());
         double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ());
         Box bb = entity.getBoundingBox();
         double hw = bb.getLengthX() / 2.0;
         double hd = bb.getLengthZ() / 2.0;
         Box box = new Box(x - hw, y, z - hd, x + hw, y + bb.getLengthY(), z + hd);
         event.renderer.box(box, this.fillColor.get(), this.outlineColor.get(), this.mode.get(), 0);
      }
   }

   private boolean isValid(Entity entity) {
      SpawnGroup group = entity.getType().getSpawnGroup();
      if (this.players.get() && entity.getType() == EntityType.PLAYER) {
         return true;
      }

      if (this.hostiles.get() && group == SpawnGroup.MONSTER) {
         return true;
      }

      if (this.animals.get() && (group == SpawnGroup.CREATURE || group == SpawnGroup.WATER_CREATURE || group == SpawnGroup.WATER_AMBIENT || group == SpawnGroup.UNDERGROUND_WATER_CREATURE || group == SpawnGroup.AXOLOTLS)) {
         return true;
      }

      if (this.ambient.get() && group == SpawnGroup.AMBIENT) {
         return true;
      }

      if (this.invisibles.get() && entity.isInvisible()) {
         return true;
      }

      if (this.items.get() && (entity.getType() == EntityType.ITEM || entity.getType() == EntityType.EXPERIENCE_BOTTLE)) {
         return true;
      }

      return this.others.get();
   }
}
