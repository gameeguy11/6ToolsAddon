package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.mixin.music.MusicTrackerAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.sound.MusicSound;
import org.jetbrains.annotations.Nullable;

public class MusicTweaks extends Module {
   private static final Random RANDOM = new Random();
   private final SettingGroup sgPitch;
   private final SettingGroup sgVolume;
   private final SettingGroup sgCooldown;
   private final SettingGroup sgNowPlaying;
   private final SettingGroup sgOverworldSoundtrack;
   private final SettingGroup sgCreativeSoundtrack;
   private final SettingGroup sgUnderwaterSoundtrack;
   private final SettingGroup sgNetherSoundtrack;
   private final SettingGroup sgEndSoundtrack;
   private final SettingGroup sgRecordsSoundtrack;
   private final SettingGroup sgMenuSoundtrack;
   private final Setting<Boolean> startOnEnable;
   private final Setting<Boolean> stopOnDisable;
   private final Setting<Boolean> displayNowPlaying;
   private final Setting<Boolean> fadeOut;
   private final Setting<DisplayType> displayTypeSetting;
   private final Setting<Boolean> overrideDelayMode;
   private final Setting<Integer> timeUntilNextSong;
   private final Setting<Integer> minTimeUntilNextSong;
   private final Setting<Integer> maxTimeUntilNextSong;
   private final Setting<Boolean> randomPitch;
   private final Setting<Boolean> trippyPitchSetting;
   private final Setting<Integer> pitchAdjustment;
   private final Setting<Integer> pitchRange;
   private final Setting<Integer> pitchIntensity;
   private final Setting<Integer> weightedChanceSetting;
   private final Setting<Integer> volume;
   private final Setting<Boolean> minecraft;
   private final Setting<Boolean> clark;
   private final Setting<Boolean> sweden;
   private final Setting<Boolean> subwooferLullaby;
   private final Setting<Boolean> livingMice;
   private final Setting<Boolean> haggstrom;
   private final Setting<Boolean> danny;
   private final Setting<Boolean> key;
   private final Setting<Boolean> oxygene;
   private final Setting<Boolean> dryHands;
   private final Setting<Boolean> wetHands;
   private final Setting<Boolean> miceOnVenus;
   private final Setting<Boolean> aerie;
   private final Setting<Boolean> ancestry;
   private final Setting<Boolean> aFamiliarRoom;
   private final Setting<Boolean> anOrdinaryDay;
   private final Setting<Boolean> bromeliad;
   private final Setting<Boolean> comfortingMemories;
   private final Setting<Boolean> crescentDunes;
   private final Setting<Boolean> echoInTheWind;
   private final Setting<Boolean> firebugs;
   private final Setting<Boolean> floatingDream;
   private final Setting<Boolean> infiniteAmethyst;
   private final Setting<Boolean> labyrinthine;
   private final Setting<Boolean> leftToBloom;
   private final Setting<Boolean> oneMoreDay;
   private final Setting<Boolean> standTall;
   private final Setting<Boolean> wending;
   private final Setting<Boolean> deeper;
   private final Setting<Boolean> eldUnknown;
   private final Setting<Boolean> endless;
   private final Setting<Boolean> featherfall;
   private final Setting<Boolean> komorebi;
   private final Setting<Boolean> pokopoko;
   private final Setting<Boolean> puzzlebox;
   private final Setting<Boolean> watcher;
   private final Setting<Boolean> yakusoku;
   private final Setting<Boolean> biomeFest;
   private final Setting<Boolean> blindSpots;
   private final Setting<Boolean> hauntMuskie;
   private final Setting<Boolean> ariaMath;
   private final Setting<Boolean> dreiton;
   private final Setting<Boolean> tasWell;
   private final Setting<Boolean> axolotl;
   private final Setting<Boolean> dragonFish;
   private final Setting<Boolean> shuniji;
   private final Setting<Boolean> concreteHalls;
   private final Setting<Boolean> deadVoxel;
   private final Setting<Boolean> warmth;
   private final Setting<Boolean> balladOfTheCats;
   private final Setting<Boolean> chrysopoeia;
   private final Setting<Boolean> rubedo;
   private final Setting<Boolean> soBelow;
   private final Setting<Boolean> theEnd;
   private final Setting<Boolean> boss;
   private final Setting<Boolean> alpha;
   private final Setting<Boolean> record5;
   private final Setting<Boolean> record11;
   private final Setting<Boolean> record13;
   private final Setting<Boolean> recordCat;
   private final Setting<Boolean> recordBlocks;
   private final Setting<Boolean> recordChirp;
   private final Setting<Boolean> recordFar;
   private final Setting<Boolean> recordMall;
   private final Setting<Boolean> recordMellohi;
   private final Setting<Boolean> recordStal;
   private final Setting<Boolean> recordStrad;
   private final Setting<Boolean> recordWard;
   private final Setting<Boolean> recordWait;
   private final Setting<Boolean> recordOtherside;
   private final Setting<Boolean> recordPigstep;
   private final Setting<Boolean> recordRelic;
   private final Setting<Boolean> recordCreator;
   private final Setting<Boolean> recordCreatorMusicBox;
   private final Setting<Boolean> recordPrecipice;
   private final Setting<Boolean> mutation;
   private final Setting<Boolean> moogCity2;
   private final Setting<Boolean> beginning2;
   private final Setting<Boolean> floatingTrees;
   private @Nullable String lastDim;
   private @Nullable String currentSong;
   private @Nullable MusicSound currentType;
   private @Nullable PitchDirection lastDirection;

