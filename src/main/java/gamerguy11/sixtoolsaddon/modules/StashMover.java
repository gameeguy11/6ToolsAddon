package gamerguy11.sixtoolsaddon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.accessor.InputAccessor;
import gamerguy11.sixtoolsaddon.mixin.accessor.PlayerInventoryAccessor;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.ChestType;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.block.BarrelBlock;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Mode;

public class StashMover extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgInput;
   private final SettingGroup sgPearl;
   private final SettingGroup sgGoBack;
   private final SettingGroup sgResetPearl;
   private final SettingGroup sgDelays;
   private final Setting<Double> containerReach;
   private final SettingGroup sgRendering;
   private final Setting<Boolean> pauseOnLag;
   private final Setting<Integer> maxRetries;
   private final Setting<BlockPos> outputPearlPickupPos;
   private final Setting<BlockPos> outputPearlThrowPos;
   private final Setting<Boolean> debugMode;
   private final Setting<Boolean> onlyShulkers;
   private final Setting<Boolean> breakEmptyContainers;
   private final Setting<Boolean> fillEnderChest;
   private final Setting<String> pearlPlayerName;
   private final Setting<String> pearlCommand;
   private final Setting<Integer> pearlTimeout;
   private final Setting<Integer> pearlRetryDelay;
   private final Setting<OutputTravelMethod> outputTravelMethod;
   private final Setting<GoBackMethod> goBackMethod;
   private final Setting<Double> homeCommandCooldown;
   private final Setting<Double> homeTeleportWait;
   private long lastHomeCommandSentTime;
   private final Setting<String> homeNameToOutput;
   private final Setting<String> tpaTargetOutput;
   private final Setting<String> goBackPlayerName;
   private final Setting<String> goBackCommand;
   private final Setting<String> homeNameToInput;
   private final Setting<String> tpaTargetInput;
   private final Setting<Double> outputPearlThrowPitch;
   private final Setting<Double> outputPearlThrowYaw;
   private final Setting<BlockPos> inputPearlPickupPos;
   private final Setting<BlockPos> inputPearlThrowPos;
   private final Setting<Double> inputPearlThrowPitch;
   private final Setting<Double> inputPearlThrowYaw;
   private final Setting<Integer> pearlWaitTime;
   private final Setting<Double> positionTolerance;
   private final Setting<Double> trapdoorEdgeDistance;
   private final Setting<Integer> openDelay;
   private final Setting<Double> transferDelay;
   private final Setting<Integer> closeDelay;
   private final Setting<Integer> moveDelay;
   private final Setting<Boolean> renderSelection;
   private final Setting<Integer> outlineWidth;
   private final Setting<SettingColor> inputAreaColor;
   private final Setting<SettingColor> outputAreaColor;
   private final Setting<SettingColor> inputContainerColor;
   private final Setting<SettingColor> outputContainerColor;
   private final Setting<SettingColor> activeContainerColor;
   private final Setting<SettingColor> emptyContainerColor;
   private final Setting<SettingColor> fullContainerColor;
   private ProcessState currentState;
   private ProcessState lastDebugState;
   private int stateTimer;
   private int retryCount;
   private long lastActionTime;
   private boolean isSelecting;
   private static BlockPos inputAreaPos1 = null;
   private static BlockPos inputAreaPos2 = null;
   private static BlockPos outputAreaPos1 = null;
   private static BlockPos outputAreaPos2 = null;
   private static BlockPos selectionPos1 = null;
   private static SelectionMode selectionMode;
   private static final Set<ContainerInfo> inputContainers;
   private static final Set<ContainerInfo> outputContainers;
   private ContainerInfo currentContainer;
   private BlockPos enderChestPos;
   private Direction approachDirection;
   private BlockPos lastBaritoneGoal;
   private int containerOpenFailures;
   private int pathfindingFailures;
   private Vec3d lastPlayerPos;
   private int stuckCounter;
   private int stuckRecoveryAttempts;
   private int jumpTimer;
   private long lastPearlMessageTime;
   private String lastRandomString;
   private boolean waitingForPearl;
   private int pearlRetryCount;
   private Vec3d initialPlayerPos;
   private boolean hasThrownPearl;
   private long pearlThrowTime;
   private boolean hasPlacedShulker;
   private boolean isGoingToInput;
   private ItemStack offhandBackup;
   private int pearlFailRetries;
   private int previousSlot;
   private int rotationStabilizationTimer;
   private boolean rotationSet;
   private BlockPos safeRetreatPos;
   private boolean waitingForRespawn;
   private long lastKillTime;
   private int killRetryCount;
   private int itemsTransferred;
   private int containersProcessed;
   private boolean inventoryFull;
   private boolean enderChestFull;
   private boolean enderChestHasItems;
   private boolean enderChestEmptied;
   private static StashMover INSTANCE;

   private boolean homeCommandOnCooldown() {
      return (double)(System.currentTimeMillis() - this.lastHomeCommandSentTime) < (Double)this.homeCommandCooldown.get() * (double)1000.0F;
   }

   private int transferDelayTicks() {
      return (int)Math.round((Double)this.transferDelay.get() * (double)20.0F);
   }

   public StashMover() {
      super(SixToolsAddon.CATEGORY, "stash-mover", "Automatically moves items between stash areas using pearl loading");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgInput = this.settings.createGroup("Input");
      this.sgPearl = this.settings.createGroup("Travel To Output");
      this.sgGoBack = this.settings.createGroup("Go Back");
      this.sgResetPearl = this.settings.createGroup("Reset Pearl");
      this.sgDelays = this.settings.createGroup("Delays");
      this.containerReach = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("container-reach")).description("Maximum reach distance for opening containers")).defaultValue((double)4.0F).min((double)2.5F).max((double)5.0F).sliderRange((double)2.5F, (double)5.0F).build());
      this.sgRendering = this.settings.createGroup("Rendering");
      this.pauseOnLag = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("pause-on-lag")).description("Pause when server is lagging")).defaultValue(true)).build());
      this.maxRetries = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("max-retries")).description("Maximum retries for failed actions")).defaultValue(3)).min(1).max(10).build());
      this.outputPearlPickupPos = this.sgGeneral.add(((BlockPosSetting.Builder)((BlockPosSetting.Builder)((BlockPosSetting.Builder)(new BlockPosSetting.Builder()).name("output-pickup-pos")).description("Position for pearl pickup at output")).defaultValue(new BlockPos(0, 64, 0))).build());
      this.outputPearlThrowPos = this.sgGeneral.add(((BlockPosSetting.Builder)((BlockPosSetting.Builder)((BlockPosSetting.Builder)(new BlockPosSetting.Builder()).name("output-throw-pos")).description("Position for pearl throw at output")).defaultValue(new BlockPos(0, 64, 0))).build());
      this.debugMode = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("debug-mode")).description("Show debug messages and state transitions")).defaultValue(false)).build());
      this.onlyShulkers = this.sgInput.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("only-shulkers")).description("Only take shulker boxes from input chests")).defaultValue(true)).build());
      this.breakEmptyContainers = this.sgInput.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("break-empty")).description("Break empty containers after emptying them")).defaultValue(false)).build());
      this.fillEnderChest = this.sgInput.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("fill-enderchest")).description("Fill ender chest to maximize movement")).defaultValue(true)).build());
      this.pearlPlayerName = this.sgPearl.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("pearl-player")).description("Player name to message for pearl loading (Input→Output)")).defaultValue("PlayerName")).build());
      this.pearlCommand = this.sgPearl.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("pearl-command")).description("Command to send for pearl loading (Input→Output)")).defaultValue("pearl")).build());
      this.pearlTimeout = this.sgPearl.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("pearl-timeout")).description("Timeout for pearl loading in seconds")).defaultValue(10)).min(5).max(30).build());
      this.pearlRetryDelay = this.sgPearl.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("pearl-retry-delay")).description("Delay between pearl command retries in ticks")).defaultValue(100)).min(20).max(200).build());
      this.outputTravelMethod = this.sgPearl.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("output-travel-method")).description("How to travel from input to output area.")).defaultValue(StashMover.OutputTravelMethod.PEARL)).build());
      this.goBackMethod = this.sgGoBack.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("go-back-method")).description("Method to go back to input area")).defaultValue(StashMover.GoBackMethod.PEARL)).build());
      this.homeCommandCooldown = this.sgPearl.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("home-command-cooldown")).description("Minimum seconds to wait between sending /home commands (matches your server's home cooldown).")).defaultValue((double)0.0F).min((double)0.0F).max((double)600.0F).sliderMax((double)600.0F).visible(() -> this.outputTravelMethod.get() == StashMover.OutputTravelMethod.HOME || this.goBackMethod.get() == StashMover.GoBackMethod.HOME)).build());
      this.homeTeleportWait = this.sgPearl.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("home-teleport-wait")).description("How long to wait for the /home or /tpa teleport to actually happen (e.g. your server's teleport warmup) before considering it timed out and retrying.")).defaultValue((double)15.0F).min((double)0.0F).max((double)60.0F).sliderRange((double)0.0F, (double)60.0F).visible(() -> this.outputTravelMethod.get() == StashMover.OutputTravelMethod.HOME || this.outputTravelMethod.get() == StashMover.OutputTravelMethod.TPA || this.goBackMethod.get() == StashMover.GoBackMethod.HOME || this.goBackMethod.get() == StashMover.GoBackMethod.TPA)).build());
      this.lastHomeCommandSentTime = 0L;
      this.homeNameToOutput = this.sgPearl.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("home-name-output")).description("Home name used with /home when traveling to output.")).defaultValue("output")).visible(() -> this.outputTravelMethod.get() == StashMover.OutputTravelMethod.HOME)).build());
      this.tpaTargetOutput = this.sgPearl.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("tpa-target-output")).description("Player name for /tpa when traveling to output.")).defaultValue("PlayerName")).visible(() -> this.outputTravelMethod.get() == StashMover.OutputTravelMethod.TPA)).build());
      this.goBackPlayerName = this.sgGoBack.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("go-back-player")).description("Player name for go back pearl loading (Output→Input)")).defaultValue("PlayerName")).visible(() -> this.goBackMethod.get() == StashMover.GoBackMethod.PEARL)).build());
      this.goBackCommand = this.sgGoBack.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("go-back-command")).description("Command for go back pearl loading (Output→Input)")).defaultValue("back")).visible(() -> this.goBackMethod.get() == StashMover.GoBackMethod.PEARL)).build());
      this.homeNameToInput = this.sgGoBack.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("home-name-input")).description("Home name used with /home when going back to input.")).defaultValue("input")).visible(() -> this.goBackMethod.get() == StashMover.GoBackMethod.HOME)).build());
      this.tpaTargetInput = this.sgGoBack.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("tpa-target-input")).description("Player name for /tpa when going back to input.")).defaultValue("PlayerName")).visible(() -> this.goBackMethod.get() == StashMover.GoBackMethod.TPA)).build());
      this.outputPearlThrowPitch = this.sgResetPearl.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("output-throw-pitch")).description("Pitch for throwing pearl at output (90 = straight down)")).defaultValue((double)90.0F).sliderRange((double)-90.0F, (double)90.0F).build());
      this.outputPearlThrowYaw = this.sgResetPearl.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("output-throw-yaw")).description("Yaw for throwing pearl at output")).defaultValue((double)0.0F).sliderRange((double)-180.0F, (double)180.0F).build());
      this.inputPearlPickupPos = this.sgResetPearl.add(((BlockPosSetting.Builder)((BlockPosSetting.Builder)((BlockPosSetting.Builder)((BlockPosSetting.Builder)(new BlockPosSetting.Builder()).name("input-pickup-pos")).description("Position for pearl pickup at input")).defaultValue(new BlockPos(0, 64, 0))).visible(() -> this.goBackMethod.get() == StashMover.GoBackMethod.PEARL)).build());
      this.inputPearlThrowPos = this.sgResetPearl.add(((BlockPosSetting.Builder)((BlockPosSetting.Builder)((BlockPosSetting.Builder)((BlockPosSetting.Builder)(new BlockPosSetting.Builder()).name("input-throw-pos")).description("Position for pearl throw at input")).defaultValue(new BlockPos(0, 64, 0))).visible(() -> this.goBackMethod.get() == StashMover.GoBackMethod.PEARL)).build());
      this.inputPearlThrowPitch = this.sgResetPearl.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("input-throw-pitch")).description("Pitch for throwing pearl at input (90 = straight down)")).defaultValue((double)90.0F).sliderRange((double)-90.0F, (double)90.0F).visible(() -> this.goBackMethod.get() == StashMover.GoBackMethod.PEARL)).build());
      this.inputPearlThrowYaw = this.sgResetPearl.add(((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("input-throw-yaw")).description("Yaw for throwing pearl at input")).defaultValue((double)0.0F).sliderRange((double)-180.0F, (double)180.0F).visible(() -> this.goBackMethod.get() == StashMover.GoBackMethod.PEARL)).build());
      this.pearlWaitTime = this.sgResetPearl.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("pearl-wait-time")).description("Time to wait after throwing pearl (seconds)")).defaultValue(5)).min(1).max(10).build());
      this.positionTolerance = this.sgResetPearl.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("position-tolerance")).description("How close to target position before throwing pearl (blocks)")).defaultValue(0.3).min(0.1).max((double)2.0F).sliderRange(0.1, (double)2.0F).build());
      this.trapdoorEdgeDistance = this.sgResetPearl.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("trapdoor-edge-distance")).description("Distance from trapdoor edge when positioning (blocks)")).defaultValue(0.4).min(0.2).max((double)1.0F).sliderRange(0.2, (double)1.0F).build());
      this.openDelay = this.sgDelays.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("open-delay")).description("Delay after opening container in ticks")).defaultValue(10)).min(5).max(30).build());
      this.transferDelay = this.sgDelays.add(((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("transfer-delay")).description("Delay between item transfers in seconds")).defaultValue(0.1).min((double)0.0F).max((double)600.0F).sliderMax((double)600.0F).build());
      this.closeDelay = this.sgDelays.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("close-delay")).description("Delay after closing container in ticks")).defaultValue(5)).min(0).max(20).build());
      this.moveDelay = this.sgDelays.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("move-delay")).description("Delay between movements in ticks")).defaultValue(20)).min(5).max(50).build());
      this.renderSelection = this.sgRendering.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("render-selection")).description("Render selection areas")).defaultValue(true)).build());
      this.outlineWidth = this.sgRendering.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("outline-width")).description("Width of area outlines")).defaultValue(2)).min(1).max(5).sliderRange(1, 5).build());
      this.inputAreaColor = this.sgRendering.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("input-area-outline")).description("Outline color for input area")).defaultValue(new SettingColor(0, 255, 0, 255)).build());
      this.outputAreaColor = this.sgRendering.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("output-area-outline")).description("Outline color for output area")).defaultValue(new SettingColor(0, 100, 255, 255)).build());
      this.inputContainerColor = this.sgRendering.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("input-container-color")).description("Color for input containers (not empty)")).defaultValue(new SettingColor(0, 255, 0, 100)).build());
      this.outputContainerColor = this.sgRendering.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("output-container-color")).description("Color for output containers (not full)")).defaultValue(new SettingColor(0, 100, 255, 100)).build());
      this.activeContainerColor = this.sgRendering.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("active-container-color")).description("Color for currently active container")).defaultValue(new SettingColor(255, 255, 0, 150)).build());
      this.emptyContainerColor = this.sgRendering.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("empty-container-color")).description("Color for empty containers")).defaultValue(new SettingColor(128, 128, 128, 50)).build());
      this.fullContainerColor = this.sgRendering.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("full-container-color")).description("Color for full containers")).defaultValue(new SettingColor(255, 0, 0, 50)).build());
      this.currentState = StashMover.ProcessState.IDLE;
      this.lastDebugState = StashMover.ProcessState.IDLE;
      this.stateTimer = 0;
      this.retryCount = 0;
      this.lastActionTime = 0L;
      this.isSelecting = false;
      this.currentContainer = null;
      this.enderChestPos = null;
      this.approachDirection = null;
      this.lastBaritoneGoal = null;
      this.containerOpenFailures = 0;
      this.pathfindingFailures = 0;
      this.lastPlayerPos = null;
      this.stuckCounter = 0;
      this.stuckRecoveryAttempts = 0;
      this.jumpTimer = 0;
      this.lastPearlMessageTime = 0L;
      this.lastRandomString = "";
      this.waitingForPearl = false;
      this.pearlRetryCount = 0;
      this.initialPlayerPos = null;
      this.hasThrownPearl = false;
      this.pearlThrowTime = 0L;
      this.hasPlacedShulker = false;
      this.isGoingToInput = false;
      this.offhandBackup = ItemStack.EMPTY;
      this.pearlFailRetries = 0;
      this.previousSlot = -1;
      this.rotationStabilizationTimer = 0;
      this.rotationSet = false;
      this.safeRetreatPos = null;
      this.waitingForRespawn = false;
      this.lastKillTime = 0L;
      this.killRetryCount = 0;
      this.itemsTransferred = 0;
      this.containersProcessed = 0;
      this.inventoryFull = false;
      this.enderChestFull = false;
      this.enderChestHasItems = false;
      this.enderChestEmptied = false;
      INSTANCE = this;
   }

   public void onActivate() {
      this.stateTimer = 0;
      this.retryCount = 0;
      this.itemsTransferred = 0;
      this.containersProcessed = 0;
      this.inventoryFull = false;
      this.enderChestFull = false;
      this.currentContainer = null;
      this.waitingForPearl = false;
      this.pearlRetryCount = 0;
      this.containerOpenFailures = 0;
      String prefix = (String)Config.get().prefix.get();
      this.info("StashMover activated", new Object[0]);
      if (inputAreaPos1 != null && inputAreaPos2 != null) {
         this.info("§aInput area set with §f" + inputContainers.size() + "§a containers", new Object[0]);
      } else {
         this.info("§7Use §f" + prefix + "setinput §7to select input area", new Object[0]);
      }

      if (outputAreaPos1 != null && outputAreaPos2 != null) {
         this.info("§bOutput area set with §f" + outputContainers.size() + "§b containers", new Object[0]);
      } else {
         this.info("§7Use §f" + prefix + "setoutput §7to select output area", new Object[0]);
      }

      if (this.hasValidAreas()) {
         this.info("§eStarting automated transfer process...", new Object[0]);
         this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
         this.stateTimer = 0;
      } else {
         this.info("§cConfigure both input and output areas to start", new Object[0]);
         this.currentState = StashMover.ProcessState.IDLE;
         this.stateTimer = 20;
      }

   }

   public void onDeactivate() {
      if (this.mc.currentScreen instanceof GenericContainerScreen) {
         this.mc.player.closeHandledScreen();
      }

      if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
      }

      this.mc.options.sneakKey.setPressed(false);
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.backKey.setPressed(false);
      this.mc.options.leftKey.setPressed(false);
      this.mc.options.rightKey.setPressed(false);
      this.mc.options.sprintKey.setPressed(false);
      if (this.mc.player != null && this.mc.player.input != null) {
         ((InputAccessor)this.mc.player.input).setMovementForward(0.0F);
         ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
      }

      this.currentState = StashMover.ProcessState.IDLE;
      this.info("StashMover deactivated", new Object[0]);
   }

   public void handleBlockSelectionPublic(BlockPos pos) {
      this.handleBlockSelection(pos);
   }

   private void handleBlockSelection(BlockPos pos) {
      switch (selectionMode.ordinal()) {
         case 1:
            selectionPos1 = pos;
            selectionMode = StashMover.SelectionMode.INPUT_SECOND;
            this.info("§aInput area first corner set", new Object[0]);
            this.info("§eLeft-click another block to set the second corner", new Object[0]);
            break;
         case 2:
            if (pos.equals(selectionPos1)) {
               this.warning("Second corner must be different from the first!", new Object[0]);
               return;
            }

            this.setInputArea(selectionPos1, pos);
            selectionMode = StashMover.SelectionMode.NONE;
            selectionPos1 = null;
            this.info("§aInput area selection complete!", new Object[0]);
            break;
         case 3:
            selectionPos1 = pos;
            selectionMode = StashMover.SelectionMode.OUTPUT_SECOND;
            this.info("§bOutput area first corner set", new Object[0]);
            this.info("§eLeft-click another block to set the second corner", new Object[0]);
            break;
         case 4:
            if (pos.equals(selectionPos1)) {
               this.warning("Second corner must be different from the first!", new Object[0]);
               return;
            }

            this.setOutputArea(selectionPos1, pos);
            selectionMode = StashMover.SelectionMode.NONE;
            selectionPos1 = null;
            this.info("§bOutput area selection complete!", new Object[0]);
      }

   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.isActive()) {
            if (this.stateTimer > 0) {
               --this.stateTimer;
               if (this.currentState != StashMover.ProcessState.IDLE) {
                  return;
               }
            }

            if (!(Boolean)this.pauseOnLag.get() || !this.isServerLagging()) {
               this.handleCurrentState();
            }
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.isActive() || selectionMode != StashMover.SelectionMode.NONE) {
         if ((Boolean)this.renderSelection.get() || selectionMode != StashMover.SelectionMode.NONE) {
            if (inputAreaPos1 != null && inputAreaPos2 != null) {
               Box inputBox = new Box((double)inputAreaPos1.getX(), (double)inputAreaPos1.getY(), (double)inputAreaPos1.getZ(), (double)(inputAreaPos2.getX() + 1), (double)(inputAreaPos2.getY() + 1), (double)(inputAreaPos2.getZ() + 1));
               event.renderer.box(inputBox, (Color)this.inputAreaColor.get(), (Color)this.inputAreaColor.get(), ShapeMode.Lines, (Integer)this.outlineWidth.get());
            }

            if (outputAreaPos1 != null && outputAreaPos2 != null) {
               Box outputBox = new Box((double)outputAreaPos1.getX(), (double)outputAreaPos1.getY(), (double)outputAreaPos1.getZ(), (double)(outputAreaPos2.getX() + 1), (double)(outputAreaPos2.getY() + 1), (double)(outputAreaPos2.getZ() + 1));
               event.renderer.box(outputBox, (Color)this.outputAreaColor.get(), (Color)this.outputAreaColor.get(), ShapeMode.Lines, (Integer)this.outlineWidth.get());
            }

            if (this.isActive()) {
               for(ContainerInfo container : inputContainers) {
                  if (!container.isEmpty) {
                     SettingColor color = container == this.currentContainer ? (SettingColor)this.activeContainerColor.get() : (SettingColor)this.inputContainerColor.get();
                     this.renderContainer(event, container, color);
                  }
               }

               for(ContainerInfo container : outputContainers) {
                  if (!container.isFull) {
                     SettingColor color = container == this.currentContainer ? (SettingColor)this.activeContainerColor.get() : (SettingColor)this.outputContainerColor.get();
                     this.renderContainer(event, container, color);
                  }
               }
            }

            if (selectionMode != StashMover.SelectionMode.NONE && selectionPos1 != null) {
               BlockPos currentPos = this.mc.crosshairTarget != null && this.mc.crosshairTarget.getType() == HitResult.Type.BLOCK ? ((BlockHitResult)this.mc.crosshairTarget).getBlockPos() : this.mc.player.getBlockPos();
               Box selectionBox = new Box((double)Math.min(selectionPos1.getX(), currentPos.getX()), (double)Math.min(selectionPos1.getY(), currentPos.getY()), (double)Math.min(selectionPos1.getZ(), currentPos.getZ()), (double)(Math.max(selectionPos1.getX(), currentPos.getX()) + 1), (double)(Math.max(selectionPos1.getY(), currentPos.getY()) + 1), (double)(Math.max(selectionPos1.getZ(), currentPos.getZ()) + 1));
               SettingColor color = selectionMode != StashMover.SelectionMode.INPUT_FIRST && selectionMode != StashMover.SelectionMode.INPUT_SECOND ? new SettingColor(0, 100, 255, 100) : new SettingColor(0, 255, 0, 100);
               event.renderer.box(selectionBox, color, color, ShapeMode.Both, 0);
               Box corner1 = new Box((double)selectionPos1.getX(), (double)selectionPos1.getY(), (double)selectionPos1.getZ(), (double)(selectionPos1.getX() + 1), (double)(selectionPos1.getY() + 1), (double)(selectionPos1.getZ() + 1));
               event.renderer.box(corner1, new SettingColor(255, 255, 0, 200), new SettingColor(255, 255, 0, 100), ShapeMode.Both, 0);
            }

         }
      }
   }

   private void renderContainer(Render3DEvent event, ContainerInfo container, SettingColor color) {
      Box box = new Box((double)container.pos.getX(), (double)container.pos.getY(), (double)container.pos.getZ(), (double)(container.pos.getX() + 1), (double)(container.pos.getY() + 1), (double)(container.pos.getZ() + 1));
      if (container.type == StashMover.ContainerType.DOUBLE_CHEST || container.type == StashMover.ContainerType.DOUBLE_TRAPPED_CHEST) {
         BlockState state = this.mc.world.getBlockState(container.pos);
         if (state.contains(Properties.CHEST_TYPE)) {
            ChestType chestType = (ChestType)state.get(Properties.CHEST_TYPE);
            Direction facing = (Direction)state.get(Properties.HORIZONTAL_FACING);
            if (chestType == ChestType.LEFT) {
               BlockPos otherPos = container.pos.offset(facing.rotateYClockwise());
               box = box.union(new Box((double)otherPos.getX(), (double)otherPos.getY(), (double)otherPos.getZ(), (double)(otherPos.getX() + 1), (double)(otherPos.getY() + 1), (double)(otherPos.getZ() + 1)));
            } else if (chestType == ChestType.RIGHT) {
               BlockPos otherPos = container.pos.offset(facing.rotateYCounterclockwise());
               box = box.union(new Box((double)otherPos.getX(), (double)otherPos.getY(), (double)otherPos.getZ(), (double)(otherPos.getX() + 1), (double)(otherPos.getY() + 1), (double)(otherPos.getZ() + 1)));
            }
         }
      }

      event.renderer.box(box, color, color, ShapeMode.Both, 1);
   }

   private void handleCurrentState() {
      if ((Boolean)this.debugMode.get() && this.currentState != this.lastDebugState) {
         this.info("State: " + String.valueOf(this.currentState) + " (timer: " + this.stateTimer + ")", new Object[0]);
         this.lastDebugState = this.currentState;
      }

      switch (this.currentState.ordinal()) {
         case 0 -> this.handleIdleState();
         case 1 -> this.checkLocation();
         case 2 -> this.handleInputProcess();
         case 3 -> this.handlePearlLoading();
         case 4 -> this.handleResetPearlPickup();
         case 5 -> this.handleResetPearlPlaceShulker();
         case 6 -> this.handleResetPearlApproach();
         case 7 -> this.handleResetPearlPrepare();
         case 8 -> this.handleResetPearlThrow();
         case 9 -> this.handleResetPearlWait();
         case 10 -> this.handleOutputProcess();
         case 11 -> this.handleGoingBack();
         case 12 -> this.handleOpeningContainer();
         case 13 -> this.handleTransferringItems();
         case 14 -> this.handleClosingContainer();
         case 15 -> this.handleBreakingContainer();
         case 16 -> this.handleMovingToContainer();
         case 17 -> this.handleManualMoving();
         case 18 -> this.handleOpeningEnderChest();
         case 19 -> this.handleFillingEnderChest();
         case 20 -> this.handleEmptyingEnderChest();
         case 21 -> this.handleWaiting();
         case 22 -> this.handleMovingForwardRetry();
      }

   }

   public void startInputSelection() {
      selectionMode = StashMover.SelectionMode.INPUT_FIRST;
      selectionPos1 = null;
      this.info("§aInput area selection started - §fLeft-click §afirst corner block", new Object[0]);
   }

   public void startOutputSelection() {
      selectionMode = StashMover.SelectionMode.OUTPUT_FIRST;
      selectionPos1 = null;
      this.info("§bOutput area selection started - §fLeft-click §bfirst corner block", new Object[0]);
   }

   public void cancelSelection() {
      selectionMode = StashMover.SelectionMode.NONE;
      selectionPos1 = null;
      this.info("§cSelection cancelled", new Object[0]);
   }

   public void setInputArea(BlockPos pos1, BlockPos pos2) {
      inputAreaPos1 = new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
      inputAreaPos2 = new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
      this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
      this.info("§aInput area set with §f" + inputContainers.size() + " §acontainers", new Object[0]);
   }

   public void setOutputArea(BlockPos pos1, BlockPos pos2) {
      outputAreaPos1 = new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
      outputAreaPos2 = new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
      this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false);
      this.info("§bOutput area set with §f" + outputContainers.size() + " §bcontainers", new Object[0]);
   }

   private void detectContainersInArea(BlockPos pos1, BlockPos pos2, boolean isInput) {
      Set<ContainerInfo> containers = isInput ? inputContainers : outputContainers;
      containers.clear();
      Set<BlockPos> processedPositions = new HashSet();

      for(int x = pos1.getX(); x <= pos2.getX(); ++x) {
         for(int y = pos1.getY(); y <= pos2.getY(); ++y) {
            for(int z = pos1.getZ(); z <= pos2.getZ(); ++z) {
               BlockPos pos = new BlockPos(x, y, z);
               if (!processedPositions.contains(pos)) {
                  BlockState state = this.mc.world.getBlockState(pos);
                  Block block = state.getBlock();
                  ContainerInfo container = null;
                  if (block instanceof ChestBlock && !(block instanceof TrappedChestBlock)) {
                     if (state.contains(Properties.CHEST_TYPE)) {
                        ChestType chestType = (ChestType)state.get(Properties.CHEST_TYPE);
                        if (chestType != ChestType.SINGLE) {
                           Direction facing = (Direction)state.get(Properties.HORIZONTAL_FACING);
                           BlockPos otherPos = null;
                           if (chestType == ChestType.LEFT) {
                              otherPos = pos.offset(facing.rotateYClockwise());
                           } else {
                              otherPos = pos.offset(facing.rotateYCounterclockwise());
                           }

                           processedPositions.add(otherPos);
                           container = new ContainerInfo(pos, StashMover.ContainerType.DOUBLE_CHEST);
                        } else {
                           container = new ContainerInfo(pos, StashMover.ContainerType.CHEST);
                        }
                     } else {
                        container = new ContainerInfo(pos, StashMover.ContainerType.CHEST);
                     }
                  } else if (block instanceof TrappedChestBlock) {
                     if (state.contains(Properties.CHEST_TYPE)) {
                        ChestType chestType = (ChestType)state.get(Properties.CHEST_TYPE);
                        if (chestType != ChestType.SINGLE) {
                           Direction facing = (Direction)state.get(Properties.HORIZONTAL_FACING);
                           BlockPos otherPos = null;
                           if (chestType == ChestType.LEFT) {
                              otherPos = pos.offset(facing.rotateYClockwise());
                           } else {
                              otherPos = pos.offset(facing.rotateYCounterclockwise());
                           }

                           processedPositions.add(otherPos);
                           container = new ContainerInfo(pos, StashMover.ContainerType.DOUBLE_TRAPPED_CHEST);
                        } else {
                           container = new ContainerInfo(pos, StashMover.ContainerType.TRAPPED_CHEST);
                        }
                     } else {
                        container = new ContainerInfo(pos, StashMover.ContainerType.TRAPPED_CHEST);
                     }
                  } else if (block instanceof BarrelBlock) {
                     container = new ContainerInfo(pos, StashMover.ContainerType.BARREL);
                  }

                  if (container != null) {
                     containers.add(container);
                     processedPositions.add(pos);
                  }
               }
            }
         }
      }

   }

   private void startProcess() {
      if (!this.hasValidAreas()) {
         this.error("Please set input and output areas first!", new Object[0]);
      } else {
         this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
         this.info("Starting StashMover process...", new Object[0]);
      }
   }

   public void startProcessManually() {
      this.startProcess();
   }

   private void stopCurrentProcess() {
      if (this.mc.currentScreen instanceof GenericContainerScreen) {
         this.mc.player.closeHandledScreen();
      }

      if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
      }

      this.currentState = StashMover.ProcessState.IDLE;
      this.currentContainer = null;
      this.waitingForPearl = false;
      this.pearlRetryCount = 0;
      this.stateTimer = 0;
      this.retryCount = 0;
      this.waitingForRespawn = false;
      this.killRetryCount = 0;
      this.initialPlayerPos = null;
      this.hasThrownPearl = false;
      this.hasPlacedShulker = false;
      this.enderChestHasItems = false;
      this.enderChestFull = false;
      this.pearlFailRetries = 0;
      this.offhandBackup = ItemStack.EMPTY;
      this.info("Process stopped", new Object[0]);
   }

   public void stopProcessManually() {
      this.stopCurrentProcess();
   }

   private void checkLocation() {
      if (this.isNearInputArea()) {
         this.info("Near input area, starting input process", new Object[0]);
         this.enderChestEmptied = false;
         this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
         this.info("Found " + inputContainers.size() + " input containers", new Object[0]);
         this.currentState = StashMover.ProcessState.INPUT_PROCESS;
         this.stateTimer = 0;
      } else if (this.isNearOutputArea()) {
         this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false);
         this.info("Found " + outputContainers.size() + " output containers", new Object[0]);
         if (this.outputTravelMethod.get() != StashMover.OutputTravelMethod.PEARL && this.goBackMethod.get() != StashMover.GoBackMethod.PEARL) {
            this.info("Near output area, starting output process", new Object[0]);
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
            this.stateTimer = 5;
         } else {
            this.info("Near output area, resetting pearl first", new Object[0]);
            this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
            this.hasThrownPearl = false;
            this.hasPlacedShulker = false;
            this.isGoingToInput = false;
         }
      } else {
         this.warning("Not near any configured area! Will retry in 5 seconds...", new Object[0]);
         if ((Boolean)this.debugMode.get()) {
            this.warning("Input area not set", new Object[0]);
            this.warning("Output area not set", new Object[0]);
         }

         this.currentState = StashMover.ProcessState.IDLE;
         this.stateTimer = 100;
      }

   }

   private void handleInputProcess() {
      if (this.currentState != StashMover.ProcessState.OPENING_ENDERCHEST) {
         if (this.isInventoryFull()) {
            if ((Boolean)this.fillEnderChest.get() && !this.isEnderChestFull()) {
               this.info("Inventory full, checking enderchest...", new Object[0]);
               this.findOrPlaceEnderChest();
            } else {
               this.info("Inventory full and enderchest not available/full, starting pearl loading", new Object[0]);
               this.currentState = StashMover.ProcessState.LOADING_PEARL;
            }
         } else {
            this.findNextInputContainer();
         }
      }
   }

   private void findNextInputContainer() {
      this.currentContainer = (ContainerInfo)inputContainers.stream().filter((c) -> !c.isEmpty).min(Comparator.comparingDouble((c) -> this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(c.pos)))).orElse(null);
      if (this.currentContainer == null) {
         if (!this.isInventoryFull() && (!(Boolean)this.fillEnderChest.get() || !this.hasItemsInEnderChest())) {
            this.info("No containers with items found, rescanning...", new Object[0]);
            this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
            this.currentContainer = (ContainerInfo)inputContainers.stream().filter((c) -> !c.isEmpty).min(Comparator.comparingDouble((c) -> this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(c.pos)))).orElse(null);
            if (this.currentContainer != null) {
               this.info("Found container after rescan", new Object[0]);
               this.moveToContainer(this.currentContainer);
            } else {
               this.info("No containers found, waiting 5 seconds...", new Object[0]);
               this.currentState = StashMover.ProcessState.IDLE;
               this.stateTimer = 100;
            }
         } else {
            this.info("All input containers processed, starting pearl loading!", new Object[0]);
            this.currentState = StashMover.ProcessState.LOADING_PEARL;
         }
      } else {
         this.info("Moving to container", new Object[0]);
         this.moveToContainer(this.currentContainer);
      }

   }

   private void moveToContainer(ContainerInfo container) {
      if (this.currentContainer != container) {
         this.containerOpenFailures = 0;
         this.stuckCounter = 0;
         this.stuckRecoveryAttempts = 0;
         this.lastPlayerPos = null;
      }

      Vec3d eyePos = this.mc.player.getEyePos();
      double distance = eyePos.distanceTo(Vec3d.ofCenter(container.pos));
      if (distance > (double)3.5F) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
         BlockPos validPosition = this.findValidStandingPositionNear(container.pos);
         if (validPosition != null) {
            GoalBlock goal = new GoalBlock(validPosition);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            this.currentState = StashMover.ProcessState.MOVING_TO_CONTAINER;
            if (distance > (double)20.0F) {
               this.stateTimer = 200;
            } else if (distance > (double)10.0F) {
               this.stateTimer = 120;
            } else {
               this.stateTimer = 80;
            }

            this.info("Moving to container (distance: " + String.format("%.1f", distance) + "m)", new Object[0]);
         } else {
            int nearDistance = Math.max(2, ((Double)this.containerReach.get()).intValue());
            GoalNear goal = new GoalNear(container.pos, nearDistance);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            this.currentState = StashMover.ProcessState.MOVING_TO_CONTAINER;
            this.stateTimer = 120;
            this.warning("No ideal position found, using GoalNear for container", new Object[0]);
         }
      } else {
         this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
         this.stateTimer = 5;
      }

   }

   private BlockPos findValidStandingPositionNear(BlockPos containerPos) {
      for(int yOffset = 0; yOffset >= -2; --yOffset) {
         for(Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos checkPos = containerPos.offset(dir).add(0, yOffset, 0);
            if (this.isValidStandingSpot(checkPos)) {
               Vec3d standingEyePos = Vec3d.of(checkPos).add((double)0.5F, 1.62, (double)0.5F);
               double reach = standingEyePos.distanceTo(Vec3d.ofCenter(containerPos));
               if (reach <= 4.2) {
                  return checkPos;
               }
            }
         }

         BlockPos[] diagonals = new BlockPos[]{containerPos.add(1, yOffset, 1), containerPos.add(1, yOffset, -1), containerPos.add(-1, yOffset, 1), containerPos.add(-1, yOffset, -1)};

         for(BlockPos checkPos : diagonals) {
            if (this.isValidStandingSpot(checkPos)) {
               Vec3d standingEyePos = Vec3d.of(checkPos).add((double)0.5F, 1.62, (double)0.5F);
               double reach = standingEyePos.distanceTo(Vec3d.ofCenter(containerPos));
               if (reach <= 4.2) {
                  return checkPos;
               }
            }
         }
      }

      for(Direction dir : Direction.Type.HORIZONTAL) {
         for(int yOffset = 0; yOffset >= -2; --yOffset) {
            BlockPos checkPos = containerPos.offset(dir, 2).add(0, yOffset, 0);
            if (this.isValidStandingSpot(checkPos)) {
               Vec3d standingEyePos = Vec3d.of(checkPos).add((double)0.5F, 1.62, (double)0.5F);
               double reach = standingEyePos.distanceTo(Vec3d.ofCenter(containerPos));
               if (reach <= 4.2) {
                  return checkPos;
               }
            }
         }
      }

      return null;
   }

   private boolean isValidStandingSpot(BlockPos pos) {
      BlockState below = this.mc.world.getBlockState(pos.down());
      BlockState at = this.mc.world.getBlockState(pos);
      BlockState above = this.mc.world.getBlockState(pos.up());
      return below.isSolidBlock(this.mc.world, pos.down()) && !below.isAir() && (at.isAir() || !at.isSolidBlock(this.mc.world, pos)) && (above.isAir() || !above.isSolidBlock(this.mc.world, pos.up()));
   }

   private void handleMovingToContainer() {
      if (this.currentContainer == null) {
         this.currentState = StashMover.ProcessState.INPUT_PROCESS;
      } else {
         Vec3d eyePos = this.mc.player.getEyePos();
         Vec3d playerPos = this.mc.player.getEntityPos();
         double distance = eyePos.distanceTo(Vec3d.ofCenter(this.currentContainer.pos));
         if (this.lastPlayerPos != null) {
            double movementDelta = playerPos.distanceTo(this.lastPlayerPos);
            if (movementDelta < 0.1) {
               ++this.stuckCounter;
            } else {
               this.stuckCounter = Math.max(0, this.stuckCounter - 2);
            }

            if (this.stuckCounter >= 40) {
               this.warning("Stuck detected! Attempting recovery...", new Object[0]);
               this.performStuckRecovery();
               return;
            }
         }

         this.lastPlayerPos = playerPos;
         if (distance <= (double)3.5F) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
            this.stateTimer = 5;
            this.info("Reached container, opening...", new Object[0]);
            this.pathfindingFailures = 0;
            this.stuckCounter = 0;
            this.stuckRecoveryAttempts = 0;
            this.lastPlayerPos = null;
         } else if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            ++this.pathfindingFailures;
            if (this.pathfindingFailures >= 2) {
               this.warning("Pathfinding failed, using improved manual movement", new Object[0]);
               this.improvedManualMovement(this.currentContainer);
               this.pathfindingFailures = 0;
            } else {
               this.warning("Pathfinding stopped, trying alternative path", new Object[0]);
               this.alternativePathToContainer(this.currentContainer);
            }
         } else if (this.stateTimer > 0) {
            --this.stateTimer;
            if (this.stateTimer == 0) {
               this.warning("Movement timeout, switching to manual", new Object[0]);
               BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
               this.improvedManualMovement(this.currentContainer);
            }
         }

      }
   }

   private void handleOpeningContainer() {
      if (this.currentContainer == null) {
         if (this.isNearOutputArea()) {
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
         } else {
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
         }

      } else {
         Vec3d eyePos = this.mc.player.getEyePos();
         Vec3d playerPos = this.mc.player.getEntityPos();
         double distance = eyePos.distanceTo(Vec3d.ofCenter(this.currentContainer.pos));
         double horizontalDistance = Math.sqrt(Math.pow((double)this.currentContainer.pos.getX() + (double)0.5F - playerPos.x, (double)2.0F) + Math.pow((double)this.currentContainer.pos.getZ() + (double)0.5F - playerPos.z, (double)2.0F));
         double verticalDiff = Math.abs((double)this.currentContainer.pos.getY() - eyePos.y);
         boolean isDiagonal = verticalDiff > (double)1.5F && horizontalDistance < (double)2.5F;
         double effectiveReach = isDiagonal ? 3.2 : (double)3.5F;
         if (distance > effectiveReach) {
            if (this.retryCount < 2) {
               this.info("Too far (" + String.format("%.1f", distance) + "m), moving closer...", new Object[0]);
               this.improvedManualMovement(this.currentContainer);
               ++this.retryCount;
            } else {
               this.warning("Can't reach, trying manual approach", new Object[0]);
               this.manualMoveToContainer(this.currentContainer);
            }
         } else {
            this.retryCount = 0;
            Vec3d containerCenter = Vec3d.ofCenter(this.currentContainer.pos);
            double targetYaw = Rotations.getYaw(containerCenter);
            double targetPitch = Rotations.getPitch(containerCenter);
            this.mc.player.setYaw((float)targetYaw);
            this.mc.player.setPitch((float)targetPitch);

            for(int i = 0; i < 2; ++i) {
               this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)targetYaw, (float)targetPitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
            }

            this.info("Opening container (attempt " + (this.containerOpenFailures + 1) + ")", new Object[0]);
            this.performImprovedInteraction(containerCenter);
            this.currentState = StashMover.ProcessState.WAITING;
            this.stateTimer = 15;
         }
      }
   }

   private void performStandardInteraction(Vec3d containerCenter) {
      Vec3d eyePos = this.mc.player.getEyePos();
      Direction clickFace = this.getOptimalClickFace(this.currentContainer.pos, eyePos);
      Vec3d hitVec = this.calculatePreciseHitVector(this.currentContainer.pos, clickFace, eyePos);
      double yaw = Rotations.getYaw(hitVec);
      double pitch = Rotations.getPitch(hitVec);
      this.mc.player.setYaw((float)yaw);
      this.mc.player.setPitch((float)pitch);
      this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
      BlockHitResult hitResult = new BlockHitResult(hitVec, clickFace, this.currentContainer.pos, false);
      ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
      if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
         result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hitResult);
      }

      if ((Boolean)this.debugMode.get()) {
         this.info("Interaction: face=" + String.valueOf(clickFace) + ", result=" + String.valueOf(result), new Object[0]);
      }

   }

   private Vec3d calculatePreciseHitVector(BlockPos pos, Direction face, Vec3d eyePos) {
      double x = (double)pos.getX() + (double)0.5F;
      double y = (double)pos.getY() + (double)0.5F;
      double z = (double)pos.getZ() + (double)0.5F;
      double heightDiff = (double)pos.getY() - eyePos.y;
      switch (face) {
         case UP:
            y = (double)pos.getY() + (double)1.0F;
            x = (double)pos.getX() + (double)0.5F;
            z = (double)pos.getZ() + (double)0.5F;
            break;
         case DOWN:
            y = (double)pos.getY();
            x = (double)pos.getX() + (double)0.5F;
            z = (double)pos.getZ() + (double)0.5F;
            break;
         case NORTH:
            z = (double)pos.getZ();
            if (heightDiff > (double)1.5F) {
               y = (double)pos.getY() + 0.3;
            } else if (heightDiff > (double)0.5F) {
               y = (double)pos.getY() + 0.4;
            }
            break;
         case SOUTH:
            z = (double)pos.getZ() + (double)1.0F;
            if (heightDiff > (double)1.5F) {
               y = (double)pos.getY() + 0.3;
            } else if (heightDiff > (double)0.5F) {
               y = (double)pos.getY() + 0.4;
            }
            break;
         case WEST:
            x = (double)pos.getX();
            if (heightDiff > (double)1.5F) {
               y = (double)pos.getY() + 0.3;
            } else if (heightDiff > (double)0.5F) {
               y = (double)pos.getY() + 0.4;
            }
            break;
         case EAST:
            x = (double)pos.getX() + (double)1.0F;
            if (heightDiff > (double)1.5F) {
               y = (double)pos.getY() + 0.3;
            } else if (heightDiff > (double)0.5F) {
               y = (double)pos.getY() + 0.4;
            }
      }

      return new Vec3d(x, y, z);
   }

   private void performInteractionWithMovement(Vec3d containerCenter) {
      Vec3d eyePos = this.mc.player.getEyePos();
      Vec3d currentPos = this.mc.player.getEntityPos();
      this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(currentPos.x, currentPos.y, currentPos.z, this.mc.player.getYaw(), this.mc.player.getPitch(), this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
      Direction bestFace = this.getOptimalClickFace(this.currentContainer.pos, eyePos);
      Vec3d[] hitPositions = new Vec3d[3];
      if (bestFace != Direction.UP && bestFace != Direction.DOWN) {
         hitPositions[0] = this.calculatePreciseHitVector(this.currentContainer.pos, bestFace, eyePos);
         hitPositions[1] = hitPositions[0].add((double)0.0F, 0.1, (double)0.0F);
         hitPositions[2] = hitPositions[0].add((double)0.0F, -0.1, (double)0.0F);
      } else {
         hitPositions[0] = Vec3d.ofCenter(this.currentContainer.pos);
         hitPositions[1] = Vec3d.ofCenter(this.currentContainer.pos).add(0.2, (double)0.0F, 0.2);
         hitPositions[2] = Vec3d.ofCenter(this.currentContainer.pos).add(-0.2, (double)0.0F, -0.2);
      }

      for(int i = 0; i < hitPositions.length; ++i) {
         Vec3d hitPos = hitPositions[i];
         double yaw = Rotations.getYaw(hitPos);
         double pitch = Rotations.getPitch(hitPos);
         this.mc.player.setYaw((float)yaw);
         this.mc.player.setPitch((float)pitch);
         BlockHitResult hitResult = new BlockHitResult(hitPos, bestFace, this.currentContainer.pos, false);
         ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
         if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
            result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hitResult);
         }

         if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
            if ((Boolean)this.debugMode.get()) {
               this.info("Container opened with position " + i + ", face: " + String.valueOf(bestFace), new Object[0]);
            }

            return;
         }
      }

      if ((Boolean)this.debugMode.get()) {
         this.info("All interaction attempts failed, face: " + String.valueOf(bestFace), new Object[0]);
      }

   }

   private void performAggressiveInteraction(Vec3d containerCenter) {
      Vec3d eyePos = this.mc.player.getEyePos();
      Vec3d pos = this.mc.player.getEntityPos();
      this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(pos.x, pos.y, pos.z, this.mc.player.getYaw(), this.mc.player.getPitch(), this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
      double heightDiff = (double)this.currentContainer.pos.getY() - eyePos.y;
      Direction[] facesToTry;
      if (heightDiff > (double)2.0F) {
         facesToTry = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
      } else if (heightDiff > (double)1.0F) {
         facesToTry = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
      } else {
         facesToTry = new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH};
      }

      for(Direction face : facesToTry) {
         Vec3d hitVec = this.calculatePreciseHitVector(this.currentContainer.pos, face, eyePos);
         double yaw = Rotations.getYaw(hitVec);
         double pitch = Rotations.getPitch(hitVec);
         this.mc.player.setYaw((float)yaw);
         this.mc.player.setPitch((float)pitch);
         this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
         BlockHitResult hitResult = new BlockHitResult(hitVec, face, this.currentContainer.pos, false);
         ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
         if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
            result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hitResult);
         }

         if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
            if ((Boolean)this.debugMode.get()) {
               this.info("Opened with face: " + String.valueOf(face), new Object[0]);
            }

            return;
         }
      }

      if ((Boolean)this.debugMode.get()) {
         int var10001 = this.currentContainer.pos.getY();
         this.warning("Failed to open chest at Y=" + var10001 + ", height diff=" + String.format("%.1f", heightDiff), new Object[0]);
      }

   }

   private void handleOpeningContainer_OLD() {
      if (this.currentContainer == null) {
         if (this.isNearOutputArea()) {
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
         } else {
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
         }

      } else {
         double distance = this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(this.currentContainer.pos));
         if (distance > (Double)this.containerReach.get()) {
            if (this.retryCount < 3) {
               this.info("Too far from container (" + String.format("%.1f", distance) + "m), moving closer...", new Object[0]);
               this.moveToContainer(this.currentContainer);
               ++this.retryCount;
            } else {
               this.warning("Baritone pathfinding failed, attempting manual approach", new Object[0]);
               this.manualMoveToContainer(this.currentContainer);
            }
         } else {
            this.retryCount = 0;
            if (this.containerOpenFailures >= 2) {
               if (this.containerOpenFailures % 2 == 0) {
                  this.mc.options.leftKey.setPressed(true);
               } else {
                  this.mc.options.leftKey.setPressed(false);
                  this.mc.options.rightKey.setPressed(true);
               }

               if (this.containerOpenFailures % 3 == 0) {
                  this.mc.options.leftKey.setPressed(false);
                  this.mc.options.rightKey.setPressed(false);
               }
            }

            double offsetX = (double)0.0F;
            double offsetY = (double)0.0F;
            double offsetZ = (double)0.0F;
            Vec3d containerCenter;
            switch (this.containerOpenFailures % 9) {
               case 0:
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos);
                  break;
               case 1:
                  offsetY = (double)0.25F;
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos).add((double)0.0F, offsetY, (double)0.0F);
                  break;
               case 2:
                  offsetY = (double)-0.25F;
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos).add((double)0.0F, offsetY, (double)0.0F);
                  break;
               case 3:
                  offsetX = 0.2;
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos).add(offsetX, (double)0.0F, (double)0.0F);
                  break;
               case 4:
                  offsetX = -0.2;
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos).add(offsetX, (double)0.0F, (double)0.0F);
                  break;
               case 5:
                  offsetZ = 0.2;
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos).add((double)0.0F, (double)0.0F, offsetZ);
                  break;
               case 6:
                  offsetZ = -0.2;
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos).add((double)0.0F, (double)0.0F, offsetZ);
                  break;
               case 7:
                  offsetX = 0.15;
                  offsetY = 0.15;
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos).add(offsetX, offsetY, (double)0.0F);
                  break;
               case 8:
                  offsetX = -0.15;
                  offsetZ = 0.15;
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos).add(offsetX, (double)0.0F, offsetZ);
                  break;
               default:
                  containerCenter = Vec3d.ofCenter(this.currentContainer.pos);
            }

            double yaw = Rotations.getYaw(containerCenter);
            double pitch = Rotations.getPitch(containerCenter);
            this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
            this.mc.player.setYaw((float)yaw);
            this.mc.player.setPitch((float)pitch);
            if (this.containerOpenFailures == 0 && this.stateTimer <= 0) {
               this.stateTimer = 5;
            }

            if (this.stateTimer > 0) {
               --this.stateTimer;
               this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
            } else {
               if (this.containerOpenFailures >= 2) {
                  this.mc.options.leftKey.setPressed(false);
                  this.mc.options.rightKey.setPressed(false);
                  this.mc.options.forwardKey.setPressed(false);
                  this.mc.options.backKey.setPressed(false);
                  switch (this.containerOpenFailures % 4) {
                     case 0:
                        this.mc.options.leftKey.setPressed(true);
                        this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(this.mc.player.getX() - 0.01, this.mc.player.getY(), this.mc.player.getZ(), this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
                        this.mc.options.leftKey.setPressed(false);
                        break;
                     case 1:
                        this.mc.options.rightKey.setPressed(true);
                        this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(this.mc.player.getX() + 0.01, this.mc.player.getY(), this.mc.player.getZ(), this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
                        this.mc.options.rightKey.setPressed(false);
                        break;
                     case 2:
                        this.mc.options.forwardKey.setPressed(true);
                        this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ() - 0.01, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
                        this.mc.options.forwardKey.setPressed(false);
                        break;
                     case 3:
                        this.mc.options.backKey.setPressed(true);
                        this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ() + 0.01, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
                        this.mc.options.backKey.setPressed(false);
                  }
               }

               this.info("Attempting to open container (attempt " + (this.containerOpenFailures + 1) + "/8)", new Object[0]);
               Direction clickFace = this.getOptimalClickFace(this.currentContainer.pos, this.mc.player.getEyePos());
               BlockHitResult hitResult = new BlockHitResult(containerCenter, clickFace, this.currentContainer.pos, false);
               this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
               this.mc.options.leftKey.setPressed(false);
               this.mc.options.rightKey.setPressed(false);
               this.mc.options.forwardKey.setPressed(false);
               this.mc.options.backKey.setPressed(false);
               this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
               ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
               if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
                  result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hitResult);
               }

               if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
                  this.info("Interaction result: " + String.valueOf(result), new Object[0]);
               } else {
                  this.info("Interaction sent successfully", new Object[0]);
               }

               this.currentState = StashMover.ProcessState.WAITING;
               this.stateTimer = 5;
            }
         }
      }
   }

   private void handleWaiting() {
      if (this.mc.currentScreen instanceof GenericContainerScreen) {
         this.containerOpenFailures = 0;
         this.pathfindingFailures = 0;
         this.retryCount = 0;
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         this.currentState = StashMover.ProcessState.TRANSFERRING_ITEMS;
         this.stateTimer = this.transferDelayTicks();
         this.info("Container opened successfully!", new Object[0]);
      } else if (this.stateTimer > 0) {
         --this.stateTimer;
         if (this.stateTimer % 2 == 0 && this.currentContainer != null) {
            Vec3d containerCenter = Vec3d.ofCenter(this.currentContainer.pos);
            double yaw = Rotations.getYaw(containerCenter);
            double pitch = Rotations.getPitch(containerCenter);
            this.mc.player.setYaw((float)yaw);
            this.mc.player.setPitch((float)pitch);
            this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
            Direction[] faces = new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            Direction face = faces[this.stateTimer % faces.length];
            Vec3d hitVec = this.calculatePreciseHitVector(this.currentContainer.pos, face, this.mc.player.getEyePos());
            BlockHitResult hitResult = new BlockHitResult(hitVec, face, this.currentContainer.pos, false);
            ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
            if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
               this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hitResult);
            }
         }

      } else {
         ++this.containerOpenFailures;
         this.info("Container didn't open, attempt " + this.containerOpenFailures + "/10", new Object[0]);
         if (this.containerOpenFailures >= 10) {
            this.warning("Cannot open container at Y=" + this.currentContainer.pos.getY() + " after 10 attempts", new Object[0]);
            this.stopAllMovement();
            if (this.isNearInputArea()) {
               this.currentContainer.isEmpty = true;
               this.info("Marked input container as empty/inaccessible", new Object[0]);
            } else if (this.isNearOutputArea()) {
               this.currentContainer.isFull = true;
               this.info("Marked output container as full/inaccessible", new Object[0]);
            }

            this.currentContainer = null;
            this.containerOpenFailures = 0;
            this.retryCount = 0;
            this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
         } else if (this.currentContainer != null) {
            Vec3d eyePos = this.mc.player.getEyePos();
            Vec3d containerCenter = Vec3d.ofCenter(this.currentContainer.pos);
            double distance = eyePos.distanceTo(containerCenter);
            if (this.containerOpenFailures >= 5) {
               this.performSmartRepositioning(this.currentContainer.pos, distance);
               this.currentState = StashMover.ProcessState.MOVING_FORWARD_RETRY;
               this.stateTimer = 20;
            } else if (distance > (double)3.0F && this.containerOpenFailures < 3) {
               double yaw = Rotations.getYaw(containerCenter);
               double pitch = Rotations.getPitch(containerCenter);
               this.mc.player.setYaw((float)yaw);
               this.mc.player.setPitch((float)pitch);
               if (distance > (double)4.0F) {
                  this.stateTimer = 20;
               } else {
                  this.stateTimer = 20;
               }

               this.info("Moving closer to container (distance: " + String.format("%.1f", distance) + ")", new Object[0]);
               this.currentState = StashMover.ProcessState.MOVING_FORWARD_RETRY;
            } else if (this.containerOpenFailures >= 3 && distance > (double)2.5F) {
               this.info("Trying side approach after " + this.containerOpenFailures + " failures", new Object[0]);
               Vec3d playerPos = this.mc.player.getEntityPos();
               Vec3d toContainer = Vec3d.of(this.currentContainer.pos).add((double)0.5F, (double)0.0F, (double)0.5F).subtract(playerPos);
               this.mc.options.leftKey.setPressed(true);
               this.mc.options.forwardKey.setPressed(true);
               this.stateTimer = 10;
               this.currentState = StashMover.ProcessState.MOVING_FORWARD_RETRY;
            } else if (distance < (double)2.0F) {
               this.info("Too close to container, backing up...", new Object[0]);
               this.mc.options.backKey.setPressed(true);
               this.stateTimer = 5;
               this.currentState = StashMover.ProcessState.MOVING_FORWARD_RETRY;
            } else {
               this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
               this.stateTimer = 2;
            }
         } else {
            this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
         }

      }
   }

   private void handleMovingForwardRetry() {
      if (this.mc.currentScreen instanceof GenericContainerScreen) {
         this.stopAllMovement();
         this.containerOpenFailures = 0;
         this.pathfindingFailures = 0;
         this.retryCount = 0;
         this.currentState = StashMover.ProcessState.TRANSFERRING_ITEMS;
         this.stateTimer = this.transferDelayTicks();
         this.info("Container opened successfully!", new Object[0]);
      } else if (this.currentContainer == null) {
         this.stopAllMovement();
         this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
      } else {
         Vec3d containerCenter = Vec3d.ofCenter(this.currentContainer.pos);
         Vec3d eyePos = this.mc.player.getEyePos();
         Vec3d playerPos = this.mc.player.getEntityPos();
         double distance = eyePos.distanceTo(containerCenter);
         Vec3d toContainer = Vec3d.of(this.currentContainer.pos).add((double)0.5F, (double)0.0F, (double)0.5F).subtract(playerPos);
         double horizontalDistance = Math.sqrt(toContainer.x * toContainer.x + toContainer.z * toContainer.z);
         double targetYaw = Rotations.getYaw(containerCenter);
         double targetPitch = Rotations.getPitch(containerCenter);
         float currentYaw = this.mc.player.getYaw();
         float currentPitch = this.mc.player.getPitch();

         float yawDiff;
         for(yawDiff = (float)(targetYaw - (double)currentYaw); yawDiff > 180.0F; yawDiff -= 360.0F) {
         }

         while(yawDiff < -180.0F) {
            yawDiff += 360.0F;
         }

         float pitchDiff = (float)(targetPitch - (double)currentPitch);
         float smoothingFactor = 0.6F;
         float newYaw = currentYaw + yawDiff * smoothingFactor;
         float newPitch = currentPitch + pitchDiff * smoothingFactor;
         this.mc.player.setYaw(newYaw);
         this.mc.player.setPitch(newPitch);
         this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYaw, newPitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
         if (this.containerOpenFailures >= 3 && this.mc.options.leftKey.isPressed()) {
            if (this.stateTimer > 3) {
               this.mc.options.leftKey.setPressed(true);
               this.mc.options.forwardKey.setPressed(true);
            } else {
               this.stopAllMovement();
            }
         } else if (this.stateTimer > 3) {
            if (horizontalDistance > (double)3.5F) {
               this.mc.options.forwardKey.setPressed(true);
               this.mc.options.sprintKey.setPressed(true);
            } else if (horizontalDistance > (double)2.0F) {
               this.mc.options.forwardKey.setPressed(true);
               this.mc.options.sprintKey.setPressed(false);
               this.mc.options.sneakKey.setPressed(false);
            } else if (horizontalDistance > 1.2) {
               this.mc.options.forwardKey.setPressed(true);
               this.mc.options.sneakKey.setPressed(true);
               this.mc.options.sprintKey.setPressed(false);
            } else {
               this.stopAllMovement();
            }
         } else {
            this.stopAllMovement();
         }

         if (distance <= (double)4.5F && this.stateTimer % 4 == 0) {
            this.attemptContainerInteraction(containerCenter, eyePos, this.stateTimer);
         }

         --this.stateTimer;
         if (this.stateTimer <= 0) {
            this.stopAllMovement();
            double finalDistance = eyePos.distanceTo(containerCenter);
            if (finalDistance <= (double)4.0F) {
               this.info("At good distance (" + String.format("%.1f", finalDistance) + "), attempting to open", new Object[0]);
               this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
               this.stateTimer = 5;
            } else if (this.containerOpenFailures >= 5) {
               this.warning("Failed to reach container after " + this.containerOpenFailures + " attempts, using Baritone", new Object[0]);
               this.moveToContainer(this.currentContainer);
            } else {
               this.info("Distance still " + String.format("%.1f", finalDistance) + ", trying alternative approach", new Object[0]);
               this.attemptAlternativeApproach();
            }
         }

      }
   }

   private void handleIntelligentMovement(double horizontalDistance, Vec3d toContainer) {
      boolean blocked = this.isPathBlocked(toContainer);
      boolean needsJump = this.shouldJump();
      boolean canStrafe = this.canStrafeAround();
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.backKey.setPressed(false);
      this.mc.options.leftKey.setPressed(false);
      this.mc.options.rightKey.setPressed(false);
      this.mc.options.sneakKey.setPressed(false);
      this.mc.options.jumpKey.setPressed(false);
      if (!(horizontalDistance < 1.2)) {
         if (blocked) {
            if (needsJump) {
               this.mc.options.jumpKey.setPressed(true);
               this.mc.options.forwardKey.setPressed(true);
               if ((Boolean)this.debugMode.get()) {
                  this.info("Jumping over obstacle", new Object[0]);
               }
            } else if (canStrafe) {
               this.handleStrafeMovement(toContainer);
            } else {
               this.attemptAlternativeApproach();
            }
         } else if (horizontalDistance > (double)3.0F) {
            this.mc.options.forwardKey.setPressed(true);
            this.mc.options.sprintKey.setPressed(true);
         } else if (horizontalDistance > 1.8) {
            this.mc.options.forwardKey.setPressed(true);
            this.mc.options.sprintKey.setPressed(false);
         } else {
            this.mc.options.forwardKey.setPressed(true);
            this.mc.options.sneakKey.setPressed(true);
         }

      }
   }

   private boolean isPathBlocked(Vec3d toContainer) {
      Vec3d checkPos = this.mc.player.getEntityPos().add(toContainer.normalize().multiply((double)1.0F));
      BlockPos blockPos = BlockPos.ofFloored(checkPos);
      BlockPos blockPosAbove = blockPos.up();
      BlockState state = this.mc.world.getBlockState(blockPos);
      BlockState stateAbove = this.mc.world.getBlockState(blockPosAbove);
      return !state.isAir() && state.isSolidBlock(this.mc.world, blockPos) || !stateAbove.isAir() && stateAbove.isSolidBlock(this.mc.world, blockPosAbove);
   }

   private boolean shouldJump() {
      Vec3d feetPos = this.mc.player.getEntityPos();
      Vec3d forwardPos = feetPos.add(this.mc.player.getRotationVector().multiply((double)1.0F));
      BlockPos feetBlock = BlockPos.ofFloored(forwardPos);
      BlockPos headBlock = feetBlock.up();
      BlockPos aboveBlock = feetBlock.up(2);
      BlockState feetState = this.mc.world.getBlockState(feetBlock);
      BlockState headState = this.mc.world.getBlockState(headBlock);
      BlockState aboveState = this.mc.world.getBlockState(aboveBlock);
      return !feetState.isAir() && headState.isAir() && aboveState.isAir();
   }

   private boolean canStrafeAround() {
      Vec3d leftCheck = this.mc.player.getEntityPos().add(this.mc.player.getRotationVector().rotateY((float)Math.toRadians((double)90.0F)));
      Vec3d rightCheck = this.mc.player.getEntityPos().add(this.mc.player.getRotationVector().rotateY((float)Math.toRadians((double)-90.0F)));
      BlockPos leftBlock = BlockPos.ofFloored(leftCheck);
      BlockPos rightBlock = BlockPos.ofFloored(rightCheck);
      return this.mc.world.getBlockState(leftBlock).isAir() || this.mc.world.getBlockState(rightBlock).isAir();
   }

   private void handleStrafeMovement(Vec3d toContainer) {
      Vec3d left = this.mc.player.getRotationVector().rotateY((float)Math.toRadians((double)90.0F));
      Vec3d right = this.mc.player.getRotationVector().rotateY((float)Math.toRadians((double)-90.0F));
      Vec3d leftCheck = this.mc.player.getEntityPos().add(left);
      Vec3d rightCheck = this.mc.player.getEntityPos().add(right);
      BlockPos leftBlock = BlockPos.ofFloored(leftCheck);
      BlockPos rightBlock = BlockPos.ofFloored(rightCheck);
      boolean leftClear = this.mc.world.getBlockState(leftBlock).isAir();
      boolean rightClear = this.mc.world.getBlockState(rightBlock).isAir();
      this.mc.options.forwardKey.setPressed(true);
      if (leftClear && !rightClear) {
         this.mc.options.leftKey.setPressed(true);
         if ((Boolean)this.debugMode.get()) {
            this.info("Strafing left around obstacle", new Object[0]);
         }
      } else if (rightClear && !leftClear) {
         this.mc.options.rightKey.setPressed(true);
         if ((Boolean)this.debugMode.get()) {
            this.info("Strafing right around obstacle", new Object[0]);
         }
      } else if (leftClear && rightClear) {
         Vec3d containerPos = Vec3d.of(this.currentContainer.pos);
         double leftDist = leftCheck.distanceTo(containerPos);
         double rightDist = rightCheck.distanceTo(containerPos);
         if (leftDist < rightDist) {
            this.mc.options.leftKey.setPressed(true);
         } else {
            this.mc.options.rightKey.setPressed(true);
         }
      }

   }

   private void attemptAlternativeApproach() {
      if (this.currentContainer != null) {
         Vec3d playerPos = this.mc.player.getEntityPos();
         Vec3d containerPos = Vec3d.of(this.currentContainer.pos).add((double)0.5F, (double)0.0F, (double)0.5F);
         Vec3d[] approachPoints = new Vec3d[]{containerPos.add((double)2.0F, (double)0.0F, (double)0.0F), containerPos.add((double)-2.0F, (double)0.0F, (double)0.0F), containerPos.add((double)0.0F, (double)0.0F, (double)2.0F), containerPos.add((double)0.0F, (double)0.0F, (double)-2.0F), containerPos.add((double)1.5F, (double)0.0F, (double)1.5F), containerPos.add((double)-1.5F, (double)0.0F, (double)1.5F), containerPos.add((double)1.5F, (double)0.0F, (double)-1.5F), containerPos.add((double)-1.5F, (double)0.0F, (double)-1.5F)};
         Vec3d bestPoint = null;
         double bestDistance = Double.MAX_VALUE;

         for(Vec3d point : approachPoints) {
            BlockPos checkPos = BlockPos.ofFloored(point);
            if (this.mc.world.getBlockState(checkPos).isAir() && this.mc.world.getBlockState(checkPos.up()).isAir()) {
               double dist = playerPos.distanceTo(point);
               if (dist < bestDistance) {
                  bestDistance = dist;
                  bestPoint = point;
               }
            }
         }

         if (bestPoint != null) {
            this.info("Trying alternative approach angle", new Object[0]);
            GoalBlock goal = new GoalBlock(BlockPos.ofFloored(bestPoint));
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            this.currentState = StashMover.ProcessState.MOVING_TO_CONTAINER;
            this.stateTimer = 60;
         } else {
            this.warning("No valid approach to container, skipping", new Object[0]);
            this.currentContainer.isEmpty = true;
            this.currentContainer = null;
            this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
         }

      }
   }

   private void attemptContainerInteraction(Vec3d containerCenter, Vec3d eyePos, int timer) {
      Direction optimalFace = this.getOptimalClickFace(this.currentContainer.pos, eyePos);
      double yaw = Rotations.getYaw(containerCenter);
      double pitch = Rotations.getPitch(containerCenter);
      this.mc.player.setYaw((float)yaw);
      this.mc.player.setPitch((float)pitch);
      Vec3d pos = this.mc.player.getEntityPos();
      this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(pos.x, pos.y, pos.z, (float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
      Vec3d optimalTarget = this.calculateOptimalTargetPoint(this.currentContainer.pos, eyePos);
      Vec3d[] hitPositions = new Vec3d[]{containerCenter, containerCenter.add((double)0.0F, 0.15, (double)0.0F), containerCenter.add((double)0.0F, -0.15, (double)0.0F), optimalTarget};

      for(int i = 0; i < 2; ++i) {
         Vec3d hitPos = hitPositions[timer % hitPositions.length];
         BlockHitResult hitResult = new BlockHitResult(hitPos, optimalFace, this.currentContainer.pos, false);
         ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
         if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
            result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hitResult);
         }

         if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
            if ((Boolean)this.debugMode.get()) {
               this.info("Interaction successful!", new Object[0]);
            }
            break;
         }
      }

   }

   private Vec3d calculateOptimalTargetPoint(BlockPos containerPos, Vec3d eyePos) {
      double heightDiff = (double)containerPos.getY() - eyePos.y;
      double yOffset = (double)0.0F;
      if (heightDiff > (double)1.5F) {
         yOffset = -0.3;
      } else if (heightDiff > (double)0.5F) {
         yOffset = -0.15;
      } else if (heightDiff < (double)-1.5F) {
         yOffset = 0.3;
      } else if (heightDiff < (double)-0.5F) {
         yOffset = 0.15;
      }

      if (this.containerOpenFailures > 0) {
         double variation = (double)(this.containerOpenFailures % 3 - 1) * 0.1;
         yOffset += variation;
      }

      return Vec3d.ofCenter(containerPos).add((double)0.0F, yOffset, (double)0.0F);
   }

   private void stopAllMovement() {
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.backKey.setPressed(false);
      this.mc.options.leftKey.setPressed(false);
      this.mc.options.rightKey.setPressed(false);
      this.mc.options.jumpKey.setPressed(false);
      this.mc.options.sneakKey.setPressed(false);
      this.mc.options.sprintKey.setPressed(false);
   }

   private void handleTransferringItems() {
      if (this.currentContainer != null && this.mc.currentScreen instanceof GenericContainerScreen) {
         Vec3d containerCenter = Vec3d.ofCenter(this.currentContainer.pos);
         double yaw = Rotations.getYaw(containerCenter);
         double pitch = Rotations.getPitch(containerCenter);
         this.mc.player.setYaw((float)yaw);
         this.mc.player.setPitch((float)pitch);
      }

      if (this.isNearInputArea()) {
         this.handleInputTransferringItems();
      } else if (this.isNearOutputArea()) {
         this.handleOutputTransferringItems();
      } else {
         this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
      }

   }

   private void handleInputTransferringItems() {
      if (!(this.mc.currentScreen instanceof GenericContainerScreen)) {
         if (this.currentContainer != null && !this.currentContainer.isEmpty && !this.currentContainer.isFull) {
            boolean inventoryHasSpace = false;

            for(int j = 0; j < 36; ++j) {
               if (this.mc.player.getInventory().getStack(j).isEmpty()) {
                  inventoryHasSpace = true;
                  break;
               }
            }

            if (inventoryHasSpace) {
               this.warning("Container window closed unexpectedly! Reopening...", new Object[0]);
               this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
               this.stateTimer = 5;
               ++this.containerOpenFailures;
               if (this.containerOpenFailures > 3) {
                  this.warning("Failed to reopen container multiple times, skipping", new Object[0]);
                  this.currentContainer = null;
                  this.containerOpenFailures = 0;
                  this.currentState = StashMover.ProcessState.INPUT_PROCESS;
               }

               return;
            }
         }

         this.currentState = StashMover.ProcessState.INPUT_PROCESS;
      } else if (this.stateTimer > 0) {
         --this.stateTimer;
      } else {
         ScreenHandler currentHandler = this.mc.player.currentScreenHandler;
         if (!(currentHandler instanceof GenericContainerScreenHandler)) {
            this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
         } else {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler)currentHandler;
            if ((Boolean)this.onlyShulkers.get()) {
               for(int j = 0; j < 36; ++j) {
                  ItemStack invStack = this.mc.player.getInventory().getStack(j);
                  if (!invStack.isEmpty() && invStack.getCount() > 0 && !this.isShulkerBox(invStack.getItem())) {
                     InvUtils.drop().slot(j);
                     this.info("Dropping non-shulker: " + invStack.getItem().getName().getString(), new Object[0]);
                     this.stateTimer = 5;
                     return;
                  }
               }
            }

            if (this.isInventoryFull()) {
               this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
            } else {
               boolean transferredItem = false;

               for(int i = 0; i < this.currentContainer.totalSlots; ++i) {
                  Slot slot = handler.getSlot(i);
                  ItemStack stack = slot.getStack();
                  if (!stack.isEmpty() && (!(Boolean)this.onlyShulkers.get() || this.isShulkerBox(stack.getItem()))) {
                     this.mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                     transferredItem = true;
                     ++this.itemsTransferred;
                     this.stateTimer = this.transferDelayTicks();
                     return;
                  }
               }

               if (!transferredItem) {
                  boolean containerActuallyEmpty = true;

                  for(int i = 0; i < this.currentContainer.totalSlots; ++i) {
                     Slot slot = handler.getSlot(i);
                     ItemStack stack = slot.getStack();
                     if (!stack.isEmpty() && (!(Boolean)this.onlyShulkers.get() || this.isShulkerBox(stack.getItem()))) {
                        containerActuallyEmpty = false;
                        break;
                     }
                  }

                  if (containerActuallyEmpty) {
                     this.currentContainer.isEmpty = true;
                     this.info("Container is now empty", new Object[0]);
                  }

                  this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
               }

            }
         }
      }
   }

   private void handleClosingContainer() {
      if (this.mc.currentScreen instanceof GenericContainerScreen) {
         this.mc.player.closeHandledScreen();
      }

      this.stateTimer = (Integer)this.closeDelay.get();
      if (this.isNearOutputArea()) {
         if ((Boolean)this.onlyShulkers.get()) {
            for(int i = 0; i < 36; ++i) {
               ItemStack stack = this.mc.player.getInventory().getStack(i);
               if (!stack.isEmpty() && stack.getCount() > 0 && !this.isShulkerBox(stack.getItem())) {
                  InvUtils.drop().slot(i);
                  this.info("Dropped non-shulker at output: " + stack.getItem().getName().getString(), new Object[0]);
                  this.stateTimer = 5;
                  return;
               }
            }
         }

         boolean inventoryEmpty = !this.hasItemsToTransfer();
         if (inventoryEmpty && this.enderChestHasItems && (Boolean)this.fillEnderChest.get()) {
            this.info("Getting items from enderchest to continue depositing", new Object[0]);
            this.enderChestPos = this.findNearbyEnderChest();
            if (this.enderChestPos != null) {
               this.currentState = StashMover.ProcessState.OPENING_ENDERCHEST;
               this.stateTimer = 5;
            } else {
               FindItemResult enderChest = InvUtils.findInHotbar(new Item[]{Items.ENDER_CHEST});
               if (enderChest.found()) {
                  BlockPos placePos = this.findSuitablePlacePos();
                  if (placePos != null) {
                     this.placeEnderChest(placePos, enderChest.slot());
                  }
               }
            }

            return;
         }

         if (inventoryEmpty && !this.enderChestHasItems) {
            this.info("All items deposited, going back to input", new Object[0]);
            this.currentContainer = null;
            this.currentState = StashMover.ProcessState.GOING_BACK;
            return;
         }

         this.currentContainer = null;
         this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
      } else if (this.isNearInputArea()) {
         if ((Boolean)this.onlyShulkers.get()) {
            boolean foundNonShulker = false;

            for(int i = 0; i < 36; ++i) {
               ItemStack stack = this.mc.player.getInventory().getStack(i);
               if (!stack.isEmpty() && stack.getCount() > 0 && !this.isShulkerBox(stack.getItem())) {
                  InvUtils.drop().slot(i);
                  this.info("Dropped non-shulker: " + stack.getItem().getName().getString(), new Object[0]);
                  this.stateTimer = 5;
                  foundNonShulker = true;
                  if (this.currentContainer != null && !this.currentContainer.isEmpty) {
                     this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
                  } else {
                     this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                  }

                  return;
               }
            }

            if (!foundNonShulker) {
               this.info("No non-shulker items to drop", new Object[0]);
            }
         }

         if (this.currentContainer != null && this.currentContainer.isEmpty && (Boolean)this.breakEmptyContainers.get()) {
            this.currentState = StashMover.ProcessState.BREAKING_CONTAINER;
            return;
         }

         if (this.isInventoryFull()) {
            this.info("Inventory full", new Object[0]);
            if ((Boolean)this.fillEnderChest.get() && !this.isEnderChestFull()) {
               this.info("Checking enderchest...", new Object[0]);
               this.findOrPlaceEnderChest();
            } else {
               this.info("Inventory and enderchest full, starting pearl loading", new Object[0]);
               this.currentContainer = null;
               this.currentState = StashMover.ProcessState.LOADING_PEARL;
            }
         } else {
            this.currentContainer = null;
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
         }
      } else {
         this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
      }

   }

   private void handleBreakingContainer() {
      if (this.currentContainer == null) {
         this.currentState = StashMover.ProcessState.INPUT_PROCESS;
      } else {
         this.mc.interactionManager.updateBlockBreakingProgress(this.currentContainer.pos, Direction.UP);
         if (this.mc.world.getBlockState(this.currentContainer.pos).isAir()) {
            inputContainers.remove(this.currentContainer);
            ++this.containersProcessed;
            this.currentContainer = null;
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
            this.stateTimer = (Integer)this.moveDelay.get();
         }

      }
   }

   private void findOrPlaceEnderChest() {
      this.enderChestPos = this.findNearbyEnderChest();
      if (this.enderChestPos != null) {
         this.currentState = StashMover.ProcessState.OPENING_ENDERCHEST;
         this.stateTimer = 0;
         GoalNear goal = new GoalNear(this.enderChestPos, 2);
         BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
         this.info("Moving to enderchest", new Object[0]);
      } else {
         FindItemResult enderChest = InvUtils.findInHotbar(new Item[]{Items.ENDER_CHEST});
         if (enderChest.found()) {
            BlockPos placePos = this.findSuitablePlacePos();
            if (placePos != null) {
               this.placeEnderChest(placePos, enderChest.slot());
            }
         } else {
            this.currentContainer = null;
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
         }
      }

   }

   private void handleOpeningEnderChest() {
      if (this.enderChestPos == null) {
         this.enderChestPos = this.findNearbyEnderChest();
         if (this.enderChestPos == null) {
            FindItemResult enderChest = InvUtils.findInHotbar(new Item[]{Items.ENDER_CHEST});
            if (enderChest.found()) {
               BlockPos placePos = this.findSuitablePlacePos();
               if (placePos != null) {
                  this.placeEnderChest(placePos, enderChest.slot());
                  return;
               }
            }

            this.warning("No enderchest found or available to place", new Object[0]);
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
            return;
         }
      }

      if (this.mc.currentScreen instanceof GenericContainerScreen) {
         this.containerOpenFailures = 0;
         if (this.isNearOutputArea()) {
            this.currentState = StashMover.ProcessState.EMPTYING_ENDERCHEST;
         } else {
            this.currentState = StashMover.ProcessState.FILLING_ENDERCHEST;
         }

         this.stateTimer = this.transferDelayTicks();
      } else {
         Vec3d eyePos = this.mc.player.getEyePos();
         double distance = eyePos.distanceTo(Vec3d.ofCenter(this.enderChestPos));
         if (distance <= (double)4.5F) {
            Vec3d enderChestCenter = Vec3d.ofCenter(this.enderChestPos);
            double targetYaw = Rotations.getYaw(enderChestCenter);
            double targetPitch = Rotations.getPitch(enderChestCenter);
            this.mc.player.setYaw((float)targetYaw);
            this.mc.player.setPitch((float)targetPitch);
            BlockHitResult hitResult = new BlockHitResult(enderChestCenter, Direction.UP, this.enderChestPos, false);
            this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
            this.stateTimer = 10;
         } else if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            GoalNear goal = new GoalNear(this.enderChestPos, 2);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            this.info("Moving to enderchest", new Object[0]);
         }

         if (this.stateTimer > 0) {
            --this.stateTimer;
         }

      }
   }

   private void handleFillingEnderChest() {
      if (!(this.mc.currentScreen instanceof GenericContainerScreen)) {
         this.checkNextStepAfterEnderChest();
      } else if (this.stateTimer > 0) {
         --this.stateTimer;
      } else {
         ScreenHandler var2 = this.mc.player.currentScreenHandler;
         if (!(var2 instanceof GenericContainerScreenHandler)) {
            this.mc.player.closeHandledScreen();
            this.checkNextStepAfterEnderChest();
         } else {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler)var2;
            boolean var7 = false;
            boolean enderChestHasSpace = false;

            for(int j = 0; j < 27; ++j) {
               if (handler.getSlot(j).getStack().isEmpty()) {
                  enderChestHasSpace = true;
                  break;
               }
            }

            if (!enderChestHasSpace) {
               this.enderChestFull = true;
               this.info("Enderchest is full", new Object[0]);
               this.mc.player.closeHandledScreen();
               this.stateTimer = (Integer)this.closeDelay.get();
               this.checkNextStepAfterEnderChest();
            } else {
               for(int i = 27; i < 63; ++i) {
                  Slot slot = handler.getSlot(i);
                  ItemStack stack = slot.getStack();
                  if (!stack.isEmpty() && (!(Boolean)this.onlyShulkers.get() || this.isShulkerBox(stack.getItem()))) {
                     this.mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                     var7 = true;
                     this.enderChestHasItems = true;
                     this.stateTimer = this.transferDelayTicks();
                     this.info("Transferred item to enderchest", new Object[0]);
                     return;
                  }
               }

               if (!var7) {
                  boolean inventoryHasItems = false;

                  for(int i = 0; i < 36; ++i) {
                     ItemStack invStack = this.mc.player.getInventory().getStack(i);
                     if (!invStack.isEmpty() && (!(Boolean)this.onlyShulkers.get() || this.isShulkerBox(invStack.getItem()))) {
                        inventoryHasItems = true;
                        break;
                     }
                  }

                  if (inventoryHasItems) {
                     this.stateTimer = this.transferDelayTicks();
                     return;
                  }

                  this.mc.player.closeHandledScreen();
                  this.stateTimer = (Integer)this.closeDelay.get();
                  this.checkNextStepAfterEnderChest();
               }

            }
         }
      }
   }

   private void checkNextStepAfterEnderChest() {
      if (this.isInventoryFull() && this.enderChestFull) {
         this.info("Both inventory and enderchest are full, starting pearl loading", new Object[0]);
         this.currentContainer = null;
         this.currentState = StashMover.ProcessState.LOADING_PEARL;
      } else {
         this.currentContainer = null;
         this.currentState = StashMover.ProcessState.INPUT_PROCESS;
      }

   }

   private void handleEmptyingEnderChest() {
      if (!(this.mc.currentScreen instanceof GenericContainerScreen)) {
         if (this.hasItemsToTransfer()) {
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
         } else {
            this.enderChestEmptied = true;
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
         }

      } else if (this.stateTimer > 0) {
         --this.stateTimer;
      } else {
         ScreenHandler var2 = this.mc.player.currentScreenHandler;
         if (!(var2 instanceof GenericContainerScreenHandler)) {
            this.mc.player.closeHandledScreen();
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
         } else {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler)var2;
            boolean var8 = false;
            boolean inventoryHasSpace = false;

            for(int i = 0; i < 36; ++i) {
               if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                  inventoryHasSpace = true;
                  break;
               }
            }

            if (!inventoryHasSpace) {
               this.info("Inventory full, closing enderchest to deposit items", new Object[0]);
               this.mc.player.closeHandledScreen();
               this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
            } else {
               boolean enderChestIsEmpty = true;

               for(int i = 0; i < 27; ++i) {
                  if (!handler.getSlot(i).getStack().isEmpty()) {
                     enderChestIsEmpty = false;
                     break;
                  }
               }

               if (enderChestIsEmpty) {
                  this.enderChestHasItems = false;
                  this.enderChestEmptied = true;
                  this.info("Enderchest is empty, all items transferred", new Object[0]);
                  this.mc.player.closeHandledScreen();
                  this.stateTimer = (Integer)this.closeDelay.get();
                  this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
               } else {
                  for(int i = 0; i < 27; ++i) {
                     Slot slot = handler.getSlot(i);
                     ItemStack stack = slot.getStack();
                     if (!stack.isEmpty() && (!(Boolean)this.onlyShulkers.get() || this.isShulkerBox(stack.getItem()))) {
                        this.mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                        var8 = true;
                        this.stateTimer = this.transferDelayTicks();
                        this.info("Retrieved item from enderchest", new Object[0]);
                        return;
                     }
                  }

                  if (!var8 && !enderChestIsEmpty) {
                     if (inventoryHasSpace && (Boolean)this.onlyShulkers.get()) {
                        this.info("Only non-shulkers left in enderchest", new Object[0]);
                        this.enderChestHasItems = false;
                        this.mc.player.closeHandledScreen();
                        this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                     } else {
                        this.info("Inventory full, depositing items first", new Object[0]);
                        this.mc.player.closeHandledScreen();
                        this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                     }
                  }

               }
            }
         }
      }
   }

   private void handlePearlLoading() {
      if (this.outputTravelMethod.get() != StashMover.OutputTravelMethod.PEARL) {
         this.handleCommandTravelToOutput();
      } else {
         if (!this.waitingForPearl) {
            this.sendPearlCommand();
            this.waitingForPearl = true;
            this.lastPearlMessageTime = System.currentTimeMillis();
            this.pearlRetryCount = 0;
            this.initialPlayerPos = this.mc.player.getEntityPos();
         }

         Vec3d currentPos = this.mc.player.getEntityPos();
         double distance = currentPos.distanceTo(this.initialPlayerPos);
         if (distance > (double)100.0F) {
            if (this.isNearOutputArea()) {
               this.info("Successfully pearl loaded to output area!", new Object[0]);
               this.waitingForPearl = false;
               this.ensureOffhandHasItem();
               this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
               this.hasThrownPearl = false;
               this.hasPlacedShulker = false;
               this.isGoingToInput = false;
               return;
            }

            if (!this.isNearInputArea()) {
               this.warning("Teleported but not to output area, retrying...", new Object[0]);
               this.waitingForPearl = false;
               this.currentState = StashMover.ProcessState.LOADING_PEARL;
               return;
            }
         }

         if (System.currentTimeMillis() - this.lastPearlMessageTime > (long)((Integer)this.pearlTimeout.get() * 1000)) {
            if (this.pearlRetryCount < (Integer)this.maxRetries.get()) {
               ++this.pearlRetryCount;
               this.info("Pearl loading timeout, retrying (attempt " + this.pearlRetryCount + "/" + String.valueOf(this.maxRetries.get()) + ")", new Object[0]);
               this.sendPearlCommand();
               this.lastPearlMessageTime = System.currentTimeMillis();
            } else {
               this.error("Pearl loading failed after " + String.valueOf(this.maxRetries.get()) + " retries!", new Object[0]);
               this.currentState = StashMover.ProcessState.IDLE;
               this.waitingForPearl = false;
            }
         }

      }
   }

   private void handleCommandTravelToOutput() {
      this.handleHomeTravel(true);
   }

   private void handleCommandTravelToInput() {
      this.handleHomeTravel(false);
   }

   private void handleHomeTravel(boolean toOutput) {
      boolean useHome = toOutput ? this.outputTravelMethod.get() == StashMover.OutputTravelMethod.HOME : this.goBackMethod.get() == StashMover.GoBackMethod.HOME;
      if (!this.waitingForPearl) {
         if (useHome && this.homeCommandOnCooldown()) {
            this.stateTimer = 20;
         } else {
            String cmd = useHome ? "/home " + (toOutput ? (String)this.homeNameToOutput.get() : (String)this.homeNameToInput.get()) : "/tpa " + (toOutput ? (String)this.tpaTargetOutput.get() : (String)this.tpaTargetInput.get());
            ChatUtils.sendPlayerMsg(cmd);
            this.info("Sent travel command: " + cmd, new Object[0]);
            if (useHome) {
               this.lastHomeCommandSentTime = System.currentTimeMillis();
            }

            this.waitingForPearl = true;
            this.lastPearlMessageTime = System.currentTimeMillis();
            this.pearlRetryCount = 0;
            this.initialPlayerPos = this.mc.player.getEntityPos();
         }
      } else {
         Vec3d currentPos = this.mc.player.getEntityPos();
         double distance = currentPos.distanceTo(this.initialPlayerPos);
         boolean atTarget = toOutput ? this.isNearOutputArea() : this.isNearInputArea();
         boolean atOther = toOutput ? this.isNearInputArea() : this.isNearOutputArea();
         if (distance > (double)100.0F || atTarget) {
            if (atTarget) {
               this.info("Arrived at " + (toOutput ? "output" : "input") + " area!", new Object[0]);
               this.waitingForPearl = false;
               this.currentState = toOutput ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
               this.stateTimer = 5;
               return;
            }

            if (!atOther) {
               this.warning("Teleported but not to expected area, retrying...", new Object[0]);
               this.waitingForPearl = false;
               return;
            }
         }

         double teleportWaitMs = (Double)this.homeTeleportWait.get() * (double)1000.0F;
         if ((double)(System.currentTimeMillis() - this.lastPearlMessageTime) > teleportWaitMs) {
            if (useHome) {
               if ((Boolean)this.debugMode.get()) {
                  this.info("Home command didn't teleport us yet, will recheck cooldown", new Object[0]);
               }

               this.waitingForPearl = false;
            } else if (this.pearlRetryCount < (Integer)this.maxRetries.get()) {
               ++this.pearlRetryCount;
               this.info("Travel timeout, retrying (attempt " + this.pearlRetryCount + "/" + String.valueOf(this.maxRetries.get()) + ")", new Object[0]);
               this.waitingForPearl = false;
            } else {
               this.error("Travel failed after " + String.valueOf(this.maxRetries.get()) + " retries!", new Object[0]);
               this.currentState = StashMover.ProcessState.IDLE;
               this.waitingForPearl = false;
            }
         }

      }
   }

   private void sendPearlCommand() {
      String randomSuffix = this.generateRandomString(8);
      String command = String.format("/msg %s %s %s", this.pearlPlayerName.get(), this.pearlCommand.get(), randomSuffix);
      ChatUtils.sendPlayerMsg(command);
      this.lastRandomString = randomSuffix;
      this.info("Sent pearl command: " + command, new Object[0]);
   }

   private void handleResetPearlPickup() {
      BlockPos pickupPos;
      if (this.isGoingToInput) {
         pickupPos = (BlockPos)this.inputPearlPickupPos.get();
      } else {
         pickupPos = (BlockPos)this.outputPearlPickupPos.get();
      }

      this.ensureOffhandHasItem();
      double distance = this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pickupPos));
      if (distance > (double)3.0F) {
         GoalBlock goal = new GoalBlock(pickupPos);
         BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
         this.stateTimer = (Integer)this.moveDelay.get();
      } else {
         this.currentState = StashMover.ProcessState.RESET_PEARL_PLACE_SHULKER;
         this.stateTimer = 10;
      }

   }

   private void handleResetPearlPlaceShulker() {
      if (!this.hasPlacedShulker) {
         ItemStack slot0 = this.mc.player.getInventory().getStack(0);
         if (slot0.getItem() == Items.ENDER_PEARL) {
            for(int i = 1; i < 36; ++i) {
               if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                  InvUtils.move().from(36).to(i);
                  this.info("Moved pearl from slot 0 temporarily", new Object[0]);
                  break;
               }
            }
         }

         this.ensureOffhandHasItem();
         slot0 = this.mc.player.getInventory().getStack(0);
         if (this.isShulkerBox(slot0.getItem())) {
            this.offhandBackup = this.mc.player.getOffHandStack().copy();
            if (!this.mc.player.getOffHandStack().isEmpty()) {
               for(int i = 1; i < 36; ++i) {
                  if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                     InvUtils.move().fromOffhand().to(i);
                     break;
                  }
               }
            }

            InvUtils.move().from(36).toOffhand();
            this.hasPlacedShulker = true;
            this.info("Placed shulker in offhand, now walking to pressure plate", new Object[0]);
            BlockPos pickupPos = this.isGoingToInput ? (BlockPos)this.inputPearlPickupPos.get() : (BlockPos)this.outputPearlPickupPos.get();
            GoalBlock goal = new GoalBlock(pickupPos);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            this.stateTimer = 30;
         } else {
            this.info("No shulker in slot 0, continuing without offhand shulker", new Object[0]);
            this.hasPlacedShulker = true;
            this.stateTimer = 5;
         }
      } else {
         FindItemResult pearl = InvUtils.find(new Item[]{Items.ENDER_PEARL});
         if (pearl.found()) {
            this.info("Pearl picked up, moving to throw location", new Object[0]);
            this.currentState = StashMover.ProcessState.RESET_PEARL_APPROACH;
            this.stateTimer = 5;
         } else {
            BlockPos pickupPos = this.isGoingToInput ? (BlockPos)this.inputPearlPickupPos.get() : (BlockPos)this.outputPearlPickupPos.get();
            double distance = this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pickupPos));
            if (distance > (double)1.0F) {
               GoalBlock goal = new GoalBlock(pickupPos);
               BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            }

            this.stateTimer = 5;
         }
      }

   }

   private void handleResetPearlApproach() {
      BlockPos throwPos;
      if (this.isGoingToInput) {
         throwPos = (BlockPos)this.inputPearlThrowPos.get();
      } else {
         throwPos = (BlockPos)this.outputPearlThrowPos.get();
      }

      double distance = this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(throwPos));
      if (distance <= (double)1.5F) {
         if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
         }

         this.lastBaritoneGoal = null;
         this.safeRetreatPos = this.mc.player.getBlockPos();
         this.info("Starting precise positioning from adjacent block - stored safe retreat position", new Object[0]);
         this.currentState = StashMover.ProcessState.RESET_PEARL_PREPARE;
         this.stateTimer = 5;
      } else if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
         this.stateTimer = 5;
      } else {
         BlockPos goalPos = null;
         this.safeRetreatPos = null;
         Direction[] preferredDirections = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

         for(Direction dir : preferredDirections) {
            BlockPos adjacent = throwPos.offset(dir);
            BlockState adjacentState = this.mc.world.getBlockState(adjacent);
            BlockState belowState = this.mc.world.getBlockState(adjacent.down());
            if (adjacentState.isAir() && belowState.isSolidBlock(this.mc.world, adjacent.down())) {
               goalPos = adjacent;
               this.safeRetreatPos = adjacent;
               this.approachDirection = dir.getOpposite();
               this.info("Found safe approach position from " + String.valueOf(dir) + " side", new Object[0]);
               break;
            }
         }

         if (goalPos == null) {
            for(Direction dir : preferredDirections) {
               BlockPos candidate = throwPos.offset(dir, 2);
               BlockState state = this.mc.world.getBlockState(candidate);
               BlockState belowState = this.mc.world.getBlockState(candidate.down());
               if (state.isAir() && belowState.isSolidBlock(this.mc.world, candidate.down())) {
                  goalPos = candidate;
                  this.safeRetreatPos = throwPos.offset(dir);
                  this.approachDirection = dir.getOpposite();
                  this.info("Using fallback approach position from " + String.valueOf(dir) + " side", new Object[0]);
                  break;
               }
            }
         }

         if (goalPos == null) {
            goalPos = throwPos.offset(Direction.NORTH, 2);
            this.safeRetreatPos = throwPos.offset(Direction.NORTH);
            this.approachDirection = Direction.SOUTH;
            this.warning("Using fallback approach position", new Object[0]);
         }

         if (this.lastBaritoneGoal == null || !this.lastBaritoneGoal.equals(goalPos)) {
            this.lastBaritoneGoal = goalPos;
            GoalBlock goal = new GoalBlock(goalPos);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            this.info("Pathing to approach position", new Object[0]);
         }

         this.stateTimer = (Integer)this.moveDelay.get();
      }
   }

   private void handleResetPearlPrepare() {
      BlockPos throwPos;
      double throwYaw;
      double throwPitch;
      if (this.isGoingToInput) {
         throwPos = (BlockPos)this.inputPearlThrowPos.get();
         throwYaw = (Double)this.inputPearlThrowYaw.get();
         throwPitch = (Double)this.inputPearlThrowPitch.get();
      } else {
         throwPos = (BlockPos)this.outputPearlThrowPos.get();
         throwYaw = (Double)this.outputPearlThrowYaw.get();
         throwPitch = (Double)this.outputPearlThrowPitch.get();
      }

      this.mc.options.sneakKey.setPressed(true);
      BlockState throwState = this.mc.world.getBlockState(throwPos);
      boolean isTrapdoor = throwState.getBlock() instanceof TrapdoorBlock;
      double targetX = (double)throwPos.getX() + (double)0.5F;
      double targetZ = (double)throwPos.getZ() + (double)0.5F;
      double dx = targetX - this.mc.player.getX();
      double dz = targetZ - this.mc.player.getZ();
      double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
      double requiredYaw = Math.toDegrees(Math.atan2(-dx, dz));
      if (this.approachDirection == null && horizontalDistance > 0.1) {
         double approachAngle = Math.toDegrees(Math.atan2(dx, -dz));
         if (Math.abs(approachAngle) <= (double)45.0F) {
            this.approachDirection = Direction.NORTH;
         } else if (Math.abs(approachAngle) >= (double)135.0F) {
            this.approachDirection = Direction.SOUTH;
         } else if (approachAngle > (double)45.0F && approachAngle < (double)135.0F) {
            this.approachDirection = Direction.EAST;
         } else {
            this.approachDirection = Direction.WEST;
         }

         this.info("Approach direction: " + String.valueOf(this.approachDirection), new Object[0]);
      }

      double requiredPitch = (double)15.0F;
      this.mc.player.setYaw((float)requiredYaw);
      this.mc.player.setPitch((float)requiredPitch);
      boolean inPosition = horizontalDistance < (Double)this.positionTolerance.get();
      if (!inPosition) {
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(true);
         this.mc.options.forwardKey.setPressed(true);
         ++this.stateTimer;
         if (this.stateTimer % 20 == 0) {
            this.info(String.format("Approaching water (%.2f blocks away) Yaw: %.1f", horizontalDistance, requiredYaw), new Object[0]);
         }

         if (this.stateTimer > 60 && horizontalDistance > (double)2.0F) {
            if (this.stateTimer % 40 < 5) {
               this.mc.options.forwardKey.setPressed(false);
               this.mc.options.backKey.setPressed(true);
               this.info("Backing up briefly to unstick", new Object[0]);
            } else {
               this.mc.options.backKey.setPressed(false);
               this.mc.options.forwardKey.setPressed(true);
            }
         }

         if (this.stateTimer > 120) {
            if (horizontalDistance < (Double)this.positionTolerance.get() * (double)1.5F) {
               this.info("Close enough after timeout", new Object[0]);
               inPosition = true;
            } else {
               this.info(String.format("Still approaching (%.2f blocks away)", horizontalDistance), new Object[0]);
            }
         }

      } else {
         if (inPosition) {
            this.mc.options.forwardKey.setPressed(false);
            this.mc.options.backKey.setPressed(false);
            this.mc.options.leftKey.setPressed(false);
            this.mc.options.rightKey.setPressed(false);
            this.mc.options.sneakKey.setPressed(true);
            Rotations.rotate(throwYaw, throwPitch);
            this.mc.player.setYaw((float)throwYaw);
            this.mc.player.setPitch((float)throwPitch);
            String var10001 = String.format("%.3f", throwYaw);
            this.info("Switching to throw angle - Yaw: " + var10001 + " Pitch: " + String.format("%.3f", throwPitch), new Object[0]);
            this.info("In position, ready to throw", new Object[0]);
            this.currentState = StashMover.ProcessState.RESET_PEARL_THROW;
            this.stateTimer = 5;
         }

      }
   }

   private void handleResetPearlThrow() {
      BlockPos throwPos;
      double throwYaw;
      double throwPitch;
      if (this.isGoingToInput) {
         throwPos = (BlockPos)this.inputPearlThrowPos.get();
         throwYaw = (Double)this.inputPearlThrowYaw.get();
         throwPitch = (Double)this.inputPearlThrowPitch.get();
      } else {
         throwPos = (BlockPos)this.outputPearlThrowPos.get();
         throwYaw = (Double)this.outputPearlThrowYaw.get();
         throwPitch = (Double)this.outputPearlThrowPitch.get();
      }

      if (!this.hasThrownPearl) {
         this.mc.options.sneakKey.setPressed(true);
         BlockState throwState = this.mc.world.getBlockState(throwPos);
         boolean isTrapdoor = throwState.getBlock() instanceof TrapdoorBlock;
         if (!this.rotationSet) {
            Rotations.rotate(throwYaw, throwPitch);
            this.mc.player.setYaw((float)throwYaw);
            this.mc.player.setPitch((float)throwPitch);
            String var10001 = String.format("%.3f", throwYaw);
            this.info("Set exact throw angle: Yaw=" + var10001 + " Pitch=" + String.format("%.3f", throwPitch), new Object[0]);
            this.rotationSet = true;
            this.rotationStabilizationTimer = 10;
            return;
         }

         if (this.rotationStabilizationTimer > 0) {
            Rotations.rotate(throwYaw, throwPitch);
            this.mc.player.setYaw((float)throwYaw);
            this.mc.player.setPitch((float)throwPitch);
            --this.rotationStabilizationTimer;
            if (this.rotationStabilizationTimer == 0) {
               this.info("Rotation stabilized, ready to throw", new Object[0]);
            }

            return;
         }

         FindItemResult pearl = InvUtils.find(new Item[]{Items.ENDER_PEARL});
         if (pearl.found()) {
            if (this.mc.player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
               this.previousSlot = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
               InvUtils.swap(pearl.slot(), false);
               this.stateTimer = 3;
               return;
            }

            if (this.mc.player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
               this.warning("Pearl not in hand, retrying swap", new Object[0]);
               return;
            }

            if (this.stateTimer > 1) {
               --this.stateTimer;
               return;
            }

            this.initialPlayerPos = this.mc.player.getEntityPos();
            this.info("Throwing pearl", new Object[0]);
            this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
            this.hasThrownPearl = true;
            this.pearlThrowTime = System.currentTimeMillis();
            this.info("Pearl thrown! Walking back immediately!", new Object[0]);
            this.mc.options.sneakKey.setPressed(true);
            this.mc.options.forwardKey.setPressed(false);
            this.mc.options.leftKey.setPressed(false);
            this.mc.options.rightKey.setPressed(false);
            this.mc.options.sprintKey.setPressed(false);
            this.mc.options.backKey.setPressed(true);
            ((InputAccessor)this.mc.player.input).setMovementForward(-1.0F);
            ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
            this.rotationSet = false;
            this.stateTimer = 20;
            this.currentState = StashMover.ProcessState.RESET_PEARL_WAIT;
         } else {
            this.error("No ender pearl found!", new Object[0]);
            this.mc.options.sneakKey.setPressed(false);
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
         }
      }

   }

   private void handleResetPearlWait() {
      if (this.stateTimer > 0) {
         this.mc.options.sneakKey.setPressed(true);
         double throwPitch;
         double throwYaw;
         if (this.isGoingToInput) {
            throwYaw = (Double)this.inputPearlThrowYaw.get();
            throwPitch = (Double)this.inputPearlThrowPitch.get();
         } else {
            throwYaw = (Double)this.outputPearlThrowYaw.get();
            throwPitch = (Double)this.outputPearlThrowPitch.get();
         }

         Rotations.rotate(throwYaw, throwPitch);
         this.mc.player.setYaw((float)throwYaw);
         this.mc.player.setPitch((float)throwPitch);
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.sprintKey.setPressed(false);
         this.mc.options.backKey.setPressed(true);
         ((InputAccessor)this.mc.player.input).setMovementForward(-1.0F);
         ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
         if (this.safeRetreatPos != null) {
            double distanceToSafe = this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(this.safeRetreatPos));
            if (distanceToSafe < (double)0.5F) {
               this.info("Reached safe position!", new Object[0]);
               this.stateTimer = 0;
            }
         }

         --this.stateTimer;
         if (this.stateTimer == 19) {
            this.info("Walking backward to safe position!", new Object[0]);
         } else if (this.stateTimer == 10) {
            this.info("Still backing up...", new Object[0]);
         }

         if (this.stateTimer == 0) {
            this.info("Safe distance reached", new Object[0]);
            this.mc.options.forwardKey.setPressed(false);
            this.mc.options.backKey.setPressed(false);
            ((InputAccessor)this.mc.player.input).setMovementForward(0.0F);
            this.mc.options.sneakKey.setPressed(false);
            Rotations.rotate((double)this.mc.player.getYaw(), (double)this.mc.player.getPitch());
            if (this.initialPlayerPos == null) {
               this.initialPlayerPos = this.mc.player.getEntityPos();
            }
         }

      } else {
         this.mc.options.sneakKey.setPressed(false);
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.sprintKey.setPressed(false);
         Rotations.rotate((double)this.mc.player.getYaw(), (double)this.mc.player.getPitch());
         if (System.currentTimeMillis() - this.pearlThrowTime > (long)((Integer)this.pearlWaitTime.get() * 1000)) {
            if (this.isGoingToInput) {
               BlockPos throwPos = (BlockPos)this.inputPearlThrowPos.get();
            } else {
               BlockPos throwPos = (BlockPos)this.outputPearlThrowPos.get();
            }

            double distance = this.mc.player.getEntityPos().distanceTo(this.initialPlayerPos);
            FindItemResult pearlCheck = InvUtils.find(new Item[]{Items.ENDER_PEARL});
            boolean stillHasPearl = pearlCheck.found() && pearlCheck.count() > 0;
            if (distance < (double)5.0F && !stillHasPearl) {
               this.info("Pearl successfully placed in stasis (no pearl in inventory)", new Object[0]);
               this.restoreOffhandItem();
               if (this.previousSlot >= 0 && this.previousSlot < 9) {
                  ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(this.previousSlot);
                  this.previousSlot = -1;
               }

               this.hasThrownPearl = false;
               this.hasPlacedShulker = false;
               this.pearlFailRetries = 0;
               this.approachDirection = null;
               this.lastBaritoneGoal = null;
               this.rotationSet = false;
               this.rotationStabilizationTimer = 0;
               this.safeRetreatPos = null;
               Rotations.rotate((double)this.mc.player.getYaw(), (double)this.mc.player.getPitch());
               if (this.isNearInputArea()) {
                  this.info("Continuing to input process", new Object[0]);
                  this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                  this.findNextInputContainer();
               } else if (this.isNearOutputArea()) {
                  this.info("Continuing to output process", new Object[0]);
                  this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                  this.stateTimer = 5;
               } else {
                  this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
               }
            } else {
               this.warning("Pearl was loaded! Teleportation detected", new Object[0]);
               ++this.pearlFailRetries;
               if (this.pearlFailRetries < (Integer)this.maxRetries.get()) {
                  this.warning("Pearl throw failed, retrying (attempt " + this.pearlFailRetries + "/" + String.valueOf(this.maxRetries.get()) + ")", new Object[0]);
                  this.restoreOffhandItem();
                  this.hasThrownPearl = false;
                  this.hasPlacedShulker = false;
                  this.rotationSet = false;
                  this.rotationStabilizationTimer = 0;
                  this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
               } else {
                  this.error("Pearl throw failed after " + String.valueOf(this.maxRetries.get()) + " attempts!", new Object[0]);
                  this.restoreOffhandItem();
                  this.currentState = StashMover.ProcessState.IDLE;
               }
            }
         }

      }
   }

   private void restoreOffhandItem() {
      ItemStack offhandItem = this.mc.player.getOffHandStack();
      if (!offhandItem.isEmpty() && this.isShulkerBox(offhandItem.getItem())) {
         this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, this.mc.player);
         this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 36, 0, SlotActionType.PICKUP, this.mc.player);
         this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, this.mc.player);
         this.info("Moved shulker back to hotbar slot 0", new Object[0]);
      }

      if (!this.offhandBackup.isEmpty()) {
         for(int i = 0; i < 36; ++i) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (ItemStack.areEqual(stack, this.offhandBackup)) {
               this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, this.mc.player);
               this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 0, SlotActionType.PICKUP, this.mc.player);
               this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, this.mc.player);
               this.info("Restored original offhand item", new Object[0]);
               break;
            }
         }
      }

      this.offhandBackup = ItemStack.EMPTY;
   }

   private void handleOutputProcess() {
      if (this.stateTimer > 0) {
         --this.stateTimer;
         Rotations.rotate((double)this.mc.player.getYaw(), (double)this.mc.player.getPitch());
      } else {
         this.enderChestFull = false;
         if (this.hasItemsToTransfer()) {
            if (this.currentContainer == null) {
               this.findNextOutputContainer();
            } else {
               this.moveToContainer(this.currentContainer);
            }

         } else {
            if ((Boolean)this.fillEnderChest.get()) {
               if (!this.enderChestEmptied) {
                  this.info("Inventory empty, checking enderchest for items...", new Object[0]);
                  this.enderChestPos = this.findNearbyEnderChest();
                  if (this.enderChestPos != null) {
                     this.currentState = StashMover.ProcessState.OPENING_ENDERCHEST;
                     this.stateTimer = 5;
                     return;
                  }

                  FindItemResult enderChest = InvUtils.findInHotbar(new Item[]{Items.ENDER_CHEST});
                  if (enderChest.found()) {
                     BlockPos placePos = this.findSuitablePlacePos();
                     if (placePos != null) {
                        this.info("Placing enderchest to check for items", new Object[0]);
                        this.placeEnderChest(placePos, enderChest.slot());
                        return;
                     }
                  }

                  this.warning("No enderchest available, skipping enderchest check", new Object[0]);
                  this.enderChestEmptied = true;
               } else {
                  this.info("All items deposited and enderchest verified empty, going back to input", new Object[0]);
                  this.currentState = StashMover.ProcessState.GOING_BACK;
                  this.enderChestEmptied = false;
               }
            } else {
               this.info("All items deposited, going back to input", new Object[0]);
               this.currentState = StashMover.ProcessState.GOING_BACK;
            }

         }
      }
   }

   private void findNextOutputContainer() {
      this.currentContainer = (ContainerInfo)outputContainers.stream().filter((c) -> !c.isFull).min(Comparator.comparingDouble((c) -> this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(c.pos)))).orElse(null);
      if (this.currentContainer == null) {
         this.info("All output containers full, rescanning...", new Object[0]);
         this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false);
         this.currentContainer = (ContainerInfo)outputContainers.stream().filter((c) -> !c.isFull).min(Comparator.comparingDouble((c) -> this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(c.pos)))).orElse(null);
         if (this.currentContainer == null) {
            this.warning("All output containers are still full! Going back to input.", new Object[0]);
            this.currentState = StashMover.ProcessState.GOING_BACK;
         } else {
            this.info("Found available container after rescan", new Object[0]);
            this.moveToContainer(this.currentContainer);
         }
      } else {
         this.info("Moving to output container", new Object[0]);
         this.moveToContainer(this.currentContainer);
      }

   }

   private void handleOutputTransferringItems() {
      if (!(this.mc.currentScreen instanceof GenericContainerScreen)) {
         if (this.currentContainer != null && !this.currentContainer.isFull) {
            boolean hasItems = false;

            for(int i = 0; i < 36; ++i) {
               if (!this.mc.player.getInventory().getStack(i).isEmpty()) {
                  hasItems = true;
                  break;
               }
            }

            if (hasItems) {
               this.warning("Container window closed unexpectedly! Reopening...", new Object[0]);
               this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
               this.stateTimer = 5;
               ++this.containerOpenFailures;
               if (this.containerOpenFailures > 3) {
                  this.warning("Failed to reopen container multiple times, marking as full", new Object[0]);
                  this.currentContainer.isFull = true;
                  this.currentContainer = null;
                  this.containerOpenFailures = 0;
                  this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
               }

               return;
            }
         }

         this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
      } else if (this.stateTimer > 0) {
         --this.stateTimer;
      } else {
         ScreenHandler currentHandler = this.mc.player.currentScreenHandler;
         if (!(currentHandler instanceof GenericContainerScreenHandler)) {
            this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
         } else {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler)currentHandler;
            boolean transferredItem = false;
            boolean containerHasSpace = false;

            for(int i = 0; i < this.currentContainer.totalSlots; ++i) {
               if (handler.getSlot(i).getStack().isEmpty()) {
                  containerHasSpace = true;
                  break;
               }
            }

            if (!containerHasSpace) {
               this.currentContainer.isFull = true;
               this.info("Container is now full", new Object[0]);
               this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
            } else {
               boolean hasItems = false;

               for(int i = 0; i < 36; ++i) {
                  if (!this.mc.player.getInventory().getStack(i).isEmpty()) {
                     hasItems = true;
                     break;
                  }
               }

               if (!hasItems) {
                  this.info("No items left to transfer", new Object[0]);
                  this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
               } else {
                  int playerInventoryStart = this.currentContainer.totalSlots;

                  for(int i = playerInventoryStart; i < playerInventoryStart + 36; ++i) {
                     Slot slot = handler.getSlot(i);
                     ItemStack stack = slot.getStack();
                     if (!stack.isEmpty() && (!(Boolean)this.onlyShulkers.get() || this.isShulkerBox(stack.getItem()))) {
                        this.mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                        transferredItem = true;
                        ++this.itemsTransferred;
                        this.stateTimer = this.transferDelayTicks();
                        return;
                     }
                  }

                  if (!transferredItem) {
                     boolean inventoryEmpty = true;

                     for(int i = 0; i < 36; ++i) {
                        if (!this.mc.player.getInventory().getStack(i).isEmpty()) {
                           inventoryEmpty = false;
                           break;
                        }
                     }

                     if (inventoryEmpty) {
                        if (this.enderChestHasItems && (Boolean)this.fillEnderChest.get()) {
                           this.info("Inventory empty but enderchest has items, retrieving from enderchest", new Object[0]);
                           this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                        } else {
                           this.info("Inventory and enderchest empty", new Object[0]);
                           this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                        }
                     } else {
                        this.currentContainer.isFull = true;
                        this.info("Container is now full", new Object[0]);
                        this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                     }
                  }

               }
            }
         }
      }
   }

   private boolean hasItemsInEnderChest() {
      return this.enderChestHasItems && !this.enderChestEmptied;
   }

   private void handleGoingBack() {
      switch (((GoBackMethod)this.goBackMethod.get()).ordinal()) {
         case 0: {
            if (!this.waitingForRespawn) {
               String killCommand = "/kill";
               if (this.killRetryCount > 0) {
                  killCommand = "/kill " + this.generateRandomString(6);
               }

               ChatUtils.sendPlayerMsg(killCommand);
               this.info("Sent kill command: " + killCommand, new Object[0]);
               this.waitingForRespawn = true;
               this.lastKillTime = System.currentTimeMillis();
               this.initialPlayerPos = this.mc.player.getEntityPos();
            }

            if (this.mc.player.isDead() || this.mc.player.getHealth() <= 0.0F) {
               this.mc.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(Mode.PERFORM_RESPAWN));
               this.info("Sent respawn packet", new Object[0]);
            }

            Vec3d currentPos = this.mc.player.getEntityPos();
            double distance = currentPos.distanceTo(this.initialPlayerPos);
            if ((distance > (double)100.0F || this.mc.player.getHealth() > 0.0F) && System.currentTimeMillis() - this.lastKillTime > 1000L) {
               if (this.isNearInputArea()) {
                  this.info("Respawned at input area!", new Object[0]);
                  this.waitingForRespawn = false;
                  this.killRetryCount = 0;
                  this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                  this.findNextInputContainer();
               } else if (System.currentTimeMillis() - this.lastKillTime > 5000L) {
                  ++this.killRetryCount;
                  this.waitingForRespawn = false;
                  this.warning("Kill command may have been spam filtered, retrying with random suffix...", new Object[0]);
               }
            }
            break;
         }
         case 1: {
         }
         case 2: {
         }
         case 3: {
            if (this.goBackMethod.get() != StashMover.GoBackMethod.PEARL) {
               this.handleCommandTravelToInput();
               return;
            }

            if (!this.waitingForPearl) {
               this.sendGoBackPearlCommand();
               this.waitingForPearl = true;
               this.lastPearlMessageTime = System.currentTimeMillis();
               this.pearlRetryCount = 0;
               this.initialPlayerPos = this.mc.player.getEntityPos();
            }

            Vec3d currentPos = this.mc.player.getEntityPos();
            double distance = currentPos.distanceTo(this.initialPlayerPos);
            if (distance > (double)100.0F) {
               if (this.isNearInputArea()) {
                  this.info("Successfully returned to input area via pearl!", new Object[0]);
                  this.waitingForPearl = false;
                  this.ensureOffhandHasItem();
                  this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
                  this.hasThrownPearl = false;
                  this.hasPlacedShulker = false;
                  this.isGoingToInput = true;
               } else if (!this.isNearOutputArea()) {
                  this.warning("Teleported but not to input area, retrying...", new Object[0]);
                  this.waitingForPearl = false;
               }
            } else if (System.currentTimeMillis() - this.lastPearlMessageTime > (long)((Integer)this.pearlTimeout.get() * 1000)) {
               if (this.pearlRetryCount < (Integer)this.maxRetries.get()) {
                  ++this.pearlRetryCount;
                  this.info("Go back pearl timeout, retrying (attempt " + this.pearlRetryCount + "/" + String.valueOf(this.maxRetries.get()) + ")", new Object[0]);
                  this.sendGoBackPearlCommand();
                  this.lastPearlMessageTime = System.currentTimeMillis();
               } else {
                  this.error("Go back pearl loading failed after " + String.valueOf(this.maxRetries.get()) + " retries!", new Object[0]);
                  this.currentState = StashMover.ProcessState.IDLE;
                  this.waitingForPearl = false;
               }
            }
         }
      }

   }

   private void sendGoBackPearlCommand() {
      String randomSuffix = this.generateRandomString(8);
      String command = String.format("/msg %s %s %s", this.goBackPlayerName.get(), this.goBackCommand.get(), randomSuffix);
      ChatUtils.sendPlayerMsg(command);
      this.info("Sent go back command: " + command, new Object[0]);
   }

   private boolean hasValidAreas() {
      return inputAreaPos1 != null && inputAreaPos2 != null && outputAreaPos1 != null && outputAreaPos2 != null;
   }

   private boolean isNearInputArea() {
      if (inputAreaPos1 != null && inputAreaPos2 != null) {
         BlockPos playerPos = this.mc.player.getBlockPos();
         return playerPos.getX() >= inputAreaPos1.getX() - 10 && playerPos.getX() <= inputAreaPos2.getX() + 10 && playerPos.getY() >= inputAreaPos1.getY() - 5 && playerPos.getY() <= inputAreaPos2.getY() + 5 && playerPos.getZ() >= inputAreaPos1.getZ() - 10 && playerPos.getZ() <= inputAreaPos2.getZ() + 10;
      } else {
         return false;
      }
   }

   private boolean isNearOutputArea() {
      if (outputAreaPos1 != null && outputAreaPos2 != null) {
         BlockPos playerPos = this.mc.player.getBlockPos();
         return playerPos.getX() >= outputAreaPos1.getX() - 10 && playerPos.getX() <= outputAreaPos2.getX() + 10 && playerPos.getY() >= outputAreaPos1.getY() - 5 && playerPos.getY() <= outputAreaPos2.getY() + 5 && playerPos.getZ() >= outputAreaPos1.getZ() - 10 && playerPos.getZ() <= outputAreaPos2.getZ() + 10;
      } else {
         return false;
      }
   }

   private boolean isServerLagging() {
      return false;
   }

   private boolean isInventoryFull() {
      if ((Boolean)this.onlyShulkers.get()) {
         int shulkerCount = 0;

         for(int i = 0; i < 36; ++i) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && this.isShulkerBox(stack.getItem())) {
               ++shulkerCount;
            }
         }

         return shulkerCount >= 36;
      } else {
         for(int i = 0; i < 36; ++i) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) {
               return false;
            }
         }

         return true;
      }
   }

   private boolean isEnderChestFull() {
      return this.enderChestFull;
   }

   private boolean hasItemsToTransfer() {
      for(int i = 0; i < 36; ++i) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (!stack.isEmpty() && (!(Boolean)this.onlyShulkers.get() || this.isShulkerBox(stack.getItem()))) {
            return true;
         }
      }

      return false;
   }

   private boolean isShulkerBox(Item item) {
      return item == Items.SHULKER_BOX || item == Items.WHITE_SHULKER_BOX || item == Items.ORANGE_SHULKER_BOX || item == Items.MAGENTA_SHULKER_BOX || item == Items.LIGHT_BLUE_SHULKER_BOX || item == Items.YELLOW_SHULKER_BOX || item == Items.LIME_SHULKER_BOX || item == Items.PINK_SHULKER_BOX || item == Items.GRAY_SHULKER_BOX || item == Items.LIGHT_GRAY_SHULKER_BOX || item == Items.CYAN_SHULKER_BOX || item == Items.PURPLE_SHULKER_BOX || item == Items.BLUE_SHULKER_BOX || item == Items.BROWN_SHULKER_BOX || item == Items.GREEN_SHULKER_BOX || item == Items.RED_SHULKER_BOX || item == Items.BLACK_SHULKER_BOX;
   }

   private boolean isContainerItem(Item item) {
      return item == Items.CHEST || item == Items.TRAPPED_CHEST || item == Items.BARREL || item == Items.ENDER_CHEST;
   }

   private BlockPos findNearbyEnderChest() {
      int searchRadius = 32;
      BlockPos playerPos = this.mc.player.getBlockPos();
      BlockPos closestEnderChest = null;
      double closestDistance = Double.MAX_VALUE;

      for(int x = -searchRadius; x <= searchRadius; ++x) {
         for(int y = -5; y <= 5; ++y) {
            for(int z = -searchRadius; z <= searchRadius; ++z) {
               BlockPos pos = playerPos.add(x, y, z);
               double dist = playerPos.getSquaredDistance(pos);
               if (!(dist > (double)(searchRadius * searchRadius)) && this.mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                  Block block = this.mc.world.getBlockState(pos).getBlock();
                  if (block instanceof EnderChestBlock && dist < closestDistance) {
                     closestDistance = dist;
                     closestEnderChest = pos;
                  }
               }
            }
         }
      }

      if (closestEnderChest != null) {
         this.info("Found enderchest nearby", new Object[0]);
      }

      return closestEnderChest;
   }

   private BlockPos findSuitablePlacePos() {
      BlockPos playerPos = this.mc.player.getBlockPos();

      for(int x = -2; x <= 2; ++x) {
         for(int z = -2; z <= 2; ++z) {
            BlockPos pos = playerPos.add(x, 0, z);
            if (this.mc.world.getBlockState(pos).isAir() && this.mc.world.getBlockState(pos.down()).isSolidBlock(this.mc.world, pos.down())) {
               return pos;
            }
         }
      }

      return null;
   }

   private void placeEnderChest(BlockPos pos, int slot) {
      InvUtils.swap(slot, false);
      BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos.down(), false);
      this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
      this.enderChestPos = pos;
      this.currentState = StashMover.ProcessState.OPENING_ENDERCHEST;
      this.stateTimer = (Integer)this.openDelay.get();
   }

   private String generateRandomString(int length) {
      String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
      StringBuilder result = new StringBuilder();
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for(int i = 0; i < length; ++i) {
         result.append(chars.charAt(random.nextInt(chars.length())));
      }

      return result.toString();
   }

   private void handleIdleState() {
      if (this.stateTimer <= 0) {
         if (this.hasValidAreas()) {
            this.info("Rechecking location...", new Object[0]);
            this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
         } else {
            this.stateTimer = 100;
         }
      }

   }

   private void handleManualMoving() {
      if (this.stateTimer > 0) {
         --this.stateTimer;
      } else {
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         if (this.currentContainer != null) {
            Vec3d eyePos = this.mc.player.getEyePos();
            double distance = eyePos.distanceTo(Vec3d.ofCenter(this.currentContainer.pos));
            if (distance <= (double)3.5F) {
               this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
               this.stateTimer = 5;
            } else {
               this.manualMoveToContainer(this.currentContainer);
            }
         } else if (this.isNearOutputArea()) {
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
         } else {
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
         }

      }
   }

   private void manualMoveToContainer(ContainerInfo container) {
      if (container != null) {
         Vec3d targetPos = Vec3d.ofCenter(container.pos);
         Vec3d eyePos = this.mc.player.getEyePos();
         double distance = eyePos.distanceTo(targetPos);
         double yaw = Rotations.getYaw(targetPos);
         double pitch = Rotations.getPitch(targetPos);
         this.mc.player.setYaw((float)yaw);
         this.mc.player.setPitch((float)pitch);
         if (distance > 3.2) {
            this.info("Manual move to container, distance from eye: " + String.format("%.1f", distance), new Object[0]);
            this.mc.options.forwardKey.setPressed(true);
            this.mc.options.sneakKey.setPressed(true);
            this.currentState = StashMover.ProcessState.MANUAL_MOVING;
            this.stateTimer = 20;
         } else {
            this.mc.options.forwardKey.setPressed(false);
            this.mc.options.sneakKey.setPressed(false);
            this.info("Close enough to container (eye distance: " + String.format("%.1f", distance) + "), opening...", new Object[0]);
            this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
            this.stateTimer = 5;
         }

      }
   }

   private Direction getOptimalClickFace(BlockPos containerPos, Vec3d eyePos) {
      double heightDiff = (double)containerPos.getY() - eyePos.y;
      if (heightDiff > (double)2.0F) {
         double dx = eyePos.x - ((double)containerPos.getX() + (double)0.5F);
         double dz = eyePos.z - ((double)containerPos.getZ() + (double)0.5F);
         if (Math.abs(dx) > Math.abs(dz)) {
            return dx > (double)0.0F ? Direction.WEST : Direction.EAST;
         } else {
            return dz > (double)0.0F ? Direction.NORTH : Direction.SOUTH;
         }
      } else {
         Vec3d containerCenter = Vec3d.ofCenter(containerPos);
         Vec3d toContainer = containerCenter.subtract(eyePos).normalize();
         Direction bestFace = Direction.UP;
         double bestDot = Double.NEGATIVE_INFINITY;

         for(Direction face : Direction.values()) {
            Vec3d faceNormal = Vec3d.of(face.getVector());
            double dot = toContainer.dotProduct(faceNormal);
            if (dot > bestDot) {
               bestDot = dot;
               bestFace = face;
            }
         }

         if (bestFace == Direction.DOWN) {
            bestFace = Direction.UP;
         }

         return bestFace;
      }
   }

   public SelectionMode getSelectionMode() {
      return selectionMode;
   }

   public BlockPos getSelectionPos1() {
      return selectionPos1;
   }

   public boolean isSelecting() {
      return selectionMode != StashMover.SelectionMode.NONE;
   }

   public ProcessState getCurrentState() {
      return this.currentState;
   }

   public int getItemsTransferred() {
      return this.itemsTransferred;
   }

   public int getContainersProcessed() {
      return this.containersProcessed;
   }

   public boolean hasInputArea() {
      return inputAreaPos1 != null && inputAreaPos2 != null;
   }

   public boolean hasOutputArea() {
      return outputAreaPos1 != null && outputAreaPos2 != null;
   }

   public int getInputContainerCount() {
      return inputContainers.size();
   }

   public int getOutputContainerCount() {
      return outputContainers.size();
   }

   public void clearAreas() {
      inputAreaPos1 = null;
      inputAreaPos2 = null;
      outputAreaPos1 = null;
      outputAreaPos2 = null;
      inputContainers.clear();
      outputContainers.clear();
      selectionMode = StashMover.SelectionMode.NONE;
      this.info("All areas cleared", new Object[0]);
   }

   public void renderAreas(Render3DEvent event) {
      if (inputAreaPos1 != null && inputAreaPos2 != null) {
         Box inputBox = new Box((double)inputAreaPos1.getX(), (double)inputAreaPos1.getY(), (double)inputAreaPos1.getZ(), (double)(inputAreaPos2.getX() + 1), (double)(inputAreaPos2.getY() + 1), (double)(inputAreaPos2.getZ() + 1));
         SettingColor inputColor = new SettingColor(0, 255, 0, 50);
         event.renderer.box(inputBox, inputColor, inputColor, ShapeMode.Both, 0);
      }

      if (outputAreaPos1 != null && outputAreaPos2 != null) {
         Box outputBox = new Box((double)outputAreaPos1.getX(), (double)outputAreaPos1.getY(), (double)outputAreaPos1.getZ(), (double)(outputAreaPos2.getX() + 1), (double)(outputAreaPos2.getY() + 1), (double)(outputAreaPos2.getZ() + 1));
         SettingColor outputColor = new SettingColor(0, 0, 255, 50);
         event.renderer.box(outputBox, outputColor, outputColor, ShapeMode.Both, 0);
      }

   }

   private boolean validateChestTarget(BlockPos chestPos) {
      HitResult hitResult = this.mc.crosshairTarget;
      if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
         BlockHitResult blockHit = (BlockHitResult)hitResult;
         BlockPos targetPos = blockHit.getBlockPos();
         return targetPos.equals(chestPos);
      } else {
         return false;
      }
   }

   private void performImprovedInteraction(Vec3d containerCenter) {
      Vec3d eyePos = this.mc.player.getEyePos();
      Vec3d pos = this.mc.player.getEntityPos();
      this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(pos.x, pos.y, pos.z, this.mc.player.getYaw(), this.mc.player.getPitch(), this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
      Direction optimalFace = this.calculateOptimalFace(this.currentContainer.pos, eyePos);
      boolean success = false;
      success = this.tryDirectInteraction(this.currentContainer.pos, optimalFace, eyePos);
      if (!success && this.containerOpenFailures >= 2) {
         success = this.tryAllFacesInteraction(this.currentContainer.pos, eyePos);
      }

      if (!success && this.containerOpenFailures >= 4) {
         this.performPacketSpamInteraction(this.currentContainer.pos, eyePos);
      }

   }

   private boolean tryDirectInteraction(BlockPos pos, Direction face, Vec3d eyePos) {
      Vec3d hitVec = this.calculatePreciseHitVector(pos, face, eyePos);
      double yaw = Rotations.getYaw(hitVec);
      double pitch = Rotations.getPitch(hitVec);
      this.mc.player.setYaw((float)yaw);
      this.mc.player.setPitch((float)pitch);
      this.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, this.mc.player.isOnGround(), this.mc.player.horizontalCollision));
      BlockHitResult hitResult = new BlockHitResult(hitVec, face, pos, false);
      ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
      if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
         result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hitResult);
      }

      return result == ActionResult.SUCCESS || result == ActionResult.CONSUME;
   }

   private boolean tryAllFacesInteraction(BlockPos pos, Vec3d eyePos) {
      Direction[] facesToTry = new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};

      for(Direction face : facesToTry) {
         if (this.tryDirectInteraction(pos, face, eyePos)) {
            if ((Boolean)this.debugMode.get()) {
               this.info("Successfully interacted using face: " + String.valueOf(face), new Object[0]);
            }

            return true;
         }
      }

      return false;
   }

   private void performPacketSpamInteraction(BlockPos pos, Vec3d eyePos) {
      Vec3d[] hitPositions = new Vec3d[]{Vec3d.ofCenter(pos), Vec3d.ofCenter(pos).add((double)0.0F, (double)0.25F, (double)0.0F), Vec3d.ofCenter(pos).add((double)0.25F, (double)0.0F, (double)0.0F), Vec3d.ofCenter(pos).add((double)0.0F, (double)0.0F, (double)0.25F), Vec3d.ofCenter(pos).add((double)-0.25F, (double)0.0F, (double)0.0F), Vec3d.ofCenter(pos).add((double)0.0F, (double)0.0F, (double)-0.25F)};

      for(int i = 0; i < 3; ++i) {
         Vec3d hitPos = hitPositions[i % hitPositions.length];
         Direction face = i == 0 ? Direction.UP : (i == 1 ? Direction.NORTH : Direction.EAST);
         BlockHitResult hitResult = new BlockHitResult(hitPos, face, pos, false);
         ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
         if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
            this.mc.interactionManager.interactBlock(this.mc.player, Hand.OFF_HAND, hitResult);
         }
      }

      if ((Boolean)this.debugMode.get()) {
         this.info("Sent packet spam interaction for stubborn chest", new Object[0]);
      }

   }

   private Direction calculateOptimalFace(BlockPos pos, Vec3d eyePos) {
      double dx = (double)pos.getX() + (double)0.5F - eyePos.x;
      double dy = (double)pos.getY() + (double)0.5F - eyePos.y;
      double dz = (double)pos.getZ() + (double)0.5F - eyePos.z;
      double absDx = Math.abs(dx);
      double absDy = Math.abs(dy);
      double absDz = Math.abs(dz);
      if (absDy > absDx && absDy > absDz) {
         return dy > (double)0.0F ? Direction.DOWN : Direction.UP;
      } else if (absDx > absDz) {
         return dx > (double)0.0F ? Direction.WEST : Direction.EAST;
      } else {
         return dz > (double)0.0F ? Direction.NORTH : Direction.SOUTH;
      }
   }

   private void performSmartRepositioning(BlockPos chestPos, double currentDistance) {
      Vec3d chestCenter = Vec3d.ofCenter(chestPos);
      Vec3d playerPos = this.mc.player.getEntityPos();
      double optimalDistance = 2.8;
      Vec3d direction = playerPos.subtract(chestCenter).normalize();
      Vec3d optimalPos = chestCenter.add(direction.multiply(optimalDistance));
      double dx = optimalPos.x - playerPos.x;
      double dz = optimalPos.z - playerPos.z;
      if (Math.abs(dx) > Math.abs(dz)) {
         if (dx > 0.2) {
            this.mc.options.rightKey.setPressed(true);
            this.info("Repositioning: moving right", new Object[0]);
         } else if (dx < -0.2) {
            this.mc.options.leftKey.setPressed(true);
            this.info("Repositioning: moving left", new Object[0]);
         }
      } else if (dz > 0.2) {
         this.mc.options.backKey.setPressed(true);
         this.info("Repositioning: moving back", new Object[0]);
      } else if (dz < -0.2) {
         this.mc.options.forwardKey.setPressed(true);
         this.info("Repositioning: moving forward", new Object[0]);
      }

      if (currentDistance > optimalDistance + (double)0.5F) {
         this.mc.options.forwardKey.setPressed(true);
      } else if (currentDistance < optimalDistance - (double)0.5F) {
         this.mc.options.backKey.setPressed(true);
      }

      if ((Boolean)this.debugMode.get()) {
         this.info(String.format("Smart repositioning: current=%.1f, optimal=%.1f", currentDistance, optimalDistance), new Object[0]);
      }

   }

   private void improvedManualMovement(ContainerInfo container) {
      if (container != null) {
         Vec3d targetPos = Vec3d.ofCenter(container.pos);
         Vec3d playerPos = this.mc.player.getEntityPos();
         Vec3d eyePos = this.mc.player.getEyePos();
         double distance = eyePos.distanceTo(targetPos);
         double horizontalDistance = Math.sqrt(Math.pow(targetPos.x - playerPos.x, (double)2.0F) + Math.pow(targetPos.z - playerPos.z, (double)2.0F));
         this.stopAllMovement();
         double yaw = Rotations.getYaw(targetPos);
         double pitch = Rotations.getPitch(targetPos);
         this.mc.player.setYaw((float)yaw);
         this.mc.player.setPitch((float)pitch);
         if (distance > 3.2) {
            if (horizontalDistance > (double)10.0F) {
               this.mc.options.forwardKey.setPressed(true);
               this.mc.options.sprintKey.setPressed(true);
               this.info("Sprinting to container (" + String.format("%.1f", distance) + "m)", new Object[0]);
            } else if (horizontalDistance > (double)4.0F) {
               this.mc.options.forwardKey.setPressed(true);
               this.mc.options.sprintKey.setPressed(false);
               this.info("Walking to container (" + String.format("%.1f", distance) + "m)", new Object[0]);
            } else {
               this.mc.options.forwardKey.setPressed(true);
               this.mc.options.sneakKey.setPressed(true);
               this.info("Sneaking to container (" + String.format("%.1f", distance) + "m)", new Object[0]);
            }

            if (this.isBlockedAhead()) {
               this.mc.options.jumpKey.setPressed(true);
               this.jumpTimer = 5;
            } else if (this.jumpTimer > 0) {
               --this.jumpTimer;
               if (this.jumpTimer == 0) {
                  this.mc.options.jumpKey.setPressed(false);
               }
            }

            this.currentState = StashMover.ProcessState.MANUAL_MOVING;
            this.stateTimer = 30;
         } else {
            this.stopAllMovement();
            this.info("Reached container, opening...", new Object[0]);
            this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
            this.stateTimer = 5;
         }

      }
   }

   private void alternativePathToContainer(ContainerInfo container) {
      if (container != null) {
         BlockPos targetPos = container.pos;
         BlockPos[] alternatives = new BlockPos[]{targetPos.north(2), targetPos.south(2), targetPos.east(2), targetPos.west(2), targetPos.north(2).up(), targetPos.south(2).up(), targetPos.east(2).up(), targetPos.west(2).up()};
         BlockPos bestAlternative = null;
         double bestDistance = Double.MAX_VALUE;

         for(BlockPos alt : alternatives) {
            if (this.isValidStandingPosition(alt)) {
               double dist = this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(alt));
               if (dist < bestDistance) {
                  bestDistance = dist;
                  bestAlternative = alt;
               }
            }
         }

         if (bestAlternative != null) {
            GoalBlock goal = new GoalBlock(bestAlternative);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            this.currentState = StashMover.ProcessState.MOVING_TO_CONTAINER;
            this.stateTimer = 100;
            this.info("Using alternative path via " + String.valueOf(bestAlternative), new Object[0]);
         } else {
            this.warning("No alternative paths found, using manual movement", new Object[0]);
            this.improvedManualMovement(container);
         }

      }
   }

   private void ensureOffhandHasItem() {
      ItemStack offhandStack = this.mc.player.getOffHandStack();
      ItemStack slot0 = this.mc.player.getInventory().getStack(0);
      if (!slot0.isEmpty() && slot0.getItem() != Items.ENDER_PEARL) {
         if (offhandStack.isEmpty()) {
            this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 36, 0, SlotActionType.PICKUP, this.mc.player);
            this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, this.mc.player);
            this.info("Moved " + slot0.getItem().getName().getString() + " from slot 0 to offhand", new Object[0]);
         } else {
            for(int i = 9; i < 36; ++i) {
               if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                  this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 36, 0, SlotActionType.PICKUP, this.mc.player);
                  this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, this.mc.player);
                  this.info("Moved slot 0 to inventory to free space for pearl", new Object[0]);
                  break;
               }
            }
         }

      } else {
         if (offhandStack.isEmpty()) {
            for(int i = 1; i < 9; ++i) {
               ItemStack stack = this.mc.player.getInventory().getStack(i);
               if (!stack.isEmpty() && stack.getItem() != Items.ENDER_PEARL) {
                  this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 36 + i, 0, SlotActionType.PICKUP, this.mc.player);
                  this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, this.mc.player);
                  this.info("Moved " + stack.getItem().getName().getString() + " to offhand", new Object[0]);
                  return;
               }
            }

            for(int i = 9; i < 36; ++i) {
               ItemStack stack = this.mc.player.getInventory().getStack(i);
               if (!stack.isEmpty() && stack.getItem() != Items.ENDER_PEARL && !this.isShulkerBox(stack.getItem())) {
                  this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, this.mc.player);
                  this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, this.mc.player);
                  this.info("Moved item from inventory to offhand", new Object[0]);
                  return;
               }
            }
         }

      }
   }

   private void performStuckRecovery() {
      BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
      this.stopAllMovement();
      ++this.stuckRecoveryAttempts;
      if (this.stuckRecoveryAttempts >= 3) {
         this.warning("Failed to recover from stuck state, skipping container", new Object[0]);
         if (this.currentContainer != null) {
            this.currentContainer.isEmpty = true;
            this.currentContainer = null;
         }

         this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
         this.stuckRecoveryAttempts = 0;
         this.stuckCounter = 0;
         this.lastPlayerPos = null;
      } else {
         switch (this.stuckRecoveryAttempts) {
            case 1:
               this.info("Stuck recovery: jumping backward", new Object[0]);
               this.mc.options.jumpKey.setPressed(true);
               this.mc.options.backKey.setPressed(true);
               this.currentState = StashMover.ProcessState.MANUAL_MOVING;
               this.stateTimer = 20;
               break;
            case 2:
               this.info("Stuck recovery: strafing", new Object[0]);
               this.mc.options.jumpKey.setPressed(true);
               this.mc.options.leftKey.setPressed(true);
               this.currentState = StashMover.ProcessState.MANUAL_MOVING;
               this.stateTimer = 20;
               break;
            default:
               this.info("Stuck recovery: manual movement", new Object[0]);
               this.improvedManualMovement(this.currentContainer);
         }

         this.stuckCounter = 0;
      }
   }

   private boolean isBlockedAhead() {
      Vec3d playerPos = this.mc.player.getEntityPos();
      Vec3d lookVec = this.mc.player.getRotationVector();
      Vec3d checkPos = playerPos.add(lookVec.multiply((double)1.0F));
      BlockPos blockPos = BlockPos.ofFloored(checkPos);
      BlockPos blockAbove = blockPos.up();
      return !this.mc.world.getBlockState(blockPos).isAir() || !this.mc.world.getBlockState(blockAbove).isAir();
   }

   private boolean isValidStandingPosition(BlockPos pos) {
      BlockState groundState = this.mc.world.getBlockState(pos.down());
      BlockState feetState = this.mc.world.getBlockState(pos);
      BlockState headState = this.mc.world.getBlockState(pos.up());
      return groundState.isSolidBlock(this.mc.world, pos.down()) && feetState.isAir() && headState.isAir();
   }

   static {
      selectionMode = StashMover.SelectionMode.NONE;
      inputContainers = ConcurrentHashMap.newKeySet();
      outputContainers = ConcurrentHashMap.newKeySet();
   }

   public static enum GoBackMethod {
      KILL("Kill"),
      PEARL("Pearl Loading"),
      HOME("/home Command"),
      TPA("/tpa Command");

      private final String name;

      private GoBackMethod(String name) {
         this.name = name;
      }

      public String toString() {
         return this.name;
      }

      private static GoBackMethod[] $values() {
         return new GoBackMethod[]{KILL, PEARL, HOME, TPA};
      }
   }

   public static enum OutputTravelMethod {
      PEARL("Pearl Loading"),
      HOME("/home Command"),
      TPA("/tpa Command");

      private final String name;

      private OutputTravelMethod(String name) {
         this.name = name;
      }

      public String toString() {
         return this.name;
      }

      private static OutputTravelMethod[] $values() {
         return new OutputTravelMethod[]{PEARL, HOME, TPA};
      }
   }

   public static enum SelectionMode {
      NONE,
      INPUT_FIRST,
      INPUT_SECOND,
      OUTPUT_FIRST,
      OUTPUT_SECOND;

      private static SelectionMode[] $values() {
         return new SelectionMode[]{NONE, INPUT_FIRST, INPUT_SECOND, OUTPUT_FIRST, OUTPUT_SECOND};
      }
   }

   public static enum ProcessState {
      IDLE,
      CHECKING_LOCATION,
      INPUT_PROCESS,
      LOADING_PEARL,
      RESET_PEARL_PICKUP,
      RESET_PEARL_PLACE_SHULKER,
      RESET_PEARL_APPROACH,
      RESET_PEARL_PREPARE,
      RESET_PEARL_THROW,
      RESET_PEARL_WAIT,
      OUTPUT_PROCESS,
      GOING_BACK,
      OPENING_CONTAINER,
      TRANSFERRING_ITEMS,
      CLOSING_CONTAINER,
      BREAKING_CONTAINER,
      MOVING_TO_CONTAINER,
      MANUAL_MOVING,
      OPENING_ENDERCHEST,
      FILLING_ENDERCHEST,
      EMPTYING_ENDERCHEST,
      WAITING,
      MOVING_FORWARD_RETRY;

      private static ProcessState[] $values() {
         return new ProcessState[]{IDLE, CHECKING_LOCATION, INPUT_PROCESS, LOADING_PEARL, RESET_PEARL_PICKUP, RESET_PEARL_PLACE_SHULKER, RESET_PEARL_APPROACH, RESET_PEARL_PREPARE, RESET_PEARL_THROW, RESET_PEARL_WAIT, OUTPUT_PROCESS, GOING_BACK, OPENING_CONTAINER, TRANSFERRING_ITEMS, CLOSING_CONTAINER, BREAKING_CONTAINER, MOVING_TO_CONTAINER, MANUAL_MOVING, OPENING_ENDERCHEST, FILLING_ENDERCHEST, EMPTYING_ENDERCHEST, WAITING, MOVING_FORWARD_RETRY};
      }
   }

   private static class ContainerInfo {
      public final BlockPos pos;
      public final ContainerType type;
      public boolean isEmpty = false;
      public boolean isFull = false;
      public int slotsFilled = 0;
      public int totalSlots = 27;

      public ContainerInfo(BlockPos pos, ContainerType type) {
         this.pos = pos;
         this.type = type;
         if (type == StashMover.ContainerType.BARREL) {
            this.totalSlots = 27;
         } else if (type == StashMover.ContainerType.DOUBLE_CHEST || type == StashMover.ContainerType.DOUBLE_TRAPPED_CHEST) {
            this.totalSlots = 54;
         }

      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            ContainerInfo that = (ContainerInfo)o;
            return this.pos.equals(that.pos);
         } else {
            return false;
         }
      }

      public int hashCode() {
         return this.pos.hashCode();
      }
   }

   private static enum ContainerType {
      CHEST,
      DOUBLE_CHEST,
      TRAPPED_CHEST,
      DOUBLE_TRAPPED_CHEST,
      BARREL,
      ENDER_CHEST;

      private static ContainerType[] $values() {
         return new ContainerType[]{CHEST, DOUBLE_CHEST, TRAPPED_CHEST, DOUBLE_TRAPPED_CHEST, BARREL, ENDER_CHEST};
      }
   }
}
