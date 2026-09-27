package gamerguy11.sixtoolsaddon.gui;

import gamerguy11.sixtoolsaddon.homes.Home;
import gamerguy11.sixtoolsaddon.homes.HomeStore;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedMinus;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HomesScreen extends WindowScreen {
    public HomesScreen(GuiTheme theme) {
        super(theme, "Homes");
    }

    @Override
    public void initWidgets() {
        Settings general = new Settings();
        Setting<Integer> defaultRadius = general.getDefaultGroup().add(new IntSetting.Builder()
            .name("default-radius")
            .description("Radius (in blocks) for newly created homes. A home is a CIRCLE in X/Z (height is ignored), "
                + "so the radius is measured from the center outwards: radius 50 = a circle 100 blocks across.")
            .defaultValue(50).min(1).noSlider()
            .onChanged(HomeStore::setDefaultRadius)
            .build());
        defaultRadius.set(HomeStore.defaultRadius());

        add(theme.settings(general)).expandX();
        add(theme.label("A home is a circle (X/Z only, all heights). Radius 50 = 100 blocks across.")).expandX();

        WButton applyAll = add(theme.button("Apply radius to all existing homes")).expandX().widget();
        applyAll.action = () -> {
            HomeStore.applyRadiusToAll(HomeStore.defaultRadius());
            reload();
        };

        add(theme.horizontalSeparator()).expandX();

        WTable table = add(theme.table()).expandX().minWidth(400).widget();

        if (HomeStore.all().isEmpty()) {
            table.add(theme.label("No homes yet")).expandX().pad(10);
        } else {
            HomeStore.all().entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Home> e) -> String.valueOf(e.getValue().name)))
                .forEach(entry -> {
                    String id = entry.getKey();
                    Home home = entry.getValue();

                    table.add(theme.label(home.name + (home.protect ? "  [protected]" : "")));

                    WButton edit = table.add(theme.button(GuiRenderer.EDIT)).expandCellX().right().widget();
                    edit.action = () -> mc.setScreen(new EditHomeScreen(theme, home, id, this));

                    WConfirmedMinus delete = table.add(theme.confirmedMinus()).right().widget();
                    delete.action = () -> {
                        HomeStore.remove(id);
                        reload();
                    };

                    table.row();
                });
        }

        add(theme.horizontalSeparator()).expandX();

        WButton addNew = add(theme.button("Add home at current position")).expandX().widget();
        addNew.action = () -> mc.setScreen(new EditHomeScreen(theme, null, UUID.randomUUID().toString(), this));
    }

    public static class EditHomeScreen extends WindowScreen {
        private final Settings settings = new Settings();
        private final SettingGroup sg = settings.getDefaultGroup();

        private final Setting<String> name = sg.add(new StringSetting.Builder()
            .name("name").description("Name of the home.").defaultValue("").build());

        private final Setting<BlockPos> coords = sg.add(new BlockPosSetting.Builder()
            .name("coordinates").description("Center of the home.").build());

        private final Setting<Integer> radius = sg.add(new IntSetting.Builder()
            .name("radius").description("Circle radius in blocks (X/Z, all heights). Radius 50 = a circle 100 blocks across.")
            .defaultValue(50).min(1).noSlider().build());

        private final Setting<Dimension> dimension = sg.add(new EnumSetting.Builder<Dimension>()
            .name("dimension").description("Dimension of the home.").defaultValue(Dimension.Overworld).build());

        private final Setting<Boolean> protect = sg.add(new BoolSetting.Builder()
            .name("protect").description("While you are inside this home, Auto TPY will not accept teleport requests.")
            .defaultValue(true).build());

        private final Setting<Boolean> deny = sg.add(new BoolSetting.Builder()
            .name("deny-requests").description("Answer requests with /tpn instead of silently ignoring them.")
            .defaultValue(false).build());

        private final Setting<Boolean> allowFriends = sg.add(new BoolSetting.Builder()
            .name("allow-friends").description("Friends can still teleport to you inside this home.")
            .defaultValue(false).build());

        private final Setting<Boolean> overrideCooldown = sg.add(new BoolSetting.Builder()
            .name("custom-return-cooldown")
            .description("Use a return cooldown just for this home instead of the Auto Return Home module's default.")
            .defaultValue(false).build());

        private final Setting<Integer> cooldown = sg.add(new IntSetting.Builder()
            .name("return-cooldown")
            .description("Seconds after landing in this home before it auto-runs \"/home <name>\" again (needs the Auto Return Home module enabled). Only used if custom-return-cooldown is on.")
            .defaultValue(60).min(0).sliderRange(0, 600).build());

        private final Home existing;
        private final String id;
        private final HomesScreen parentScreen;

        public EditHomeScreen(GuiTheme theme, Home existing, String id, HomesScreen parentScreen) {
            super(theme, existing != null ? "Edit \"" + existing.name + "\"" : "New Home");
            this.existing = existing;
            this.id = id;
            this.parentScreen = parentScreen;
        }

        @Override
        public void initWidgets() {
            if (existing != null) {
                name.set(existing.name);
                coords.set(new BlockPos(existing.x, existing.y, existing.z));
                radius.set(existing.radius);
                dimension.set(existing.dimension);
                protect.set(existing.protect);
                deny.set(existing.denyInstead);
                allowFriends.set(existing.allowFriends);
                overrideCooldown.set(existing.overrideCooldown);
                cooldown.set(existing.cooldown);
            } else if (mc.player != null) {
                radius.set(HomeStore.defaultRadius());
                coords.set(mc.player.getBlockPos());
                dimension.set(PlayerUtils.getDimension());
            }

            add(theme.settings(settings)).expandX();
            add(theme.horizontalSeparator()).expandX();

            WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();
            WButton save = buttons.add(theme.button(existing != null ? "Update" : "Create")).expandX().widget();
            save.action = this::save;
            enterAction = this::save;

            WButton cancel = buttons.add(theme.button("Cancel")).expandX().widget();
            cancel.action = () -> mc.setScreen(parentScreen);
        }

        private void save() {
            if (name.get().isBlank()) return;

            BlockPos p = coords.get();
            Home home = new Home(name.get().trim(), p.getX(), p.getY(), p.getZ(), radius.get(), dimension.get());
            home.protect = protect.get();
            home.denyInstead = deny.get();
            home.allowFriends = allowFriends.get();
            home.overrideCooldown = overrideCooldown.get();
            home.cooldown = cooldown.get();

            HomeStore.save(id, home);
            mc.setScreen(new HomesScreen(theme));
        }
    }
}
