package gamerguy11.sixtoolsaddon.hud;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ThemeColorUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.hud.Alignment;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.block.Block;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.stat.StatHandler;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Mode;

public class StatsHud extends HudElement {
   public static final HudElementInfo INFO;
   private final SettingGroup sgGeneral;
   private final SettingGroup sgScale;
   private final SettingGroup sgBackground;
   private final SettingGroup sgGrid;
   private final SettingGroup sgSync;
   private final SettingGroup sgOrder;
   private final SettingGroup sgStats;
   private final Setting<Boolean> shadow;
   private final Setting<Alignment> alignment;
   private final Setting<SettingColor> textColor;
   private final Setting<Boolean> textColorUseTheme;
   private final Setting<Integer> border;
   private final Setting<Boolean> customScale;
   private final Setting<Double> scale;
   private final Setting<Boolean> background;
   private final Setting<SettingColor> backgroundColor;
   private final Setting<Boolean> backgroundColorUseTheme;
   private final Setting<Boolean> snapToGrid;
   private final Setting<Integer> gridSize;
   private final Setting<Boolean> autoSync;
   private final Setting<Integer> syncDelay;
   private final Setting<Integer> updateInterval;
   private static final String KEY_PLAYTIME = "playtime";
   private static final String KEY_DISTANCE = "distancetravelled";
   private static final String KEY_DISTANCE_WALKED = "distancewalked";
   private static final String KEY_DISTANCE_SPRINTED = "distancesprinted";
   private static final String KEY_DISTANCE_FLOWN = "distanceflown";
   private static final String KEY_DISTANCE_SWUM = "distanceswum";
   private static final String KEY_BLOCKS = "blocksbroken";
   private static final String KEY_MOBS = "mobskilled";
   private static final String KEY_PVP = "playerkills";
   private static final String KEY_ITEMSCRAFTED = "itemscrafted";
   private static final String KEY_ITEMSUSED = "itemsused";
   private static final String KEY_ITEMSPICKED = "itemspickedup";
   private static final String KEY_DEATHS = "deaths";
   private static final String KEY_TIMESINCEDEATH = "timesincedeath";
   private static final String KEY_TIMESINCESLEEP = "timesincesleep";
   private final Setting<List<String>> statOrder;
   private final Setting<Boolean> showRates;
   private final Setting<Boolean> showPlayTime;
   private final Setting<Boolean> showDistance;
   private final Setting<Boolean> showDistanceWalked;
   private final Setting<Boolean> showDistanceSprinted;
   private final Setting<Boolean> showDistanceFlown;
   private final Setting<Boolean> showDistanceSwum;
   private final Setting<Boolean> showBlocks;
   private final Setting<BlockMode> blocksCountMode;
   private final Setting<List<Block>> blocks;
   private final Setting<Boolean> showMobs;
   private final Setting<EntityMode> mobsCountMode;
   private final Setting<Set<EntityType<?>>> mobs;
   private final Setting<Boolean> showPvpKills;
   private final Setting<Boolean> showItemsCrafted;
   private final Setting<CraftMode> craftedCountMode;
   private final Setting<List<Item>> craftedItems;
   private final Setting<Boolean> showItemsUsed;
   private final Setting<UseMode> usedCountMode;
   private final Setting<List<Item>> usedItems;
   private final Setting<Boolean> showItemsPicked;
   private final Setting<PickupMode> itemsCountMode;
   private final Setting<List<Item>> items;
   private final Setting<Boolean> showDeaths;
   private final Setting<Boolean> showTimeSinceDeath;
   private final Setting<Boolean> showTimeSinceSleep;
   private List<Block> allBlocks;
   private List<Item> allItems;
   private List<EntityType<?>> allEntities;
   private final Map<String, String> cachedStatLines;
   private boolean computedOnce;
   private long lastComputeMs;
   private long lastSyncMs;

