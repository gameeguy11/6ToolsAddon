package gamerguy11.sixtoolsaddon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import net.minecraft.client.gui.screen.Screen;

public class ChatTab extends Tab {
   public ChatTab() {
      super("Chat");
   }

   public TabScreen createScreen(GuiTheme theme) {
      return new ChatScreen(theme, this);
   }

   public boolean isScreen(Screen screen) {
      return screen instanceof ChatScreen;
   }
}
