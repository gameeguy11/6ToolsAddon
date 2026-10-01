package gamerguy11.sixtoolsaddon.gui;

import gamerguy11.sixtoolsaddon.deathlogger.DeathLogStore;
import gamerguy11.sixtoolsaddon.deathlogger.DeathRecord;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;

public class DeathLogScreen extends WindowScreen {
   public DeathLogScreen(GuiTheme theme) {
      super(theme, "Death Log");
   }

   public void initWidgets() {
      this.add(this.theme.label("Showing the last 10 deaths. Full history is saved to sixtoolsaddon-deaths.txt")).expandX();
      this.add(this.theme.horizontalSeparator()).expandX();
      List<DeathRecord> entries = DeathLogStore.lastEntries(10);
      WTable table = (WTable)this.add(this.theme.table()).expandX().minWidth((double)400.0F).widget();
      if (entries.isEmpty()) {
         table.add(this.theme.label("No deaths logged yet")).expandX().pad((double)10.0F);
      } else {
         for(DeathRecord entry : entries) {
            table.add(this.theme.label(entry.timestamp));
            table.add(this.theme.label(entry.coordsString()));
            table.add(this.theme.label(entry.dimension)).expandCellX().right();
            table.row();
         }
      }

   }
}
