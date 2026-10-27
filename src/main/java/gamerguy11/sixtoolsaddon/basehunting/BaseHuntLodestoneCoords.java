package gamerguy11.sixtoolsaddon.basehunting;

import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.GlobalPos;
import gamerguy11.sixtoolsaddon.SixToolsAddon;

import java.util.Optional;

public class BaseHuntLodestoneCoords extends Module {

    public BaseHuntLodestoneCoords() {
        super(SixToolsAddon.BASE_HUNTING, "lodestone-coords", "Decodes the coordinates bound to the lodestone compass in your hand.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        parseOwnCompass();
        toggle();
    }

    private void parseOwnCompass() {

        ItemStack mainHand = mc.player.getMainHandStack();
        if (!mainHand.isEmpty() && mainHand.isOf(Items.COMPASS)) {
            String result = decodeCompass(mainHand);
            if (result != null) {
                info("§a[Decoded] §r" + result);
                return;
            }
        }

        ItemStack offHand = mc.player.getOffHandStack();
        if (!offHand.isEmpty() && offHand.isOf(Items.COMPASS)) {
            String result = decodeCompass(offHand);
            if (result != null) {
                info("§a[Decoded] §r" + result);
                return;
            }
        }

        info("§cNo bound Lodestone compass found in hand.\n");
    }

    private String decodeCompass(ItemStack stack) {
        try {
            LodestoneTrackerComponent tracker = stack.get(DataComponentTypes.LODESTONE_TRACKER);
            if (tracker == null) return null;

            Optional<GlobalPos> target = tracker.target();
            if (target.isEmpty()) return null;

            GlobalPos globalPos = target.get();
            int x = globalPos.pos().getX();
            int y = globalPos.pos().getY();
            int z = globalPos.pos().getZ();
            String dimension = globalPos.dimension().getValue().toString();

            return String.format("Dimension: §a%s §f| Coordinate: §eX:%d Y:%d Z:%d",
                    dimension, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}
