package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.render.BetterShader;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;

public class BetterShaders extends Module {
   public enum RenderMode {
      Fill,
      Outline,
      Both
   }

   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgRender = this.settings.getDefaultGroup();
   private final Setting<Boolean> players = this.sgTargets.add(new BoolSetting.Builder().name("players").description("Renders the shader effect on player entities.").defaultValue(true).build());
   private final Setting<Boolean> hostiles = this.sgTargets.add(new BoolSetting.Builder().name("hostiles").description("Renders the shader effect on hostile entities.").defaultValue(true).build());
   private final Setting<Boolean> animals = this.sgTargets.add(new BoolSetting.Builder().name("animals").description("Renders the shader effect on animal entities.").defaultValue(true).build());
   private final Setting<Boolean> ambient = this.sgTargets.add(new BoolSetting.Builder().name("ambient").description("Renders the shader effect on ambient entities.").defaultValue(false).build());
   private final Setting<Boolean> invisibles = this.sgTargets.add(new BoolSetting.Builder().name("invisibles").description("Renders the shader effect on invisible entities.").defaultValue(true).build());
   private final Setting<Boolean> items = this.sgTargets.add(new BoolSetting.Builder().name("items").description("Renders the shader effect on item entities.").defaultValue(true).build());
   private final Setting<Boolean> crystals = this.sgTargets.add(new BoolSetting.Builder().name("crystals").description("Renders the shader effect on crystal entities.").defaultValue(true).build());
   private final Setting<Boolean> others = this.sgTargets.add(new BoolSetting.Builder().name("others").description("Renders the shader effect on miscellaneous entities.").defaultValue(false).build());
   public final Setting<RenderMode> mode = this.sgRender.add(new EnumSetting.Builder<RenderMode>().name("mode").description("Fill, outline or both.").defaultValue(RenderMode.Both).build());
   public final Setting<Integer> opacity = this.sgRender.add(new IntSetting.Builder().name("opacity").description("The opacity of the fill.").defaultValue(80).min(0).max(255).sliderMin(0).sliderMax(255).visible(() -> this.mode.get() != RenderMode.Outline).build());
   private final Setting<SettingColor> color = this.sgRender.add(new ColorSetting.Builder().name("color").description("The color of the shader effect.").defaultValue(new SettingColor(255, 255, 255, 255)).build());
   public final Setting<Boolean> animated = this.sgRender.add(new BoolSetting.Builder().name("animated-colors").description("Scrolling enchantment-glint style animated colors.").defaultValue(false).build());
   public final Setting<Double> animationSpeed = this.sgRender.add(new DoubleSetting.Builder().name("animation-speed").description("Speed of the animation.").defaultValue(1.0).min(0.1).sliderMax(5.0).visible(this.animated::get).build());
   public final Setting<SettingColor> animatedColor = this.sgRender.add(new ColorSetting.Builder().name("animated-color").description("The color of the animated glint.").defaultValue(new SettingColor(160, 80, 255, 255)).visible(this.animated::get).build());
   private final Setting<Boolean> colorFriends = this.sgRender.add(new BoolSetting.Builder().name("color-friends").description("Uses a separate color for friended players.").defaultValue(true).build());
   private final Setting<SettingColor> friendColor = this.sgRender.add(new ColorSetting.Builder().name("friend-color").description("The color of the shader effect on friends.").defaultValue(new SettingColor(85, 255, 255, 255)).visible(this.colorFriends::get).build());

   public BetterShaders() {
      super(SixToolsAddon.CATEGORY, "better-shaders", "Overlays the selected entities with a fill and a glowing outline shader.");
   }

   @Override
   public void onActivate() {
      BetterShader.get();
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      BetterShader shader = BetterShader.getIfCreated();
      if (shader != null) {
         shader.render();
      }
   }

   public Color getColor(Entity entity) {
      SettingColor base = (this.colorFriends.get() && entity instanceof PlayerEntity player && Friends.get().isFriend(player)) ? this.friendColor.get() : this.color.get();
      return new Color(base.r, base.g, base.b, 255);
   }

   public boolean isValid(Entity entity) {
      if (entity == this.mc.player && this.mc.options.getPerspective().isFirstPerson()) {
         return false;
      }

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

      if (this.crystals.get() && entity.getType() == EntityType.END_CRYSTAL) {
         return true;
      }

      return this.others.get();
   }
}
