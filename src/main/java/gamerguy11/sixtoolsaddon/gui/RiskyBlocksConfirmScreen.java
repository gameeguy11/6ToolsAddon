package gamerguy11.sixtoolsaddon.gui;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.gui.screen.Screen;

public class RiskyBlocksConfirmScreen extends WindowScreen {
   private final Runnable onConfirm;
   private final Runnable onCancel;
   private final Screen previousScreen;

   public RiskyBlocksConfirmScreen(GuiTheme theme, Screen previousScreen, Runnable onConfirm, Runnable onCancel) {
      super(theme, "Are you sure?");
      this.previousScreen = previousScreen;
      this.onConfirm = onConfirm;
      this.onCancel = onCancel;
   }

   public void initWidgets() {
      this.add(this.theme.label("You're about to allow ESP/tracers on ANY block, not just storage and beds.")).expandX();
      this.add(this.theme.label("Common blocks (dirt, stone, etc.) can appear by the thousands, which can")).expandX();
      this.add(this.theme.label("hurt your FPS and makes it obvious something isn't normal vanilla behavior.")).expandX();
      this.add(this.theme.label("Only continue if you know exactly which blocks you want to add.")).expandX();
      this.add(this.theme.horizontalSeparator()).expandX();
      WHorizontalList buttons = (WHorizontalList)this.add(this.theme.horizontalList()).expandX().widget();
      WButton confirm = (WButton)buttons.add(this.theme.button("I understand, continue")).expandX().widget();
      confirm.action = () -> {
         this.onConfirm.run();
         MeteorClient.mc.setScreen(this.previousScreen);
      };
      WButton cancel = (WButton)buttons.add(this.theme.button("Cancel")).expandX().widget();
      cancel.action = () -> {
         this.onCancel.run();
         MeteorClient.mc.setScreen(this.previousScreen);
      };
   }
}
