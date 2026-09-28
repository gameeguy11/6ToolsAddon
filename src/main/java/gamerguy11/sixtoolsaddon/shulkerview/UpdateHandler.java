package gamerguy11.sixtoolsaddon.shulkerview;

import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class UpdateHandler {
    private final ObjectList<ShulkerInfo> shulkerList = ObjectLists.synchronize(new ObjectArrayList<>());
    private final ShulkerView config;

    public UpdateHandler(ShulkerView config) {
        this.config = config;
    }

    public void tick() {
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;

        List<ShulkerInfo> found = new ArrayList<>();
        boolean stack = config.isStackIdentical();

        for (Slot slot : screen.getScreenHandler().slots) {
            ShulkerInfo info = ShulkerInfo.create(config, slot.getStack(), slot.id);
            if (info == null) continue;

            // Merge into an earlier identical shulker; the first one keeps its slot for click-to-pick-up.
            boolean merged = false;
            if (stack) {
                for (int i = 0; i < found.size(); i++) {
                    ShulkerInfo existing = found.get(i);
                    if (existing.sameAs(info)) {
                        found.set(i, existing.withCount(existing.count() + 1));
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged) found.add(info);
        }

        // Fullest shulker first, then emptier ones further down. Stable sort keeps inventory order for ties;
        // for equal contents, the bigger stack of identical boxes goes first.
        found.sort(Comparator.comparingInt(ShulkerInfo::totalItems).reversed()
            .thenComparing(Comparator.comparingInt(ShulkerInfo::count).reversed()));

        shulkerList.clear();
        shulkerList.addAll(found);
    }

    public ObjectList<ShulkerInfo> getShulkerList() {
        return shulkerList;
    }
}
