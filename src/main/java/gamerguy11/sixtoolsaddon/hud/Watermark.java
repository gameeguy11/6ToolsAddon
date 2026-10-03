package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import gamerguy11.sixtoolsaddon.utils.WatermarkImage;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.fabricmc.loader.api.FabricLoader;

public class Watermark extends HudElement {
   public static final HudElementInfo INFO;
   private static final String ADDON_ID = "sixtoolsaddon";
   private final SettingGroup sgGeneral;
   private final SettingGroup sgImage;
   private final SettingGroup sgScale;
   private final Setting<Display> display;
   private final Setting<String> text;
   private final Setting<SettingColor> color;
   private final Setting<Boolean> colorUseTheme;
   private final Setting<Boolean> shadow;
   private final Setting<Boolean> defaultIcon;
   private final Setting<String> image;
   private final Setting<Double> imageHeight;
   private final Setting<Double> gap;
   private final Setting<Boolean> tintImage;
   private final Setting<Boolean> customScale;
   private final Setting<Double> scale;
   private final WatermarkImage watermarkImage;
   private final String version;

   public Watermark() {
      super(INFO);
      this.watermarkImage = new WatermarkImage();
      this.version = FabricLoader.getInstance()
         .getModContainer(ADDON_ID)
         .map(container -> container.getMetadata().getVersion().getFriendlyString())
         .orElse("unknown");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.display = this.sgGeneral.add(new EnumSetting.Builder<Display>()
         .name("display")
         .description("Show only the text, only the image, or the image in front of the text.")
         .defaultValue(Display.ImageAndText)
         .build());
      this.text = this.sgGeneral.add(new StringSetting.Builder()
         .name("text")
         .description("The text to show. {version} is your 6Tools version and {name} is 6Tools.")
         .defaultValue("{name} {version}")
         .build());
      this.color = this.sgGeneral.add(new ColorSetting.Builder()
         .name("color")
         .description("Color of the text.")
         .defaultValue(new SettingColor(SixToolsAddon.THEME_COLOR.r, SixToolsAddon.THEME_COLOR.g, SixToolsAddon.THEME_COLOR.b))
         .build());
      this.colorUseTheme = this.sgGeneral.add(new BoolSetting.Builder()
         .name("color-use-theme")
         .description("Use the current Meteor theme accent color.")
         .defaultValue(false)
         .build());
      this.shadow = this.sgGeneral.add(new BoolSetting.Builder()
         .name("shadow")
         .description("Renders a shadow behind the text.")
         .defaultValue(true)
         .build());
      this.sgImage = this.settings.createGroup("Image");
      this.defaultIcon = this.sgImage.add(new BoolSetting.Builder()
         .name("default-icon")
         .description("Use the 6Tools icon when no image file is set below. Turn off to show no image until you set one.")
         .defaultValue(true)
         .build());
      this.image = this.sgImage.add(new StringSetting.Builder()
         .name("image")
         .description("File name of an image in .minecraft/config/sixtoolsaddon/watermark/, or a full path. Leave empty to use the 6Tools icon.")
         .defaultValue("")
         .build());
      this.imageHeight = this.sgImage.add(new DoubleSetting.Builder()
         .name("image-height")
         .description("Height of the image in pixels. The width follows the image's proportions.")
         .defaultValue(16.0)
         .min(4.0)
         .sliderRange(4.0, 128.0)
         .build());
      this.gap = this.sgImage.add(new DoubleSetting.Builder()
         .name("gap")
         .description("Space between the image and the text.")
         .defaultValue(4.0)
         .min(0.0)
         .sliderRange(0.0, 20.0)
         .visible(() -> this.display.get() == Display.ImageAndText)
         .build());
      this.tintImage = this.sgImage.add(new BoolSetting.Builder()
         .name("tint-image")
         .description("Multiplies the image by the text color.")
         .defaultValue(false)
         .build());
      this.sgScale = this.settings.createGroup("Scale");
      this.customScale = this.sgScale.add(new BoolSetting.Builder()
         .name("custom-scale")
         .description("Applies a custom scale to this HUD element instead of the global HUD text scale.")
         .defaultValue(false)
         .build());
      this.scale = this.sgScale.add(new DoubleSetting.Builder()
         .name("scale")
         .description("Custom scale.")
         .defaultValue(1.0)
         .min(0.5)
         .sliderRange(0.5, 3.0)
         .visible(this.customScale::get)
         .build());
   }

