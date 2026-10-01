package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.deathlogger.DeathLogStore;
import gamerguy11.sixtoolsaddon.deathlogger.DeathRecord;
import gamerguy11.sixtoolsaddon.gui.DeathLogScreen;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;

public class DeathLogger extends Module {
   private int lastX;
   private int lastY;
   private int lastZ;
   private String lastDimension = "Overworld";
   private boolean hasPosition;
   private boolean wasAlive = true;

   public DeathLogger() {
      super(SixToolsAddon.CATEGORY, "death-logger", "Logs the coordinates and dimension of every death to a text file.");
   }

   public void onActivate() {
      this.hasPosition = false;
      this.wasAlive = true;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         boolean alive = this.mc.player.getHealth() > 0.0F && !this.mc.player.isDead();
         if (alive) {
            this.lastX = this.mc.player.getBlockX();
            this.lastY = this.mc.player.getBlockY();
            this.lastZ = this.mc.player.getBlockZ();
            this.lastDimension = PlayerUtils.getDimension().name();
            this.hasPosition = true;
         } else if (this.wasAlive && this.hasPosition) {
            DeathLogStore.append(DeathRecord.now(this.lastX, this.lastY, this.lastZ, this.lastDimension));
         }

         this.wasAlive = alive;
      }
   }

   public WWidget getWidget(GuiTheme theme) {
      WButton button = theme.button("View Death Log");
      button.action = () -> this.mc.setScreen(new DeathLogScreen(theme));
      return button;
   }
}
