package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.network.OtherClientPlayerEntity;

public class Parkinsons extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Double> speed;
   private float storedYaw;
   private float storedPitch;
   private boolean forward;
   private boolean backward;
   private boolean left;
   private boolean right;
   private boolean up;
   private boolean down;

   public Parkinsons() {
      super(SixToolsAddon.CATEGORY, "parkinsons", "WARNINGS: your real player stays standing still Don't run it together with any Freecam. Turns itself off when you leave the world.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.speed = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("speed")).description("Camera speed in blocks per tick. 1 = the original (20 blocks per second).")).defaultValue((double)1.0F).min(0.05).sliderRange(0.1, (double)3.0F).build());
   }

   public void onActivate() {
      if (this.mc.player != null && this.mc.world != null) {
         this.storedYaw = this.mc.player.getYaw();
         this.storedPitch = this.mc.player.getPitch();
         this.forward = Input.isPressed(this.mc.options.forwardKey);
         this.backward = Input.isPressed(this.mc.options.backKey);
         this.left = Input.isPressed(this.mc.options.leftKey);
         this.right = Input.isPressed(this.mc.options.rightKey);
         this.up = Input.isPressed(this.mc.options.jumpKey);
         this.down = Input.isPressed(this.mc.options.sneakKey);
         this.unpress();
         OtherClientPlayerEntity freecamEntity = new OtherClientPlayerEntity(this.mc.world, this.mc.player.getGameProfile());
         freecamEntity.copyPositionAndRotation(this.mc.player);
         freecamEntity.setYaw(this.mc.player.getYaw());
         freecamEntity.setPitch(this.mc.player.getPitch());
         freecamEntity.setNoGravity(true);
         freecamEntity.noClip = true;
         freecamEntity.setOnGround(false);
         this.mc.setCameraEntity(freecamEntity);
      } else {
         this.toggle();
      }
   }

   @EventHandler
   public void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         Entity camera = this.mc.getCameraEntity();
         if (camera != null && camera != this.mc.player) {
            camera.setYaw(this.mc.player.getYaw());
            camera.setPitch(this.mc.player.getPitch());
            Vec3d look = Vec3d.fromPolar(0.0F, camera.getYaw()).normalize();
            Vec3d strafe = (new Vec3d(-look.z, (double)0.0F, look.x)).normalize();
            Vec3d velocity = Vec3d.ZERO;
            if (this.forward) {
               velocity = velocity.add(look);
            }

            if (this.backward) {
               velocity = velocity.subtract(look);
            }

            if (this.left) {
               velocity = velocity.subtract(strafe);
            }

            if (this.right) {
               velocity = velocity.add(strafe);
            }

            if (this.up) {
               velocity = velocity.add((double)0.0F, (double)1.0F, (double)0.0F);
            }

            if (this.down) {
               velocity = velocity.add((double)0.0F, (double)-1.0F, (double)0.0F);
            }

            if (velocity.lengthSquared() > (double)0.0F) {
               velocity = velocity.normalize().multiply((Double)this.speed.get());
               Vec3d pos = camera.getEntityPos();
               camera.setPos(pos.x + velocity.x, pos.y + velocity.y, pos.z + velocity.z);
            }

         }
      }
   }

   @EventHandler
   public void onKey(KeyEvent event) {
      if (this.mc.currentScreen == null) {
         int key = event.key();
         boolean pressed = event.action != KeyAction.Release;
         boolean handled = true;
         if (Input.getKey(this.mc.options.forwardKey) == key) {
            this.forward = pressed;
            this.mc.options.forwardKey.setPressed(false);
         } else if (Input.getKey(this.mc.options.backKey) == key) {
            this.backward = pressed;
            this.mc.options.backKey.setPressed(false);
         } else if (Input.getKey(this.mc.options.leftKey) == key) {
            this.left = pressed;
            this.mc.options.leftKey.setPressed(false);
         } else if (Input.getKey(this.mc.options.rightKey) == key) {
            this.right = pressed;
            this.mc.options.rightKey.setPressed(false);
         } else if (Input.getKey(this.mc.options.jumpKey) == key) {
            this.up = pressed;
            this.mc.options.jumpKey.setPressed(false);
         } else if (Input.getKey(this.mc.options.sneakKey) == key) {
            this.down = pressed;
            this.mc.options.sneakKey.setPressed(false);
         } else {
            handled = false;
         }

         if (handled) {
            event.cancel();
         }

      }
   }

   @EventHandler
   public void onGameLeft(GameLeftEvent event) {
      if (this.isActive()) {
         this.toggle();
      }

   }

   private void unpress() {
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.backKey.setPressed(false);
      this.mc.options.leftKey.setPressed(false);
      this.mc.options.rightKey.setPressed(false);
      this.mc.options.jumpKey.setPressed(false);
      this.mc.options.sneakKey.setPressed(false);
   }

   public void onDeactivate() {
      this.forward = this.backward = this.left = this.right = this.up = this.down = false;
      if (this.mc.player != null) {
         this.mc.setCameraEntity(this.mc.player);
         this.mc.player.setYaw(this.storedYaw);
         this.mc.player.setPitch(this.storedPitch);
      }
   }
}
