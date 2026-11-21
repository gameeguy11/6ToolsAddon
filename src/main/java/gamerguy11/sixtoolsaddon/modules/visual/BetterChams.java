package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import java.util.HashMap;
import java.util.Map;

public class BetterChams extends Module {

   private final SettingGroup sgEntities = this.settings.createGroup("Entities");
   private final SettingGroup sgFriends = this.settings.createGroup("Friends");
   private final SettingGroup sgCrystals = this.settings.createGroup("Crystals");
   private final SettingGroup sgRanges = this.settings.createGroup("Ranges");
   private final SettingGroup sgPop = this.settings.createGroup("Pop");

   private final Map<Integer, Long> pops = new HashMap<>();

   private final Setting<Boolean> players = this.sgEntities.add(new BoolSetting.Builder().name("players").description("Renders the chams on player entities.").defaultValue(true).build());
   private final Setting<Boolean> self = this.sgEntities.add(new BoolSetting.Builder().name("self").description("Also renders the chams on yourself.").defaultValue(false).build());
   private final Setting<Boolean> hostiles = this.sgEntities.add(new BoolSetting.Builder().name("hostiles").description("Renders the chams on hostile entities.").defaultValue(false).build());
   private final Setting<Boolean> passives = this.sgEntities.add(new BoolSetting.Builder().name("passives").description("Renders the chams on passive entities.").defaultValue(false).build());
   private final Setting<Boolean> entityPulse = this.sgEntities.add(new BoolSetting.Builder().name("pulse").description("Adds a pulsing effect to the chams opacity.").defaultValue(false).build());
   private final Setting<ShapeMode> entityMode = this.sgEntities.add(new EnumSetting.Builder<ShapeMode>().name("mode").description("Sides is the fill, Lines is the outline.").defaultValue(ShapeMode.Both).build());
   private final Setting<SettingColor> entityFill = this.sgEntities.add(new ColorSetting.Builder().name("fill-color").description("The color of the fill.").defaultValue(new SettingColor(255, 255, 255, 40)).visible(() -> this.entityMode.get() != ShapeMode.Lines).build());
   private final Setting<SettingColor> entityOutline = this.sgEntities.add(new ColorSetting.Builder().name("outline-color").description("The color of the outline.").defaultValue(new SettingColor(255, 255, 255, 255)).visible(() -> this.entityMode.get() != ShapeMode.Sides).build());
   private final Setting<Boolean> damageModify = this.sgEntities.add(new BoolSetting.Builder().name("damage-color").description("Changes the color of the chams when an entity takes damage.").defaultValue(false).build());
   private final Setting<SettingColor> damageColor = this.sgEntities.add(new ColorSetting.Builder().name("damage-color-value").description("The color applied when an entity takes damage.").defaultValue(new SettingColor(255, 0, 0, 255)).visible(this.damageModify::get).build());

   private final Setting<Boolean> colorFriends = this.sgFriends.add(new BoolSetting.Builder().name("color-friends").description("Uses separate colors for friended players.").defaultValue(true).build());
   private final Setting<SettingColor> friendFill = this.sgFriends.add(new ColorSetting.Builder().name("friend-fill-color").description("The fill color on friends.").defaultValue(new SettingColor(85, 255, 255, 40)).visible(this.colorFriends::get).build());
   private final Setting<SettingColor> friendOutline = this.sgFriends.add(new ColorSetting.Builder().name("friend-outline-color").description("The outline color on friends.").defaultValue(new SettingColor(85, 255, 255, 255)).visible(this.colorFriends::get).build());

