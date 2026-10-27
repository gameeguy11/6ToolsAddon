package gamerguy11.sixtoolsaddon.basehunting;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;

public final class XaeroHuntingLoader {
    private XaeroHuntingLoader() {
    }

    public static void init() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("xaeroplus") || !loader.isModLoaded("xaerominimap") || !loader.isModLoaded("xaeroworldmap")) {
            SixToolsAddon.LOG.info("XaeroPlus, Xaero's Minimap or Xaero's World Map is missing, skipping the Xaero base hunting modules.");
            return;
        }

        Modules.get().add(new BaseHuntBetterStashFinder());
        Modules.get().add(new BaseHuntOldChunkNotifier());
        Modules.get().add(new BaseHuntTrailFollower());
    }
}