   public void tick(HudRenderer renderer) {
      Layout layout = this.layout(renderer);
      this.setSize(layout.width, layout.height);
   }

   public void render(HudRenderer renderer) {
      Layout layout = this.layout(renderer);
      if (layout.width <= 0.0 || layout.height <= 0.0) {
         return;
      }

      SettingColor textColor = ThemeColorUtils.resolve(this.color.get(), this.colorUseTheme.get());
      double cursor = this.x;
      if (layout.showImage) {
         Color tint = this.tintImage.get() ? textColor : Color.WHITE;
         double imageY = this.y + (layout.height - layout.imageHeight) / 2.0;
         renderer.texture(this.watermarkImage.id(), cursor, imageY, layout.imageWidth, layout.imageHeight, tint);
         cursor += layout.imageWidth;
         if (layout.text != null) {
            cursor += layout.gapWidth;
         }
      }

      if (layout.text != null) {
         double textY = this.y + (layout.height - layout.textHeight) / 2.0;
         renderer.text(layout.text, cursor, textY, textColor, this.shadow.get(), this.getScale());
      }
   }

   private Layout layout(HudRenderer renderer) {
      Layout layout = new Layout();
      Display mode = this.display.get();
      double scaleValue = this.getScale();
      boolean shadowOn = this.shadow.get();

      String imageSetting = this.image.get();
      boolean wantsImage = mode != Display.Text && (!imageSetting.isBlank() || this.defaultIcon.get());
      if (wantsImage && this.watermarkImage.update(imageSetting)) {
         layout.showImage = true;
         layout.imageHeight = this.imageHeight.get() * scaleValue;
         layout.imageWidth = layout.imageHeight * this.watermarkImage.width() / Math.max(1, this.watermarkImage.height());
      }

      if (mode != Display.Image) {
         layout.text = this.resolveText();
      } else if (!layout.showImage && this.isInEditor()) {
         layout.text = "No image";
      }

      if (layout.text != null && layout.text.isEmpty()) {
         layout.text = null;
      }

      if (layout.text != null) {
         layout.textWidth = renderer.textWidth(layout.text, shadowOn, scaleValue);
         layout.textHeight = renderer.textHeight(shadowOn, scaleValue);
      }

      layout.gapWidth = layout.showImage && layout.text != null ? this.gap.get() * scaleValue : 0.0;
      layout.width = (layout.showImage ? layout.imageWidth : 0.0) + layout.gapWidth + layout.textWidth;
      layout.height = Math.max(layout.showImage ? layout.imageHeight : 0.0, layout.text != null ? layout.textHeight : 0.0);
      return layout;
   }

   private String resolveText() {
      return this.text.get().replace("{version}", this.version).replace("{name}", "6Tools");
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : Hud.get().getTextScale();
   }

   public enum Display {
      Text("Text"),
      Image("Image"),
      ImageAndText("Image and Text");

      private final String label;

      Display(String label) {
         this.label = label;
      }

      public String toString() {
         return this.label;
      }
   }

   private static final class Layout {
      private boolean showImage;
      private String text;
      private double imageWidth;
      private double imageHeight;
      private double textWidth;
      private double textHeight;
      private double gapWidth;
      private double width;
      private double height;
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "6tools-watermark", "Watermark", "Shows your 6Tools version as text, an image, or both. Color and image are customizable.", Watermark::new);
   }
}
