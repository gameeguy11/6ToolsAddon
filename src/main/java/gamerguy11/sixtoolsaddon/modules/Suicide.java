package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Suicide extends Module {
    public Suicide() {
        super(SixToolsAddon.CATEGORY, "suicide", "Kills yourself. Recommended.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgConditions = settings.createGroup("Conditions");

    public final Setting<Boolean> disableDeath = sgGeneral.add(new BoolSetting.Builder()
            .name("disable-on-death")
            .description("Disables the module on death.")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> enableCA = sgGeneral.add(new BoolSetting.Builder()
            .name("enable-crystal-aura")
            .description("Enables Meteor's Crystal Aura module when triggered, instead of the built-in auto-crystal below.")
            .defaultValue(false)
            .build()
    );

    private final SettingGroup sgCrystal = settings.createGroup("Auto Crystal");

    private final Setting<Boolean> autoCrystal = sgCrystal.add(new BoolSetting.Builder()
            .name("enabled")
            .description("Places and pops an end crystal on yourself when triggered. Requires standing on obsidian or bedrock.")
            .defaultValue(true)
            .visible(() -> !enableCA.get())
            .build()
    );

    private final Setting<Integer> crystalDelay = sgCrystal.add(new IntSetting.Builder()
            .name("delay-ticks")
            .description("Ticks to wait between each place/attack attempt.")
            .defaultValue(2)
            .min(0)
            .sliderRange(0, 20)
            .visible(() -> !enableCA.get() && autoCrystal.get())
            .build()
    );

    private final Setting<Boolean> waitForConditions = sgConditions.add(new BoolSetting.Builder()
            .name("wait-for-conditions")
            .description("Only starts killing you once the conditions below are met, instead of immediately on activation.")
            .defaultValue(true)
            .build()
    );

    private final Setting<RequireMode> requireMode = sgConditions.add(new EnumSetting.Builder<RequireMode>()
            .name("require-mode")
            .description("Whether both conditions must be true, or just one of them, to trigger.")
            .defaultValue(RequireMode.All)
            .visible(waitForConditions::get)
            .build()
    );

    private final Setting<Integer> minTotems = sgConditions.add(new IntSetting.Builder()
            .name("min-totems")
            .description("Triggers once you have this many totems of undying or fewer (0 means you have none left).")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 5)
            .visible(waitForConditions::get)
            .build()
    );

    private final Setting<Double> minHealth = sgConditions.add(new DoubleSetting.Builder()
            .name("min-health")
            .description("Triggers once your health (including absorption) is this or lower.")
            .defaultValue(20)
            .min(0)
            .sliderRange(0, 40)
            .visible(waitForConditions::get)
            .build()
    );

    private boolean triggered = false;
    private int cooldown = 0;

    @Override
    public void onActivate() {
        triggered = false;
        cooldown = 0;

        if (!waitForConditions.get()) trigger();
    }

    @Override
    public void onDeactivate() {
        triggered = false;
        cooldown = 0;

        if (enableCA.get() && Modules.get().isActive(CrystalAura.class)) {
            Modules.get().get(CrystalAura.class).toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (!triggered) {
            if (!waitForConditions.get()) return;

            boolean totemsMet = countTotems() <= minTotems.get();
            boolean healthMet = (mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= minHealth.get();

            boolean shouldTrigger = requireMode.get() == RequireMode.All
                    ? (totemsMet && healthMet)
                    : (totemsMet || healthMet);

            if (shouldTrigger) trigger();
            return;
        }

        if (!enableCA.get() && autoCrystal.get()) tickAutoCrystal();
    }

    private void trigger() {
        triggered = true;

        if (enableCA.get() && !Modules.get().isActive(CrystalAura.class)) {
            Modules.get().get(CrystalAura.class).toggle();
        }
    }

    private void tickAutoCrystal() {
        if (mc.world == null || mc.interactionManager == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        BlockPos basePos = mc.player.getBlockPos().down();
        BlockPos crystalPos = basePos.up();

        if (mc.world.getBlockState(basePos).getBlock() != Blocks.OBSIDIAN
                && mc.world.getBlockState(basePos).getBlock() != Blocks.BEDROCK) return;

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                new Box(crystalPos), c -> c.getBlockPos().equals(crystalPos))) {
            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
            cooldown = crystalDelay.get();
            return;
        }

        FindItemResult crystalItem = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (!crystalItem.found()) return;

        InvUtils.swap(crystalItem.slot(), true);

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(basePos), Direction.UP, basePos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        InvUtils.swapBack();

        cooldown = crystalDelay.get();
    }

    private int countTotems() {
        int count = 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) count += stack.getCount();
        }

        return count;
    }

    @EventHandler(priority = 6969)
    private void onDeath(OpenScreenEvent event) {
        if (event.screen instanceof DeathScreen && disableDeath.get()) {
            toggle();
        }
    }

    public enum RequireMode {
        All,
        Any
    }
}