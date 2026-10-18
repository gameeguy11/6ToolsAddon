package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class SwingSpeed extends Module {
   public enum HandMode {
      Default,
      None,
      Packet,
      Mainhand,
      Offhand,
      Both
   }

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
      this.modifySpeed = this.sgGeneral.add(new BoolSetting.Builder().name("modify-speed").description("Modifies the speed of your swing animation.").defaultValue(true).build());
      this.speed = this.sgGeneral.add(new IntSetting.Builder().name("speed").description("The speed at which your hand will be swinging.").defaultValue(15).min(1).sliderMin(1).max(20).sliderMax(20).visible(this.modifySpeed::get).build());
      this.hand = this.sgHand.add(new EnumSetting.Builder<HandMode>().name("hand").description("The hand used for swinging. None cancels the swing entirely, Packet only tells the server.").defaultValue(HandMode.Default).build());
      this.noPacket = this.sgHand.add(new BoolSetting.Builder().name("no-packet").description("Only swings client-side, so nobody can tell you are swinging.").defaultValue(false).visible(() -> this.hand.get() != HandMode.Packet && this.hand.get() != HandMode.None).build());
      this.rotationX = this.sgRotation.add(new BoolSetting.Builder().name("rotation-x").description("Enables the swing rotation on the X axis.").defaultValue(true).build());
      this.rotationY = this.sgRotation.add(new BoolSetting.Builder().name("rotation-y").description("Enables the swing rotation on the Y axis.").defaultValue(true).build());
      this.rotationZ = this.sgRotation.add(new BoolSetting.Builder().name("rotation-z").description("Enables the swing rotation on the Z axis.").defaultValue(true).build());
   }

   public int getSwingDuration(int original) {
      if (!this.modifySpeed.get() || !this.mc.options.getPerspective().isFirstPerson()) {
         return original;
      }

      return 21 - this.speed.get();
   }
}
