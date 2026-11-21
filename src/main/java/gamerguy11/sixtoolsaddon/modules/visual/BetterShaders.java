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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;

public class BetterShaders extends Module {
   public enum RenderMode {
      Fill,
      Outline,
      Both
   }

   public enum Style {
      Solid,
      Gradient,
      Rainbow
   }

   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgRanges = this.settings.createGroup("Ranges");
   private final SettingGroup sgRender = this.settings.getDefaultGroup();
   private final SettingGroup sgLook = this.settings.createGroup("Look");

   private final Setting<Boolean> players = this.sgTargets.add(new BoolSetting.Builder().name("players").description("Renders the shader effect on player entities.").defaultValue(true).build());
   private final Setting<Boolean> self = this.sgTargets.add(new BoolSetting.Builder().name("self").description("Renders the shader effect on yourself in third person.").defaultValue(true).build());
   private final Setting<Boolean> hostiles = this.sgTargets.add(new BoolSetting.Builder().name("hostiles").description("Renders the shader effect on hostile entities.").defaultValue(true).build());
   private final Setting<Boolean> animals = this.sgTargets.add(new BoolSetting.Builder().name("animals").description("Renders the shader effect on animal entities.").defaultValue(true).build());
   private final Setting<Boolean> ambient = this.sgTargets.add(new BoolSetting.Builder().name("ambient").description("Renders the shader effect on ambient entities.").defaultValue(false).build());
   private final Setting<Boolean> invisibles = this.sgTargets.add(new BoolSetting.Builder().name("invisibles").description("Renders the shader effect on invisible entities.").defaultValue(true).build());
   private final Setting<Boolean> items = this.sgTargets.add(new BoolSetting.Builder().name("items").description("Renders the shader effect on item entities.").defaultValue(true).build());
   private final Setting<Boolean> crystals = this.sgTargets.add(new BoolSetting.Builder().name("crystals").description("Renders the shader effect on crystal entities.").defaultValue(true).build());
   private final Setting<Boolean> pearls = this.sgTargets.add(new BoolSetting.Builder().name("pearls").description("Renders the shader effect on ender pearls.").defaultValue(false).build());
   private final Setting<Boolean> exp = this.sgTargets.add(new BoolSetting.Builder().name("exp").description("Renders the shader effect on experience orbs and bottles.").defaultValue(false).build());
   private final Setting<Boolean> minecarts = this.sgTargets.add(new BoolSetting.Builder().name("minecarts").description("Renders the shader effect on minecarts.").defaultValue(false).build());
   private final Setting<Boolean> dead = this.sgTargets.add(new BoolSetting.Builder().name("dead").description("Keeps rendering the shader effect on dying entities.").defaultValue(true).build());
   private final Setting<Boolean> others = this.sgTargets.add(new BoolSetting.Builder().name("others").description("Renders the shader effect on miscellaneous entities.").defaultValue(false).build());

   private final Setting<Double> range = this.sgRanges.add(new DoubleSetting.Builder().name("range").description("Maximum distance for most entities.").defaultValue(60).min(1).sliderRange(1, 300).build());
   private final Setting<Double> crystalRange = this.sgRanges.add(new DoubleSetting.Builder().name("crystal-range").description("Maximum distance for crystals.").defaultValue(60).min(1).sliderRange(1, 300).build());
   private final Setting<Double> itemRange = this.sgRanges.add(new DoubleSetting.Builder().name("item-range").description("Maximum distance for items.").defaultValue(60).min(1).sliderRange(1, 300).build());
   private final Setting<Boolean> fade = this.sgRanges.add(new BoolSetting.Builder().name("fade").description("Fades the fill out as entities approach the range limit.").defaultValue(false).build());
   private final Setting<Double> fadeStart = this.sgRanges.add(new DoubleSetting.Builder().name("fade-start").description("Percent of the range where the fade begins.").defaultValue(60).min(0).max(99).sliderRange(0, 99).visible(this.fade::get).build());

   public final Setting<RenderMode> mode = this.sgRender.add(new EnumSetting.Builder<RenderMode>().name("mode").description("Fill, outline or both.").defaultValue(RenderMode.Both).build());
   public final Setting<Integer> opacity = this.sgRender.add(new IntSetting.Builder().name("opacity").description("The opacity of the fill.").defaultValue(80).min(0).max(255).sliderMin(0).sliderMax(255).visible(() -> this.mode.get() != RenderMode.Outline).build());
   private final Setting<SettingColor> color = this.sgRender.add(new ColorSetting.Builder().name("color").description("The color of the shader effect.").defaultValue(new SettingColor(255, 255, 255, 255)).build());
   private final Setting<Boolean> colorFriends = this.sgRender.add(new BoolSetting.Builder().name("color-friends").description("Uses a separate color for friended players.").defaultValue(true).build());
   private final Setting<SettingColor> friendColor = this.sgRender.add(new ColorSetting.Builder().name("friend-color").description("The color of the shader effect on friends.").defaultValue(new SettingColor(85, 255, 255, 255)).visible(this.colorFriends::get).build());

