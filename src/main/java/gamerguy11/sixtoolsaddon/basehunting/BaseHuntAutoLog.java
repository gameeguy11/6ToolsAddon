package gamerguy11.sixtoolsaddon.basehunting;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class BaseHuntAutoLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> elytraLow = sgGeneral.add(new BoolSetting.Builder()
        .name("elytra-durability")
        .description("Logs out when the worn elytra is about to break.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> elytraDurability = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-durability")
        .description("Durability left on the elytra that triggers the log out.")
        .defaultValue(15)
        .range(1, 431)
        .sliderRange(1, 100)
        .visible(elytraLow::get)
        .build()
    );

    private final Setting<Boolean> playerNear = sgGeneral.add(new BoolSetting.Builder()
        .name("player-in-range")
        .description("Logs out when another player comes within range.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> playerRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("player-range")
        .defaultValue(128.0)
        .range(8.0, 512.0)
        .sliderRange(8.0, 256.0)
        .visible(playerNear::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .defaultValue(true)
        .visible(playerNear::get)
        .build()
    );

    private final Setting<Boolean> belowY = sgGeneral.add(new BoolSetting.Builder()
        .name("below-y")
        .description("Logs out when you fall below a height, for example after the elytra stops working.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-y")
        .defaultValue(40)
        .range(-64, 320)
        .sliderRange(-64, 200)
        .visible(belowY::get)
        .build()
    );

    private final Setting<Boolean> health = sgGeneral.add(new BoolSetting.Builder()
        .name("low-health")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minHealth = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-health")
        .defaultValue(8)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(health::get)
        .build()
    );

    public BaseHuntAutoLog() {
        super(SixToolsAddon.BASE_HUNTING, "hunt-autolog", "Disconnects while base hunting when the elytra is nearly broken, a player shows up, you drop too low or your health is low.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (elytraLow.get()) {
            ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chest.contains(DataComponentTypes.GLIDER) && chest.isDamageable()) {
                int left = chest.getMaxDamage() - chest.getDamage();
                if (left <= elytraDurability.get()) {
                    logOut("Elytra durability is down to " + left + ".");
                    return;
                }
            }
        }

        if (belowY.get() && mc.player.getY() < minY.get()) {
            logOut("Dropped below Y " + minY.get() + ".");
            return;
        }

        if (health.get() && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= minHealth.get()) {
            logOut("Health is down to " + Math.round(mc.player.getHealth()) + ".");
            return;
        }

        if (playerNear.get()) {
            for (PlayerEntity other : mc.world.getPlayers()) {
                if (other == mc.player) continue;
                if (ignoreFriends.get() && Friends.get().isFriend(other)) continue;
                if (mc.player.distanceTo(other) <= playerRange.get()) {
                    logOut(other.getName().getString() + " came within " + Math.round(mc.player.distanceTo(other)) + " blocks.");
                    return;
                }
            }
        }
    }

    private void logOut(String reason) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.getConnection().disconnect(Text.literal("[Hunt AutoLog] " + reason));
        toggle();
    }
}
