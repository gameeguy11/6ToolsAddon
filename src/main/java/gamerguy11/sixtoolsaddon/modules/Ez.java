package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class Ez extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgKill;
   private final SettingGroup sgPop;
   private final Setting<Double> range;
   private final Setting<Integer> delay;
   private final Setting<Boolean> killEnabled;
   private final Setting<List<String>> killMessages;
   private final Setting<Boolean> popEnabled;
   private final Setting<List<String>> popMessages;
   private final Random random;
   private final List<String> queue;
   private final Set<Integer> deadNotified;
   private int lastKillIndex;
   private int lastPopIndex;
   private int timer;

   public Ez() {
      super(SixToolsAddon.CATEGORY, "ez", "Sends a message you write yourself when a nearby player dies.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgKill = this.settings.createGroup("Kill");
      this.sgPop = this.settings.createGroup("Pop");
      this.range = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("range")).description("Only trigger for players within this distance of you.")).defaultValue((double)25.0F).min((double)0.0F).sliderRange((double)0.0F, (double)50.0F).build());
      this.delay = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("delay")).description("Ticks to wait between sending queued messages.")).defaultValue(50)).min(0).sliderRange(0, 100).build());
      this.killEnabled = this.sgKill.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("enabled")).description("Sends a message when a nearby non-friend player dies, regardless of who killed them.")).defaultValue(true)).build());
      this.killMessages = this.sgKill.add(((StringListSetting.Builder)((StringListSetting.Builder)(new StringListSetting.Builder()).name("kill-messages")).description("Write your own messages here - one is picked at random each time. Use <NAME> to insert the player's name. Empty by default: nothing sends until you add something.")).build());
      this.popEnabled = this.sgPop.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("enabled")).description("Sends a message when a nearby non-friend player pops a totem.")).defaultValue(false)).build());
      this.popMessages = this.sgPop.add(((StringListSetting.Builder)((StringListSetting.Builder)(new StringListSetting.Builder()).name("pop-messages")).description("Write your own messages here - one is picked at random each time. Use <NAME> to insert the player's name. Empty by default: nothing sends until you add something.")).build());
      this.random = new Random();
      this.queue = new LinkedList();
      this.deadNotified = new HashSet();
      this.lastKillIndex = -1;
      this.lastPopIndex = -1;
   }

   public void onActivate() {
      this.timer = 0;
      this.queue.clear();
      this.deadNotified.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         ++this.timer;
         if (this.timer >= (Integer)this.delay.get() && !this.queue.isEmpty()) {
            ChatUtils.sendPlayerMsg((String)this.queue.remove(0));
            this.timer = 0;
         }

         if ((Boolean)this.killEnabled.get()) {
            for(PlayerEntity player : this.mc.world.getPlayers()) {
               if (player != null && player != this.mc.player && !Friends.get().isFriend(player) && !(this.mc.player.getEntityPos().distanceTo(player.getEntityPos()) > (Double)this.range.get())) {
                  int id = player.getId();
                  if (player.getHealth() <= 0.0F) {
                     if (this.deadNotified.add(id)) {
                        this.queueMessage((List)this.killMessages.get(), this.lastKillIndex, (i) -> this.lastKillIndex = i, player.getName().getString());
                     }
                  } else {
                     this.deadNotified.remove(id);
                  }
               }
            }

         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.mc.player != null && this.mc.world != null) {
         if ((Boolean)this.popEnabled.get()) {
            Packet var3 = event.packet;
            if (var3 instanceof EntityStatusS2CPacket) {
               EntityStatusS2CPacket packet = (EntityStatusS2CPacket)var3;
               if (packet.getStatus() == 35) {
                  Entity entity = packet.getEntity(this.mc.world);
                  if (entity instanceof PlayerEntity) {
                     PlayerEntity player = (PlayerEntity)entity;
                     if (entity != this.mc.player && !Friends.get().isFriend(player) && this.mc.player.getEntityPos().distanceTo(player.getEntityPos()) <= (Double)this.range.get()) {
                        this.queueMessage((List)this.popMessages.get(), this.lastPopIndex, (i) -> this.lastPopIndex = i, player.getName().getString());
                     }
                  }
               }
            }
         }

      }
   }

   private void queueMessage(List<String> messages, int lastIndex, IntConsumer setLastIndex, String victimName) {
      if (!messages.isEmpty()) {
         int index = this.random.nextInt(messages.size());
         if (messages.size() > 1 && index == lastIndex) {
            index = (index + 1) % messages.size();
         }

         setLastIndex.accept(index);
         this.queue.add(((String)messages.get(index)).replace("<NAME>", victimName));
      }
   }
}
