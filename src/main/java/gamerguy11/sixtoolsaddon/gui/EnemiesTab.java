package gamerguy11.sixtoolsaddon.gui;

import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemy;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.screen.Screen;

public class EnemiesTab extends Tab {
   public EnemiesTab() {
      super("Enemies");
   }

   public TabScreen createScreen(GuiTheme theme) {
      return new EnemiesScreen(theme, this);
   }

   public boolean isScreen(Screen screen) {
      return screen instanceof EnemiesScreen;
   }

   private static class EnemiesScreen extends WindowTabScreen {
      public EnemiesScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
      }

      public void initWidgets() {
         WTable table = (WTable)this.add(this.theme.table()).expandX().minWidth((double)400.0F).widget();
         this.initTable(table);
         this.add(this.theme.horizontalSeparator()).expandX();
         WHorizontalList list = (WHorizontalList)this.add(this.theme.horizontalList()).expandX().widget();
         WTextBox nameW = (WTextBox)list.add(this.theme.textBox("", (c1, c2) -> c2 != ' ')).expandX().widget();
         nameW.setFocused(true);
         WPlus addButton = (WPlus)list.add(new WRedPlus()).widget();
         addButton.action = () -> {
            String name = nameW.get().trim();
            if (!name.isEmpty()) {
               if (Enemies.get().add(new Enemy(name))) {
                  nameW.set("");
                  this.initTable(table);
                  nameW.setFocused(true);
               }

            }
         };
         this.enterAction = addButton.action;
      }

      private void initTable(WTable table) {
         table.clear();
         if (!Enemies.get().isEmpty()) {
            for(Enemy enemy : Enemies.get()) {
               table.add(this.theme.label(enemy.getName()));
               WMinus remove = (WMinus)table.add(this.theme.minus()).expandCellX().right().widget();
               remove.action = () -> {
                  Enemies.get().remove(enemy);
                  this.initTable(table);
               };
               table.row();
            }

         }
      }
   }

   private static class WRedPlus extends WPlus implements MeteorWidget {
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         MeteorGuiTheme theme = this.theme();
         double pad = this.pad();
         double s = theme.scale((double)3.0F);
         this.renderBackground(renderer, this, this.pressed, this.mouseOver);
         renderer.quad(this.x + pad, this.y + this.height / (double)2.0F - s / (double)2.0F, this.width - pad * (double)2.0F, s, (Color)theme.minusColor.get());
         renderer.quad(this.x + this.width / (double)2.0F - s / (double)2.0F, this.y + pad, s, this.height - pad * (double)2.0F, (Color)theme.minusColor.get());
      }
   }
}
