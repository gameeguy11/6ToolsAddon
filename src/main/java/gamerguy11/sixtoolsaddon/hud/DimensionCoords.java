package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;

public class DimensionCoords extends HudElement {
   public static final HudElementInfo INFO;
   private final SettingGroup sgGeneral;
   private final Setting<Boolean> showTitle;
   private final Setting<Boolean> showCurrentDim;
   private final Setting<Double> textScale;
   private final Setting<Boolean> textShadow;
   private final Setting<SettingColor> titleColor;
   private final Setting<SettingColor> overworldColor;
   private final Setting<SettingColor> netherColor;
   private final Setting<SettingColor> endColor;
   private final Setting<Boolean> showLabels;
   private final Setting<Boolean> removeCommas;
   private final Setting<Boolean> horizontalLayout;

   public DimensionCoords() {
      super(INFO);
      this.sgGeneral = this.settings.getDefaultGroup();
      this.showTitle = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-title")).description("Display the HUD title.")).defaultValue(false)).build());
      this.showCurrentDim = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-current-dimension")).description("Show current dimension name.")).defaultValue(false)).build());
      this.textScale = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("text-scale")).description("Scale of the text.")).defaultValue((double)1.0F).min(0.1).sliderRange(0.1, (double)3.0F).build());
      this.textShadow = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("text-shadow")).description("Render shadow behind the text.")).defaultValue(true)).build());
      this.titleColor = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("title-color")).description("Color for the title text.")).defaultValue(new SettingColor(255, 255, 255, 255)).build());
      this.overworldColor = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("overworld-color")).description("Color for overworld coordinates.")).defaultValue(new SettingColor(0, 255, 0, 255)).build());
      this.netherColor = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("nether-color")).description("Color for nether coordinates.")).defaultValue(new SettingColor(255, 0, 0, 255)).build());
      this.endColor = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("end-color")).description("Color for end coordinates.")).defaultValue(new SettingColor(255, 0, 255, 255)).build());
      this.showLabels = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-labels")).description("Show dimension labels (e.g. 'Overworld:', 'Nether:').")).defaultValue(true)).build());
      this.removeCommas = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("remove-commas")).description("Remove commas from coordinates.")).defaultValue(false)).build());
      this.horizontalLayout = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("horizontal-layout")).description("Display coordinates horizontally next to each other.")).defaultValue(false)).build());
   }

   public void render(HudRenderer renderer) {
      if (MeteorClient.mc.world != null && MeteorClient.mc.player != null) {
         BlockPos playerPos = MeteorClient.mc.player.getBlockPos();
         Identifier dimensionId = MeteorClient.mc.world.getRegistryKey().getValue();
         double curX = (double)this.x;
         double curY = (double)this.y;
         double maxWidth = (double)0.0F;
         double height = (double)0.0F;
         double textHeight = renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         double spacing = (double)2.0F;
         if ((Boolean)this.showTitle.get()) {
            String title = "Dimension Coords";
            double titleWidth = renderer.textWidth(title, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(title, curX, curY, (Color)this.titleColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
         }

         if ((Boolean)this.showCurrentDim.get()) {
            String dimName = this.getDimensionName(dimensionId);
            String dimText = "Current: " + dimName;
            double dimWidth = renderer.textWidth(dimText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(dimText, curX, curY, (Color)this.titleColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, dimWidth);
         }

         String coordFormat = (Boolean)this.removeCommas.get() ? "%d %d %d" : "%d, %d, %d";
         if (this.isOverworld(dimensionId)) {
            String overworldLabel = (Boolean)this.showLabels.get() ? "Overworld: " : "";
            String netherLabel = (Boolean)this.showLabels.get() ? "Nether: " : "";
            String overworldText = overworldLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            String netherText = netherLabel + String.format(coordFormat, playerPos.getX() / 8, playerPos.getY(), playerPos.getZ() / 8);
            if ((Boolean)this.horizontalLayout.get()) {
               double overworldWidth = renderer.textWidth(overworldText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               renderer.text(overworldText, curX, curY, (Color)this.overworldColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curX += overworldWidth + spacing * (double)3.0F;
               renderer.text(netherText, curX, curY, (Color)this.netherColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double netherWidth = renderer.textWidth(netherText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               maxWidth = Math.max(maxWidth, overworldWidth + netherWidth + spacing * (double)3.0F);
               height += textHeight;
            } else {
               double overworldWidth = renderer.textWidth(overworldText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double netherWidth = renderer.textWidth(netherText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               renderer.text(overworldText, curX, curY, (Color)this.overworldColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curY += textHeight + spacing;
               height += textHeight + spacing;
               maxWidth = Math.max(maxWidth, overworldWidth);
               renderer.text(netherText, curX, curY, (Color)this.netherColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double var10000 = curY + textHeight + spacing;
               height += textHeight + spacing;
               maxWidth = Math.max(maxWidth, netherWidth);
            }
         } else if (this.isNether(dimensionId)) {
            String netherLabel = (Boolean)this.showLabels.get() ? "Nether: " : "";
            String overworldLabel = (Boolean)this.showLabels.get() ? "Overworld: " : "";
            String netherText = netherLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            String overworldText = overworldLabel + String.format(coordFormat, playerPos.getX() * 8, playerPos.getY(), playerPos.getZ() * 8);
            if ((Boolean)this.horizontalLayout.get()) {
               double netherWidth = renderer.textWidth(netherText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               renderer.text(netherText, curX, curY, (Color)this.netherColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curX += netherWidth + spacing * (double)3.0F;
               renderer.text(overworldText, curX, curY, (Color)this.overworldColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double overworldWidth = renderer.textWidth(overworldText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               maxWidth = Math.max(maxWidth, netherWidth + overworldWidth + spacing * (double)3.0F);
               height += textHeight;
            } else {
               double netherWidth = renderer.textWidth(netherText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double overworldWidth = renderer.textWidth(overworldText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               renderer.text(netherText, curX, curY, (Color)this.netherColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curY += textHeight + spacing;
               height += textHeight + spacing;
               maxWidth = Math.max(maxWidth, netherWidth);
               renderer.text(overworldText, curX, curY, (Color)this.overworldColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double var51 = curY + textHeight + spacing;
               height += textHeight + spacing;
               maxWidth = Math.max(maxWidth, overworldWidth);
            }
         } else if (this.isEnd(dimensionId)) {
            String endLabel = (Boolean)this.showLabels.get() ? "The End: " : "";
            String endText = endLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            double endWidth = renderer.textWidth(endText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(endText, curX, curY, (Color)this.endColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            double var52 = curY + textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, endWidth);
         }

         this.setSize(maxWidth, height > (double)0.0F ? height - spacing : (double)0.0F);
      } else {
         if (this.isInEditor()) {
            renderer.text("Dimension Coords", (double)this.x, (double)this.y, (Color)this.titleColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            this.setSize(renderer.textWidth("Dimension Coords", (Boolean)this.textShadow.get(), (Double)this.textScale.get()), renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()));
         }

      }
   }

   private boolean isOverworld(Identifier dimensionId) {
      return dimensionId.equals(Identifier.of("minecraft:overworld"));
   }

   private boolean isNether(Identifier dimensionId) {
      return dimensionId.equals(Identifier.of("minecraft:the_nether"));
   }

   private boolean isEnd(Identifier dimensionId) {
      return dimensionId.equals(Identifier.of("minecraft:the_end"));
   }

   private String getDimensionName(Identifier dimensionId) {
      if (this.isOverworld(dimensionId)) {
         return "Overworld";
      } else if (this.isNether(dimensionId)) {
         return "Nether";
      } else {
         return this.isEnd(dimensionId) ? "The End" : dimensionId.getPath();
      }
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "DimensionCoords", "Displays coordinates for both overworld and nether dimensions.", DimensionCoords::new);
   }
}