   private final Setting<Boolean> crystals = this.sgCrystals.add(new BoolSetting.Builder().name("crystals").description("Renders the chams on crystal entities.").defaultValue(true).build());
   private final Setting<Boolean> crystalPulse = this.sgCrystals.add(new BoolSetting.Builder().name("crystal-pulse").description("Adds a pulsing effect to the crystal chams opacity.").defaultValue(false).visible(this.crystals::get).build());
   private final Setting<ShapeMode> crystalMode = this.sgCrystals.add(new EnumSetting.Builder<ShapeMode>().name("crystal-mode").description("Sides is the fill, Lines is the outline.").defaultValue(ShapeMode.Both).visible(this.crystals::get).build());
   private final Setting<SettingColor> crystalFill = this.sgCrystals.add(new ColorSetting.Builder().name("crystal-fill-color").description("The fill color on crystals.").defaultValue(new SettingColor(255, 255, 255, 40)).visible(() -> this.crystals.get() && this.crystalMode.get() != ShapeMode.Lines).build());
   private final Setting<SettingColor> crystalOutline = this.sgCrystals.add(new ColorSetting.Builder().name("crystal-outline-color").description("The outline color on crystals.").defaultValue(new SettingColor(255, 255, 255, 255)).visible(() -> this.crystals.get() && this.crystalMode.get() != ShapeMode.Sides).build());

   private final Setting<Double> pulseSpeed = this.sgEntities.add(new DoubleSetting.Builder().name("pulse-speed").description("Speed of the pulse.").defaultValue(0.75).min(0.05).sliderMax(3.0).visible(() -> this.entityPulse.get() || this.crystalPulse.get()).build());
   private final Setting<Boolean> dead = this.sgEntities.add(new BoolSetting.Builder().name("dead").description("Keeps rendering the chams on dying entities.").defaultValue(true).build());

   private final Setting<Double> playerRange = this.sgRanges.add(new DoubleSetting.Builder().name("player-range").description("Maximum distance for players.").defaultValue(60).min(1).sliderRange(1, 300).build());
   private final Setting<Double> crystalRange = this.sgRanges.add(new DoubleSetting.Builder().name("crystal-range").description("Maximum distance for crystals.").defaultValue(60).min(1).sliderRange(1, 300).build());
   private final Setting<Double> otherRange = this.sgRanges.add(new DoubleSetting.Builder().name("other-range").description("Maximum distance for other entities.").defaultValue(60).min(1).sliderRange(1, 300).build());
   private final Setting<Boolean> fade = this.sgRanges.add(new BoolSetting.Builder().name("fade").description("Fades the chams out as entities approach the range limit.").defaultValue(false).build());
   private final Setting<Double> fadeStart = this.sgRanges.add(new DoubleSetting.Builder().name("fade-start").description("Percent of the range where the fade begins.").defaultValue(60).min(0).max(99).sliderRange(0, 99).visible(this.fade::get).build());

   private final Setting<Boolean> popColors = this.sgPop.add(new BoolSetting.Builder().name("pop-colors").description("Recolors a player's chams for a moment after they pop a totem.").defaultValue(false).build());
   private final Setting<Double> popTime = this.sgPop.add(new DoubleSetting.Builder().name("pop-time").description("Seconds the pop colors stay.").defaultValue(1.0).min(0.1).sliderMax(5.0).visible(this.popColors::get).build());
   private final Setting<SettingColor> popFill = this.sgPop.add(new ColorSetting.Builder().name("pop-fill-color").description("The fill color after a pop.").defaultValue(new SettingColor(255, 170, 0, 90)).visible(this.popColors::get).build());
   private final Setting<SettingColor> popOutline = this.sgPop.add(new ColorSetting.Builder().name("pop-outline-color").description("The outline color after a pop.").defaultValue(new SettingColor(255, 170, 0, 255)).visible(this.popColors::get).build());

   public BetterChams() {
      super(SixToolsAddon.CATEGORY, "better-chams", "Renders a see-through wireframe model over the selected entities.");
   }

