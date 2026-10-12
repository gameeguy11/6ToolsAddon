package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Spoofer extends Module {
   public enum Mode {
      Offset,
      Fixed,
      Random
   }

   private static final Pattern XYZ = Pattern.compile("^XYZ: (-?\\d+(?:\\.\\d+)?) / (-?\\d+(?:\\.\\d+)?) / (-?\\d+(?:\\.\\d+)?)(.*)$");
   private static final Pattern BLOCK = Pattern.compile("^Block: (-?\\d+) (-?\\d+) (-?\\d+) \\[\\d+ \\d+ \\d+\\](.*)$");
   private static final Pattern CHUNK = Pattern.compile("^Chunk: (-?\\d+) (-?\\d+) (-?\\d+) \\[\\d+ \\d+ in r\\.-?\\d+\\.-?\\d+\\.mca\\](.*)$");
   private static final Pattern CLIPBOARD = Pattern.compile("((?:tp @s |setblock |summon [\\w:.]+ ))(-?\\d+(?:\\.\\d+)?) (-?\\d+(?:\\.\\d+)?) (-?\\d+(?:\\.\\d+)?)");
   private static final Pattern MAP_COORDS = Pattern.compile("X: (-?\\d+)((?:,)? Y: (-?\\d+))?(,)? Z: (-?\\d+)");
   private static final Pattern TARGET = Pattern.compile("^(Targeted (?:Block|Fluid): )(-?\\d+), (-?\\d+), (-?\\d+)$");

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> spoofCoords = this.sgGeneral.add(new BoolSetting.Builder()
      .name("coords")
      .description("Shows fake coordinates everywhere this addon and the F3 screen display them.")
      .defaultValue(true)
      .build());
   private final Setting<Mode> mode = this.sgGeneral.add(new EnumSetting.Builder<Mode>()
      .name("mode")
      .description("Offset shifts your real position by a random amount so the fake coords still move as you walk. Fixed shows one random position that never changes. Random is like Offset but the offset is 3,000,000 to 10,000,000 blocks, is never shown anywhere, and is re-rolled every time you leave a server.")
      .defaultValue(Mode.Offset)
      .onChanged(newMode -> {
         if (newMode == Mode.Random) {
            this.rollRandomOffset();
         }
      })
      .build());
   private final Setting<Integer> range = this.sgGeneral.add(new IntSetting.Builder()
      .name("range")
      .description("How far from 0 the random X and Z values can be.")
      .defaultValue(100000)
      .min(1)
      .max(29000000)
      .sliderRange(1000, 1000000)
      .visible(() -> this.mode.get() != Mode.Random)
      .build());
   private final Setting<Boolean> randomSign = this.sgGeneral.add(new BoolSetting.Builder()
      .name("random-sign")
      .description("Random mode only: each axis of the hidden offset is randomly added or subtracted, so fake coords can be negative.")
      .defaultValue(true)
      .visible(() -> this.mode.get() == Mode.Random)
      .build());
   private final Setting<Boolean> spoofY = this.sgGeneral.add(new BoolSetting.Builder()
      .name("spoof-y")
      .description("Also fakes the Y level.")
      .defaultValue(false)
      .build());
   private final Setting<Boolean> hideHardware = this.sgGeneral.add(new BoolSetting.Builder()
      .name("hide-hardware")
      .description("Hides the CPU and display lines on the right side of the F3 screen.")
      .defaultValue(true)
      .build());
   private final Setting<Boolean> rerollOnEnable = this.sgGeneral.add(new BoolSetting.Builder()
      .name("reroll-on-enable")
      .description("Picks a new random position every time the module is turned on.")
      .defaultValue(true)
      .build());

   private final Random random = new Random();
   private int offsetX;
   private int offsetY;
   private int offsetZ;
   private int randomX;
   private int randomZ;
   private int fixedX;
   private int fixedY;
   private int fixedZ;

   public Spoofer() {
      super(SixToolsAddon.CATEGORY, "spoofer", "Spoofs information about you on your own screen. Client-side only, nothing is sent to the server.");
      this.reroll();
   }

   public void onActivate() {
      if (this.rerollOnEnable.get()) {
         this.reroll();
      }
   }

   @EventHandler
   public void onGameLeft(GameLeftEvent event) {
      if (this.mode.get() == Mode.Random) {
         this.rollRandomOffset();
      }
   }

   private void rollRandomOffset() {
      this.randomX = this.randomMagnitude();
      this.randomZ = this.randomMagnitude();
   }

   private int randomMagnitude() {
      int value = this.randomBetween(3000000, 10000000);
      return this.randomSign.get() && this.random.nextBoolean() ? -value : value;
   }

   private int ox() {
      return this.mode.get() == Mode.Random ? this.randomX : this.offsetX;
   }

   private int oz() {
      return this.mode.get() == Mode.Random ? this.randomZ : this.offsetZ;
   }

   public void reroll() {
      this.rollRandomOffset();
      int bound = this.range.get();
      this.offsetX = this.randomBetween(-bound, bound);
      this.offsetZ = this.randomBetween(-bound, bound);
      this.offsetY = this.randomBetween(-100, 100);
      this.fixedX = this.randomBetween(-bound, bound);
      this.fixedZ = this.randomBetween(-bound, bound);
      this.fixedY = this.randomBetween(-50, 200);
   }

   public void setFixed(int x, int y, int z) {
      this.mode.set(Mode.Fixed);
      this.fixedX = x;
      this.fixedY = y;
      this.fixedZ = z;
   }

   public void setMode(Mode newMode) {
      this.mode.set(newMode);
   }

   public void setRange(int newRange) {
      this.range.set(newRange);
      this.reroll();
   }

   public void setSpoofY(boolean value) {
      this.spoofY.set(value);
   }

   public Mode getMode() {
      return this.mode.get();
   }

   public int getRange() {
      return this.range.get();
   }

   public boolean isSpoofingY() {
      return this.spoofY.get();
   }

   public BlockPos getFakePosition() {
      if (this.mode.get() == Mode.Random) {
         return BlockPos.ORIGIN;
      }

      if (this.mode.get() == Mode.Fixed) {
         return new BlockPos(this.fixedX, this.spoofY.get() ? this.fixedY : 0, this.fixedZ);
      }

      return new BlockPos(this.offsetX, this.spoofY.get() ? this.offsetY : 0, this.offsetZ);
   }

   private int randomBetween(int min, int max) {
      return min + this.random.nextInt(max - min + 1);
   }

   private int fakeBlockX(int real) {
      return this.mode.get() == Mode.Fixed ? this.fixedX : real + this.ox();
   }

   private int fakeBlockY(int real) {
      if (!this.spoofY.get()) {
         return real;
      }

      return this.mode.get() == Mode.Fixed ? this.fixedY : real + this.offsetY;
   }

   private int fakeBlockZ(int real) {
      return this.mode.get() == Mode.Fixed ? this.fixedZ : real + this.oz();
   }

   private double fakeX(double real) {
      return this.mode.get() == Mode.Fixed ? this.fixedX + (real - Math.floor(real)) : real + this.ox();
   }

   private double fakeY(double real) {
      if (!this.spoofY.get()) {
         return real;
      }

      return this.mode.get() == Mode.Fixed ? this.fixedY + (real - Math.floor(real)) : real + this.offsetY;
   }

   private double fakeZ(double real) {
      return this.mode.get() == Mode.Fixed ? this.fixedZ + (real - Math.floor(real)) : real + this.oz();
   }

   private static Spoofer activeSpoofer() {
      Modules modules = Modules.get();
      if (modules == null) {
         return null;
      }

      Spoofer spoofer = modules.get(Spoofer.class);
      return spoofer != null && spoofer.isActive() && spoofer.spoofCoords.get() ? spoofer : null;
   }

   public static double spoofX(double real) {
      Spoofer spoofer = activeSpoofer();
      return spoofer == null ? real : spoofer.fakeX(real);
   }

   public static double spoofY(double real) {
      Spoofer spoofer = activeSpoofer();
      return spoofer == null ? real : spoofer.fakeY(real);
   }

   public static double spoofZ(double real) {
      Spoofer spoofer = activeSpoofer();
      return spoofer == null ? real : spoofer.fakeZ(real);
   }

   public static String processMapLine(String line) {
      if (line == null) {
         return null;
      }

      Spoofer spoofer = activeSpoofer();
      if (spoofer == null) {
         return line;
      }

      Matcher matcher = MAP_COORDS.matcher(line);
      if (!matcher.find()) {
         return line;
      }

      StringBuilder result = new StringBuilder(line.substring(0, matcher.start()));
      result.append("X: ").append(spoofer.fakeBlockX(Integer.parseInt(matcher.group(1))));
      if (matcher.group(2) != null) {
         result.append(matcher.group(2), 0, matcher.start(3) - matcher.start(2)).append(spoofer.fakeBlockY(Integer.parseInt(matcher.group(3))));
      }

      result.append(matcher.group(4) == null ? "" : ",").append(" Z: ").append(spoofer.fakeBlockZ(Integer.parseInt(matcher.group(5))));
      result.append(line.substring(matcher.end()));
      return result.toString();
   }

   public static boolean isMasking() {
      return activeSpoofer() != null;
   }

   public static String processClipboard(String text) {
      if (text == null) {
         return null;
      }

      Spoofer spoofer = activeSpoofer();
      if (spoofer == null) {
         return text;
      }

      Matcher matcher = CLIPBOARD.matcher(text);
      StringBuilder result = new StringBuilder();
      while (matcher.find()) {
         String replacement = matcher.group(1) + spoofer.clipboardValue(matcher.group(2), 0) + " " + spoofer.clipboardValue(matcher.group(3), 1) + " " + spoofer.clipboardValue(matcher.group(4), 2);
         matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
      }

      matcher.appendTail(result);
      return result.toString();
   }

   private String clipboardValue(String raw, int axis) {
      if (raw.contains(".")) {
         double value = Double.parseDouble(raw);
         double faked = axis == 0 ? this.fakeX(value) : axis == 1 ? this.fakeY(value) : this.fakeZ(value);
         return String.format(Locale.ROOT, "%.2f", faked);
      }

      int value = Integer.parseInt(raw);
      int faked = axis == 0 ? this.fakeBlockX(value) : axis == 1 ? this.fakeBlockY(value) : this.fakeBlockZ(value);
      return Integer.toString(faked);
   }

   public static BlockPos spoof(BlockPos real) {
      Spoofer spoofer = activeSpoofer();
      if (spoofer == null) {
         return real;
      }

      return new BlockPos(spoofer.fakeBlockX(real.getX()), spoofer.fakeBlockY(real.getY()), spoofer.fakeBlockZ(real.getZ()));
   }

   public static String processDebugLine(String line) {
      if (line == null) {
         return null;
      }

      Spoofer spoofer = activeSpoofer();
      if (spoofer == null) {
         return line;
      }

      return spoofer.rewrite(line);
   }

   private String rewrite(String line) {
      if (this.hideHardware.get()) {
         if (line.startsWith("CPU:")) {
            return "CPU: hidden";
         }

         if (line.startsWith("Display:")) {
            return "Display: hidden";
         }
      }

      if (line.startsWith("XYZ:")) {
         Matcher matcher = XYZ.matcher(line);
         if (!matcher.matches()) {
            return "XYZ: hidden";
         }

         double x = this.fakeX(Double.parseDouble(matcher.group(1)));
         double y = this.fakeY(Double.parseDouble(matcher.group(2)));
         double z = this.fakeZ(Double.parseDouble(matcher.group(3)));
         return String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f%s", x, y, z, matcher.group(4));
      }

      if (line.startsWith("Block:")) {
         Matcher matcher = BLOCK.matcher(line);
         if (!matcher.matches()) {
            return "Block: hidden";
         }

         int x = this.fakeBlockX(Integer.parseInt(matcher.group(1)));
         int y = this.fakeBlockY(Integer.parseInt(matcher.group(2)));
         int z = this.fakeBlockZ(Integer.parseInt(matcher.group(3)));
         return String.format(Locale.ROOT, "Block: %d %d %d [%d %d %d]%s", x, y, z, Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16), matcher.group(4));
      }

      if (line.startsWith("Chunk:")) {
         Matcher matcher = CHUNK.matcher(line);
         Entity camera = MinecraftClient.getInstance().getCameraEntity();
         if (!matcher.matches() || camera == null) {
            return "Chunk: hidden";
         }

         int chunkX = Math.floorDiv(this.fakeBlockX(camera.getBlockX()), 16);
         int chunkY = this.spoofY.get() ? Math.floorDiv(this.fakeBlockY(camera.getBlockY()), 16) : Integer.parseInt(matcher.group(2));
         int chunkZ = Math.floorDiv(this.fakeBlockZ(camera.getBlockZ()), 16);
         return String.format(Locale.ROOT, "Chunk: %d %d %d [%d %d in r.%d.%d.mca]%s", chunkX, chunkY, chunkZ, Math.floorMod(chunkX, 32), Math.floorMod(chunkZ, 32), Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32), matcher.group(4));
      }

      if (line.startsWith("Targeted Block:") || line.startsWith("Targeted Fluid:")) {
         Matcher matcher = TARGET.matcher(line);
         if (!matcher.matches()) {
            return line.substring(0, line.indexOf(':') + 1) + " hidden";
         }

         int x = this.fakeBlockX(Integer.parseInt(matcher.group(2)));
         int y = this.fakeBlockY(Integer.parseInt(matcher.group(3)));
         int z = this.fakeBlockZ(Integer.parseInt(matcher.group(4)));
         return matcher.group(1) + x + ", " + y + ", " + z;
      }

      return line;
   }
}