   public MusicTweaks() {
      super(SixToolsAddon.CATEGORY, "MusicTweaks", "Allows you to fuck with the background music.");
      this.sgPitch = this.settings.createGroup("Pitch");
      this.sgVolume = this.settings.createGroup("Volume");
      this.sgCooldown = this.settings.createGroup("Cooldown");
      this.sgNowPlaying = this.settings.createGroup("Now Playing");
      this.sgOverworldSoundtrack = this.settings.createGroup("Overworld Soundtrack");
      this.sgCreativeSoundtrack = this.settings.createGroup("Creative Soundtrack");
      this.sgUnderwaterSoundtrack = this.settings.createGroup("Underwater Soundtrack");
      this.sgNetherSoundtrack = this.settings.createGroup("Nether Soundtrack");
      this.sgEndSoundtrack = this.settings.createGroup("End Soundtrack");
      this.sgRecordsSoundtrack = this.settings.createGroup("Music Discs");
      this.sgMenuSoundtrack = this.settings.createGroup("Menu Soundtrack");
      this.startOnEnable = this.sgNowPlaying.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("start-on-enable")).description("Start playing music when enabling the module. Won't overwrite a currently-playing song.")).defaultValue(true)).build());
      this.stopOnDisable = this.sgNowPlaying.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("stop-on-disable")).description("Stop the currently playing music when disabling the module.")).defaultValue(true)).build());
      this.displayNowPlaying = this.sgNowPlaying.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("display-now-playing")).description("Displays the name of the currently playing song.")).defaultValue(true)).build());
      SettingGroup var10001 = this.sgNowPlaying;
      BoolSetting.Builder var10002 = (BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("fade-out-display")).description("Fade out the display instead of keeping it active for the duration of the song.");
      Setting<Boolean> var10003 = this.displayNowPlaying;
      Objects.requireNonNull(var10003);
      this.fadeOut = var10001.add(((BoolSetting.Builder)((BoolSetting.Builder)var10002.visible(var10003::get)).defaultValue(false)).build());
      var10001 = this.sgNowPlaying;
      EnumSetting.Builder var4 = (EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("display-mode")).defaultValue(MusicTweaks.DisplayType.Chat);
      var10003 = this.displayNowPlaying;
      Objects.requireNonNull(var10003);
      this.displayTypeSetting = var10001.add(((EnumSetting.Builder)var4.visible(var10003::get)).build());
      this.overrideDelayMode = this.sgCooldown.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("use-exact-delay")).description("Use one specific cooldown between songs instead of a random range.")).defaultValue(false)).build());
      var10001 = this.sgCooldown;
      IntSetting.Builder var5 = (IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("song-delay-seconds")).description("Desired cooldown between songs. Will apply after next song if not currently playing (or module toggle.)")).range(0, 10000).sliderRange(0, 2400).defaultValue(300);
      var10003 = this.overrideDelayMode;
      Objects.requireNonNull(var10003);
      this.timeUntilNextSong = var10001.add(((IntSetting.Builder)var5.visible(var10003::get)).build());
      this.minTimeUntilNextSong = this.sgCooldown.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("minimum-delay-seconds")).description("Minimum desired cooldown between songs (in seconds.)")).range(0, 10000).sliderRange(0, 1200).defaultValue(600)).visible(() -> !(Boolean)this.overrideDelayMode.get())).build());
      this.maxTimeUntilNextSong = this.sgCooldown.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("maximum-delay-seconds")).description("Maximum desired cooldown between songs (in seconds.)")).range(0, 10000).sliderRange(0, 2400).defaultValue(1200)).visible(() -> !(Boolean)this.overrideDelayMode.get())).build());
      this.randomPitch = this.sgPitch.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("random-pitch")).description("Use a random pitch within a range instead of the same adjusted pitch each time.")).defaultValue(false)).build());
      var10001 = this.sgPitch;
      BoolSetting.Builder var6 = (BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("trippy-pitch")).description(":3")).defaultValue(false);
      var10003 = this.randomPitch;
      Objects.requireNonNull(var10003);
      this.trippyPitchSetting = var10001.add(((BoolSetting.Builder)var6.visible(var10003::get)).build());
      this.pitchAdjustment = this.sgPitch.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("song-pitch-adjustment")).description("Desired pitch adjustment.")).range(-500, 500).sliderRange(-250, 250).defaultValue(0)).visible(() -> !(Boolean)this.randomPitch.get())).build());
      this.pitchRange = this.sgPitch.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("random-pitch-adjustment-range")).description("Will apply on the next song.")).range(1, 500).sliderRange(1, 500).defaultValue(37)).visible(() -> (Boolean)this.randomPitch.get() && !(Boolean)this.trippyPitchSetting.get())).build());
      this.pitchIntensity = this.sgPitch.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("intensity")).range(0, 1000).sliderRange(0, 500).defaultValue(77)).visible(() -> (Boolean)this.randomPitch.get() && (Boolean)this.trippyPitchSetting.get())).build());
      this.weightedChanceSetting = this.sgPitch.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("weighted-chance-%")).sliderRange(0, 100).defaultValue(95)).visible(() -> (Boolean)this.randomPitch.get() && (Boolean)this.trippyPitchSetting.get())).build());
      this.volume = this.sgVolume.add(((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("volume-%-boost")).sliderRange(-100, 250).range(-100, 400).defaultValue(0)).build());
      this.minecraft = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Minecraft")).description("calm1.ogg")).defaultValue(true)).build());
      this.clark = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Clark")).description("calm2.ogg")).defaultValue(true)).build());
      this.sweden = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Sweden")).description("calm3.ogg")).defaultValue(true)).build());
      this.subwooferLullaby = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Subwoofer-Lullaby")).description("hal1.ogg")).defaultValue(true)).build());
      this.livingMice = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Living-Mice")).description("hal2.ogg")).defaultValue(true)).build());
      this.haggstrom = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Haggstrom")).description("hal3.ogg")).defaultValue(true)).build());
      this.danny = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Danny")).description("hal4.ogg")).defaultValue(true)).build());
      this.key = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Key")).description("nuance1.ogg")).defaultValue(true)).build());
      this.oxygene = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Oxygene")).description("nuance2.ogg")).defaultValue(true)).build());
      this.dryHands = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Dry-Hands")).description("piano1.ogg")).defaultValue(true)).build());
      this.wetHands = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Wet-Hands")).description("piano2.ogg")).defaultValue(true)).build());
      this.miceOnVenus = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Mice-on-Venus")).description("piano3.ogg")).defaultValue(true)).build());
      this.aerie = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Aerie")).description("aerie.ogg")).defaultValue(false)).build());
      this.ancestry = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Ancestry")).description("ancestry.ogg")).defaultValue(false)).build());
      this.aFamiliarRoom = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-A-Familiar-Room")).description("a_familiar_room.ogg")).defaultValue(false)).build());
      this.anOrdinaryDay = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Kumi-Tanioka-/-An-Ordinary-Day")).description("an_ordinary_day.ogg")).defaultValue(false)).build());
      this.bromeliad = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-Bromeliad")).description("bromeliad.ogg")).defaultValue(true)).build());
      this.comfortingMemories = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Kumi-Tanioka-/-Comforting-Memories")).description("comforting_memories.ogg")).defaultValue(false)).build());
      this.crescentDunes = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-Crescent-Dunes")).description("crescent_dunes.ogg")).defaultValue(false)).build());
      this.echoInTheWind = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-Echo-in-the-Wind")).description("echo_in_the_wind.ogg")).defaultValue(false)).build());
      this.firebugs = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Firebugs")).description("firebugs.ogg")).defaultValue(true)).build());
      this.floatingDream = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Kumi-Tanioka-/-Floating-Dream")).description("floating_dream.ogg")).defaultValue(false)).build());
      this.infiniteAmethyst = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Infinite-Amethyst")).description("infinite_amethyst.ogg")).defaultValue(false)).build());
      this.labyrinthine = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Labyrinthine")).description("labyrinthine.ogg")).defaultValue(false)).build());
      this.leftToBloom = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Left-to-Bloom")).description("left_to_bloom.ogg")).defaultValue(false)).build());
      this.oneMoreDay = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-One-More-Day")).description("one_more_day.ogg")).defaultValue(false)).build());
      this.standTall = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Stand-Tall")).description("stand_tall.ogg")).defaultValue(false)).build());
      this.wending = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Wending")).description("wending.ogg")).defaultValue(true)).build());
      this.deeper = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Deeper")).description("deeper.ogg")).defaultValue(true)).build());
      this.eldUnknown = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Eld-Unknown")).description("eld_unknown.ogg")).defaultValue(true)).build());
      this.endless = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Endless")).description("endless.ogg")).defaultValue(true)).build());
      this.featherfall = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-Featherfall")).description("featherfall.ogg")).defaultValue(true)).build());
      this.komorebi = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Kumi-Tanioka-/-komorebi")).description("komorebi.ogg")).defaultValue(true)).build());
      this.pokopoko = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Kumi-Tanioka-/-pokopoko")).description("pokopoko.ogg")).defaultValue(true)).build());
      this.puzzlebox = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-Puzzlebox")).description("puzzlebox.ogg")).defaultValue(true)).build());
      this.watcher = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-Watcher")).description("watcher.ogg")).defaultValue(true)).build());
      this.yakusoku = this.sgOverworldSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Kumi-Tanioka-/-yakusoku")).description("yakusoku.ogg")).defaultValue(true)).build());
      this.biomeFest = this.sgCreativeSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Biome-Fest")).description("creative1.ogg")).defaultValue(false)).build());
      this.blindSpots = this.sgCreativeSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Blind-Spots")).description("creative2.ogg")).defaultValue(true)).build());
      this.hauntMuskie = this.sgCreativeSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Haunt-Muskie")).description("creative3.ogg")).defaultValue(true)).build());
      this.ariaMath = this.sgCreativeSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Aria-Math")).description("creative4.ogg")).defaultValue(true)).build());
      this.dreiton = this.sgCreativeSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Dreiton")).description("creative5.ogg")).defaultValue(false)).build());
      this.tasWell = this.sgCreativeSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Taswell")).description("creative6.ogg")).defaultValue(true)).build());
      this.axolotl = this.sgUnderwaterSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Axolotl")).description("axolotl.ogg")).defaultValue(true)).build());
      this.dragonFish = this.sgUnderwaterSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Dragon-Fish")).description("dragon_fish.ogg")).defaultValue(true)).build());
      this.shuniji = this.sgUnderwaterSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Shuniji")).description("shuniji.ogg")).defaultValue(true)).build());
      this.concreteHalls = this.sgNetherSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Concrete-Halls")).description("nether1.ogg")).defaultValue(false)).build());
      this.deadVoxel = this.sgNetherSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Dead-Voxel")).description("nether2.ogg")).defaultValue(false)).build());
      this.warmth = this.sgNetherSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Warmth")).description("nether3.ogg")).defaultValue(false)).build());
      this.balladOfTheCats = this.sgNetherSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Ballad-of-the-Cats")).description("nether4.ogg")).defaultValue(false)).build());
      this.chrysopoeia = this.sgNetherSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Chrysopoeia")).description("chrysopoeia.ogg")).defaultValue(false)).build());
      this.rubedo = this.sgNetherSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Rubedo")).description("rubedo.ogg")).defaultValue(false)).build());
      this.soBelow = this.sgNetherSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-So-Below")).description("so_below.ogg")).defaultValue(false)).build());
      this.theEnd = this.sgEndSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-The-End")).description("end.ogg")).defaultValue(false)).build());
      this.boss = this.sgEndSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Boss")).description("boss.ogg")).defaultValue(false)).build());
      this.alpha = this.sgEndSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Alpha")).description("credits.ogg")).defaultValue(false)).build());
      this.record5 = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Samuel-Aberg-/-5")).description("Music Disc: 5")).defaultValue(false)).build());
      this.record11 = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-11")).description("Music Disc: 11")).defaultValue(false)).build());
      this.record13 = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-13")).description("Music Disc: 13")).defaultValue(false)).build());
      this.recordCat = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Cat")).description("Music Disc: Cat")).defaultValue(true)).build());
      this.recordBlocks = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Blocks")).description("Music Disc: Blocks")).defaultValue(true)).build());
      this.recordChirp = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Chirp")).description("Music Disc: Chirp")).defaultValue(false)).build());
      this.recordFar = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Far")).description("Music Disc: Far")).defaultValue(false)).build());
      this.recordMall = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Mall")).description("Music Disc: Mall")).defaultValue(true)).build());
      this.recordMellohi = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Mellohi")).description("Music Disc: Mellohi")).defaultValue(false)).build());
      this.recordStal = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Stal")).description("Music Disc: Stal")).defaultValue(false)).build());
      this.recordStrad = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Strad")).description("Music Disc: Strad")).defaultValue(false)).build());
      this.recordWard = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Ward")).description("Music Disc: Ward")).defaultValue(false)).build());
      this.recordWait = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Wait")).description("Music Disc: Wait")).defaultValue(false)).build());
      this.recordOtherside = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Otherside")).description("Music Disc: Otherside")).defaultValue(false)).build());
      this.recordPigstep = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Pigstep")).description("Music Disc: Pigstep")).defaultValue(false)).build());
      this.recordRelic = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-Relic")).description("Music Disc: Relic")).defaultValue(false)).build());
      this.recordCreator = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Creator")).description("Music Disc: Creator")).defaultValue(false)).build());
      this.recordCreatorMusicBox = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Lena-Raine-/-Creator-(Music-Box)")).description("Music Disc: Creator (Music Box)")).defaultValue(false)).build());
      this.recordPrecipice = this.sgRecordsSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("Aaron-Cherof-/-Precipice")).description("Music Disc: Precipice")).defaultValue(false)).build());
      this.mutation = this.sgMenuSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Mutation")).description("menu1.ogg")).defaultValue(false)).build());
      this.moogCity2 = this.sgMenuSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Moog-City-2")).description("menu2.ogg")).defaultValue(false)).build());
      this.beginning2 = this.sgMenuSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Beginning-2")).description("menu3.ogg")).defaultValue(false)).build());
      this.floatingTrees = this.sgMenuSoundtrack.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("C418-/-Floating-Trees")).description("menu4.ogg")).defaultValue(false)).build());
      this.lastDim = null;
      this.currentSong = null;
      this.currentType = null;
      this.lastDirection = null;
      this.runInMainMenu = true;
      this.sgOverworldSoundtrack.sectionExpanded = false;
      this.sgCreativeSoundtrack.sectionExpanded = false;
      this.sgUnderwaterSoundtrack.sectionExpanded = false;
      this.sgNetherSoundtrack.sectionExpanded = false;
      this.sgEndSoundtrack.sectionExpanded = false;
      this.sgRecordsSoundtrack.sectionExpanded = false;
      this.sgMenuSoundtrack.sectionExpanded = false;
   }

   public MusicSound getType() {
      if (this.currentType != null) {
         return this.currentType;
      } else {
         int min;
         int max;
         if (this.mc.player == null) {
            min = 69;
            max = 420;
         } else if ((Boolean)this.overrideDelayMode.get()) {
            min = this.getTimeUntilNextSong();
            max = this.getTimeUntilNextSong();
         } else {
            min = (Integer)this.minTimeUntilNextSong.get() * 20;
            max = (Integer)this.maxTimeUntilNextSong.get() * 20;
         }

         if (max <= min) {
            max = min + 1;
         }

         MusicSound type = new MusicSound(SoundEvents.MUSIC_GAME, min, RANDOM.nextInt(min, max), false);
         this.currentType = type;
         return type;
      }
   }

   public String getSongName(String songID) {
      String songName;
      switch (songID) {
         case "minecraft.ogg" -> songName = "C418 - Minecraft";
         case "clark.ogg" -> songName = "C418 - Clark";
         case "sweden.ogg" -> songName = "C418 - Sweden";
         case "biome_fest.ogg" -> songName = "C418 - Biome Fest";
         case "blind_spots.ogg" -> songName = "C418 - Blind Spots";
         case "haunt_muskie.ogg" -> songName = "C418 - Haunt Muskie";
         case "aria_math.ogg" -> songName = "C418 - Aria Math";
         case "dreiton.ogg" -> songName = "C418 - Dreiton";
         case "taswell.ogg" -> songName = "C418 - Taswell";
         case "subwoofer_lullaby.ogg" -> songName = "C418 - Subwoofer Lullaby";
         case "living_mice.ogg" -> songName = "C418 - Living Mice";
         case "haggstrom.ogg" -> songName = "C418 - Haggstrom";
         case "danny.ogg" -> songName = "C418 - Danny";
         case "key.ogg" -> songName = "C418 - Key";
         case "oxygene.ogg" -> songName = "C418 - Oxygène";
         case "dry_hands.ogg" -> songName = "C418 - Dry Hands";
         case "wet_hands.ogg" -> songName = "C418 - Wet Hands";
         case "mice_on_venus.ogg" -> songName = "C418 - Mice on Venus";
         case "aerie.ogg" -> songName = "Lena Raine - Aerie";
         case "ancestry.ogg" -> songName = "Lena Raine - Ancestry";
         case "a_familiar_room.ogg" -> songName = "Aaron Cherof - A Familiar Room";
         case "an_ordinary_day.ogg" -> songName = "Kumi Tanioka - An Ordinary Day";
         case "bromeliad.ogg" -> songName = "Aaron Cherof - Bromeliad";
         case "comforting_memories.ogg" -> songName = "Kumi Tanioka - Comforting Memories";
         case "crescent_dunes.ogg" -> songName = "Aaron Cherof - Crescent Dunes";
         case "echo_in_the_wind.ogg" -> songName = "Aaron Cherof - Echo in the Wind";
         case "firebugs.ogg" -> songName = "Lena Raine - Firebugs";
         case "floating_dream.ogg" -> songName = "Kumi Tanioka - Floating Dream";
         case "infinite_amethyst.ogg" -> songName = "Lena Raine - Infinite Amethyst";
         case "labyrinthine.ogg" -> songName = "Lena Raine - Labyrinthine";
         case "left_to_bloom.ogg" -> songName = "Lena Raine - Left to Bloom";
         case "one_more_day.ogg" -> songName = "Lena Raine - One More Day";
         case "stand_tall.ogg" -> songName = "Lena Raine - Stand Tall";
         case "wending.ogg" -> songName = "Lena Raine - Wending";
         case "axolotl.ogg" -> songName = "C418 - Axolotl";
         case "dragon_fish.ogg" -> songName = "C418 - Dragon Fish";
         case "shuniji.ogg" -> songName = "C418 - Shuniji";
         case "concrete_halls.ogg" -> songName = "C418 - Concrete Halls";
         case "dead_voxel.ogg" -> songName = "C418 - Dead Voxel";
         case "warmth.ogg" -> songName = "C418 - Warmth";
         case "ballad_of_the_cats.ogg" -> songName = "C418 - Ballad of the Cats";
         case "chrysopoeia.ogg" -> songName = "Lena Raine - Chrysopoeia";
         case "rubedo.ogg" -> songName = "Lena Raine - Rubedo";
         case "so_below.ogg" -> songName = "Lena Raine - So Below";
         case "boss.ogg" -> songName = "C418 - Boss";
         case "the_end.ogg" -> songName = "C418 - The End";
         case "mutation.ogg" -> songName = "C418 - Mutation";
         case "moog_city_2.ogg" -> songName = "C418 - Moog City 2";
         case "beginning_2.ogg" -> songName = "C418 - Beginning 2";
         case "floating_trees.ogg" -> songName = "C418 - Floating Trees";
         case "alpha.ogg" -> songName = "C418 - Alpha";
         case "5.ogg" -> songName = "Samuel Aberg - 5";
         case "11.ogg" -> songName = "C418 - 11";
         case "13.ogg" -> songName = "C418 - 13";
         case "cat.ogg" -> songName = "C418 - Cat";
         case "blocks.ogg" -> songName = "C418 - Blocks";
         case "chirp.ogg" -> songName = "C418 - Chirp";
         case "far.ogg" -> songName = "C418 - Far";
         case "mall.ogg" -> songName = "C418 - Mall";
         case "mellohi.ogg" -> songName = "C418 - Mellohi";
         case "stal.ogg" -> songName = "C418 - Stal";
         case "strad.ogg" -> songName = "C418 - Strad";
         case "ward.ogg" -> songName = "C418 - Ward";
         case "wait.ogg" -> songName = "C418 - Wait";
         case "otherside.ogg" -> songName = "Lena Raine - Otherside";
         case "pigstep.ogg" -> songName = "Lena Raine - Pigstep";
         case "relic.ogg" -> songName = "Aaron Cherof - Relic";
         case "deeper.ogg" -> songName = "Lena Raine - Deeper";
         case "eld_unknown.ogg" -> songName = "Lena Raine - Eld Unknown";
         case "endless.ogg" -> songName = "Lena Raine - Endless";
         case "featherfall.ogg" -> songName = "Aaron Cherof - Featherfall";
         case "puzzlebox.ogg" -> songName = "Aaron Cherof - Puzzlebox";
         case "watcher.ogg" -> songName = "Aaron Cherof - Watcher";
         case "komorebi.ogg" -> songName = "Kumi Tanioka - komorebi";
         case "pokopoko.ogg" -> songName = "Kumi Tanioka - pokopoko";
         case "yakusoku.ogg" -> songName = "Kumi Tanioka - yakusoku";
         case "creator.ogg" -> songName = "Lena Raine - Creator";
         case "creator_music_box.ogg" -> songName = "Lena Raine - Creator (Music Box)";
         case "precipice.ogg" -> songName = "Aaron Cherof - Precipice";
         default -> songName = "Unknown Track";
      }

      return songName;
   }

   public List<String> getSoundSet() {
      List<String> ids = new ArrayList();
      if ((Boolean)this.minecraft.get()) {
         ids.add("minecraft:music/game/minecraft");
      }

      if ((Boolean)this.clark.get()) {
         ids.add("minecraft:music/game/clark");
      }

      if ((Boolean)this.sweden.get()) {
         ids.add("minecraft:music/game/sweden");
      }

      if ((Boolean)this.biomeFest.get()) {
         ids.add("minecraft:music/game/creative/biome_fest");
      }

      if ((Boolean)this.blindSpots.get()) {
         ids.add("minecraft:music/game/creative/blind_spots");
      }

      if ((Boolean)this.hauntMuskie.get()) {
         ids.add("minecraft:music/game/creative/haunt_muskie");
      }

      if ((Boolean)this.ariaMath.get()) {
         ids.add("minecraft:music/game/creative/aria_math");
      }

      if ((Boolean)this.dreiton.get()) {
         ids.add("minecraft:music/game/creative/dreiton");
      }

      if ((Boolean)this.tasWell.get()) {
         ids.add("minecraft:music/game/creative/taswell");
      }

      if ((Boolean)this.subwooferLullaby.get()) {
         ids.add("minecraft:music/game/subwoofer_lullaby");
      }

      if ((Boolean)this.livingMice.get()) {
         ids.add("minecraft:music/game/living_mice");
      }

      if ((Boolean)this.haggstrom.get()) {
         ids.add("minecraft:music/game/haggstrom");
      }

      if ((Boolean)this.danny.get()) {
         ids.add("minecraft:music/game/danny");
      }

      if ((Boolean)this.key.get()) {
         ids.add("minecraft:music/game/key");
      }

      if ((Boolean)this.oxygene.get()) {
         ids.add("minecraft:music/game/oxygene");
      }

      if ((Boolean)this.dryHands.get()) {
         ids.add("minecraft:music/game/dry_hands");
      }

      if ((Boolean)this.wetHands.get()) {
         ids.add("minecraft:music/game/wet_hands");
      }

      if ((Boolean)this.miceOnVenus.get()) {
         ids.add("minecraft:music/game/mice_on_venus");
      }

      if ((Boolean)this.aerie.get()) {
         ids.add("minecraft:music/game/swamp/aerie");
      }

      if ((Boolean)this.bromeliad.get()) {
         ids.add("minecraft:music/game/bromeliad");
      }

      if ((Boolean)this.firebugs.get()) {
         ids.add("minecraft:music/game/swamp/firebugs");
      }

      if ((Boolean)this.leftToBloom.get()) {
         ids.add("minecraft:music/game/left_to_bloom");
      }

      if ((Boolean)this.axolotl.get()) {
         ids.add("minecraft:music/game/water/axolotl");
      }

      if ((Boolean)this.dragonFish.get()) {
         ids.add("minecraft:music/game/water/dragon_fish");
      }

      if ((Boolean)this.shuniji.get()) {
         ids.add("minecraft:music/game/water/shuniji");
      }

      if ((Boolean)this.labyrinthine.get()) {
         ids.add("minecraft:music/game/swamp/labyrinthine");
      }

      if ((Boolean)this.echoInTheWind.get()) {
         ids.add("minecraft:music/game/echo_in_the_wind");
      }

      if ((Boolean)this.standTall.get()) {
         ids.add("minecraft:music/game/stand_tall");
      }

      if ((Boolean)this.ancestry.get()) {
         ids.add("minecraft:music/game/ancestry");
      }

      if ((Boolean)this.aFamiliarRoom.get()) {
         ids.add("minecraft:music/game/a_familiar_room");
      }

      if ((Boolean)this.oneMoreDay.get()) {
         ids.add("minecraft:music/game/one_more_day");
      }

      if ((Boolean)this.wending.get()) {
         ids.add("minecraft:music/game/wending");
      }

      if ((Boolean)this.infiniteAmethyst.get()) {
         ids.add("minecraft:music/game/infinite_amethyst");
      }

      if ((Boolean)this.anOrdinaryDay.get()) {
         ids.add("minecraft:music/game/an_ordinary_day");
      }

      if ((Boolean)this.crescentDunes.get()) {
         ids.add("minecraft:music/game/crescent_dunes");
      }

      if ((Boolean)this.floatingDream.get()) {
         ids.add("minecraft:music/game/floating_dream");
      }

      if ((Boolean)this.comfortingMemories.get()) {
         ids.add("minecraft:music/game/comforting_memories");
      }

      if ((Boolean)this.mutation.get()) {
         ids.add("minecraft:music/menu/mutation");
      }

      if ((Boolean)this.moogCity2.get()) {
         ids.add("minecraft:music/menu/moog_city_2");
      }

      if ((Boolean)this.beginning2.get()) {
         ids.add("minecraft:music/menu/beginning_2");
      }

      if ((Boolean)this.floatingTrees.get()) {
         ids.add("minecraft:music/menu/floating_trees");
      }

      if ((Boolean)this.alpha.get()) {
         ids.add("minecraft:music/game/end/alpha");
      }

      if ((Boolean)this.theEnd.get()) {
         ids.add("minecraft:music/game/end/the_end");
      }

      if ((Boolean)this.boss.get()) {
         ids.add("minecraft:music/game/end/boss");
      }

      if ((Boolean)this.soBelow.get()) {
         ids.add("minecraft:music/game/nether/soulsand_valley/so_below");
      }

      if ((Boolean)this.rubedo.get()) {
         ids.add("minecraft:music/game/nether/nether_wastes/rubedo");
      }

      if ((Boolean)this.chrysopoeia.get()) {
         ids.add("minecraft:music/game/nether/crimson_forest/chrysopoeia");
      }

      if ((Boolean)this.concreteHalls.get()) {
         ids.add("minecraft:music/game/nether/concrete_halls");
      }

      if ((Boolean)this.deadVoxel.get()) {
         ids.add("minecraft:music/game/nether/dead_voxel");
      }

      if ((Boolean)this.warmth.get()) {
         ids.add("minecraft:music/game/nether/warmth");
      }

      if ((Boolean)this.balladOfTheCats.get()) {
         ids.add("minecraft:music/game/nether/ballad_of_the_cats");
      }

      if ((Boolean)this.record5.get()) {
         ids.add("minecraft:records/5");
      }

      if ((Boolean)this.record11.get()) {
         ids.add("minecraft:records/11");
      }

      if ((Boolean)this.record13.get()) {
         ids.add("minecraft:records/13");
      }

      if ((Boolean)this.recordCat.get()) {
         ids.add("minecraft:records/cat");
      }

      if ((Boolean)this.recordBlocks.get()) {
         ids.add("minecraft:records/blocks");
      }

      if ((Boolean)this.recordChirp.get()) {
         ids.add("minecraft:records/chirp");
      }

      if ((Boolean)this.recordFar.get()) {
         ids.add("minecraft:records/far");
      }

      if ((Boolean)this.recordMall.get()) {
         ids.add("minecraft:records/mall");
      }

      if ((Boolean)this.recordMellohi.get()) {
         ids.add("minecraft:records/mellohi");
      }

      if ((Boolean)this.recordStal.get()) {
         ids.add("minecraft:records/stal");
      }

      if ((Boolean)this.recordStrad.get()) {
         ids.add("minecraft:records/strad");
      }

      if ((Boolean)this.recordWard.get()) {
         ids.add("minecraft:records/ward");
      }

      if ((Boolean)this.recordWait.get()) {
         ids.add("minecraft:records/wait");
      }

      if ((Boolean)this.recordOtherside.get()) {
         ids.add("minecraft:records/otherside");
      }

      if ((Boolean)this.recordPigstep.get()) {
         ids.add("minecraft:records/pigstep");
      }

      if ((Boolean)this.recordRelic.get()) {
         ids.add("minecraft:records/relic");
      }

      if ((Boolean)this.deeper.get()) {
         ids.add("minecraft:music/game/deeper");
      }

      if ((Boolean)this.eldUnknown.get()) {
         ids.add("minecraft:music/game/eld_unknown");
      }

      if ((Boolean)this.endless.get()) {
         ids.add("minecraft:music/game/endless");
      }

      if ((Boolean)this.featherfall.get()) {
         ids.add("minecraft:music/game/featherfall");
      }

      if ((Boolean)this.komorebi.get()) {
         ids.add("minecraft:music/game/komorebi");
      }

      if ((Boolean)this.pokopoko.get()) {
         ids.add("minecraft:music/game/pokopoko");
      }

      if ((Boolean)this.puzzlebox.get()) {
         ids.add("minecraft:music/game/puzzlebox");
      }

      if ((Boolean)this.watcher.get()) {
         ids.add("minecraft:music/game/watcher");
      }

      if ((Boolean)this.yakusoku.get()) {
         ids.add("minecraft:music/game/yakusoku");
      }

      if ((Boolean)this.recordCreator.get()) {
         ids.add("minecraft:records/creator");
      }

      if ((Boolean)this.recordPrecipice.get()) {
         ids.add("minecraft:records/precipice");
      }

      if ((Boolean)this.recordCreatorMusicBox.get()) {
         ids.add("minecraft:records/creator_music_box");
      }

      if (this.currentSong != null && ids.size() > 1) {
         for(int n = 0; n < ids.size(); ++n) {
            String var10000 = this.currentSong;
            Object var10001 = ids.get(n);
            if (var10000.equals("Sound[" + (String)var10001 + "]")) {
               ids.remove(n);
               this.currentSong = null;
               break;
            }
         }
      }

      return ids;
   }

   public float getNextPitchStep(float currentPitch) {
      if (this.lastDirection == null) {
         this.lastDirection = MusicTweaks.PitchDirection.Descending;
         float intensity = -((float)(Integer)this.pitchIntensity.get() / 10000.0F);
         return MathHelper.clamp(currentPitch + currentPitch * intensity, -5.0F, 5.0F);
      } else {
         switch (this.lastDirection.ordinal()) {
            case 0: {
               float weightedChance = RANDOM.nextFloat(0.0F, 1.0F);
               float intensity;
               if (weightedChance <= (float)(Integer)this.weightedChanceSetting.get() / 100.0F) {
                  intensity = (float)(Integer)this.pitchIntensity.get() / 10000.0F;
               } else {
                  intensity = -((float)(Integer)this.pitchIntensity.get() / 10000.0F);
                  this.lastDirection = MusicTweaks.PitchDirection.Descending;
               }

               return MathHelper.clamp(currentPitch + currentPitch * intensity, -5.0F, 5.0F);
            }
            case 1: {
               float weightedChance = RANDOM.nextFloat(0.0F, 1.0F);
               float intensity;
               if (weightedChance <= (float)(Integer)this.weightedChanceSetting.get() / 100.0F) {
                  intensity = -((float)(Integer)this.pitchIntensity.get() / 10000.0F);
               } else {
                  intensity = (float)(Integer)this.pitchIntensity.get() / 10000.0F;
                  this.lastDirection = MusicTweaks.PitchDirection.Ascending;
               }

               return MathHelper.clamp(currentPitch + currentPitch * intensity, -5.0F, 5.0F);
            }
            default:
               return currentPitch;
         }
      }
   }

   public void sendNowPlayingMessage(String songName) {
      if (this.mc.player != null) {
         String[] pieces = songName.split(" - ", 2);
         String artist = pieces[0];
         String track = pieces.length > 1 ? pieces[1] : "";
         ChatUtils.info("Now Playing: " + artist + " - " + track, new Object[0]);
      }
   }

   public void nullifyCurrentType() {
      this.currentType = null;
   }

   public MinecraftClient getClient() {
      return this.mc;
   }

   public boolean shouldFadeOut() {
      return (Boolean)this.fadeOut.get();
   }

   public boolean randomPitch() {
      return (Boolean)this.randomPitch.get();
   }

   public boolean trippyPitch() {
      return (Boolean)this.trippyPitchSetting.get();
   }

   public float getVolumeAdjustment() {
      return (float)(Integer)this.volume.get() / 100.0F;
   }

   public boolean overrideDelay() {
      return (Boolean)this.overrideDelayMode.get();
   }

   public DisplayType getDisplayMode() {
      return (DisplayType)this.displayTypeSetting.get();
   }

   public void setCurrentSong(@Nullable String id) {
      this.currentSong = id;
   }

   public int getTimeUntilNextSong() {
      return (Integer)this.timeUntilNextSong.get() * 20;
   }

   public float getPitchAdjustment() {
      return (float)(Integer)this.pitchAdjustment.get() / 1000.0F;
   }

   public boolean shouldDisplayNowPlaying() {
      return (Boolean)this.displayNowPlaying.get();
   }

   public float getRandomPitch() {
      return RANDOM.nextFloat((float)(-(Integer)this.pitchRange.get()) / 1000.0F, (float)(Integer)this.pitchRange.get() / 1000.0F);
   }

   public void onActivate() {
      if ((Boolean)this.startOnEnable.get()) {
         MusicSound type = this.getType();
         if (((MusicTrackerAccessor)this.mc.getMusicTracker()).getCurrent() == null) {
            this.mc.getMusicTracker().play(type);
         }

      }
   }

   public void onDeactivate() {
      if ((Boolean)this.stopOnDisable.get()) {
         this.mc.getMusicTracker().stop();
      }

      this.nullifyCurrentType();
   }

   @EventHandler
   private void onGameJoin(GameJoinedEvent event) {
      SoundInstance instance = ((MusicTrackerAccessor)this.mc.getMusicTracker()).getCurrent();
      if (instance != null) {
         MusicSound type = this.getType();
         if (type != this.mc.getMusicInstance()) {
            this.mc.getMusicTracker().stop();
            this.mc.getMusicTracker().play(type);
         }
      }

      if (this.mc.world != null) {
         this.lastDim = this.mc.world.getDimensionEntry().getIdAsString();
      }

   }

   @EventHandler
   private void onDimensionChange(PacketEvent.Receive event) {
      if (this.mc.world != null) {
         if (event.packet instanceof PlayerRespawnS2CPacket) {
            String dimensionType = this.mc.world.getDimensionEntry().getIdAsString();
            if (this.lastDim != null && !dimensionType.equals(this.lastDim)) {
               MusicSound type = this.getType();
               this.mc.getMusicTracker().stop();
               this.mc.getMusicTracker().play(type);
               this.lastDim = dimensionType;
            }

         }
      }
   }

   public static enum DisplayType {
      Chat,
      Record;

      private static DisplayType[] $values() {
         return new DisplayType[]{Chat, Record};
      }
   }

   private static enum PitchDirection {
      Ascending,
      Descending;

      private static PitchDirection[] $values() {
         return new PitchDirection[]{Ascending, Descending};
      }
   }
}