   @Override
   public void onDeactivate() {
      this.pops.clear();
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (!this.popColors.get() || this.mc.world == null) {
         return;
      }

      if (event.packet instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
         Entity entity = packet.getEntity(this.mc.world);
         if (entity != null) {
            this.pops.put(entity.getId(), System.currentTimeMillis());
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world == null || this.mc.player == null) {
         return;
      }

      long now = System.currentTimeMillis();
      this.pops.values().removeIf(t -> now - t > 10000L);

      for (Entity entity : this.mc.world.getEntities()) {
         if (entity == this.mc.player && !this.self.get()) {
            continue;
         }

         if (entity != this.mc.player && entity.distanceTo(this.mc.player) > rangeFor(entity)) {
            continue;
         }

         if (entity instanceof EndCrystalEntity) {
            if (this.crystals.get()) {
               Color fill = this.crystalFill.get();
               Color outline = this.crystalOutline.get();
               if (this.crystalPulse.get()) {
                  fill = pulse(fill);
                  outline = pulse(outline);
               }

               WireframeEntityRenderer.render(event, entity, 1.0, faded(entity, fill), faded(entity, outline), this.crystalMode.get());
            }

            continue;
         }

         if (entity instanceof LivingEntity living && this.isValid(living)) {
            if (!this.dead.get() && living.isDead()) {
               continue;
            }

            boolean hurt = this.damageModify.get() && living.hurtTime > 0;
            boolean friend = this.colorFriends.get() && living instanceof PlayerEntity player && Friends.get().isFriend(player);
            Long popped = this.popColors.get() ? this.pops.get(living.getId()) : null;
            boolean popping = popped != null && now - popped < this.popTime.get() * 1000.0;

            SettingColor fillBase = popping ? this.popFill.get() : friend ? this.friendFill.get() : this.entityFill.get();
            SettingColor outlineBase = popping ? this.popOutline.get() : friend ? this.friendOutline.get() : this.entityOutline.get();

            Color fill = hurt ? withAlpha(this.damageColor.get(), fillBase.a) : fillBase;
            Color outline = hurt ? withAlpha(this.damageColor.get(), outlineBase.a) : outlineBase;

            if (this.entityPulse.get()) {
               fill = pulse(fill);
               outline = pulse(outline);
            }

            WireframeEntityRenderer.render(event, living, 1.0, faded(living, fill), faded(living, outline), this.entityMode.get());
         }
      }
   }

   private double rangeFor(Entity entity) {
      if (entity instanceof EndCrystalEntity) {
         return this.crystalRange.get();
      }

      return entity.getType() == EntityType.PLAYER ? this.playerRange.get() : this.otherRange.get();
   }

   private Color faded(Entity entity, Color color) {
      if (!this.fade.get() || entity == this.mc.player) {
         return color;
      }

      double limit = rangeFor(entity);
      double start = limit * this.fadeStart.get() / 100.0;
      double dist = entity.distanceTo(this.mc.player);
      if (dist <= start) {
         return color;
      }

      double factor = Math.max(0.0, Math.min(1.0, 1.0 - (dist - start) / Math.max(limit - start, 0.001)));
      return new Color(color.r, color.g, color.b, (int) (color.a * factor));
   }

   private boolean isValid(Entity entity) {
      SpawnGroup group = entity.getType().getSpawnGroup();
      if (entity == this.mc.player) {
         return this.self.get();
      }

      if (this.players.get() && entity.getType() == EntityType.PLAYER) {
         return true;
      }

      if (this.hostiles.get() && group == SpawnGroup.MONSTER) {
         return true;
      }

      return this.passives.get() && (group == SpawnGroup.CREATURE || group == SpawnGroup.WATER_CREATURE || group == SpawnGroup.WATER_AMBIENT || group == SpawnGroup.UNDERGROUND_WATER_CREATURE || group == SpawnGroup.AXOLOTLS);
   }

   private static Color withAlpha(Color color, int alpha) {
      return new Color(color.r, color.g, color.b, alpha);
   }

   private Color pulse(Color color) {
      double sin = Math.sin(2.0 * Math.PI * this.pulseSpeed.get() * (System.currentTimeMillis() / 1000.0));
      double scale = (sin + 1.0) / 2.0;
      return new Color(color.r, color.g, color.b, (int)(color.a * scale));
   }
}