   public StatsHud() {
      super(INFO);
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgScale = this.settings.createGroup("Scale");
      this.sgBackground = this.settings.createGroup("Background");
      this.sgGrid = this.settings.createGroup("Grid Snapping");
      this.sgSync = this.settings.createGroup("Sync");
      this.sgOrder = this.settings.createGroup("Order and Formatting");
      this.sgStats = this.settings.createGroup("Stats");
      this.shadow = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("shadow")).description("Renders a shadow behind the text.")).defaultValue(true)).build());
      this.alignment = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("alignment")).description("Horizontal text alignment.")).defaultValue(Alignment.Left)).build());
      this.textColor = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("text-color")).description("Color of the HUD text.")).defaultValue(new SettingColor(255, 255, 255)).build());
      this.textColorUseTheme = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("text-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.border = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("border")).description("Padding around the element.")).defaultValue(2)).sliderRange(0, 10).build());
      this.customScale = this.sgScale.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("custom-scale")).description("Applies a custom scale to this HUD element instead of the global HUD text scale.")).defaultValue(false)).build());
      SettingGroup var10001 = this.sgScale;
      DoubleSetting.Builder var10002 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("scale")).description("Custom scale.")).defaultValue((double)1.0F).min(0.1).sliderRange(0.1, (double)10.0F);
      Setting<Boolean> var10003 = this.customScale;
      Objects.requireNonNull(var10003);
      this.scale = var10001.add(((DoubleSetting.Builder)var10002.visible(var10003::get)).build());
      this.background = this.sgBackground.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("background")).description("Displays a background behind the stats.")).defaultValue(true)).build());
      var10001 = this.sgBackground;
      ColorSetting.Builder var14 = ((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("background-color")).description("Color used for the background.")).defaultValue(new SettingColor(25, 25, 25, 100));
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundColor = var10001.add(((ColorSetting.Builder)var14.visible(var10003::get)).build());
      this.backgroundColorUseTheme = this.sgBackground.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("background-use-theme")).description("Use the current Meteor theme accent color.")).defaultValue(false)).build());
      this.snapToGrid = this.sgGrid.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("snap-to-grid")).description("While dragging this element in the HUD editor, snaps it to a pixel grid instead of free placement.")).defaultValue(false)).build());
      var10001 = this.sgGrid;
      IntSetting.Builder var15 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("grid-size")).description("Size, in pixels, of one grid cell.")).defaultValue(10)).min(1).sliderRange(1, 50);
      var10003 = this.snapToGrid;
      Objects.requireNonNull(var10003);
      this.gridSize = var10001.add(((IntSetting.Builder)var15.visible(var10003::get)).build());
      this.autoSync = this.sgSync.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("auto-sync")).description("Automatically requests fresh stats from the server.")).defaultValue(true)).build());
      var10001 = this.sgSync;
      var15 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("sync-delay")).description("Delay between sync packets (seconds).")).defaultValue(5)).min(1).sliderRange(1, 300);
      var10003 = this.autoSync;
      Objects.requireNonNull(var10003);
      this.syncDelay = var10001.add(((IntSetting.Builder)var15.visible(var10003::get)).build());
      this.updateInterval = this.sgSync.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("update-interval")).description("Recomputes displayed stats every X ticks (20 ticks = 1 second).")).defaultValue(20)).min(1).sliderRange(1, 200).build());
      this.statOrder = this.sgOrder.add(((StringListSetting.Builder)((StringListSetting.Builder)((StringListSetting.Builder)(new StringListSetting.Builder()).name("stat-order")).description("Order stats appear in the HUD. Default: playtime, distancetravelled, distancewalked, distancesprinted, distanceflown, distanceswum, blocksbroken, mobskilled, playerkills, itemscrafted, itemsused, itemspickedup, deaths, timesincedeath, timesincesleep")).defaultValue(List.of("playtime", "distancetravelled", "distancewalked", "distancesprinted", "distanceflown", "distanceswum", "blocksbroken", "mobskilled", "playerkills", "itemscrafted", "itemsused", "itemspickedup", "deaths", "timesincedeath", "timesincesleep"))).build());
      this.showRates = this.sgOrder.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("hourly-rates")).description("Show hourly rates for stats. Based on total play time.")).defaultValue(false)).build());
      this.showPlayTime = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("play-time")).description("Show total play time.")).defaultValue(true)).build());
      this.showDistance = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("distance-travelled")).description("Show total distance travelled, across every movement type.")).defaultValue(true)).build());
      this.showDistanceWalked = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("distance-walked")).description("Show distance walked.")).defaultValue(false)).build());
      this.showDistanceSprinted = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("distance-sprinted")).description("Show distance sprinted.")).defaultValue(false)).build());
      this.showDistanceFlown = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("distance-flown")).description("Show distance flown (creative flight + elytra gliding).")).defaultValue(false)).build());
      this.showDistanceSwum = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("distance-swum")).description("Show distance swum (surface + underwater).")).defaultValue(false)).build());
      this.showBlocks = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("blocks-broken")).description("Show blocks broken count.")).defaultValue(true)).build());
      var10001 = this.sgStats;
      EnumSetting.Builder var17 = (EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("blocks-broken-count-mode")).description("The mode for counting Blocks.")).defaultValue(StatsHud.BlockMode.DoNotCount);
      var10003 = this.showBlocks;
      Objects.requireNonNull(var10003);
      this.blocksCountMode = var10001.add(((EnumSetting.Builder)var17.visible(var10003::get)).build());
      var10001 = this.sgStats;
      BlockListSetting.Builder var18 = (BlockListSetting.Builder)(new BlockListSetting.Builder()).name("blocks");
      var10003 = this.showBlocks;
      Objects.requireNonNull(var10003);
      this.blocks = var10001.add(((BlockListSetting.Builder)var18.visible(var10003::get)).build());
      this.showMobs = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("mobs-killed")).description("Show total mobs killed.")).defaultValue(true)).build());
      var10001 = this.sgStats;
      EnumSetting.Builder var19 = (EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("mobs-killed-count-mode")).description("The mode for counting Mobs.")).defaultValue(StatsHud.EntityMode.DoNotCount);
      var10003 = this.showMobs;
      Objects.requireNonNull(var10003);
      this.mobsCountMode = var10001.add(((EnumSetting.Builder)var19.visible(var10003::get)).build());
      var10001 = this.sgStats;
      EntityTypeListSetting.Builder var20 = (EntityTypeListSetting.Builder)(new EntityTypeListSetting.Builder()).name("mobs");
      var10003 = this.showMobs;
      Objects.requireNonNull(var10003);
      this.mobs = var10001.add(((EntityTypeListSetting.Builder)var20.visible(var10003::get)).build());
      this.showPvpKills = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("players-killed")).defaultValue(true)).build());
      this.showItemsCrafted = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("items-crafted")).description("Show items crafted count.")).defaultValue(true)).build());
      var10001 = this.sgStats;
      EnumSetting.Builder var21 = (EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("items-crafted-count-mode")).description("The mode for counting crafted Items.")).defaultValue(StatsHud.CraftMode.DoNotCount);
      var10003 = this.showItemsCrafted;
      Objects.requireNonNull(var10003);
      this.craftedCountMode = var10001.add(((EnumSetting.Builder)var21.visible(var10003::get)).build());
      var10001 = this.sgStats;
      ItemListSetting.Builder var22 = (ItemListSetting.Builder)(new ItemListSetting.Builder()).name("crafted-items");
      var10003 = this.showItemsCrafted;
      Objects.requireNonNull(var10003);
      this.craftedItems = var10001.add(((ItemListSetting.Builder)var22.visible(var10003::get)).build());
      this.showItemsUsed = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("items-used")).description("Show items used count.")).defaultValue(true)).build());
      var10001 = this.sgStats;
      EnumSetting.Builder var23 = (EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("items-used-count-mode")).description("The mode for counting used Items.")).defaultValue(StatsHud.UseMode.DoNotCount);
      var10003 = this.showItemsUsed;
      Objects.requireNonNull(var10003);
      this.usedCountMode = var10001.add(((EnumSetting.Builder)var23.visible(var10003::get)).build());
      var10001 = this.sgStats;
      ItemListSetting.Builder var24 = (ItemListSetting.Builder)(new ItemListSetting.Builder()).name("used-items");
      var10003 = this.showItemsUsed;
      Objects.requireNonNull(var10003);
      this.usedItems = var10001.add(((ItemListSetting.Builder)var24.visible(var10003::get)).build());
      this.showItemsPicked = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("items-picked-up")).description("Show items picked up count.")).defaultValue(true)).build());
      var10001 = this.sgStats;
      EnumSetting.Builder var25 = (EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("items-picked-up-count-mode")).description("The mode for counting Items.")).defaultValue(StatsHud.PickupMode.DoNotCount);
      var10003 = this.showItemsPicked;
      Objects.requireNonNull(var10003);
      this.itemsCountMode = var10001.add(((EnumSetting.Builder)var25.visible(var10003::get)).build());
      var10001 = this.sgStats;
      ItemListSetting.Builder var26 = (ItemListSetting.Builder)(new ItemListSetting.Builder()).name("picked-up-items");
      var10003 = this.showItemsPicked;
      Objects.requireNonNull(var10003);
      this.items = var10001.add(((ItemListSetting.Builder)var26.visible(var10003::get)).build());
      this.showDeaths = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("deaths")).defaultValue(true)).build());
      this.showTimeSinceDeath = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("time-since-death")).description("Show time since last death.")).defaultValue(true)).build());
      this.showTimeSinceSleep = this.sgStats.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("time-since-sleep")).description("Show time since last sleep.")).defaultValue(true)).build());
      this.cachedStatLines = new ConcurrentHashMap();
      this.computedOnce = false;
      this.lastComputeMs = 0L;
      this.lastSyncMs = 0L;
   }

   private void ensureRegistriesLoaded() {
      if (this.allBlocks == null) {
         this.allBlocks = Registries.BLOCK.stream().toList();
      }

      if (this.allItems == null) {
         this.allItems = Registries.ITEM.stream().toList();
      }

      if (this.allEntities == null) {
         this.allEntities = Registries.ENTITY_TYPE.stream().toList();
      }

   }

   private void updateStats() {
      if (MeteorClient.mc.player != null && MeteorClient.mc.player.getStatHandler() != null) {
         this.ensureRegistriesLoaded();
         long now = System.currentTimeMillis();
         if (!this.computedOnce) {
            this.computeAllStats();
            this.computedOnce = true;
            this.lastComputeMs = now;
            if ((Boolean)this.autoSync.get() && MeteorClient.mc.getNetworkHandler() != null) {
               MeteorClient.mc.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(Mode.REQUEST_STATS));
               this.lastSyncMs = now;
            }

         } else {
            if ((Boolean)this.autoSync.get() && MeteorClient.mc.getNetworkHandler() != null && now - this.lastSyncMs >= (long)(Integer)this.syncDelay.get() * 1000L) {
               MeteorClient.mc.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(Mode.REQUEST_STATS));
               this.lastSyncMs = now;
            }

            if (now - this.lastComputeMs >= (long)(Integer)this.updateInterval.get() * 50L) {
               this.computeAllStats();
               this.lastComputeMs = now;
            }

         }
      } else {
         this.computedOnce = false;
         this.cachedStatLines.clear();
      }
   }

   private double getHoursPlayed(StatHandler statHandler) {
      int playTime = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
      return (double)playTime / (double)72000.0F;
   }

   private void computeAllStats() {
      if (MeteorClient.mc.player != null) {
         StatHandler statHandler = MeteorClient.mc.player.getStatHandler();
         if (statHandler != null) {
            Map<String, String> newStats = new LinkedHashMap();
            double hours = this.getHoursPlayed(statHandler);
            if ((Boolean)this.showPlayTime.get()) {
               int playTime = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
               long hrs = (long)playTime / 72000L;
               long mins = (long)(playTime % 72000) / 1200L;
               newStats.put("playtime", String.format("Play Time: %dh %dm", hrs, mins));
            }

            int walkCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM));
            int sprintCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.SPRINT_ONE_CM));
            int crouchCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.CROUCH_ONE_CM));
            int flyCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.FLY_ONE_CM));
            int aviateCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.AVIATE_ONE_CM));
            int swimCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.SWIM_ONE_CM));
            int walkUnderWaterCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_UNDER_WATER_ONE_CM));
            int walkOnWaterCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ON_WATER_ONE_CM));
            int minecartCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.MINECART_ONE_CM));
            int boatCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.BOAT_ONE_CM));
            int pigCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.PIG_ONE_CM));
            int horseCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.HORSE_ONE_CM));
            int striderCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.STRIDER_ONE_CM));
            int happyGhastCm = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.HAPPY_GHAST_ONE_CM));
            if ((Boolean)this.showDistance.get()) {
               long totalCm = (long)walkCm + (long)sprintCm + (long)crouchCm + (long)flyCm + (long)aviateCm + (long)swimCm + (long)walkUnderWaterCm + (long)walkOnWaterCm + (long)minecartCm + (long)boatCm + (long)pigCm + (long)horseCm + (long)striderCm + (long)happyGhastCm;
               double km = (double)totalCm / (double)100000.0F;
               String text = String.format("Distance travelled: %.1fkm", km);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  text = text + String.format(" (%.1f/h)", km / hours);
               }

               newStats.put("distancetravelled", text);
            }

            if ((Boolean)this.showDistanceWalked.get()) {
               double km = (double)walkCm / (double)100000.0F;
               String text = String.format("Distance walked: %.1fkm", km);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  text = text + String.format(" (%.1f/h)", km / hours);
               }

               newStats.put("distancewalked", text);
            }

            if ((Boolean)this.showDistanceSprinted.get()) {
               double km = (double)sprintCm / (double)100000.0F;
               String text = String.format("Distance sprinted: %.1fkm", km);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  text = text + String.format(" (%.1f/h)", km / hours);
               }

               newStats.put("distancesprinted", text);
            }

            if ((Boolean)this.showDistanceFlown.get()) {
               double km = (double)(flyCm + aviateCm) / (double)100000.0F;
               String text = String.format("Distance flown: %.1fkm", km);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  text = text + String.format(" (%.1f/h)", km / hours);
               }

               newStats.put("distanceflown", text);
            }

            if ((Boolean)this.showDistanceSwum.get()) {
               double km = (double)(swimCm + walkUnderWaterCm) / (double)100000.0F;
               String text = String.format("Distance swum: %.1fkm", km);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  text = text + String.format(" (%.1f/h)", km / hours);
               }

               newStats.put("distanceswum", text);
            }

            if ((Boolean)this.showBlocks.get()) {
               int totalBlocksBroken = 0;
               List<Block> blockList = (List)this.blocks.get();
               BlockMode blockMode = (BlockMode)this.blocksCountMode.get();
               Block singleBlock = blockMode == StatsHud.BlockMode.Count && !blockList.isEmpty() ? (Block)blockList.get(0) : null;

               for(Block block : this.allBlocks) {
                  boolean shouldCount = blockMode == StatsHud.BlockMode.Count ? blockList.isEmpty() || blockList.contains(block) : !blockList.contains(block);
                  if (shouldCount) {
                     Stat<Block> stat = Stats.MINED.getOrCreateStat(block);
                     totalBlocksBroken += statHandler.getStat(stat);
                  }
               }

               String blocksText = singleBlock != null ? String.format("%s broken: %d", singleBlock.getName().getString(), totalBlocksBroken) : String.format("Blocks broken: %d", totalBlocksBroken);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  blocksText = blocksText + String.format(" (%.0f/h)", (double)totalBlocksBroken / hours);
               }

               newStats.put("blocksbroken", blocksText);
            }

            if ((Boolean)this.showMobs.get()) {
               int totalMobsKilled = 0;
               List<EntityType<?>> mobList = new ArrayList((Collection)this.mobs.get());
               EntityMode mobMode = (EntityMode)this.mobsCountMode.get();
               EntityType<?> singleMob = mobMode == StatsHud.EntityMode.Count && !mobList.isEmpty() ? (EntityType)mobList.get(0) : null;

               for(EntityType entityType : this.allEntities) {
                  boolean shouldCount = mobMode == StatsHud.EntityMode.Count ? mobList.isEmpty() || mobList.contains(entityType) : !mobList.contains(entityType);
                  if (shouldCount) {
                     Stat<EntityType<?>> stat = Stats.KILLED.getOrCreateStat(entityType);
                     totalMobsKilled += statHandler.getStat(stat);
                  }
               }

               String mobsText = singleMob != null ? String.format("%s killed: %d", singleMob.getName().getString(), totalMobsKilled) : String.format("Mobs killed: %d", totalMobsKilled);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  mobsText = mobsText + String.format(" (%.0f/h)", (double)totalMobsKilled / hours);
               }

               newStats.put("mobskilled", mobsText);
            }

            if ((Boolean)this.showPvpKills.get()) {
               int pvpKills = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAYER_KILLS));
               String text = String.format("Players killed: %d", pvpKills);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  text = text + String.format(" (%.1f/h)", (double)pvpKills / hours);
               }

               newStats.put("playerkills", text);
            }

            if ((Boolean)this.showItemsCrafted.get()) {
               int totalItemsCrafted = 0;
               List<Item> craftedItemList = new ArrayList((Collection)this.craftedItems.get());
               CraftMode craftedMode = (CraftMode)this.craftedCountMode.get();

               for(Item item : this.allItems) {
                  boolean shouldCount = craftedMode == StatsHud.CraftMode.Count ? craftedItemList.isEmpty() || craftedItemList.contains(item) : !craftedItemList.contains(item);
                  if (shouldCount) {
                     totalItemsCrafted += statHandler.getStat(Stats.CRAFTED.getOrCreateStat(item));
                  }
               }

               String craftedText;
               if (craftedMode == StatsHud.CraftMode.Count && craftedItemList.size() == 1) {
                  Item singleItem = (Item)craftedItemList.get(0);
                  craftedText = String.format("%s crafted: %d", singleItem.getName(singleItem.getDefaultStack()).getString(), totalItemsCrafted);
               } else {
                  craftedText = String.format("Items crafted: %d", totalItemsCrafted);
               }

               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  craftedText = craftedText + String.format(" (%.0f/h)", (double)totalItemsCrafted / hours);
               }

               newStats.put("itemscrafted", craftedText);
            }

            if ((Boolean)this.showItemsUsed.get()) {
               int totalItemsUsed = 0;
               List<Item> usedItemList = new ArrayList((Collection)this.usedItems.get());
               UseMode usedMode = (UseMode)this.usedCountMode.get();

               for(Item item : this.allItems) {
                  boolean shouldCount = usedMode == StatsHud.UseMode.Count ? usedItemList.isEmpty() || usedItemList.contains(item) : !usedItemList.contains(item);
                  if (shouldCount) {
                     totalItemsUsed += statHandler.getStat(Stats.USED.getOrCreateStat(item));
                  }
               }

               String usedText;
               if (usedMode == StatsHud.UseMode.Count && usedItemList.size() == 1) {
                  Item singleItem = (Item)usedItemList.get(0);
                  usedText = String.format("%s used: %d", singleItem.getName(singleItem.getDefaultStack()).getString(), totalItemsUsed);
               } else {
                  usedText = String.format("Items used: %d", totalItemsUsed);
               }

               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  usedText = usedText + String.format(" (%.0f/h)", (double)totalItemsUsed / hours);
               }

               newStats.put("itemsused", usedText);
            }

            if ((Boolean)this.showItemsPicked.get()) {
               int totalItemsPicked = 0;
               List<Item> itemList = new ArrayList((Collection)this.items.get());
               PickupMode itemMode = (PickupMode)this.itemsCountMode.get();

               for(Item item : this.allItems) {
                  boolean shouldCount = itemMode == StatsHud.PickupMode.Count ? itemList.isEmpty() || itemList.contains(item) : !itemList.contains(item);
                  if (shouldCount) {
                     totalItemsPicked += statHandler.getStat(Stats.PICKED_UP.getOrCreateStat(item));
                  }
               }

               String itemsText;
               if (itemMode == StatsHud.PickupMode.Count && itemList.size() == 1) {
                  Item singleItem = (Item)itemList.get(0);
                  itemsText = String.format("%s picked up: %d", singleItem.getName(singleItem.getDefaultStack()).getString(), totalItemsPicked);
               } else {
                  itemsText = String.format("Items picked up: %d", totalItemsPicked);
               }

               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  itemsText = itemsText + String.format(" (%.0f/h)", (double)totalItemsPicked / hours);
               }

               newStats.put("itemspickedup", itemsText);
            }

            int deaths = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));
            if ((Boolean)this.showDeaths.get()) {
               String text = String.format("Deaths: %d", deaths);
               if ((Boolean)this.showRates.get() && hours > (double)0.0F) {
                  text = text + String.format(" (%.1f/h)", (double)deaths / hours);
               }

               newStats.put("deaths", text);
            }

            if ((Boolean)this.showTimeSinceDeath.get() && deaths >= 1) {
               int ticksSinceDeath = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_DEATH));
               String var10002 = this.formatTicksAsTime(ticksSinceDeath);
               newStats.put("timesincedeath", "Since death: " + var10002);
            }

            if ((Boolean)this.showTimeSinceSleep.get()) {
               int ticksSinceSleep = statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
               String var81 = this.formatTicksAsTime(ticksSinceSleep);
               newStats.put("timesincesleep", "Since last sleep: " + var81);
            }

            this.cachedStatLines.clear();
            this.cachedStatLines.putAll(newStats);
         }
      }
   }

   private String formatTicksAsTime(int ticks) {
      long totalSeconds = (long)ticks / 20L;
      long hrs = totalSeconds / 3600L;
      long mins = totalSeconds % 3600L / 60L;
      long secs = totalSeconds % 60L;
      return hrs > 0L ? String.format("%dh %02dm %02ds", hrs, mins, secs) : String.format("%dm %02ds", mins, secs);
   }

   private List<String> getLines() {
      Stream var10000 = ((List)this.statOrder.get()).stream();
      Map var10001 = this.cachedStatLines;
      Objects.requireNonNull(var10001);
      var10000 = var10000.filter(var10001::containsKey);
      var10001 = this.cachedStatLines;
      Objects.requireNonNull(var10001);
      return var10000.map(var10001::get).toList();
   }

   public void move(int deltaX, int deltaY) {
      super.move(deltaX, deltaY);
      if ((Boolean)this.snapToGrid.get()) {
         int size = Math.max(1, (Integer)this.gridSize.get());
         this.box.x = Math.round((float)this.box.x / (float)size) * size;
         this.box.y = Math.round((float)this.box.y / (float)size) * size;
         this.updatePos();
      }

   }

   public void tick(HudRenderer renderer) {
      this.updateStats();
      List<String> lines = this.getLines();
      double width;
      double height;
      if (lines.isEmpty()) {
         width = renderer.textWidth("Stats", (Boolean)this.shadow.get(), this.getScale());
         height = renderer.textHeight((Boolean)this.shadow.get(), this.getScale());
      } else {
         width = (double)0.0F;
         height = (double)0.0F;

         for(String line : lines) {
            width = Math.max(width, renderer.textWidth(line, (Boolean)this.shadow.get(), this.getScale()));
            height += renderer.textHeight((Boolean)this.shadow.get(), this.getScale());
         }

         height += (double)((lines.size() - 1) * 2);
      }

      this.setSize(width, height);
   }

   public void setSize(double width, double height) {
      super.setSize(width + (double)((Integer)this.border.get() * 2), height + (double)((Integer)this.border.get() * 2));
   }

   protected double alignX(double width, Alignment alignment) {
      return this.box.alignX((double)(this.getWidth() - (Integer)this.border.get() * 2), width, alignment);
   }

   public void render(HudRenderer renderer) {
      if ((Boolean)this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), ThemeColorUtils.resolve((SettingColor)this.backgroundColor.get(), (Boolean)this.backgroundColorUseTheme.get()));
      }

      List<String> lines = this.getLines();
      double y = (double)(this.y + (Integer)this.border.get());
      if (lines.isEmpty()) {
         String placeholder = "Stats";
         renderer.text(placeholder, (double)(this.x + (Integer)this.border.get()) + this.alignX(renderer.textWidth(placeholder, (Boolean)this.shadow.get(), this.getScale()), (Alignment)this.alignment.get()), y, ThemeColorUtils.resolve((SettingColor)this.textColor.get(), (Boolean)this.textColorUseTheme.get()), (Boolean)this.shadow.get(), this.getScale());
      } else {
         boolean first = true;

         for(String line : lines) {
            if (!first) {
               y += renderer.textHeight((Boolean)this.shadow.get(), this.getScale()) + (double)2.0F;
            }

            first = false;
            double x = (double)(this.x + (Integer)this.border.get()) + this.alignX(renderer.textWidth(line, (Boolean)this.shadow.get(), this.getScale()), (Alignment)this.alignment.get());
            renderer.text(line, x, y, ThemeColorUtils.resolve((SettingColor)this.textColor.get(), (Boolean)this.textColorUseTheme.get()), (Boolean)this.shadow.get(), this.getScale());
         }

      }
   }

   private double getScale() {
      return (Boolean)this.customScale.get() ? (Double)this.scale.get() : Hud.get().getTextScale();
   }

   static {
      INFO = new HudElementInfo(SixToolsAddon.HUD_GROUP, "stats", "Displays player statistics, dragged and positioned like any other HUD element.", StatsHud::new);
   }

   private static enum BlockMode {
      Count,
      DoNotCount;

      private static BlockMode[] $values() {
         return new BlockMode[]{Count, DoNotCount};
      }
   }

   private static enum EntityMode {
      Count,
      DoNotCount;

      private static EntityMode[] $values() {
         return new EntityMode[]{Count, DoNotCount};
      }
   }

   private static enum CraftMode {
      Count,
      DoNotCount;

      private static CraftMode[] $values() {
         return new CraftMode[]{Count, DoNotCount};
      }
   }

   private static enum UseMode {
      Count,
      DoNotCount;

      private static UseMode[] $values() {
         return new UseMode[]{Count, DoNotCount};
      }
   }

   private static enum PickupMode {
      Count,
      DoNotCount;

      private static PickupMode[] $values() {
         return new PickupMode[]{Count, DoNotCount};
      }
   }
}
