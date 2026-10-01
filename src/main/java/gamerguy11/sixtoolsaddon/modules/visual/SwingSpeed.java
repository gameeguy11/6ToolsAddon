package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.mixin.swing.LivingEntitySwingAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class SwingSpeed extends Module {
   private static final float OUT_TICKS_BASE = 3.0F;
   private static final float BACK_TICKS_BASE = 3.0F;
   private final SettingGroup sgGeneral;
   private final Setting<Double> speed;
   private float progress;
   private float lastProgress;
   private boolean animating;

   public SwingSpeed() {
      super(SixToolsAddon.CATEGORY, "swing-speed", "Slows down the outward part of your arm swing animation. Purely visual, client-side only.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.speed = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("out-speed")).description("Multiplier for how fast the outward swing plays. 1 = vanilla speed, lower = slower. The return to rest always plays at vanilla speed.")).defaultValue((double)1.0F).min(0.001).sliderMin(0.001).sliderMax((double)5.0F).build());
      this.progress = 0.0F;
      this.lastProgress = 0.0F;
      this.animating = false;
   }

   public void onDeactivate() {
      this.progress = 0.0F;
      this.lastProgress = 0.0F;
      this.animating = false;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null) {
         this.lastProgress = this.progress;
         boolean vanillaSwinging = ((LivingEntitySwingAccessor)this.mc.player).isHandSwinging();
         if (!this.animating && vanillaSwinging) {
            this.animating = true;
            this.progress = 0.0F;
            this.lastProgress = 0.0F;
         }

         if (this.animating) {
            float step;
            if (this.progress < 0.5F) {
               step = 0.16666667F * ((Double)this.speed.get()).floatValue();
            } else {
               step = 0.16666667F;
            }

            this.progress += step;
            if (this.progress >= 1.0F) {
               this.progress = 0.0F;
               this.lastProgress = 0.0F;
               this.animating = false;
            } else if (this.progress > 0.5F && this.lastProgress < 0.5F) {
               this.progress = 0.5F;
            }

         }
      }
   }

   public float getRenderProgress(float tickDelta) {
      if (!this.animating) {
         return 0.0F;
      } else {
         float delta = this.progress - this.lastProgress;
         if (delta < 0.0F) {
            ++delta;
         }

         return this.lastProgress + delta * tickDelta;
      }
   }
}
