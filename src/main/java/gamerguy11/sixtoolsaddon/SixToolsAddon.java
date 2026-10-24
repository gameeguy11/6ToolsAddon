package gamerguy11.sixtoolsaddon;

import com.mojang.logging.LogUtils;
import gamerguy11.sixtoolsaddon.commands.ChestTrackerCommand;
import gamerguy11.sixtoolsaddon.commands.CoordsCommand;
import gamerguy11.sixtoolsaddon.commands.DubCounterCommand;
import gamerguy11.sixtoolsaddon.commands.EnemyCommand;
import gamerguy11.sixtoolsaddon.commands.InventoryCommand;
import gamerguy11.sixtoolsaddon.commands.OnlinePlayersCommand;
import gamerguy11.sixtoolsaddon.commands.SetClear;
import gamerguy11.sixtoolsaddon.commands.SetDiscordCommand;
import gamerguy11.sixtoolsaddon.commands.SetInput;
import gamerguy11.sixtoolsaddon.commands.SetOutput;
import gamerguy11.sixtoolsaddon.commands.PrintStatsCommand;
import gamerguy11.sixtoolsaddon.commands.SpoofCommand;
import gamerguy11.sixtoolsaddon.commands.StashSorterCommand;
import gamerguy11.sixtoolsaddon.commands.StashStatus;
import gamerguy11.sixtoolsaddon.chat.ChatService;
import gamerguy11.sixtoolsaddon.commands.ChatCommand;
import gamerguy11.sixtoolsaddon.gui.ChatTab;
import gamerguy11.sixtoolsaddon.gui.EnemiesTab;
import gamerguy11.sixtoolsaddon.hud.ArmorHud;
import gamerguy11.sixtoolsaddon.hud.DimensionCoords;
import gamerguy11.sixtoolsaddon.hud.DubCounterHud;
import gamerguy11.sixtoolsaddon.hud.InventoryHud;
import gamerguy11.sixtoolsaddon.hud.NowPlayingHud;
import gamerguy11.sixtoolsaddon.hud.PlayerTrackerHud;
import gamerguy11.sixtoolsaddon.hud.PvPNeccessaryHud;
import gamerguy11.sixtoolsaddon.hud.StatsHud;
import gamerguy11.sixtoolsaddon.hud.Watermark;
import gamerguy11.sixtoolsaddon.modules.AutoStashSorter;
import gamerguy11.sixtoolsaddon.modules.AutoStashSorterSelectionHandler;
import gamerguy11.sixtoolsaddon.modules.CsgoSpin;
import gamerguy11.sixtoolsaddon.modules.Efly;
import gamerguy11.sixtoolsaddon.modules.Ez;
import gamerguy11.sixtoolsaddon.modules.ForeverForward;
import gamerguy11.sixtoolsaddon.modules.InventorySorterModule;
import gamerguy11.sixtoolsaddon.modules.ItemSearchBar;
import gamerguy11.sixtoolsaddon.modules.MapDuplicator;
import gamerguy11.sixtoolsaddon.modules.MusicTweaks;
import gamerguy11.sixtoolsaddon.modules.RespawnPointBlocker;
import gamerguy11.sixtoolsaddon.modules.StashMover;
import gamerguy11.sixtoolsaddon.modules.StashMoverSelectionHandler;
import gamerguy11.sixtoolsaddon.modules.Stripper;
import gamerguy11.sixtoolsaddon.modules.SixToolsAutoFish;
import gamerguy11.sixtoolsaddon.modules.SixToolsAutoWasp;
import gamerguy11.sixtoolsaddon.modules.Suicide;
import gamerguy11.sixtoolsaddon.modules.WitherPrint;
import gamerguy11.sixtoolsaddon.modules.chesttracker.ChestTrackerModule;
import gamerguy11.sixtoolsaddon.modules.utility.AntiDrop;
import gamerguy11.sixtoolsaddon.modules.utility.AutoReturnHome;
import gamerguy11.sixtoolsaddon.modules.utility.AutoTpAccept;
import gamerguy11.sixtoolsaddon.modules.utility.ChatHighlighter;
import gamerguy11.sixtoolsaddon.modules.utility.DeathLogger;
import gamerguy11.sixtoolsaddon.modules.utility.DiscordNotifier;
import gamerguy11.sixtoolsaddon.modules.utility.Homes;
import gamerguy11.sixtoolsaddon.modules.utility.SocialSync;
import gamerguy11.sixtoolsaddon.modules.utility.ShulkerView;
import gamerguy11.sixtoolsaddon.modules.utility.SoundEditor;
import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import gamerguy11.sixtoolsaddon.modules.utility.WhisperLogger;
import gamerguy11.sixtoolsaddon.modules.visual.Parkinsons;
import gamerguy11.sixtoolsaddon.modules.visual.BetterEsp;
import gamerguy11.sixtoolsaddon.modules.visual.BetterChams;
import gamerguy11.sixtoolsaddon.modules.visual.BetterShaders;
import gamerguy11.sixtoolsaddon.modules.visual.BetterTracers;
import gamerguy11.sixtoolsaddon.modules.visual.BetterTrajectories;
import gamerguy11.sixtoolsaddon.modules.visual.DeathEffects;
import gamerguy11.sixtoolsaddon.modules.visual.HandProgress;
import gamerguy11.sixtoolsaddon.modules.visual.SwingSpeed;
import gamerguy11.sixtoolsaddon.sound.SoundEngine;
import java.util.List;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.tabs.builtin.FriendsTab;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.slf4j.Logger;

