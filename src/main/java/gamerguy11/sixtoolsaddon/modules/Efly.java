package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.Objects;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class Efly extends Module {
   private final SettingGroup sgAutopilot;
   private final SettingGroup sgMapping;
   private final SettingGroup sgBuildingMode;
   private final SettingGroup sgPlayerAvoidance;
   private final SettingGroup sgLanding;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgInventory;
   public final Setting<Boolean> mappingMode;
   public final Setting<Integer> mappingRenderRadius;
   public final Setting<Boolean> mappingHideWarning;
   public final Setting<Boolean> buildingMode;
   public final Setting<Double> buildingModeDistance;
   public final Setting<Double> buildingModeMinSpeed;
   public final Setting<Boolean> playerAvoidance;
   public final Setting<Double> avoidanceRadius;
   public final Setting<Boolean> avoidanceIgnoreFriends;
   public final Setting<Boolean> avoidWitherSkulls;
   public final Setting<Double> witherSkullRadius;
   public final Setting<Boolean> avoidArrows;
   public final Setting<Double> arrowRadius;
   public final Setting<Boolean> avoidBlocks;
   public final Setting<Double> blockAvoidanceRadius;
   public final Setting<Boolean> avoidanceVerticalStep;
   public final Setting<Integer> avoidanceStuckTicks;
   public final Setting<Boolean> avoidanceLateral;
   public final Setting<Boolean> landGently;
   public final Setting<Double> landGentlyDistance;
   public final Setting<Double> landGentlyMinDistance;
   public final Setting<Double> landGentlyMinSpeed;
   public final Setting<Double> horizontalSpeed;
   public final Setting<Double> verticalSpeed;
   public final Setting<Double> startSpeed;
   public final Setting<Double> verticalStartSpeed;
   public final Setting<Double> accelerationPlateau;
   public final Setting<Double> verticalAccelerationPlateau;
   public final Setting<Boolean> accelerateUpward;
   public final Setting<Integer> accelerationDelay;
   public final Setting<Double> accelerationStep;
   public final Setting<Double> verticalAccelerationStep;
   public final Setting<Boolean> limitMaxHeight;
   public final Setting<Double> maxHeight;
   public final Setting<Boolean> autoTakeOff;
   public final Setting<Boolean> stopInWater;
   public final Setting<Boolean> dontGoIntoUnloadedChunks;
   public final Setting<Boolean> noCrash;
   public final Setting<Integer> crashLookAhead;
   private final Setting<Boolean> instaDrop;
   public final Setting<Double> fallMultiplier;
   public final Setting<Boolean> replace;
   public final Setting<Integer> replaceDurability;
   public final Setting<ChestSwapMode> chestSwap;
   public final Setting<Boolean> autoReplenish;
   public final Setting<Integer> replenishSlot;
   public final Setting<Boolean> autoPilot;
   public final Setting<Boolean> useFireworks;
   public final Setting<Double> autoPilotFireworkDelay;
   public final Setting<Double> autoPilotMinimumHeight;
   private boolean lastJumpPressed;
   private boolean incrementJumpTimer;
   private boolean lastForwardPressed;
   private int jumpTimer;
   private double velX;
   private double velY;
   private double velZ;
   private double ticksLeft;
   private Vec3d forward;
   private Vec3d right;
   private double acceleration;
   private boolean atMaxSpeed;
   private int accelerationDelayTicks;
   private double verticalAcceleration;
   private boolean atMaxVerticalSpeed;
   private int verticalAccelerationDelayTicks;
   private boolean mappingWaitingForChunks;
   private boolean buildingModeEngaged;
   private double buildingModeEntryHorizontalSpeed;
   private double buildingModeEntryVerticalSpeed;
   private int buildingModeTicksElapsed;
   private static final int BUILDING_MODE_SLOWDOWN_TICKS = 5;
   private boolean avoidanceSteering;
   private Vec3d avoidanceLateralDir;
   private int avoidanceStuckTicksCount;
   private static final int VERTICAL_STEP_TIMEOUT_TICKS = 40;
   private boolean verticalStepActive;
   private boolean verticalStepUp;
   private double verticalStepStartY;
   private int verticalStepTicks;
   private final StaticGroundListener staticGroundListener;
   private final StaticInstaDropListener staticInstadropListener;

   public Efly() {
      super(SixToolsAddon.CATEGORY, "efly", "Specifically designed to maximise elytrafly capabilities and speed on 6b6t");
      this.sgAutopilot = this.settings.createGroup("Autopilot");
      this.sgMapping = this.settings.createGroup("Mapping");
      this.sgBuildingMode = this.settings.createGroup("Building Mode");
      this.sgPlayerAvoidance = this.settings.createGroup("Player Avoidance System");
      this.sgLanding = this.settings.createGroup("Landing");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgInventory = this.settings.createGroup("Inventory");
      this.mappingMode = this.sgMapping.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("mapping-mode")).description("Pauses the player's horizontal movement until nearby chunks are loaded")).defaultValue(false)).build());
      SettingGroup var10001 = this.sgMapping;
      IntSetting.Builder var10002 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("render-radius")).description("How many chunks around you need to load before you start moving again.")).defaultValue(9)).min(0).sliderMax(32);
      Setting<Boolean> var10003 = this.mappingMode;
      Objects.requireNonNull(var10003);
      this.mappingRenderRadius = var10001.add(((IntSetting.Builder)var10002.visible(var10003::get)).build());
      var10001 = this.sgMapping;
      BoolSetting.Builder var21 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("hide-waiting-warning")).description("Toggle the warning message about waiting for chunks to load")).defaultValue(false);
      var10003 = this.mappingMode;
      Objects.requireNonNull(var10003);
      this.mappingHideWarning = var10001.add(((BoolSetting.Builder)var21.visible(var10003::get)).build());
      this.buildingMode = this.sgBuildingMode.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("building-mode")).description("Slows you down when blocks are nearby")).defaultValue(false)).build());
      var10001 = this.sgBuildingMode;
      DoubleSetting.Builder var22 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("building-mode-distance")).description("Distance from a block that triggers the slowdown")).defaultValue((double)15.0F).min(0.1).sliderMax((double)30.0F);
      var10003 = this.buildingMode;
      Objects.requireNonNull(var10003);
      this.buildingModeDistance = var10001.add(((DoubleSetting.Builder)var22.visible(var10003::get)).build());
      var10001 = this.sgBuildingMode;
      var22 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("building-mode-min-speed")).description("Speed you ease down to when near blocks")).defaultValue(0.7).min((double)0.0F).sliderMax((double)1.0F);
      var10003 = this.buildingMode;
      Objects.requireNonNull(var10003);
      this.buildingModeMinSpeed = var10001.add(((DoubleSetting.Builder)var22.visible(var10003::get)).build());
      this.playerAvoidance = this.sgPlayerAvoidance.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("player-avoidance")).description("Distances you from other players when they get close")).defaultValue(false)).build());
      var10001 = this.sgPlayerAvoidance;
      var22 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("radius")).description("Distance at which a player triggers avoidance")).defaultValue((double)30.0F).min((double)0.0F).sliderMax((double)50.0F);
      var10003 = this.playerAvoidance;
      Objects.requireNonNull(var10003);
      this.avoidanceRadius = var10001.add(((DoubleSetting.Builder)var22.visible(var10003::get)).build());
      var10001 = this.sgPlayerAvoidance;
      BoolSetting.Builder var25 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("ignore-friends")).description("Ignores players on your friends list")).defaultValue(true);
      var10003 = this.playerAvoidance;
      Objects.requireNonNull(var10003);
      this.avoidanceIgnoreFriends = var10001.add(((BoolSetting.Builder)var25.visible(var10003::get)).build());
      var10001 = this.sgPlayerAvoidance;
      var25 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("avoid-wither-skulls")).description("Also avoids wither skulls")).defaultValue(true);
      var10003 = this.playerAvoidance;
      Objects.requireNonNull(var10003);
      this.avoidWitherSkulls = var10001.add(((BoolSetting.Builder)var25.visible(var10003::get)).build());
      this.witherSkullRadius = this.sgPlayerAvoidance.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("wither-skull-radius")).description("Distance at which a wither skull triggers avoidance")).defaultValue((double)10.0F).min((double)0.0F).sliderMax((double)50.0F).visible(() -> (Boolean)this.playerAvoidance.get() && (Boolean)this.avoidWitherSkulls.get())).build());
      var10001 = this.sgPlayerAvoidance;
      var25 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("avoid-arrows")).description("Also avoids arrows")).defaultValue(true);
      var10003 = this.playerAvoidance;
      Objects.requireNonNull(var10003);
      this.avoidArrows = var10001.add(((BoolSetting.Builder)var25.visible(var10003::get)).build());
      this.arrowRadius = this.sgPlayerAvoidance.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("arrow-radius")).description("Distance at which an arrow triggers avoidance")).defaultValue((double)50.0F).min((double)0.0F).sliderMax((double)50.0F).visible(() -> (Boolean)this.playerAvoidance.get() && (Boolean)this.avoidArrows.get())).build());
      var10001 = this.sgPlayerAvoidance;
      var25 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("avoid-blocks")).description("Also avoids nearby blocks while moving away from players and wither skulls")).defaultValue(true);
      var10003 = this.playerAvoidance;
      Objects.requireNonNull(var10003);
      this.avoidBlocks = var10001.add(((BoolSetting.Builder)var25.visible(var10003::get)).build());
      this.blockAvoidanceRadius = this.sgPlayerAvoidance.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("block-radius")).description("Distance at which a block triggers avoidance")).defaultValue((double)3.0F).min((double)0.0F).sliderMax((double)10.0F).visible(() -> (Boolean)this.playerAvoidance.get() && (Boolean)this.avoidBlocks.get())).build());
      var10001 = this.sgPlayerAvoidance;
      var25 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("vertical-step")).description("Moves up or down if avoidance gets you stuck, then avoids normally again")).defaultValue(true);
      var10003 = this.playerAvoidance;
      Objects.requireNonNull(var10003);
      this.avoidanceVerticalStep = var10001.add(((BoolSetting.Builder)var25.visible(var10003::get)).build());
      this.avoidanceStuckTicks = this.sgPlayerAvoidance.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("stuck-ticks")).description("How long you have to be stuck for vertical movement to kick in")).defaultValue(3)).min(1).sliderMax(20).visible(() -> (Boolean)this.playerAvoidance.get() && (Boolean)this.avoidanceVerticalStep.get())).build());
      var10001 = this.sgPlayerAvoidance;
      var25 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("sidestep")).description("Tries to move sideways to an incoming player rather than simply away")).defaultValue(true);
      var10003 = this.playerAvoidance;
      Objects.requireNonNull(var10003);
      this.avoidanceLateral = var10001.add(((BoolSetting.Builder)var25.visible(var10003::get)).build());
      this.landGently = this.sgLanding.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("anti-slam")).description("Slows you down when landing to prevent fall damage")).defaultValue(false)).build());
      var10001 = this.sgLanding;
      DoubleSetting.Builder var31 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("anti-slam-distance")).description("Distance from the ground where slowing begins")).defaultValue((double)20.0F).min(0.1).sliderMax((double)30.0F);
      var10003 = this.landGently;
      Objects.requireNonNull(var10003);
      this.landGentlyDistance = var10001.add(((DoubleSetting.Builder)var31.visible(var10003::get)).build());
      var10001 = this.sgLanding;
      var31 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("anti-slam-min-distance")).description("Distance from the ground where speed reaches the minimum value")).defaultValue((double)0.5F).min((double)0.0F).sliderMax((double)5.0F);
      var10003 = this.landGently;
      Objects.requireNonNull(var10003);
      this.landGentlyMinDistance = var10001.add(((DoubleSetting.Builder)var31.visible(var10003::get)).build());
      var10001 = this.sgLanding;
      var31 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("anti-slam-min-speed")).description("The speed to slow down to before landing")).defaultValue(0.2).min((double)0.0F).sliderMax((double)1.0F);
      var10003 = this.landGently;
      Objects.requireNonNull(var10003);
      this.landGentlyMinSpeed = var10001.add(((DoubleSetting.Builder)var31.visible(var10003::get)).build());
      this.horizontalSpeed = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("maximum-horizontal-speed")).description("The fastest horizontal speed will go (blocks per tick)")).defaultValue(14.999).min((double)0.0F).build());
      this.verticalSpeed = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("maximum-vertical-speed")).description("The fastest vertical speed will go (blocks per tick)")).defaultValue(5.999).min((double)0.0F).build());
      this.startSpeed = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("minimum-horizontal-speed")).description("The speed you start at when moving horizontally, before acceleration kicks in")).min((double)0.0F).defaultValue(2.999).build());
      this.verticalStartSpeed = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("minimum-vertical-speed")).description("The speed you start at when moving vertically, before acceleration kicks in")).min((double)0.0F).defaultValue(5.999).build());
      this.accelerationPlateau = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("horizontal-acceleration-plateau")).description("The horizontal speed where acceleration will tend to 0")).min(0.01).defaultValue(14.999).build());
      this.verticalAccelerationPlateau = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("vertical-acceleration-plateau")).description("The vertical speed where acceleration will tend to 0")).min(0.01).defaultValue(29.999).build());
      this.accelerateUpward = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("accelerate-upward")).description("Also accelerates upwards. Not recommended if vertical speed goes above 7.999")).defaultValue(false)).build());
      this.accelerationDelay = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("acceleration-delay")).description("Adds a slight delay before accelerating. 1 Tick is necessary to avoid getting stuck.")).min(0).sliderMax(100).defaultValue(1)).build());
      this.accelerationStep = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("horizontal-acceleration-step")).description("How fast horizontal speed ramps up")).min(0.01).max((double)5.0F).defaultValue(0.3).build());
      this.verticalAccelerationStep = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("vertical-acceleration-step")).description("How fast vertical speed ramps up")).min(0.01).max((double)5.0F).defaultValue((double)1.0F).build());
      this.limitMaxHeight = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("limit-max-height")).description("Stops you from flying above a set height")).defaultValue(true)).build());
      var10001 = this.sgGeneral;
      var31 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("max-height")).description("The max height that you will be able to reach")).defaultValue((double)500.0F).min((double)-128.0F).sliderMax((double)500.0F);
      var10003 = this.limitMaxHeight;
      Objects.requireNonNull(var10003);
      this.maxHeight = var10001.add(((DoubleSetting.Builder)var31.visible(var10003::get)).build());
      this.autoTakeOff = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("auto-take-off")).description("Takes off automatically without needing to double jump")).defaultValue(false)).build());
      this.stopInWater = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("stop-in-water")).description("Stops flying when you touch water")).defaultValue(false)).build());
      this.dontGoIntoUnloadedChunks = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("no-unloaded-chunks")).description("Stops you from flying into unloaded chunks")).defaultValue(false)).build());
      this.noCrash = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("no-crash")).description("Stops you from flying into walls")).defaultValue(false)).build());
      var10001 = this.sgGeneral;
      IntSetting.Builder var35 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("crash-look-ahead")).description("Distance to look ahead for walls")).defaultValue(3)).range(1, 15).sliderMin(1);
      var10003 = this.noCrash;
      Objects.requireNonNull(var10003);
      this.crashLookAhead = var10001.add(((IntSetting.Builder)var35.visible(var10003::get)).build());
      this.instaDrop = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("insta-drop")).description("Instantly drops you out of flight")).defaultValue(false)).build());
      this.fallMultiplier = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("fall-multiplier")).description("Multiplier for how fast you fall naturally")).defaultValue((double)0.0F).min((double)0.0F).build());
      this.replace = this.sgInventory.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("elytra-replace")).description("Replaces a broken elytra with a new one")).defaultValue(false)).build());
      var10001 = this.sgInventory;
      var35 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("replace-durability")).description("Durability left on the elytra before it's replaced")).defaultValue(2)).sliderRange(1, 500);
      var10003 = this.replace;
      Objects.requireNonNull(var10003);
      this.replaceDurability = var10001.add(((IntSetting.Builder)var35.visible(var10003::get)).build());
      this.chestSwap = this.sgInventory.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("chest-swap")).description("Swaps to an elytra when toggling this module")).defaultValue(Efly.ChestSwapMode.Never)).build());
      this.autoReplenish = this.sgInventory.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("replenish-fireworks")).description("Moves fireworks into a chosen hotbar slot")).defaultValue(false)).build());
      var10001 = this.sgInventory;
      var35 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("replenish-slot")).description("Hotbar slot to move fireworks into")).defaultValue(9)).range(1, 9).sliderRange(1, 9);
      var10003 = this.autoReplenish;
      Objects.requireNonNull(var10003);
      this.replenishSlot = var10001.add(((IntSetting.Builder)var35.visible(var10003::get)).build());
      this.autoPilot = this.sgAutopilot.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("auto-pilot")).description("Moves forward automatically while elytra flying")).defaultValue(false)).build());
      var10001 = this.sgAutopilot;
      BoolSetting.Builder var38 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("use-fireworks")).description("Uses fireworks automatically at an interval")).defaultValue(false);
      var10003 = this.autoPilot;
      Objects.requireNonNull(var10003);
      this.useFireworks = var10001.add(((BoolSetting.Builder)var38.visible(var10003::get)).build());
      var10001 = this.sgAutopilot;
      DoubleSetting.Builder var39 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("firework-delay")).description("Seconds between automatic firework uses")).min((double)1.0F).defaultValue((double)8.0F).sliderMax((double)20.0F);
      var10003 = this.useFireworks;
      Objects.requireNonNull(var10003);
      this.autoPilotFireworkDelay = var10001.add(((DoubleSetting.Builder)var39.visible(var10003::get)).build());
      var10001 = this.sgAutopilot;
      var39 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("minimum-height")).description("Minimum height autopilot needs before it flies forward")).defaultValue((double)120.0F).min((double)-128.0F).sliderMax((double)260.0F);
      var10003 = this.autoPilot;
      Objects.requireNonNull(var10003);
      this.autoPilotMinimumHeight = var10001.add(((DoubleSetting.Builder)var39.visible(var10003::get)).build());
      this.staticGroundListener = new StaticGroundListener();
      this.staticInstadropListener = new StaticInstaDropListener();
   }

   public void onActivate() {
      this.atMaxSpeed = false;
      this.lastJumpPressed = false;
      this.jumpTimer = 0;
      this.ticksLeft = (double)0.0F;
      this.accelerationDelayTicks = 0;
      this.acceleration = (Double)this.startSpeed.get();
      this.atMaxVerticalSpeed = false;
      this.verticalAccelerationDelayTicks = 0;
      this.verticalAcceleration = (Double)this.verticalStartSpeed.get();
      this.buildingModeEngaged = false;
      PlayerEntity player = this.mc.player;
      if (player != null) {
         if ((this.chestSwap.get() == Efly.ChestSwapMode.Always || this.chestSwap.get() == Efly.ChestSwapMode.WaitForGround) && player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA && this.isActive()) {
            this.swapToChestSwap();
         }

      }
   }

   public void onDeactivate() {
      this.mappingWaitingForChunks = false;
      if ((Boolean)this.autoPilot.get() && !this.isKeyPhysicallyPressed(this.mc.options.forwardKey)) {
         this.mc.options.forwardKey.setPressed(false);
      }

      this.releaseAvoidance();
      this.releaseVerticalStep();
      PlayerEntity player = this.mc.player;
      if (player != null) {
         if (this.chestSwap.get() == Efly.ChestSwapMode.Always && player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            this.swapToChestSwap();
         } else if (this.chestSwap.get() == Efly.ChestSwapMode.WaitForGround) {
            this.enableGroundListener();
         }

         if (player.isGliding() && (Boolean)this.instaDrop.get()) {
            this.enableInstaDropListener();
         }

      }
   }

   private void swapToChestSwap() {
      ChestSwap chestSwapModule = (ChestSwap)Modules.get().get(ChestSwap.class);
      if (chestSwapModule != null) {
         chestSwapModule.swap();
      }

   }

   @EventHandler
   private void onPlayerMove(PlayerMoveEvent event) {
      PlayerEntity player = this.mc.player;
      ClientWorld world = this.mc.world;
      if (player != null && world != null) {
         if (player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) {
            this.autoTakeoff();
            this.updatePlayerAvoidance();
            if (player.isGliding()) {
               this.velX = (double)0.0F;
               this.velY = event.movement.y;
               this.velZ = (double)0.0F;
               this.forward = Vec3d.fromPolar(0.0F, player.getYaw()).multiply(0.1);
               this.right = Vec3d.fromPolar(0.0F, player.getYaw() + 90.0F).multiply(0.1);
               if (player.isTouchingWater() && (Boolean)this.stopInWater.get()) {
                  ClientPlayNetworkHandler networkHandler = this.mc.getNetworkHandler();
                  if (networkHandler != null) {
                     networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
                  }

                  return;
               }

               this.handleFallMultiplier();
               this.handleAutopilot();
               this.handleAcceleration();
               this.handleVerticalAcceleration();
               this.handleHorizontalSpeed();
               this.handleVerticalSpeed();
               this.handleLandGently();
               this.handleBuildingMode();
               this.handleMaxHeight();
               int chunkX = (int)((player.getX() + this.velX) / (double)16.0F);
               int chunkZ = (int)((player.getZ() + this.velZ) / (double)16.0F);
               if ((Boolean)this.dontGoIntoUnloadedChunks.get()) {
                  if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                     ((IVec3d)event.movement).meteor$set(this.velX, this.velY, this.velZ);
                  } else {
                     ((IVec3d)event.movement).meteor$set((double)0.0F, this.velY, (double)0.0F);
                  }
               } else {
                  ((IVec3d)event.movement).meteor$set(this.velX, this.velY, this.velZ);
               }
            } else {
               this.mappingWaitingForChunks = false;
               if (this.lastForwardPressed) {
                  if (!this.isKeyPhysicallyPressed(this.mc.options.forwardKey)) {
                     this.mc.options.forwardKey.setPressed(false);
                  }

                  this.lastForwardPressed = false;
               }
            }

            if ((Boolean)this.noCrash.get() && player.isGliding()) {
               Vec3d lookAheadPos = player.getEntityPos().add(player.getVelocity().normalize().multiply((double)(Integer)this.crashLookAhead.get()));
               RaycastContext raycastContext = new RaycastContext(player.getEntityPos(), new Vec3d(lookAheadPos.getX(), player.getY(), lookAheadPos.getZ()), ShapeType.COLLIDER, FluidHandling.NONE, player);
               BlockHitResult hitResult = world.raycast(raycastContext);
               if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                  ((IVec3d)event.movement).meteor$set((double)0.0F, this.velY, (double)0.0F);
               }
            }

         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if ((Boolean)this.autoReplenish.get()) {
         FindItemResult fireworks = InvUtils.find(new Item[]{Items.FIREWORK_ROCKET});
         if (fireworks.found() && !fireworks.isHotbar()) {
            InvUtils.move().from(fireworks.slot()).toHotbar((Integer)this.replenishSlot.get() - 1);
         }
      }

      PlayerEntity player = this.mc.player;
      if ((Boolean)this.replace.get() && player != null) {
         ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
         if (chestStack.getItem() == Items.ELYTRA && chestStack.getMaxDamage() - chestStack.getDamage() <= (Integer)this.replaceDurability.get()) {
            FindItemResult elytra = InvUtils.find((stack) -> stack.getMaxDamage() - stack.getDamage() > (Integer)this.replaceDurability.get() && stack.getItem() == Items.ELYTRA);
            InvUtils.move().from(elytra.slot()).toArmor(2);
         }
      }

   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.zeroAcceleration();
      }

   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if ((Boolean)this.mappingMode.get() && this.mappingWaitingForChunks && !(Boolean)this.mappingHideWarning.get()) {
         String text = "Efly: Waiting for chunks to load (render radius: " + String.valueOf(this.mappingRenderRadius.get()) + ")";
         int orange = -23296;
         int x = (this.mc.getWindow().getScaledWidth() - this.mc.textRenderer.getWidth(text)) / 2;
         int y = this.mc.getWindow().getScaledHeight() / 2 + 20;
         event.drawContext.drawText(this.mc.textRenderer, text, x, y, orange, true);
      }
   }

   private void autoTakeoff() {
      PlayerEntity player = this.mc.player;
      if (player != null) {
         if (this.incrementJumpTimer) {
            ++this.jumpTimer;
         }

         boolean jumpPressed = this.mc.options.jumpKey.isPressed();
         if ((Boolean)this.autoTakeOff.get() && jumpPressed) {
            if (!this.lastJumpPressed && !player.isGliding()) {
               this.jumpTimer = 0;
               this.incrementJumpTimer = true;
            }

            if (this.jumpTimer >= 8) {
               this.jumpTimer = 0;
               this.incrementJumpTimer = false;
               player.setJumping(false);
               player.setSprinting(true);
               player.jump();
               ClientPlayNetworkHandler networkHandler = this.mc.getNetworkHandler();
               if (networkHandler != null) {
                  networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
               }
            }
         }

         this.lastJumpPressed = jumpPressed;
      }
   }

   private void handleAutopilot() {
      PlayerEntity player = this.mc.player;
      if (player != null && player.isGliding()) {
         if ((Boolean)this.autoPilot.get() && !this.avoidanceSteering && player.getY() > (Double)this.autoPilotMinimumHeight.get()) {
            this.mc.options.forwardKey.setPressed(true);
            this.lastForwardPressed = true;
         }

         if ((Boolean)this.useFireworks.get()) {
            if (this.ticksLeft <= (double)0.0F) {
               this.ticksLeft = (Double)this.autoPilotFireworkDelay.get() * (double)20.0F;
               FindItemResult itemResult = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
               if (!itemResult.found()) {
                  return;
               }

               ClientPlayerInteractionManager interactionManager = this.mc.interactionManager;
               if (interactionManager == null) {
                  return;
               }

               if (itemResult.isOffhand()) {
                  interactionManager.interactItem(player, Hand.OFF_HAND);
                  player.swingHand(Hand.OFF_HAND);
               } else {
                  InvUtils.swap(itemResult.slot(), true);
                  interactionManager.interactItem(player, Hand.MAIN_HAND);
                  player.swingHand(Hand.MAIN_HAND);
                  InvUtils.swapBack();
               }
            }

            --this.ticksLeft;
         }

      }
   }

   private void handleHorizontalSpeed() {
      boolean a = false;
      boolean b = false;
      if (this.mc.options.forwardKey.isPressed()) {
         this.velX += this.forward.x * this.getSpeed() * (double)10.0F;
         this.velZ += this.forward.z * this.getSpeed() * (double)10.0F;
         a = true;
      } else if (this.mc.options.backKey.isPressed()) {
         this.velX -= this.forward.x * this.getSpeed() * (double)10.0F;
         this.velZ -= this.forward.z * this.getSpeed() * (double)10.0F;
         a = true;
      }

      if (this.mc.options.rightKey.isPressed()) {
         this.velX += this.right.x * this.getSpeed() * (double)10.0F;
         this.velZ += this.right.z * this.getSpeed() * (double)10.0F;
         b = true;
      } else if (this.mc.options.leftKey.isPressed()) {
         this.velX -= this.right.x * this.getSpeed() * (double)10.0F;
         this.velZ -= this.right.z * this.getSpeed() * (double)10.0F;
         b = true;
      }

      if (a && b) {
         double diagonal = (double)1.0F / Math.sqrt((double)2.0F);
         this.velX *= diagonal;
         this.velZ *= diagonal;
      }

      if ((Boolean)this.mappingMode.get()) {
         this.mappingWaitingForChunks = !this.areChunksLoadedInRadius((Integer)this.mappingRenderRadius.get());
         if (this.mappingWaitingForChunks) {
            this.velX = (double)0.0F;
            this.velZ = (double)0.0F;
            this.resetHorizontalAcceleration();
         }
      } else {
         this.mappingWaitingForChunks = false;
      }

   }

   private boolean areChunksLoadedInRadius(int radius) {
      if (this.mc.player != null && this.mc.world != null) {
         int centerX = (int)Math.floor(this.mc.player.getX()) >> 4;
         int centerZ = (int)Math.floor(this.mc.player.getZ()) >> 4;
         int radiusSq = radius * radius;

         for(int dx = -radius; dx <= radius; ++dx) {
            for(int dz = -radius; dz <= radius; ++dz) {
               if (dx * dx + dz * dz <= radiusSq && !this.mc.world.getChunkManager().isChunkLoaded(centerX + dx, centerZ + dz)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private void handleVerticalSpeed() {
      if (this.mc.options.jumpKey.isPressed()) {
         this.velY += (double)0.5F * this.getVerticalSpeed();
      } else if (this.mc.options.sneakKey.isPressed()) {
         this.velY -= (double)0.5F * this.getVerticalSpeed();
      }

   }

   private void handleFallMultiplier() {
      if (this.velY < (double)0.0F) {
         this.velY *= (Double)this.fallMultiplier.get();
      } else if (this.velY > (double)0.0F) {
         this.velY = (double)0.0F;
      }

   }

   private void handleLandGently() {
      if ((Boolean)this.landGently.get() && !(this.velY >= (double)0.0F)) {
         double currentSpeed = -this.velY;
         double minSpeed = (Double)this.landGentlyMinSpeed.get();
         if (!(currentSpeed <= minSpeed)) {
            double startDist = (Double)this.landGentlyDistance.get();
            double minDist = Math.min((Double)this.landGentlyMinDistance.get(), startDist);
            double distanceToGround = this.distanceToGroundBelow(startDist);
            if (!(distanceToGround >= startDist)) {
               double allowedSpeed;
               if (distanceToGround <= minDist) {
                  allowedSpeed = minSpeed;
               } else {
                  double t = (distanceToGround - minDist) / Math.max(startDist - minDist, 1.0E-4);
                  allowedSpeed = minSpeed + t * (currentSpeed - minSpeed);
               }

               if (allowedSpeed < currentSpeed) {
                  this.velY = -allowedSpeed;
               }

            }
         }
      }
   }

   private double distanceToGroundBelow(double maxDistance) {
      PlayerEntity player = this.mc.player;
      ClientWorld world = this.mc.world;
      if (player != null && world != null) {
         Box box = player.getBoundingBox();
         double y = player.getY();
         double[] xs = new double[]{box.minX, (box.minX + box.maxX) / (double)2.0F, box.maxX};
         double[] zs = new double[]{box.minZ, (box.minZ + box.maxZ) / (double)2.0F, box.maxZ};
         double nearest = maxDistance;

         for(double x : xs) {
            for(double z : zs) {
               Vec3d start = new Vec3d(x, y, z);
               Vec3d end = start.add((double)0.0F, -maxDistance, (double)0.0F);
               RaycastContext raycastContext = new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, player);
               BlockHitResult hitResult = world.raycast(raycastContext);
               if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                  double distance = y - hitResult.getPos().y;
                  if (distance < nearest) {
                     nearest = distance;
                  }
               }
            }
         }

         return nearest;
      } else {
         return maxDistance;
      }
   }

   private void handleBuildingMode() {
      if ((Boolean)this.buildingMode.get() && this.isNearAnyBlock((Double)this.buildingModeDistance.get())) {
         this.zeroAcceleration();
         double minSpeed = (Double)this.buildingModeMinSpeed.get();
         double verticalTarget = minSpeed * (double)1.5F;
         double horizontalSpeed = Math.hypot(this.velX, this.velZ);
         double verticalSpeed = Math.abs(this.velY);
         if (!this.buildingModeEngaged) {
            this.buildingModeEntryHorizontalSpeed = Math.max(horizontalSpeed, minSpeed);
            this.buildingModeEntryVerticalSpeed = Math.max(verticalSpeed, verticalTarget);
            this.buildingModeTicksElapsed = 0;
            this.buildingModeEngaged = true;
         } else {
            ++this.buildingModeTicksElapsed;
         }

         double t = Math.min((double)1.0F, (double)this.buildingModeTicksElapsed / (double)5.0F);
         double buildingModeHorizontalCap = this.buildingModeEntryHorizontalSpeed + (minSpeed - this.buildingModeEntryHorizontalSpeed) * t;
         double buildingModeVerticalCap = this.buildingModeEntryVerticalSpeed + (verticalTarget - this.buildingModeEntryVerticalSpeed) * t;
         if (horizontalSpeed > buildingModeHorizontalCap) {
            double scale = buildingModeHorizontalCap / horizontalSpeed;
            this.velX *= scale;
            this.velZ *= scale;
         }

         if (verticalSpeed > buildingModeVerticalCap) {
            this.velY = Math.signum(this.velY) * buildingModeVerticalCap;
         }

      } else {
         this.buildingModeEngaged = false;
      }
   }

   private boolean isNearAnyBlock(double distance) {
      PlayerEntity player = this.mc.player;
      ClientWorld world = this.mc.world;
      if (player != null && world != null) {
         Box box = player.getBoundingBox().expand(distance);
         return world.getBlockCollisions(player, box).iterator().hasNext();
      } else {
         return false;
      }
   }

   private void handleMaxHeight() {
      if ((Boolean)this.limitMaxHeight.get() && !(this.velY <= (double)0.0F)) {
         PlayerEntity player = this.mc.player;
         if (player != null) {
            double limit = (Double)this.maxHeight.get();
            double currentY = player.getY();
            if (currentY >= limit) {
               this.velY = (double)0.0F;
            } else if (currentY + this.velY > limit) {
               this.velY = limit - currentY;
            }

         }
      }
   }

   private void handleAcceleration() {
      boolean movementKeyPressed = this.mc.options.forwardKey.isPressed() || this.mc.options.backKey.isPressed() || this.mc.options.leftKey.isPressed() || this.mc.options.rightKey.isPressed();
      if (!movementKeyPressed) {
         this.resetHorizontalAcceleration();
      } else if (!this.atMaxSpeed) {
         if (this.accelerationDelayTicks < (Integer)this.accelerationDelay.get()) {
            ++this.accelerationDelayTicks;
         } else {
            double plateau = (Double)this.accelerationPlateau.get();
            double remainingToPlateau = Math.max((double)0.0F, plateau - this.acceleration);
            double gain = (Double)this.accelerationStep.get() * (remainingToPlateau / plateau);
            this.acceleration = Math.min(this.acceleration + gain, (Double)this.horizontalSpeed.get());
            if (this.acceleration >= (Double)this.horizontalSpeed.get()) {
               this.atMaxSpeed = true;
            }

         }
      }
   }

   private void zeroAcceleration() {
      this.resetHorizontalAcceleration();
      this.atMaxVerticalSpeed = false;
      this.verticalAccelerationDelayTicks = 0;
      this.verticalAcceleration = (Double)this.verticalStartSpeed.get();
   }

   private void resetHorizontalAcceleration() {
      this.atMaxSpeed = false;
      this.accelerationDelayTicks = 0;
      this.acceleration = (Double)this.startSpeed.get();
   }

   private double getSpeed() {
      return this.acceleration;
   }

   private void handleVerticalAcceleration() {
      boolean movingUp = this.mc.options.jumpKey.isPressed();
      boolean movingDown = this.mc.options.sneakKey.isPressed();
      if (!movingUp && !movingDown) {
         this.atMaxVerticalSpeed = false;
         this.verticalAccelerationDelayTicks = 0;
         this.verticalAcceleration = (Double)this.verticalStartSpeed.get();
      } else if (movingUp && !(Boolean)this.accelerateUpward.get()) {
         this.atMaxVerticalSpeed = false;
         this.verticalAccelerationDelayTicks = 0;
         this.verticalAcceleration = (Double)this.verticalStartSpeed.get();
      } else if (!this.atMaxVerticalSpeed) {
         if (this.verticalAccelerationDelayTicks < (Integer)this.accelerationDelay.get()) {
            ++this.verticalAccelerationDelayTicks;
         } else {
            double plateau = (Double)this.verticalAccelerationPlateau.get();
            double remainingToPlateau = Math.max((double)0.0F, plateau - this.verticalAcceleration);
            double gain = (Double)this.verticalAccelerationStep.get() * (remainingToPlateau / plateau);
            this.verticalAcceleration = Math.min(this.verticalAcceleration + gain, (Double)this.verticalSpeed.get());
            if (this.verticalAcceleration >= (Double)this.verticalSpeed.get()) {
               this.atMaxVerticalSpeed = true;
            }

         }
      }
   }

   private double getVerticalSpeed() {
      return this.verticalAcceleration;
   }

   private void updatePlayerAvoidance() {
      if (!(Boolean)this.playerAvoidance.get()) {
         this.releaseAvoidance();
         this.releaseVerticalStep();
      } else if (!this.isMovementKeyPhysicallyPressed() && !this.isKeyPhysicallyPressed(this.mc.options.sneakKey)) {
         Vec3d away = this.findAvoidanceAwayVector();
         if (away == null) {
            this.releaseAvoidance();
            this.releaseVerticalStep();
         } else {
            PlayerEntity player = this.mc.player;
            if (player != null && player.isGliding()) {
               if (this.verticalStepActive) {
                  this.avoidanceSteering = true;
                  this.stepVerticalStep();
               } else {
                  this.steerTowardsDirection(away);
                  this.avoidanceSteering = true;
                  if ((Boolean)this.avoidanceVerticalStep.get()) {
                     this.updateAvoidanceStuckDetection();
                  }

               }
            }
         }
      } else {
         this.releaseAvoidanceExceptPhysical();
         this.releaseVerticalStep();
      }
   }

   private void steerTowardsDirection(Vec3d dir) {
      PlayerEntity player = this.mc.player;
      if (player != null) {
         double targetYaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
         double relative = wrapDegrees(targetYaw - (double)player.getYaw());
         int octant = ((int)Math.round(relative / (double)45.0F) % 8 + 8) % 8;
         this.mc.options.forwardKey.setPressed(octant == 0 || octant == 1 || octant == 7);
         this.mc.options.backKey.setPressed(octant == 3 || octant == 4 || octant == 5);
         this.mc.options.rightKey.setPressed(octant == 1 || octant == 2 || octant == 3);
         this.mc.options.leftKey.setPressed(octant == 5 || octant == 6 || octant == 7);
      }
   }

   private Vec3d findAvoidanceAwayVector() {
      PlayerEntity self = this.mc.player;
      ClientWorld world = this.mc.world;
      if (self != null && world != null) {
         double[] acc = new double[]{(double)0.0F, (double)0.0F};
         boolean foundThreat = false;

         for(PlayerEntity player : world.getPlayers()) {
            if (player != self && (!(Boolean)this.avoidanceIgnoreFriends.get() || !Friends.get().isFriend(player)) && this.accumulateThreat(player.getEntityPos(), (Double)this.avoidanceRadius.get(), acc)) {
               foundThreat = true;
            }
         }

         if ((Boolean)this.avoidWitherSkulls.get() || (Boolean)this.avoidArrows.get()) {
            for(Entity entity : world.getEntities()) {
               if ((Boolean)this.avoidWitherSkulls.get() && entity instanceof WitherSkullEntity) {
                  if (this.accumulateThreat(entity.getEntityPos(), (Double)this.witherSkullRadius.get(), acc)) {
                     foundThreat = true;
                  }
               } else if ((Boolean)this.avoidArrows.get() && entity instanceof ArrowEntity && this.accumulateThreat(entity.getEntityPos(), (Double)this.arrowRadius.get(), acc)) {
                  foundThreat = true;
               }
            }
         }

         if ((Boolean)this.avoidBlocks.get() && this.isBlockAvoidanceTriggered() && this.accumulateNearbyBlockThreats((Double)this.blockAvoidanceRadius.get(), acc)) {
            foundThreat = true;
         }

         if (!foundThreat) {
            return null;
         } else {
            Vec3d direction = new Vec3d(acc[0], (double)0.0F, acc[1]);
            if (direction.lengthSquared() == (double)0.0F) {
               return null;
            } else {
               direction = direction.normalize();
               if ((Boolean)this.avoidanceLateral.get()) {
                  direction = this.lateralize(direction);
               } else {
                  this.avoidanceLateralDir = null;
               }

               return direction;
            }
         }
      } else {
         return null;
      }
   }

   private boolean isPlayerWithinAvoidanceRadius() {
      PlayerEntity self = this.mc.player;
      ClientWorld world = this.mc.world;
      if (self != null && world != null) {
         double radius = (Double)this.avoidanceRadius.get();
         if (radius <= (double)0.0F) {
            return false;
         } else {
            double radiusSq = radius * radius;

            for(PlayerEntity player : world.getPlayers()) {
               if (player != self && (!(Boolean)this.avoidanceIgnoreFriends.get() || !Friends.get().isFriend(player)) && self.getEntityPos().squaredDistanceTo(player.getEntityPos()) < radiusSq) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isWitherSkullWithinAvoidanceRadius() {
      if (!(Boolean)this.avoidWitherSkulls.get()) {
         return false;
      } else {
         PlayerEntity self = this.mc.player;
         ClientWorld world = this.mc.world;
         if (self != null && world != null) {
            double radius = (Double)this.witherSkullRadius.get();
            if (radius <= (double)0.0F) {
               return false;
            } else {
               double radiusSq = radius * radius;

               for(Entity entity : world.getEntities()) {
                  if (entity instanceof WitherSkullEntity && self.getEntityPos().squaredDistanceTo(entity.getEntityPos()) < radiusSq) {
                     return true;
                  }
               }

               return false;
            }
         } else {
            return false;
         }
      }
   }

   private boolean isBlockAvoidanceTriggered() {
      return this.isPlayerWithinAvoidanceRadius() || this.isWitherSkullWithinAvoidanceRadius();
   }

   private Vec3d lateralize(Vec3d away) {
      Vec3d perpA = new Vec3d(-away.z, (double)0.0F, away.x);
      Vec3d perpB = new Vec3d(away.z, (double)0.0F, -away.x);
      boolean sideA;
      if (this.avoidanceLateralDir != null) {
         sideA = this.avoidanceLateralDir.dotProduct(perpA) >= this.avoidanceLateralDir.dotProduct(perpB);
      } else {
         PlayerEntity player = this.mc.player;
         Vec3d vel = player != null ? player.getVelocity() : Vec3d.ZERO;
         Vec3d horizontalVel = new Vec3d(vel.x, (double)0.0F, vel.z);
         sideA = !(horizontalVel.lengthSquared() > 1.0E-4) || !(horizontalVel.dotProduct(perpB) > horizontalVel.dotProduct(perpA));
      }

      double baseAngle = sideA ? (double)90.0F : (double)-90.0F;
      Vec3d optionA = this.rotateHorizontal(away, baseAngle - (double)10.0F);
      Vec3d optionB = this.rotateHorizontal(away, baseAngle + (double)10.0F);
      Vec3d chosen = optionA.dotProduct(away) >= optionB.dotProduct(away) ? optionA : optionB;
      this.avoidanceLateralDir = chosen;
      return chosen;
   }

   private Vec3d rotateHorizontal(Vec3d v, double degrees) {
      double rad = Math.toRadians(degrees);
      double cos = Math.cos(rad);
      double sin = Math.sin(rad);
      return new Vec3d(v.x * cos - v.z * sin, (double)0.0F, v.x * sin + v.z * cos);
   }

   private boolean accumulateThreat(Vec3d threatPos, double radius, double[] acc) {
      if (radius <= (double)0.0F) {
         return false;
      } else {
         PlayerEntity player = this.mc.player;
         if (player == null) {
            return false;
         } else {
            double distanceSq = player.getEntityPos().squaredDistanceTo(threatPos);
            if (!(distanceSq >= radius * radius) && distanceSq != (double)0.0F) {
               Vec3d awayFromThreat = player.getEntityPos().subtract(threatPos);
               awayFromThreat = new Vec3d(awayFromThreat.x, (double)0.0F, awayFromThreat.z);
               if (awayFromThreat.lengthSquared() == (double)0.0F) {
                  return false;
               } else {
                  double weight = (double)1.0F - Math.sqrt(distanceSq) / radius;
                  awayFromThreat = awayFromThreat.normalize().multiply(weight);
                  acc[0] += awayFromThreat.x;
                  acc[1] += awayFromThreat.z;
                  return true;
               }
            } else {
               return false;
            }
         }
      }
   }

   private boolean accumulateNearbyBlockThreats(double radius, double[] acc) {
      if (radius <= (double)0.0F) {
         return false;
      } else {
         PlayerEntity player = this.mc.player;
         ClientWorld world = this.mc.world;
         if (player != null && world != null) {
            boolean foundThreat = false;
            BlockPos center = player.getBlockPos();
            int r = (int)Math.ceil(radius);
            BlockPos.Mutable pos = new BlockPos.Mutable();

            for(int dx = -r; dx <= r; ++dx) {
               for(int dy = 0; dy <= r; ++dy) {
                  for(int dz = -r; dz <= r; ++dz) {
                     pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                     BlockState state = world.getBlockState(pos);
                     boolean isCobweb = state.isOf(Blocks.COBWEB);
                     if (isCobweb || !state.getCollisionShape(world, pos).isEmpty()) {
                        Vec3d blockCenter = new Vec3d((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)0.5F, (double)pos.getZ() + (double)0.5F);
                        if (this.accumulateThreat(blockCenter, radius, acc)) {
                           foundThreat = true;
                        }
                     }
                  }
               }
            }

            return foundThreat;
         } else {
            return false;
         }
      }
   }

   private void updateAvoidanceStuckDetection() {
      PlayerEntity player = this.mc.player;
      if (player != null) {
         this.avoidanceStuckTicksCount = player.horizontalCollision ? this.avoidanceStuckTicksCount + 1 : 0;
         if (this.avoidanceStuckTicksCount >= (Integer)this.avoidanceStuckTicks.get()) {
            this.beginVerticalStep();
         }
      }
   }

   private void beginVerticalStep() {
      PlayerEntity player = this.mc.player;
      if (player != null) {
         boolean upClear = this.hasVerticalClearance((double)1.0F);
         boolean downClear = this.hasVerticalClearance((double)-1.0F);
         if (upClear) {
            this.verticalStepUp = true;
         } else {
            if (!downClear) {
               this.avoidanceStuckTicksCount = 0;
               return;
            }

            this.verticalStepUp = false;
         }

         this.verticalStepActive = true;
         this.verticalStepStartY = player.getY();
         this.verticalStepTicks = 0;
         this.avoidanceStuckTicksCount = 0;
         this.releaseHorizontalKeys();
      }
   }

   private boolean hasVerticalClearance(double dy) {
      PlayerEntity player = this.mc.player;
      ClientWorld world = this.mc.world;
      if (player != null && world != null) {
         Box box = player.getBoundingBox().offset((double)0.0F, dy, (double)0.0F);
         return !world.getBlockCollisions(player, box).iterator().hasNext();
      } else {
         return false;
      }
   }

   private void stepVerticalStep() {
      PlayerEntity player = this.mc.player;
      if (player == null) {
         this.releaseVerticalStep();
      } else {
         ++this.verticalStepTicks;
         double traveled = Math.abs(player.getY() - this.verticalStepStartY);
         if (!(traveled >= (double)1.0F) && this.verticalStepTicks <= 40) {
            if (!this.isKeyPhysicallyPressed(this.mc.options.jumpKey)) {
               this.mc.options.jumpKey.setPressed(this.verticalStepUp);
            }

            if (!this.isKeyPhysicallyPressed(this.mc.options.sneakKey)) {
               this.mc.options.sneakKey.setPressed(!this.verticalStepUp);
            }

         } else {
            this.releaseVerticalStep();
         }
      }
   }

   private void releaseVerticalStep() {
      this.avoidanceStuckTicksCount = 0;
      if (this.verticalStepActive) {
         if (!this.isKeyPhysicallyPressed(this.mc.options.jumpKey)) {
            this.mc.options.jumpKey.setPressed(false);
         }

         if (!this.isKeyPhysicallyPressed(this.mc.options.sneakKey)) {
            this.mc.options.sneakKey.setPressed(false);
         }
      }

      this.verticalStepActive = false;
      this.verticalStepTicks = 0;
   }

   private void releaseHorizontalKeys() {
      if (!this.isKeyPhysicallyPressed(this.mc.options.forwardKey)) {
         this.mc.options.forwardKey.setPressed(false);
      }

      if (!this.isKeyPhysicallyPressed(this.mc.options.backKey)) {
         this.mc.options.backKey.setPressed(false);
      }

      if (!this.isKeyPhysicallyPressed(this.mc.options.leftKey)) {
         this.mc.options.leftKey.setPressed(false);
      }

      if (!this.isKeyPhysicallyPressed(this.mc.options.rightKey)) {
         this.mc.options.rightKey.setPressed(false);
      }

   }

   private void releaseAvoidance() {
      this.avoidanceLateralDir = null;
      if (this.avoidanceSteering) {
         if (!this.isKeyPhysicallyPressed(this.mc.options.forwardKey)) {
            this.mc.options.forwardKey.setPressed(false);
         }

         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.avoidanceSteering = false;
      }
   }

   private void releaseAvoidanceExceptPhysical() {
      this.avoidanceLateralDir = null;
      if (this.avoidanceSteering) {
         if (!this.isKeyPhysicallyPressed(this.mc.options.forwardKey)) {
            this.mc.options.forwardKey.setPressed(false);
         }

         if (!this.isKeyPhysicallyPressed(this.mc.options.backKey)) {
            this.mc.options.backKey.setPressed(false);
         }

         if (!this.isKeyPhysicallyPressed(this.mc.options.leftKey)) {
            this.mc.options.leftKey.setPressed(false);
         }

         if (!this.isKeyPhysicallyPressed(this.mc.options.rightKey)) {
            this.mc.options.rightKey.setPressed(false);
         }

         this.avoidanceSteering = false;
      }
   }

   private boolean isMovementKeyPhysicallyPressed() {
      return this.isKeyPhysicallyPressed(this.mc.options.forwardKey) || this.isKeyPhysicallyPressed(this.mc.options.backKey) || this.isKeyPhysicallyPressed(this.mc.options.leftKey) || this.isKeyPhysicallyPressed(this.mc.options.rightKey);
   }

   private boolean isKeyPhysicallyPressed(KeyBinding binding) {
      if (ForeverForward.isHolding(binding)) {
         return true;
      } else if (this.mc.getWindow() == null) {
         return binding.isPressed();
      } else {
         InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
         return key.getCategory() != InputUtil.Type.KEYSYM ? binding.isPressed() : InputUtil.isKeyPressed(this.mc.getWindow(), key.getCode());
      }
   }

   private static double wrapDegrees(double degrees) {
      double wrapped = degrees % (double)360.0F;
      if (wrapped >= (double)180.0F) {
         wrapped -= (double)360.0F;
      }

      if (wrapped < (double)-180.0F) {
         wrapped += (double)360.0F;
      }

      return wrapped;
   }

   protected void enableGroundListener() {
      MeteorClient.EVENT_BUS.subscribe(this.staticGroundListener);
   }

   protected void disableGroundListener() {
      MeteorClient.EVENT_BUS.unsubscribe(this.staticGroundListener);
   }

   protected void enableInstaDropListener() {
      MeteorClient.EVENT_BUS.subscribe(this.staticInstadropListener);
   }

   protected void disableInstaDropListener() {
      MeteorClient.EVENT_BUS.unsubscribe(this.staticInstadropListener);
   }

   private class StaticGroundListener {
      @EventHandler
      private void chestSwapGroundListener(PlayerMoveEvent event) {
         PlayerEntity player = Efly.this.mc.player;
         if (player != null && player.isOnGround()) {
            if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
               Efly.this.swapToChestSwap();
               Efly.this.disableGroundListener();
            }

         }
      }
   }

   private class StaticInstaDropListener {
      @EventHandler
      private void onInstadropTick(TickEvent.Post event) {
         ClientPlayerEntity player = Efly.this.mc.player;
         if (player != null && player.isGliding()) {
            player.setVelocity((double)0.0F, (double)0.0F, (double)0.0F);
            player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
         } else {
            Efly.this.disableInstaDropListener();
         }

      }
   }

   public static enum ChestSwapMode {
      Always,
      Never,
      WaitForGround;

      private static ChestSwapMode[] $values() {
         return new ChestSwapMode[]{Always, Never, WaitForGround};
      }
   }
}
