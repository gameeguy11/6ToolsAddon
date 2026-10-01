package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.commands.DubCounterCommand;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class DubCounterHud extends HudElement {
   public static final HudElementInfo INFO;
   private final SettingGroup sgGeneral;
   private final Setting<Boolean> showMode;
   private final Setting<Boolean> shadow;
   private final Setting<SettingColor> color;
   private final Setting<Boolean> colorUseTheme;
   private static final SettingColor WHITE;

   public DubCounterHud() {
      super(INFO);
      this.sgGeneral = this.settings.getDefaultGroup();
      this.showMode = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-mode")).description("Shows whether the count was Loaded or Rendered.")).defaultValue(true)).build());
      this.shadow = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("shadow")).description("Renders a shadow behind the text.")).defaultValue(true)).build());
      this.color = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("color")).description("Color used for the label text (the count itself stays white).")).defaultValue(new SettingColor(SixToolsAddon.THEME_COLOR.r, SixToolsAddon.THEME_COLOR.g, SixToolsAddon.THEME_COLOR.b)).build());
      this.colorUseTheme = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("color-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
   }

   private String label() {
      return "Dubs: ";
   }

   private String number() {
      return DubCounterCommand.lastDubs < 0 ? "-" : String.valueOf(DubCounterCommand.lastDubs);
   }

   private String suffix() {
      return (Boolean)this.showMode.get() && DubCounterCommand.lastMode != null ? " (" + DubCounterCommand.lastMode.name() + ")" : "";
   }

   public void tick(HudRenderer renderer) {
      double width = renderer.textWidth(this.label(), (Boolean)this.shadow.get(), this.getScale()) + renderer.textWidth(this.number(), (Boolean)this.shadow.get(), this.getScale()) + renderer.textWidth(this.suffix(), (Boolean)this.shadow.get(), this.getScale());
      this.setSize(width, renderer.textHeight((Boolean)this.shadow.get(), this.getScale()));
   }

   public void render(HudRenderer renderer) {
      double drawX = (double)this.x;
      drawX = renderer.text(this.label(), drawX, (double)this.y, ThemeColorUtils.resolve((SettingColor)this.color.get(), (Boolean)this.colorUseTheme.get()), (Boolean)this.shadow.get(), this.getScale());
      drawX = renderer.text(this.number(), drawX, (double)this.y, WHITE, (Boolean)this.shadow.get(), this.getScale());
      renderer.text(this.suffix(), drawX, (double)this.y, ThemeColorUtils.resolve((SettingColor)this.color.get(), (Boolean)this.colorUseTheme.get()), (Boolean)this.shadow.get(), this.getScale());
   }

   private double getScale() {
      return Hud.get().getTextScale();
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "dub-counter", "Shows the last result from the .dub command.", DubCounterHud::new);
      WHITE = new SettingColor(255, 255, 255);
   }
}