public class SixToolsAddon extends MeteorAddon {
   public static final Logger LOG = LogUtils.getLogger();
   public static final Category CATEGORY = new Category("6Tools Addon");
   public static final HudGroup HUD_GROUP = new HudGroup("6Tools Addon");
   public static final Color THEME_COLOR = new Color(0, 182, 182);
   private static final GithubRepo REPO = new GithubRepo("gameeguy11", "6ToolsAddon");
   private static final String FIRST_COMMIT = "0e646b42cc8ab11c24a48417f14d295d393a09a7";

   public GithubRepo getRepo() {
      return REPO;
   }

   public String getCommit() {
      return "0e646b42cc8ab11c24a48417f14d295d393a09a7";
   }

   public void onInitialize() {
      LOG.info("Initializing SixToolsAddon");
      gamerguy11.sixtoolsaddon.baritone.BaritoneCheck.init();
      String baritoneWarning = gamerguy11.sixtoolsaddon.baritone.BaritoneCheck.warningMessage();
      if (baritoneWarning != null) {
         LOG.warn(baritoneWarning);
      }
      SoundEngine.INSTANCE.rescan();
      Modules.get().add(new Efly());
      Modules.get().add(new SixToolsAutoWasp());
      Modules.get().add(new SixToolsAutoFish());
      gamerguy11.sixtoolsaddon.utils.PacketPlaceTracker.init();
      Modules.get().add(new gamerguy11.sixtoolsaddon.modules.SixToolsScaffold());
      Modules.get().add(new gamerguy11.sixtoolsaddon.modules.SixToolsSpeedmine());
      Modules.get().add(new WitherPrint());
      Modules.get().add(new ForeverForward());
      Modules.get().add(new Ez());
      Modules.get().add(new InventorySorterModule());
      Modules.get().add(new AntiDrop());
      Modules.get().add(new AutoTpAccept());
      Modules.get().add(new Homes());
      Modules.get().add(new AutoReturnHome());
      Modules.get().add(new SocialSync());
      Modules.get().add(new DiscordNotifier());
      Modules.get().add(new ChatHighlighter());
      Modules.get().add(new ShulkerView());
      Modules.get().add(new WhisperLogger());
      Modules.get().add(new SwingSpeed());
      Modules.get().add(new Parkinsons());
      Modules.get().add(new SoundEditor());
      Modules.get().add(new DeathLogger());
      Modules.get().add(new Suicide());
      Modules.get().add(new Spoofer());
      Modules.get().add(new CsgoSpin());
      Modules.get().add(new BetterEsp());
      Modules.get().add(new BetterChams());
      Modules.get().add(new BetterShaders());
      Modules.get().add(new BetterTracers());
      Modules.get().add(new BetterTrajectories());
      Modules.get().add(new DeathEffects());
      Modules.get().add(new HandProgress());
      ChestTrackerModule chestTracker = new ChestTrackerModule();
      Modules.get().add(chestTracker);
      Modules.get().add(new ItemSearchBar());
      Modules.get().add(new MapDuplicator());
      Modules.get().add(new RespawnPointBlocker());
      Modules.get().add(new Stripper());
      Modules.get().add(new MusicTweaks());
      Modules.get().add(new StashMover());
      StashMoverSelectionHandler.init();
      Modules.get().add(new AutoStashSorter());
      AutoStashSorterSelectionHandler.init();
      ChatService.INSTANCE.init();
      Commands.add(new ChatCommand());
      Commands.add(new InventoryCommand());
      Commands.add(new DubCounterCommand());
      Commands.add(new SetDiscordCommand());
      Commands.add(new EnemyCommand());
      Commands.add(new CoordsCommand());
      Commands.add(new SpoofCommand());
      Commands.add(new ChestTrackerCommand());
      Commands.add(new SetInput());
      Commands.add(new SetOutput());
      Commands.add(new SetClear());
      Commands.add(new StashStatus());
      Commands.add(new StashSorterCommand());
      Commands.add(new OnlinePlayersCommand());
      Commands.add(new PrintStatsCommand());
      Tabs.add(new EnemiesTab());
      moveTabAfter(EnemiesTab.class, FriendsTab.class);
      Tabs.add(new ChatTab());
      moveTabAfter(ChatTab.class, EnemiesTab.class);
      Hud.get().register(PlayerTrackerHud.INFO);
      Hud.get().register(DubCounterHud.INFO);
      Hud.get().register(StatsHud.INFO);
      Hud.get().register(InventoryHud.INFO);
      Hud.get().register(ArmorHud.INFO);
      Hud.get().register(PvPNeccessaryHud.INFO);
      Hud.get().register(DimensionCoords.INFO);
      Hud.get().register(NowPlayingHud.INFO);
      Hud.get().register(Watermark.INFO);
   }

   public void onRegisterCategories() {
      Modules.registerCategory(CATEGORY);
   }

   public String getPackage() {
      return "gamerguy11.sixtoolsaddon";
   }

   private static void moveTabAfter(Class<? extends Tab> tabToMove, Class<? extends Tab> anchor) {
      List<Tab> tabs = Tabs.get();
      Tab moving = Tabs.get(tabToMove);
      if (moving != null) {
         int anchorIndex = -1;

         for(int i = 0; i < tabs.size(); ++i) {
            if (anchor.isInstance(tabs.get(i))) {
               anchorIndex = i;
               break;
            }
         }

         tabs.remove(moving);
         tabs.add(anchorIndex >= 0 ? anchorIndex + 1 : tabs.size(), moving);
      }
   }
}