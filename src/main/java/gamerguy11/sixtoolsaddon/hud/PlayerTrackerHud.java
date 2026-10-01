package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.Alignment;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class PlayerTrackerHud extends HudElement {
   public static final HudElementInfo INFO;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgColors;
   private final SettingGroup sgGrid;
   private final SettingGroup sgScale;
   private final SettingGroup sgBackground;
   private final Setting<Integer> limit;
   private final Setting<Boolean> showDistance;
   private final Setting<Boolean> shadow;
   private final Setting<Alignment> alignment;
   private final Setting<Integer> border;
   private final Setting<SettingColor> friendColor;
   private final Setting<Boolean> friendColorUseTheme;
   private final Setting<SettingColor> enemyColor;
   private final Setting<Boolean> enemyColorUseTheme;
   private final Setting<SettingColor> otherColor;
   private final Setting<Boolean> otherColorUseTheme;
   private final Setting<SettingColor> distanceColor;
   private final Setting<Boolean> distanceColorUseTheme;
   private final Setting<List<String>> enemyNames;
   private final Setting<Boolean> snapToGrid;
   private final Setting<Integer> gridSize;
   private final Setting<Boolean> customScale;
   private final Setting<Double> scale;
   private final Setting<Boolean> background;
   private final Setting<SettingColor> backgroundColor;
   private final Setting<Boolean> backgroundColorUseTheme;
   private final List<AbstractClientPlayerEntity> players;

   public PlayerTrackerHud() {
      super(INFO);
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgColors = this.settings.createGroup("Colors");
      this.sgGrid = this.settings.createGroup("Grid Snapping");
      this.sgScale = this.settings.createGroup("Scale");
      this.sgBackground = this.settings.createGroup("Background");
      this.limit = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("limit")).description("Max number of players to list.")).defaultValue(20)).min(1).sliderRange(1, 50).build());
      this.showDistance = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("show-distance")).description("Shows how far away each player is, in meters (blocks).")).defaultValue(true)).build());
      this.shadow = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("shadow")).description("Renders a shadow behind the text.")).defaultValue(true)).build());
      this.alignment = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("alignment")).description("Horizontal text alignment.")).defaultValue(Alignment.Left)).build());
      this.border = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("border")).description("Padding around the element.")).defaultValue(2)).sliderRange(0, 10).build());
      this.friendColor = this.sgColors.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("friend-color")).description("Color for players on your Meteor friends list.")).defaultValue(new SettingColor(75, 225, 75)).build());
      this.friendColorUseTheme = this.sgColors.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("friend-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.enemyColor = this.sgColors.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("enemy-color")).description("Color for players on the enemy-names list below.")).defaultValue(new SettingColor(225, 75, 75)).build());
      this.enemyColorUseTheme = this.sgColors.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("enemy-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.otherColor = this.sgColors.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("other-color")).description("Color for everyone else.")).defaultValue(new SettingColor(225, 225, 225)).build());
      this.otherColorUseTheme = this.sgColors.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("other-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.distanceColor = this.sgColors.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("distance-color")).description("Color used for the distance text.")).defaultValue(new SettingColor(175, 175, 175)).build());
      this.distanceColorUseTheme = this.sgColors.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("distance-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.enemyNames = this.sgColors.add(((StringListSetting.Builder)((StringListSetting.Builder)(new StringListSetting.Builder()).name("enemy-names")).description("Player names to mark with the enemy color. Not case sensitive. Meteor Client has no built-in enemy list, so this addon keeps its own.")).build());
      this.snapToGrid = this.sgGrid.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("snap-to-grid")).description("While dragging this element in the HUD editor, snaps it to a pixel grid instead of free placement.")).defaultValue(false)).build());
      SettingGroup var10001 = this.sgGrid;
      IntSetting.Builder var10002 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("grid-size")).description("Size, in pixels, of one grid cell.")).defaultValue(10)).min(1).sliderRange(1, 50);
      Setting<Boolean> var10003 = this.snapToGrid;
      Objects.requireNonNull(var10003);
      this.gridSize = var10001.add(((IntSetting.Builder)var10002.visible(var10003::get)).build());
      this.customScale = this.sgScale.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("custom-scale")).description("Applies a custom scale to this HUD element instead of the global HUD text scale.")).defaultValue(false)).build());
      var10001 = this.sgScale;
      DoubleSetting.Builder var3 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("scale")).description("Custom scale.")).defaultValue((double)1.0F).min((double)0.5F).sliderRange((double)0.5F, (double)3.0F);
      var10003 = this.customScale;
      Objects.requireNonNull(var10003);
      this.scale = var10001.add(((DoubleSetting.Builder)var3.visible(var10003::get)).build());
      this.background = this.sgBackground.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("background")).description("Displays a background behind the list.")).defaultValue(true)).build());
      var10001 = this.sgBackground;
      ColorSetting.Builder var4 = ((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("background-color")).description("Color used for the background.")).defaultValue(new SettingColor(25, 25, 25, 100));
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundColor = var10001.add(((ColorSetting.Builder)var4.visible(var10003::get)).build());
      this.backgroundColorUseTheme = this.sgBackground.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("background-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.players = new ArrayList();
   }

   public void setSize(double width, double height) {
      super.setSize(width + (double)((Integer)this.border.get() * 2), height + (double)((Integer)this.border.get() * 2));
   }

   protected double alignX(double width, Alignment alignment) {
      return this.box.alignX((double)(this.getWidth() - (Integer)this.border.get() * 2), width, alignment);
   }

   public void move(int deltaX, int deltaY) {
      super.move(deltaX, deltaY);
      if ((Boolean)this.snapToGrid.get()) {
         int size = Math.max(1, (Integer)this.gridSize.get());
         this.box.x = Math.round((float)this.box.x / (float)size) * size;
         this.box.y = Math.round((float)this.box.y / (float)size) * size;
         this.updatePos();
      }

   }

   public void tick(HudRenderer renderer) {
      double width = renderer.textWidth("Players:", (Boolean)this.shadow.get(), this.getScale());
      double height = renderer.textHeight((Boolean)this.shadow.get(), this.getScale());
      if (MeteorClient.mc.world != null && MeteorClient.mc.getCameraEntity() != null) {
         for(AbstractClientPlayerEntity player : this.getPlayers()) {
            String text = player.getName().getString();
            if ((Boolean)this.showDistance.get()) {
               text = text + String.format(" (%sm)", Math.round(MeteorClient.mc.getCameraEntity().distanceTo(player)));
            }

            width = Math.max(width, renderer.textWidth(text, (Boolean)this.shadow.get(), this.getScale()));
            height += renderer.textHeight((Boolean)this.shadow.get(), this.getScale()) + (double)2.0F;
         }

         this.setSize(width, height);
      } else {
         this.setSize(width, height);
      }
   }

   public void render(HudRenderer renderer) {
      double y = (double)(this.y + (Integer)this.border.get());
      if ((Boolean)this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), ThemeColorUtils.resolve((SettingColor)this.backgroundColor.get(), (Boolean)this.backgroundColorUseTheme.get()));
      }

      renderer.text("Players:", (double)(this.x + (Integer)this.border.get()) + this.alignX(renderer.textWidth("Players:", (Boolean)this.shadow.get(), this.getScale()), (Alignment)this.alignment.get()), y, ThemeColorUtils.resolve((SettingColor)this.otherColor.get(), (Boolean)this.otherColorUseTheme.get()), (Boolean)this.shadow.get(), this.getScale());
      if (MeteorClient.mc.world != null && MeteorClient.mc.getCameraEntity() != null) {
         double spaceWidth = renderer.textWidth(" ", (Boolean)this.shadow.get(), this.getScale());

         for(AbstractClientPlayerEntity player : this.getPlayers()) {
            String name = player.getName().getString();
            Color color = this.colorFor(player);
            double width = renderer.textWidth(name, (Boolean)this.shadow.get(), this.getScale());
            String distanceText = null;
            if ((Boolean)this.showDistance.get()) {
               distanceText = String.format("(%sm)", Math.round(MeteorClient.mc.getCameraEntity().distanceTo(player)));
               width += spaceWidth + renderer.textWidth(distanceText, (Boolean)this.shadow.get(), this.getScale());
            }

            double x = (double)(this.x + (Integer)this.border.get()) + this.alignX(width, (Alignment)this.alignment.get());
            y += renderer.textHeight((Boolean)this.shadow.get(), this.getScale()) + (double)2.0F;
            x = renderer.text(name, x, y, color, (Boolean)this.shadow.get(), this.getScale());
            if ((Boolean)this.showDistance.get()) {
               renderer.text(distanceText, x + spaceWidth, y, ThemeColorUtils.resolve((SettingColor)this.distanceColor.get(), (Boolean)this.distanceColorUseTheme.get()), (Boolean)this.shadow.get(), this.getScale());
            }
         }

      }
   }

   private Color colorFor(AbstractClientPlayerEntity player) {
      if (Friends.get().isFriend(player)) {
         return ThemeColorUtils.resolve((SettingColor)this.friendColor.get(), (Boolean)this.friendColorUseTheme.get());
      } else {
         return this.isEnemy(player) ? ThemeColorUtils.resolve((SettingColor)this.enemyColor.get(), (Boolean)this.enemyColorUseTheme.get()) : ThemeColorUtils.resolve((SettingColor)this.otherColor.get(), (Boolean)this.otherColorUseTheme.get());
      }
   }

   private boolean isEnemy(AbstractClientPlayerEntity player) {
      String name = player.getName().getString();

      for(String enemy : (List<String>)this.enemyNames.get()) {
         if (enemy.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   private List<AbstractClientPlayerEntity> getPlayers() {
      this.players.clear();
      this.players.addAll(MeteorClient.mc.world.getPlayers());
      this.players.removeIf(Objects::isNull);
      this.players.removeIf((player) -> player.equals(MeteorClient.mc.player));
      this.players.sort(Comparator.comparingDouble((player) -> player.squaredDistanceTo(MeteorClient.mc.getCameraEntity())));
      if (this.players.size() > (Integer)this.limit.get()) {
         this.players.subList((Integer)this.limit.get(), this.players.size()).clear();
      }

      return this.players;
   }

   private double getScale() {
      return (Boolean)this.customScale.get() ? (Double)this.scale.get() : Hud.get().getTextScale();
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "player-tracker", "Lists nearby players, color-coded as friend/enemy/other, with distance.", PlayerTrackerHud::new);
   }
}
