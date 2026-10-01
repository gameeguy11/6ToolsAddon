package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.homes.Home;
import gamerguy11.sixtoolsaddon.homes.HomeStore;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

public class AutoReturnHome extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Integer> defaultCooldown;
   private final Setting<Boolean> protectedOnly;
   private final Setting<Boolean> chatFeedback;
   private final Setting<String> homeCommand;
   private Home armedHome;
   private long armedAt;

   public AutoReturnHome() {
      super(SixToolsAddon.CATEGORY, "auto-return-home", "Auto-runs /home again after a cooldown if you get teleported into one of your saved homes.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.defaultCooldown = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("default-cooldown")).description("Default seconds to wait, after landing in a home, before auto-running \"/home <name>\" again. Individual homes can override this.")).defaultValue(60)).min(1).sliderRange(1, 600).build());
      this.protectedOnly = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("protected-homes-only")).description("Only trigger for homes marked as protected. Turn off to trigger for any saved home, protected or not.")).defaultValue(false)).build());
      this.chatFeedback = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("chat-feedback")).description("Shows a countdown message when the timer starts and when it fires.")).defaultValue(true)).build());
      this.homeCommand = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("home-command")).description("Command sent to return home. %name% is replaced with the home's name.")).defaultValue("/home %name%")).build());
   }

   public void onDeactivate() {
      this.armedHome = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      Home here = (Boolean)this.protectedOnly.get() ? HomeStore.protectedHomeAtPlayer() : HomeStore.anyHomeAtPlayer();
      if (here == null) {
         this.armedHome = null;
      } else if (this.armedHome != here) {
         this.armedHome = here;
         this.armedAt = System.currentTimeMillis();
         if ((Boolean)this.chatFeedback.get()) {
            this.info("Landed in (highlight)%s(default) - returning in %ds unless you leave.", new Object[]{here.name, this.cooldownFor(here)});
         }

      } else {
         long elapsed = (System.currentTimeMillis() - this.armedAt) / 1000L;
         if (elapsed >= (long)this.cooldownFor(here)) {
            String cmd = ((String)this.homeCommand.get()).replace("%name%", here.name);
            ChatUtils.sendPlayerMsg(cmd);
            if ((Boolean)this.chatFeedback.get()) {
               this.info("Auto-returning home from (highlight)%s(default).", new Object[]{here.name});
            }

            this.armedHome = null;
         }
      }
   }

   private int cooldownFor(Home home) {
      return home.overrideCooldown ? Math.max(0, home.cooldown) : (Integer)this.defaultCooldown.get();
   }
}
