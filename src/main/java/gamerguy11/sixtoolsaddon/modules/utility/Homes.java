package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.gui.HomesScreen;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Homes extends Module {
   public Homes() {
      super(SixToolsAddon.CATEGORY, "homes", "Manage your homes. Auto TPY ignores or denies teleport requests while you stand inside a protected home.");
   }

   public WWidget getWidget(GuiTheme theme) {
      WButton button = theme.button("Manage Homes");
      button.action = () -> this.mc.setScreen(new HomesScreen(theme));
      return button;
   }
}
