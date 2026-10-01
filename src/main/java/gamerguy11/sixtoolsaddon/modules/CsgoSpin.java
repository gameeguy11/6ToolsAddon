package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.BedAura;
import meteordevelopment.meteorclient.systems.modules.combat.Quiver;
import meteordevelopment.meteorclient.systems.modules.player.EXPThrower;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BowItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Items;

public class CsgoSpin extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Mode> spinMode;
   private final Setting<Double> speed;
   private final Setting<AntiDesyncTrigger> antiDesync;
   private final Setting<Boolean> yaw;
   private final Setting<Integer> ySpeed;
   private final Setting<Boolean> pitch;
   private final Setting<Integer> pSpeed;
   private short count;
   private short yCount;
   private short pCount;

   public CsgoSpin() {
      super(SixToolsAddon.CATEGORY, "csgo-spin", "Tries to rotate you.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.spinMode = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("spin-mode")).description("The way in which to spin you.")).defaultValue(CsgoSpin.Mode.CSGO)).build());
      this.speed = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("rotation-speed")).description("The speed at which you rotate.")).defaultValue((double)20.0F).sliderMin((double)0.0F).sliderMax((double)50.0F).visible(() -> this.spinMode.get() == CsgoSpin.Mode.CS2)).build());
      this.antiDesync = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("anti-desync")).description("Stops spinning on some triggers.")).defaultValue(CsgoSpin.AntiDesyncTrigger.All)).visible(() -> this.spinMode.get() == CsgoSpin.Mode.CSGO)).build());
      this.yaw = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("yaw")).description("Spin around.")).defaultValue(true)).visible(() -> this.spinMode.get() == CsgoSpin.Mode.CSGO)).build());
      this.ySpeed = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("yaw-speed")).description("The speed at which you rotate.")).defaultValue(5)).range(1, 100).visible(() -> this.spinMode.get() == CsgoSpin.Mode.CSGO && (Boolean)this.yaw.get())).build());
      this.pitch = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("pitch")).description("Spin around.")).defaultValue(false)).visible(() -> this.spinMode.get() == CsgoSpin.Mode.CSGO)).build());
      this.pSpeed = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("rotation-speed")).description("The speed at which you rotate.")).defaultValue(5)).range(1, 100).visible(() -> this.spinMode.get() == CsgoSpin.Mode.CSGO && (Boolean)this.pitch.get())).build());
      this.count = 0;
      this.yCount = 0;
      this.pCount = 0;
   }

   public void onActivate() {
      this.count = 0;
   }

   @EventHandler
   public void onTick(TickEvent.Post event) {
      assert this.mc.player != null;

      if (this.spinMode.get() == CsgoSpin.Mode.CSGO) {
         this.csgoSpin();
      } else {
         this.cs2Spin();
      }

   }

   private void csgoSpin() {
      switch (((AntiDesyncTrigger)this.antiDesync.get()).ordinal()) {
         case 0:
            if (Modules.get().isActive(EXPThrower.class) || Modules.get().isActive(BedAura.class) || this.mc.player.getMainHandStack().getItem() instanceof ExperienceBottleItem || this.mc.player.getOffHandStack().getItem() instanceof ExperienceBottleItem || this.mc.player.getMainHandStack().getItem() instanceof EnderPearlItem || this.mc.player.getOffHandStack().getItem() instanceof EnderPearlItem || this.mc.player.getMainHandStack().getItem() instanceof BowItem || this.mc.player.getOffHandStack().getItem() instanceof BowItem || this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
               return;
            }
            break;
         case 1:
            if (Modules.get().isActive(EXPThrower.class) || Modules.get().isActive(BedAura.class) || this.mc.player.getMainHandStack().getItem() instanceof ExperienceBottleItem || this.mc.player.getOffHandStack().getItem() instanceof ExperienceBottleItem || this.mc.player.getMainHandStack().getItem() instanceof EnderPearlItem || this.mc.player.getOffHandStack().getItem() instanceof EnderPearlItem || this.mc.player.getMainHandStack().getItem() instanceof BowItem || this.mc.player.getOffHandStack().getItem() instanceof BowItem) {
               return;
            }
         case 2:
      }

      this.yCount = (short)(this.yCount + (Integer)this.ySpeed.get());
      if (this.yCount > 180) {
         this.yCount = -180;
      }

      if ((Boolean)this.pitch.get()) {
         ++this.count;
         if (this.count <= (Integer)this.pSpeed.get()) {
            this.pCount = 90;
         }

         if (this.count > (Integer)this.pSpeed.get()) {
            this.pCount = -90;
         }

         if (this.count >= (Integer)this.pSpeed.get() + (Integer)this.pSpeed.get()) {
            this.count = 0;
         }
      }

      Rotations.rotate((Boolean)this.yaw.get() ? (double)this.yCount : (double)this.mc.player.getYaw(), (Boolean)this.yaw.get() ? (double)this.pCount : (double)this.mc.player.getPitch());
   }

   private void cs2Spin() {
      Modules modules = Modules.get();
      if (!modules.isActive(EXPThrower.class) && !modules.isActive(Quiver.class) && !modules.isActive(EXPThrower.class)) {
         this.count = (short)((int)((double)this.count + (Double)this.speed.get()));
         if (this.count > 180) {
            this.count = (short)(this.count - 360);
         }

         Rotations.rotate((double)this.count, (double)0.0F);
      }

   }

   public static enum Mode {
      CSGO,
      CS2;

      private static Mode[] $values() {
         return new Mode[]{CSGO, CS2};
      }
   }

   public static enum AntiDesyncTrigger {
      All,
      ExceptElytra,
      None;

      private static AntiDesyncTrigger[] $values() {
         return new AntiDesyncTrigger[]{All, ExceptElytra, None};
      }
   }
}
