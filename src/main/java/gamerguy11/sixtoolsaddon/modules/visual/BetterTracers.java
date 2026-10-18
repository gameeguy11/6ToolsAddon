package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

public class BetterTracers extends Module {
   public enum ColorMode {
      Distance,
      Custom
   }

   public enum Target {
      Feet,
      Torso,
      Head
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Target> target = this.sgGeneral.add(new EnumSetting.Builder<Target>().name("target").description("Which part of the player the line points to.").defaultValue(Target.Feet).build());
   private final Setting<ColorMode> colorMode = this.sgGeneral.add(new EnumSetting.Builder<ColorMode>().name("color-mode").description("Distance fades green to red as players get closer.").defaultValue(ColorMode.Distance).build());
   private final Setting<SettingColor> color = this.sgGeneral.add(new ColorSetting.Builder().name("color").description("Line color in Custom mode, and the alpha for every mode.").defaultValue(new SettingColor(255, 255, 255, 255)).build());
   private final Setting<SettingColor> friendColor = this.sgGeneral.add(new ColorSetting.Builder().name("friend-color").description("Color for players on your friends list.").defaultValue(new SettingColor(85, 255, 255, 255)).build());

   public BetterTracers() {
      super(SixToolsAddon.CATEGORY, "better-tracers", "Renders a line to every other player.");
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.world == null || this.mc.player == null) {
         return;
      }

      for (PlayerEntity player : this.mc.world.getPlayers()) {
         if (player == this.mc.player) {
            continue;
         }

         double x = MathHelper.lerp(event.tickDelta, player.lastRenderX, player.getX());
         double y = MathHelper.lerp(event.tickDelta, player.lastRenderY, player.getY());
         double z = MathHelper.lerp(event.tickDelta, player.lastRenderZ, player.getZ());
         y += switch (this.target.get()) {
            case Feet -> 0.0;
            case Torso -> player.getHeight() * 0.5;
            case Head -> player.getStandingEyeHeight();
         };
         event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, this.colorFor(player));
      }
   }

   private Color colorFor(PlayerEntity player) {
      int alpha = this.color.get().a;
      if (Friends.get().isFriend(player)) {
         Color c = this.friendColor.get();
         return new Color(c.r, c.g, c.b, alpha);
      }

      if (this.colorMode.get() == ColorMode.Custom) {
         return this.color.get();
      }

      float max = 80.0f;
      float distance = MathHelper.clamp(this.mc.player.distanceTo(player), 0.0f, max);
      float near = (max - distance) / max;
      return new Color((int) (near * 255.0f), (int) ((1.0f - near) * 255.0f), 0, alpha);
   }
}
