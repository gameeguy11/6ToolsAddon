package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class DeathEffects extends Module {
   public enum Effect {
      Lightning
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Effect> effect = this.sgGeneral.add(new EnumSetting.Builder<Effect>().name("effect").description("The effect rendered when a player dies.").defaultValue(Effect.Lightning).build());
   private final Setting<Boolean> self = this.sgGeneral.add(new BoolSetting.Builder().name("self").description("Also plays the effect when you die.").defaultValue(true).build());
   private int counter;

   public DeathEffects() {
      super(SixToolsAddon.CATEGORY, "death-effects", "Renders an effect on players when they die.");
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (!(event.packet instanceof EntityStatusS2CPacket packet) || packet.getStatus() != 3) {
         return;
      }

      this.mc.execute(() -> {
         if (this.mc.world == null) {
            return;
         }

         Entity entity = packet.getEntity(this.mc.world);
         if (!(entity instanceof PlayerEntity player) || (player == this.mc.player && !this.self.get())) {
            return;
         }

         if (this.effect.get() == Effect.Lightning) {
            LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, this.mc.world);
            bolt.setPosition(player.getEntityPos());
            bolt.setId(-701 - (this.counter++ % 64));
            this.mc.world.addEntity(bolt);
         }
      });
   }
}
