package gamerguy11.sixtoolsaddon.basehunting;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

public final class HuntWaypoints {
    private static final List<BlockPos> added = new ArrayList<>();

    private HuntWaypoints() {
    }

    public static void add(String name, BlockPos pos) {

        synchronized (added) {
            for (BlockPos p : added) {
                if (Math.abs(p.getX() - pos.getX()) <= 32 && Math.abs(p.getZ() - pos.getZ()) <= 32) return;
            }
            added.add(pos);
        }

        MeteorClient.mc.execute(() -> place(name, pos));
    }

    public static void clearHistory() {
        synchronized (added) {
            added.clear();
        }
    }

    private static void place(String name, BlockPos pos) {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("xaerominimap") && loader.isModLoaded("xaeroworldmap")) {
            try {
                XaeroSide.add(name, pos);
                ChatUtils.info("Waypoint added: " + name);
                return;
            } catch (Throwable t) {
                ChatUtils.warning("Xaero waypoint failed (" + t.getClass().getSimpleName() + ": " + t.getMessage() + "), using Meteor's waypoints instead.");
            }
        }
        try {
            Waypoints.get().add(new Waypoint.Builder().name(name).pos(pos).build());
            ChatUtils.info("Waypoint added: " + name);
        } catch (Throwable t) {
            ChatUtils.error("Could not add a waypoint: " + t.getMessage());
        }
    }

    private static final class XaeroSide {
        static void add(String name, BlockPos pos) {
            xaero.hud.minimap.module.MinimapSession session = xaero.hud.minimap.BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) throw new IllegalStateException("no minimap session");
            xaero.hud.minimap.world.MinimapWorld world = session.getWorldManager().getCurrentWorld();
            if (world == null) throw new IllegalStateException("no minimap world");
            xaero.hud.minimap.waypoint.set.WaypointSet set = world.getCurrentWaypointSet();
            if (set == null) throw new IllegalStateException("no waypoint set");
            set.add(new xaero.common.minimap.waypoints.Waypoint(pos.getX(), pos.getY(), pos.getZ(), name, "S", 4, 0, false));
            xaero.map.mods.SupportMods.xaeroMinimap.requestWaypointsRefresh();
        }
    }
}
