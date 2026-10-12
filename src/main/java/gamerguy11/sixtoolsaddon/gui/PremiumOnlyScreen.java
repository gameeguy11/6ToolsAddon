package gamerguy11.sixtoolsaddon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

public class PremiumOnlyScreen extends WindowScreen {
   public PremiumOnlyScreen(GuiTheme theme) {
      super(theme, "6ToolsChat");
   }

   public void initWidgets() {
      this.add(this.theme.label("This chat is only for premium players.")).expandX().centerX();
      this.add(this.theme.label("Cracked accounts cannot be verified.", 320.0)).expandX().centerX();
      WButton ok = this.add(this.theme.button("OK")).expandX().widget();
      ok.action = this::close;
      this.enterAction = this::close;
   }
}
