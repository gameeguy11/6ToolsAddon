package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.util.MediaWatcher;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class NowPlayingHud extends HudElement {
   public static final HudElementInfo INFO;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgScale;
   private final Setting<Boolean> showArtist;
   private final Setting<Boolean> hideWhenPaused;
   private final Setting<Integer> maxLength;
   private final Setting<Boolean> shadow;
   private final Setting<SettingColor> color;
   private final Setting<Boolean> colorUseTheme;
   private final Setting<Boolean> customScale;
   private final Setting<Double> scale;

   public NowPlayingHud() {
      super(INFO);
      this.sgGeneral = this.settings.getDefaultGroup();
      this.showArtist = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-artist")).description("Shows the artist before the track title.")).defaultValue(true)).build());
      this.hideWhenPaused = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("hide-when-paused")).description("Hides the element when nothing is playing.")).defaultValue(true)).build());
      this.maxLength = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("max-length")).description("Longest text to show before it is cut off with '...'.")).defaultValue(50)).min(10).sliderRange(10, 100).build());
      this.shadow = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("shadow")).description("Renders a shadow behind the text.")).defaultValue(true)).build());
      this.color = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("color")).description("Color of the text.")).defaultValue(new SettingColor(SixToolsAddon.THEME_COLOR.r, SixToolsAddon.THEME_COLOR.g, SixToolsAddon.THEME_COLOR.b)).build());
      this.colorUseTheme = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("color-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.sgScale = this.settings.createGroup("Scale");
      this.customScale = this.sgScale.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("custom-scale")).description("Applies a custom scale to this HUD element instead of the global HUD text scale.")).defaultValue(false)).build());
      this.scale = this.sgScale.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("scale")).description("Custom scale.")).defaultValue(1.0).min(0.5).sliderRange(0.5, 3.0)).visible(this.customScale::get)).build());
   }

   private String text() {
      MediaWatcher media = MediaWatcher.INSTANCE;
      if (!media.isSupported()) {
         return this.isInEditor() ? "Now playing: " + MediaWatcher.platformHint() : null;
      }

      if (media.isToolMissing()) {
         return this.isInEditor() ? "Now playing: " + MediaWatcher.platformHint() : null;
      }

      if (!media.hasTrack() || (this.hideWhenPaused.get() && !media.isPlaying())) {
         return this.isInEditor() ? "Artist - Song Title" : null;
      }

      String text = this.showArtist.get() && !media.getArtist().isEmpty()
         ? media.getArtist() + " - " + media.getTitle()
         : media.getTitle();

      int max = this.maxLength.get();
      return text.length() > max ? text.substring(0, max - 3) + "..." : text;
   }

   public void tick(HudRenderer renderer) {
      MediaWatcher.INSTANCE.request();
      String text = this.text();
      boolean shadowOn = this.shadow.get();
      if (text == null) {
         this.setSize(0.0, 0.0);
      } else {
         this.setSize(renderer.textWidth(text, shadowOn, this.getScale()), renderer.textHeight(shadowOn, this.getScale()));
      }
   }

   public void render(HudRenderer renderer) {
      String text = this.text();
      if (text != null) {
         renderer.text(text, (double)this.x, (double)this.y, ThemeColorUtils.resolve(this.color.get(), this.colorUseTheme.get()), this.shadow.get(), this.getScale());
      }
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : Hud.get().getTextScale();
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "now-playing", "Shows the song currently playing on your PC (Windows and Linux).", NowPlayingHud::new);
   }
}
