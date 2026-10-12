package gamerguy11.sixtoolsaddon.gui;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.chat.ChatMessage;
import gamerguy11.sixtoolsaddon.chat.ChatService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class ChatScreen extends WindowTabScreen {
   private static final int VISIBLE = 16;
   private static final double WIDTH = 460.0;
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
   private static String draft = "";

   private WHorizontalList tabs;
   private WVerticalList log;
   private WTextBox input;

   public ChatScreen(GuiTheme theme, Tab tab) {
      super(theme, tab);
   }

   public void initWidgets() {
      this.tabs = this.add(this.theme.horizontalList()).expandX().widget();
      this.add(this.theme.horizontalSeparator()).expandX();
      this.log = this.add(this.theme.verticalList()).expandX().minWidth(WIDTH).widget();
      this.add(this.theme.horizontalSeparator()).expandX();
      WHorizontalList row = this.add(this.theme.horizontalList()).expandX().widget();
      this.input = row.add(this.theme.textBox(draft)).expandX().minWidth(WIDTH - 90.0).widget();
      this.input.action = () -> draft = this.input.get();
      this.input.setFocused(true);
      WButton submit = row.add(this.theme.button("Submit")).widget();
      submit.action = this::submit;
      this.enterAction = this::submit;
      this.refresh();
   }

   public void refresh() {
      if (this.tabs == null || this.log == null) {
         return;
      }

      ChatService service = ChatService.INSTANCE;
      this.tabs.clear();
      for (ChatService.Conversation c : service.conversations()) {
         boolean current = c.key.equals(service.activeKey());
         String label = current ? "[" + c.label + "]" : c.label;
         if (!current && c.unread > 0) {
            label = label + " (" + c.unread + ")";
         }

         WButton tab = this.tabs.add(this.theme.button(label)).widget();
         tab.action = () -> {
            service.select(c.key);
            this.reload();
         };
      }

      WLabel status = this.tabs.add(this.theme.label(service.status())).expandCellX().right().widget();
      status.color(this.theme.textSecondaryColor());

      this.log.clear();
      List<ChatMessage> messages = service.active().messages;
      if (messages.isEmpty()) {
         this.log.add(this.theme.label("No messages yet")).expandX();
         return;
      }

      for (int i = Math.max(0, messages.size() - VISIBLE); i < messages.size(); i++) {
         ChatMessage message = messages.get(i);
         String time = "<" + TIME.format(Instant.ofEpochMilli(message.time())) + "> ";
         String body = message.system() ? time + message.text() : time + message.sender() + ": " + message.text();
         WLabel line = this.theme.label(body, WIDTH - 20.0);
         Color color = message.system() ? this.theme.textSecondaryColor() : message.self() ? SixToolsAddon.THEME_COLOR : this.theme.textColor();
         line.color(color);
         this.log.add(line).expandX();
      }
   }

   private void submit() {
      String text = this.input.get().trim();
      if (text.isEmpty()) {
         return;
      }

      this.input.set("");
      draft = "";
      ChatService.INSTANCE.send(ChatService.INSTANCE.activeKey(), text);
   }
}
