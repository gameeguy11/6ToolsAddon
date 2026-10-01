package gamerguy11.sixtoolsaddon.gui;

import gamerguy11.sixtoolsaddon.homes.Home;
import gamerguy11.sixtoolsaddon.homes.HomeStore;
import java.util.Comparator;
import java.util.UUID;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedMinus;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.util.math.BlockPos;

public class HomesScreen extends WindowScreen {
   public HomesScreen(GuiTheme theme) {
      super(theme, "Homes");
   }

   public void initWidgets() {
      Settings general = new Settings();
      Setting<Integer> defaultRadius = general.getDefaultGroup().add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("default-radius")).description("Radius (in blocks) for newly created homes. A home is a CIRCLE in X/Z (height is ignored), so the radius is measured from the center outwards: radius 50 = a circle 100 blocks across.")).defaultValue(50)).min(1).noSlider().onChanged(HomeStore::setDefaultRadius)).build());
      defaultRadius.set(HomeStore.defaultRadius());
      this.add(this.theme.settings(general)).expandX();
      this.add(this.theme.label("A home is a circle (X/Z only, all heights). Radius 50 = 100 blocks across.")).expandX();
      WButton applyAll = (WButton)this.add(this.theme.button("Apply radius to all existing homes")).expandX().widget();
      applyAll.action = () -> {
         HomeStore.applyRadiusToAll(HomeStore.defaultRadius());
         this.reload();
      };
      this.add(this.theme.horizontalSeparator()).expandX();
      WTable table = (WTable)this.add(this.theme.table()).expandX().minWidth((double)400.0F).widget();
      if (HomeStore.all().isEmpty()) {
         table.add(this.theme.label("No homes yet")).expandX().pad((double)10.0F);
      } else {
         HomeStore.all().entrySet().stream().sorted(Comparator.comparing((e) -> String.valueOf(((Home)e.getValue()).name))).forEach((entry) -> {
            String id = (String)entry.getKey();
            Home home = (Home)entry.getValue();
            table.add(this.theme.label(home.name + (home.protect ? "  [protected]" : "")));
            WButton edit = (WButton)table.add(this.theme.button(GuiRenderer.EDIT)).expandCellX().right().widget();
            edit.action = () -> MeteorClient.mc.setScreen(new EditHomeScreen(this.theme, home, id, this));
            WConfirmedMinus delete = (WConfirmedMinus)table.add(this.theme.confirmedMinus()).right().widget();
            delete.action = () -> {
               HomeStore.remove(id);
               this.reload();
            };
            table.row();
         });
      }

      this.add(this.theme.horizontalSeparator()).expandX();
      WButton addNew = (WButton)this.add(this.theme.button("Add home at current position")).expandX().widget();
      addNew.action = () -> MeteorClient.mc.setScreen(new EditHomeScreen(this.theme, (Home)null, UUID.randomUUID().toString(), this));
   }

   public static class EditHomeScreen extends WindowScreen {
      private final Settings settings = new Settings();
      private final SettingGroup sg;
      private final Setting<String> name;
      private final Setting<BlockPos> coords;
      private final Setting<Integer> radius;
      private final Setting<Dimension> dimension;
      private final Setting<Boolean> protect;
      private final Setting<Boolean> deny;
      private final Setting<Boolean> allowFriends;
      private final Setting<Boolean> overrideCooldown;
      private final Setting<Integer> cooldown;
      private final Home existing;
      private final String id;
      private final HomesScreen parentScreen;

      public EditHomeScreen(GuiTheme theme, Home existing, String id, HomesScreen parentScreen) {
         super(theme, existing != null ? "Edit \"" + existing.name + "\"" : "New Home");
         this.sg = this.settings.getDefaultGroup();
         this.name = this.sg.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("name")).description("Name of the home.")).defaultValue("")).build());
         this.coords = this.sg.add(((BlockPosSetting.Builder)((BlockPosSetting.Builder)(new BlockPosSetting.Builder()).name("coordinates")).description("Center of the home.")).build());
         this.radius = this.sg.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("radius")).description("Circle radius in blocks (X/Z, all heights). Radius 50 = a circle 100 blocks across.")).defaultValue(50)).min(1).noSlider().build());
         this.dimension = this.sg.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("dimension")).description("Dimension of the home.")).defaultValue(Dimension.Overworld)).build());
         this.protect = this.sg.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("protect")).description("While you are inside this home, Auto TPY will not accept teleport requests.")).defaultValue(true)).build());
         this.deny = this.sg.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("deny-requests")).description("Answer requests with /tpn instead of silently ignoring them.")).defaultValue(false)).build());
         this.allowFriends = this.sg.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("allow-friends")).description("Friends can still teleport to you inside this home.")).defaultValue(false)).build());
         this.overrideCooldown = this.sg.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("custom-return-cooldown")).description("Use a return cooldown just for this home instead of the Auto Return Home module's default.")).defaultValue(false)).build());
         this.cooldown = this.sg.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("return-cooldown")).description("Seconds after landing in this home before it auto-runs \"/home <name>\" again (needs the Auto Return Home module enabled). Only used if custom-return-cooldown is on.")).defaultValue(60)).min(0).sliderRange(0, 600).build());
         this.existing = existing;
         this.id = id;
         this.parentScreen = parentScreen;
      }

      public void initWidgets() {
         if (this.existing != null) {
            this.name.set(this.existing.name);
            this.coords.set(new BlockPos(this.existing.x, this.existing.y, this.existing.z));
            this.radius.set(this.existing.radius);
            this.dimension.set(this.existing.dimension);
            this.protect.set(this.existing.protect);
            this.deny.set(this.existing.denyInstead);
            this.allowFriends.set(this.existing.allowFriends);
            this.overrideCooldown.set(this.existing.overrideCooldown);
            this.cooldown.set(this.existing.cooldown);
         } else if (MeteorClient.mc.player != null) {
            this.radius.set(HomeStore.defaultRadius());
            this.coords.set(MeteorClient.mc.player.getBlockPos());
            this.dimension.set(PlayerUtils.getDimension());
         }

         this.add(this.theme.settings(this.settings)).expandX();
         this.add(this.theme.horizontalSeparator()).expandX();
         WHorizontalList buttons = (WHorizontalList)this.add(this.theme.horizontalList()).expandX().widget();
         WButton save = (WButton)buttons.add(this.theme.button(this.existing != null ? "Update" : "Create")).expandX().widget();
         save.action = this::save;
         this.enterAction = this::save;
         WButton cancel = (WButton)buttons.add(this.theme.button("Cancel")).expandX().widget();
         cancel.action = () -> MeteorClient.mc.setScreen(this.parentScreen);
      }

      private void save() {
         if (!((String)this.name.get()).isBlank()) {
            BlockPos p = (BlockPos)this.coords.get();
            Home home = new Home(((String)this.name.get()).trim(), p.getX(), p.getY(), p.getZ(), (Integer)this.radius.get(), (Dimension)this.dimension.get());
            home.protect = (Boolean)this.protect.get();
            home.denyInstead = (Boolean)this.deny.get();
            home.allowFriends = (Boolean)this.allowFriends.get();
            home.overrideCooldown = (Boolean)this.overrideCooldown.get();
            home.cooldown = (Integer)this.cooldown.get();
            HomeStore.save(this.id, home);
            MeteorClient.mc.setScreen(new HomesScreen(this.theme));
         }
      }
   }
}
