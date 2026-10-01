package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.Iterator;
import java.util.Objects;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.gui.screen.DeathScreen;

public class Suicide extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgConditions;
   public final Setting<Boolean> disableDeath;
   public final Setting<Boolean> enableCA;
   private final SettingGroup sgCrystal;
   private final Setting<Boolean> autoCrystal;
   private final Setting<Integer> crystalDelay;
   private final Setting<Boolean> waitForConditions;
   private final Setting<RequireMode> requireMode;
   private final Setting<Integer> minTotems;
   private final Setting<Double> minHealth;
   private boolean triggered;
   private int cooldown;

   public Suicide() {
      super(SixToolsAddon.CATEGORY, "suicide", "Kills yourself. Recommended.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgConditions = this.settings.createGroup("Conditions");
      this.disableDeath = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("disable-on-death")).description("Disables the module on death.")).defaultValue(true)).build());
      this.enableCA = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("enable-crystal-aura")).description("Enables Meteor's Crystal Aura module when triggered, instead of the built-in auto-crystal below.")).defaultValue(false)).build());
      this.sgCrystal = this.settings.createGroup("Auto Crystal");
      this.autoCrystal = this.sgCrystal.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("enabled")).description("Places and pops an end crystal on yourself when triggered. Requires standing on obsidian or bedrock.")).defaultValue(true)).visible(() -> !(Boolean)this.enableCA.get())).build());
      this.crystalDelay = this.sgCrystal.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("delay-ticks")).description("Ticks to wait between each place/attack attempt.")).defaultValue(2)).min(0).sliderRange(0, 20).visible(() -> !(Boolean)this.enableCA.get() && (Boolean)this.autoCrystal.get())).build());
      this.waitForConditions = this.sgConditions.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("wait-for-conditions")).description("Only starts killing you once the conditions below are met, instead of immediately on activation.")).defaultValue(true)).build());
      SettingGroup var10001 = this.sgConditions;
      EnumSetting.Builder var10002 = (EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("require-mode")).description("Whether both conditions must be true, or just one of them, to trigger.")).defaultValue(Suicide.RequireMode.All);
      Setting<Boolean> var10003 = this.waitForConditions;
      Objects.requireNonNull(var10003);
      this.requireMode = var10001.add(((EnumSetting.Builder)var10002.visible(var10003::get)).build());
      var10001 = this.sgConditions;
      IntSetting.Builder var3 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("min-totems")).description("Triggers once you have this many totems of undying or fewer (0 means you have none left).")).defaultValue(0)).min(0).sliderRange(0, 5);
      var10003 = this.waitForConditions;
      Objects.requireNonNull(var10003);
      this.minTotems = var10001.add(((IntSetting.Builder)var3.visible(var10003::get)).build());
      var10001 = this.sgConditions;
      DoubleSetting.Builder var4 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("min-health")).description("Triggers once your health (including absorption) is this or lower.")).defaultValue((double)20.0F).min((double)0.0F).sliderRange((double)0.0F, (double)40.0F);
      var10003 = this.waitForConditions;
      Objects.requireNonNull(var10003);
      this.minHealth = var10001.add(((DoubleSetting.Builder)var4.visible(var10003::get)).build());
      this.triggered = false;
      this.cooldown = 0;
   }

   public void onActivate() {
      this.triggered = false;
      this.cooldown = 0;
      if (!(Boolean)this.waitForConditions.get()) {
         this.trigger();
      }

   }

   public void onDeactivate() {
      this.triggered = false;
      this.cooldown = 0;
      if ((Boolean)this.enableCA.get() && Modules.get().isActive(CrystalAura.class)) {
         ((CrystalAura)Modules.get().get(CrystalAura.class)).toggle();
      }

   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null) {
         if (this.triggered) {
            if (!(Boolean)this.enableCA.get() && (Boolean)this.autoCrystal.get()) {
               this.tickAutoCrystal();
            }

         } else if ((Boolean)this.waitForConditions.get()) {
            boolean totemsMet = this.countTotems() <= (Integer)this.minTotems.get();
            boolean healthMet = (double)(this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount()) <= (Double)this.minHealth.get();
            boolean shouldTrigger = this.requireMode.get() == Suicide.RequireMode.All ? totemsMet && healthMet : totemsMet || healthMet;
            if (shouldTrigger) {
               this.trigger();
            }

         }
      }
   }

   private void trigger() {
      this.triggered = true;
      if ((Boolean)this.enableCA.get() && !Modules.get().isActive(CrystalAura.class)) {
         ((CrystalAura)Modules.get().get(CrystalAura.class)).toggle();
      }

   }

   private void tickAutoCrystal() {
      if (this.mc.world != null && this.mc.interactionManager != null) {
         if (this.cooldown > 0) {
            --this.cooldown;
         } else {
            BlockPos basePos = this.mc.player.getBlockPos().down();
            BlockPos crystalPos = basePos.up();
            if (this.mc.world.getBlockState(basePos).getBlock() == Blocks.OBSIDIAN || this.mc.world.getBlockState(basePos).getBlock() == Blocks.BEDROCK) {
               Iterator var3 = this.mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(crystalPos), (c) -> c.getBlockPos().equals(crystalPos)).iterator();
               if (var3.hasNext()) {
                  EndCrystalEntity crystal = (EndCrystalEntity)var3.next();
                  this.mc.interactionManager.attackEntity(this.mc.player, crystal);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
                  this.cooldown = (Integer)this.crystalDelay.get();
               } else {
                  FindItemResult crystalItem = InvUtils.findInHotbar(new Item[]{Items.END_CRYSTAL});
                  if (crystalItem.found()) {
                     InvUtils.swap(crystalItem.slot(), true);
                     BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(basePos), Direction.UP, basePos, false);
                     this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hit);
                     this.mc.player.swingHand(Hand.MAIN_HAND);
                     InvUtils.swapBack();
                     this.cooldown = (Integer)this.crystalDelay.get();
                  }
               }
            }
         }
      }
   }

   private int countTotems() {
      int count = 0;

      for(int i = 0; i < this.mc.player.getInventory().size(); ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
            count += stack.getCount();
         }
      }

      return count;
   }

   @EventHandler(
      priority = 6969
   )
   private void onDeath(OpenScreenEvent event) {
      if (event.screen instanceof DeathScreen && (Boolean)this.disableDeath.get()) {
         this.toggle();
      }

   }

   public static enum RequireMode {
      All,
      Any;

      private static RequireMode[] $values() {
         return new RequireMode[]{All, Any};
      }
   }
}