   public final Setting<Style> style = this.sgLook.add(new EnumSetting.Builder<Style>().name("style").description("Solid uses the color as is, Gradient blends to a second color, Rainbow cycles hues.").defaultValue(Style.Solid).build());
   public final Setting<SettingColor> secondColor = this.sgLook.add(new ColorSetting.Builder().name("second-color").description("The second color of the gradient.").defaultValue(new SettingColor(160, 80, 255, 255)).visible(() -> this.style.get() == Style.Gradient).build());
   public final Setting<Integer> lineWidth = this.sgLook.add(new IntSetting.Builder().name("line-width").description("Width of the outline in pixels.").defaultValue(3).min(1).max(10).sliderMin(1).sliderMax(10).visible(() -> this.mode.get() != RenderMode.Fill).build());
   public final Setting<Double> lineStrength = this.sgLook.add(new DoubleSetting.Builder().name("line-strength").description("Strength of the outline.").defaultValue(1.0).min(0).sliderMax(5.0).visible(() -> this.mode.get() != RenderMode.Fill).build());
   public final Setting<Double> fillStrength = this.sgLook.add(new DoubleSetting.Builder().name("fill-strength").description("Strength of the fill.").defaultValue(1.0).min(0).sliderMax(5.0).visible(() -> this.mode.get() != RenderMode.Outline).build());
   public final Setting<Boolean> animated = this.sgLook.add(new BoolSetting.Builder().name("animated-colors").description("Scrolling enchantment-glint style animated colors.").defaultValue(false).build());
   public final Setting<Double> animationSpeed = this.sgLook.add(new DoubleSetting.Builder().name("animation-speed").description("Speed of the animations.").defaultValue(1.0).min(0.1).sliderMax(5.0).build());
   public final Setting<SettingColor> animatedColor = this.sgLook.add(new ColorSetting.Builder().name("animated-color").description("The color of the animated glint.").defaultValue(new SettingColor(160, 80, 255, 255)).visible(this.animated::get).build());

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
      return new Color(base.r, base.g, base.b, fadeAlpha(entity));
   }

   private int fadeAlpha(Entity entity) {
      if (!this.fade.get() || this.mc.player == null || entity == this.mc.player) {
         return 255;
      }

      double limit = limitFor(entity);
      double start = limit * this.fadeStart.get() / 100.0;
      double dist = entity.distanceTo(this.mc.player);
      if (dist <= start) {
         return 255;
      }

      double factor = 1.0 - (dist - start) / Math.max(limit - start, 0.001);
      return (int) (255 * Math.max(0.05, Math.min(1.0, factor)));
   }

   private double limitFor(Entity entity) {
      EntityType<?> type = entity.getType();
      if (type == EntityType.END_CRYSTAL) {
         return this.crystalRange.get();
      }

      if (type == EntityType.ITEM) {
         return this.itemRange.get();
      }

      return this.range.get();
   }

   public boolean isValid(Entity entity) {
      if (entity == this.mc.player) {
         return this.self.get() && !this.mc.options.getPerspective().isFirstPerson();
      }

      if (this.mc.player != null && entity.distanceTo(this.mc.player) > limitFor(entity)) {
         return false;
      }

      if (!this.dead.get() && entity instanceof LivingEntity living && living.isDead()) {
         return false;
      }

      EntityType<?> type = entity.getType();
      SpawnGroup group = type.getSpawnGroup();

      if (this.players.get() && type == EntityType.PLAYER) {
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

      if (this.items.get() && (type == EntityType.ITEM || type == EntityType.EXPERIENCE_BOTTLE)) {
         return true;
      }

      if (this.exp.get() && (type == EntityType.EXPERIENCE_ORB || type == EntityType.EXPERIENCE_BOTTLE)) {
         return true;
      }

      if (this.pearls.get() && type == EntityType.ENDER_PEARL) {
         return true;
      }

      if (this.crystals.get() && type == EntityType.END_CRYSTAL) {
         return true;
      }

      if (this.minecarts.get() && entity instanceof AbstractMinecartEntity) {
         return true;
      }

      return this.others.get();
   }
}
