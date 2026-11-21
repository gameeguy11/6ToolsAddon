package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;

public class SwingSpeed extends Module {
   public enum HandMode {
      Default,
      None,
      Packet,
      Mainhand,
      Offhand,
      Both
   }

   public enum SwingType {
      Vanilla,
      OneTwelve,
      OneEight
   }

   public final Setting<SwingType> type;
   public final Setting<Double> speedMultiplier;
   public final Setting<Boolean> shake;
   private long nextShakeTime;
   private int shakeTicks;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgHand;
   private final SettingGroup sgRotation;
   public final Setting<Boolean> modifySpeed;
   public final Setting<Integer> speed;
   public final Setting<HandMode> hand;
   public final Setting<Boolean> noPacket;
   public final Setting<Boolean> rotationX;
   public final Setting<Boolean> rotationY;
   public final Setting<Boolean> rotationZ;

   public SwingSpeed() {
      super(SixToolsAddon.CATEGORY, "swing-speed", "Modifies the speed of your swing animation and the hand used for swinging.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgHand = this.settings.createGroup("Hand");
      this.sgRotation = this.settings.createGroup("Rotation");
      this.type = this.sgGeneral.add(new EnumSetting.Builder<SwingType>().name("type").description("Vanilla uses the speed below. 1.12 and 1.8 use their old swing length and equip animation.").defaultValue(SwingType.Vanilla).build());
      this.speedMultiplier = this.sgGeneral.add(new DoubleSetting.Builder().name("speed-multiplier").description("Multiplier on the 1.12 / 1.8 swing speed.").defaultValue(1.0).min(0.1).sliderRange(0.1, 3.0).visible(() -> this.type.get() != SwingType.Vanilla).build());
      this.shake = this.sgGeneral.add(new BoolSetting.Builder().name("shake").description("Shakes the held item randomly while swinging, like 1.12 reequip bobbing.").defaultValue(false).visible(() -> this.type.get() == SwingType.OneTwelve).build());
      this.modifySpeed = this.sgGeneral.add(new BoolSetting.Builder().name("modify-speed").description("Modifies the speed of your swing animation.").defaultValue(true).visible(() -> this.type.get() == SwingType.Vanilla).build());
      this.speed = this.sgGeneral.add(new IntSetting.Builder().name("speed").description("The speed at which your hand will be swinging.").defaultValue(15).min(1).sliderMin(1).max(20).sliderMax(20).visible(() -> this.type.get() == SwingType.Vanilla && this.modifySpeed.get()).build());
      this.hand = this.sgHand.add(new EnumSetting.Builder<HandMode>().name("hand").description("The hand used for swinging. None cancels the swing entirely, Packet only tells the server.").defaultValue(HandMode.Default).build());
      this.noPacket = this.sgHand.add(new BoolSetting.Builder().name("no-packet").description("Only swings client-side, so nobody can tell you are swinging.").defaultValue(false).visible(() -> this.hand.get() != HandMode.Packet && this.hand.get() != HandMode.None).build());
      this.rotationX = this.sgRotation.add(new BoolSetting.Builder().name("rotation-x").description("Enables the swing rotation on the X axis.").defaultValue(true).build());
      this.rotationY = this.sgRotation.add(new BoolSetting.Builder().name("rotation-y").description("Enables the swing rotation on the Y axis.").defaultValue(true).build());
      this.rotationZ = this.sgRotation.add(new BoolSetting.Builder().name("rotation-z").description("Enables the swing rotation on the Z axis.").defaultValue(true).build());
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (!(event.packet instanceof HandSwingC2SPacket)) {
         return;
      }

      long now = System.currentTimeMillis();
      if (now <= this.nextShakeTime) {
         return;
      }

      this.nextShakeTime = now + ThreadLocalRandom.current().nextInt(350) + 150L;
      this.shakeTicks = 3;
   }

   public boolean consumeShakeTick() {
      if (this.shakeTicks > 0) {
         this.shakeTicks--;
         return true;
      }

      return false;
   }

   public int getSwingDuration(int original) {
      SwingType current = this.type.get();
      if (current != SwingType.Vanilla) {
         double base = current == SwingType.OneEight ? 7.0 : 6.0;
         return Math.max(1, (int) (base / this.speedMultiplier.get()));
      }

      if (!this.modifySpeed.get() || !this.mc.options.getPerspective().isFirstPerson()) {
         return original;
      }

      return 21 - this.speed.get();
   }
}
