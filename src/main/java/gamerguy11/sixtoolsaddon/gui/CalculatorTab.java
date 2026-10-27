package gamerguy11.sixtoolsaddon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.gui.screen.Screen;

/** Calculator + Nether/Overworld coordinate converter (ported from the RusherHack calculator plugin). */
public class CalculatorTab extends Tab {
   public CalculatorTab() {
      super("Calculator");
   }

   public TabScreen createScreen(GuiTheme theme) {
      return new CalculatorScreen(theme, this);
   }

   public boolean isScreen(Screen screen) {
      return screen instanceof CalculatorScreen;
   }

   private static class CalculatorScreen extends WindowTabScreen {
      private static final String[][] KEYS = {
         {"7", "8", "9", "/"},
         {"4", "5", "6", "*"},
         {"1", "2", "3", "-"},
         {"0", ".", "^", "+"},
         {"(", ")", "%", "C"}
      };
      private static String exprDraft = "";
      private static String resultDraft = "= 0";

      private WTextBox expr;
      private WLabel result;

      CalculatorScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
      }

      public void initWidgets() {
         this.add(this.theme.label("Calculator")).expandX();
         this.expr = this.add(this.theme.textBox(exprDraft, (text, c) -> Character.isDigit(c) || "+-*/%^().,".indexOf(c) >= 0))
            .expandX().minWidth(260.0).widget();
         this.expr.action = () -> exprDraft = this.expr.get();
         this.expr.setFocused(true);
         this.result = this.add(this.theme.label(resultDraft)).expandX().widget();

         WTable pad = this.add(this.theme.table()).expandX().widget();
         for (String[] row : KEYS) {
            for (String key : row) {
               WButton b = pad.add(this.theme.button(key)).expandX().widget();
               b.action = () -> {
                  if (key.equals("C")) {
                     this.setExpr("");
                     this.setResult("= 0");
                  } else {
                     this.setExpr(this.expr.get() + key);
                  }
               };
            }
            pad.row();
         }
         WButton del = pad.add(this.theme.button("Del")).expandX().widget();
         del.action = () -> {
            String v = this.expr.get();
            if (!v.isEmpty()) this.setExpr(v.substring(0, v.length() - 1));
         };
         WButton eq = pad.add(this.theme.button("=")).expandX().widget();
         eq.action = this::evaluate;
         this.enterAction = this::evaluate;

         this.add(this.theme.horizontalSeparator()).expandX();
         this.initNether();
      }

      private void initNether() {
         this.add(this.theme.label("Nether / Overworld")).expandX();
         WTextBox x = this.add(this.theme.textBox("", CalculatorScreen::numberFilter)).expandX().widget();
         WTextBox y = this.add(this.theme.textBox("", CalculatorScreen::numberFilter)).expandX().widget();
         WTextBox z = this.add(this.theme.textBox("", CalculatorScreen::numberFilter)).expandX().widget();
         this.add(this.theme.label("Fields: X, Y (optional), Z"));
         WLabel out = this.add(this.theme.label("")).expandX().widget();
         WHorizontalList buttons = this.add(this.theme.horizontalList()).expandX().widget();
         WButton toNether = buttons.add(this.theme.button("OW > Nether")).expandX().widget();
         toNether.action = () -> convert(x, y, z, out, 1.0 / 8.0);
         WButton toOw = buttons.add(this.theme.button("Nether > OW")).expandX().widget();
         toOw.action = () -> convert(x, y, z, out, 8.0);
      }

      private static boolean numberFilter(String text, char c) {
         return Character.isDigit(c) || c == '-' || c == '.';
      }

      private void setExpr(String v) {
         exprDraft = v;
         this.expr.set(v);
      }

      private void setResult(String v) {
         resultDraft = v;
         this.result.set(v);
      }

      private void evaluate() {
         try {
            this.setResult("= " + format(Expr.eval(this.expr.get())));
         } catch (RuntimeException e) {
            this.setResult("= error");
         }
      }

      private static void convert(WTextBox x, WTextBox y, WTextBox z, WLabel out, double factor) {
         try {
            double xv = Double.parseDouble(x.get().trim());
            double zv = Double.parseDouble(z.get().trim());
            String yRaw = y.get().trim();
            String yOut = yRaw.isEmpty() ? "" : " " + format(Double.parseDouble(yRaw));
            out.set((long) Math.floor(xv * factor) + yOut + " " + (long) Math.floor(zv * factor));
         } catch (NumberFormatException e) {
            out.set("invalid input");
         }
      }

      private static String format(double v) {
         if (Double.isNaN(v) || Double.isInfinite(v)) return "error";
         if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
         return Double.toString(Math.round(v * 1e10) / 1e10);
      }
   }
}
