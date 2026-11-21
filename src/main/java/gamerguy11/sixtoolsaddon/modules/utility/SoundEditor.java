package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.sound.SoundEngine;
import gamerguy11.sixtoolsaddon.sound.SoundType;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import java.util.ArrayList;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Util;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.client.gui.screen.ChatScreen;

public class SoundEditor extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Integer> masterVolume;
   private final Setting<Boolean> followGameVolume;
   private final Setting<List<String>> keywords;
   private final Map<SoundType, TypeSettings> types;
   private boolean wasDead;

   public SoundEditor() {
      super(SixToolsAddon.CATEGORY, "sound-editor", "Plays sounds from your own files for addon events. Put .ogg/.wav files in config/sixtoolsaddon/sounds/<type>/ (one folder per sound type, created automatically). Game sounds can be replaced too: add a rule in the Replacements section, e.g. only the end crystal explosion. Several files in one folder? Pick Random, Sequential or Specific per type below, or use the file list at the bottom of this window. Nothing plays for a type until its folder has a file.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.masterVolume = this.sgGeneral.add(((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("master-volume")).description("Overall volume of every sound this module plays (percent).")).defaultValue(100)).min(0).max(200).sliderRange(0, 200).build());
      this.followGameVolume = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("follow-game-volume")).description("Also scale by Minecraft's own Master Volume slider so these sounds obey it.")).defaultValue(true)).build());
      this.types = new EnumMap(SoundType.class);
      SoundEngine.INSTANCE.rescan();
      Setting<List<String>> keywordSetting = null;

      for(SoundType type : SoundType.values()) {
         SettingGroup group = this.settings.createGroup(type.display);
         TypeSettings ts = new TypeSettings();
         ts.enabled = group.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("enabled")).description(type.description)).defaultValue(type.defaultEnabled)).build());
         Setting<Boolean> enabled = ts.enabled;
         IntSetting.Builder var10002 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("volume")).description("Volume of this sound (percent).")).defaultValue(type.defaultVolume)).min(0).max(200).sliderRange(0, 200);
         Objects.requireNonNull(enabled);
         ts.volume = group.add(((IntSetting.Builder)var10002.visible(enabled::get)).build());
         DoubleSetting.Builder var10 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("pitch")).description("Playback speed. 1 = normal, 2 = an octave higher and twice as fast.")).defaultValue((double)1.0F).min((double)0.25F).max((double)4.0F).sliderRange((double)0.5F, (double)2.0F);
         Objects.requireNonNull(enabled);
         ts.pitch = group.add(((DoubleSetting.Builder)var10.visible(enabled::get)).build());
         var10 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("pitch-variation")).description("Random pitch wobble each time it plays (0.2 = up to 20% either way). Makes repeats feel less robotic.")).defaultValue(type == SoundType.TYPING ? 0.2 : (double)0.0F).min((double)0.0F).max(0.9).sliderRange((double)0.0F, (double)0.5F);
         Objects.requireNonNull(enabled);
         ts.pitchVariation = group.add(((DoubleSetting.Builder)var10.visible(enabled::get)).build());
         IntSetting.Builder var12 = ((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)(new IntSetting.Builder()).name("cooldown")).description("Minimum time between plays of this sound, in milliseconds. 0 = no limit.")).defaultValue(type.defaultCooldownMs)).min(0).max(60000).sliderRange(0, 5000);
         Objects.requireNonNull(enabled);
         ts.cooldown = group.add(((IntSetting.Builder)var12.visible(enabled::get)).build());
         EnumSetting.Builder var13 = (EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("mode")).description("How to choose when the folder has several files. Random: any file, not the same twice in a row. Sequential: go through them in alphabetical order. Specific: always the file named below.")).defaultValue(SoundEngine.Mode.Random);
         Objects.requireNonNull(enabled);
         ts.mode = group.add(((EnumSetting.Builder)var13.visible(enabled::get)).build());
         Setting<SoundEngine.Mode> mode = ts.mode;
         ts.file = group.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("file")).description("File name to play in Specific mode, e.g. boom.ogg (the extension is optional). The file list at the bottom of this window has a 'Use' button that fills this in for you. If the file isn't found, a random one plays instead.")).defaultValue("")).visible(() -> (Boolean)enabled.get() && mode.get() == SoundEngine.Mode.Specific)).build());
         if (type == SoundType.CHAT_KEYWORD) {
            StringListSetting.Builder var10001 = (StringListSetting.Builder)((StringListSetting.Builder)(new StringListSetting.Builder()).name("keywords")).description("Words or phrases to listen for in incoming chat (not case sensitive). Empty by default: it never triggers until you add some.");
            Objects.requireNonNull(enabled);
            keywordSetting = group.add(((StringListSetting.Builder)var10001.visible(enabled::get)).build());
         }

         this.types.put(type, ts);
      }

      this.keywords = keywordSetting;
   }

   public void play(SoundType type) {
      this.play(type, false);
   }

   private void play(SoundType type, boolean ignoreActive) {
      if (ignoreActive || this.isActive()) {
         TypeSettings ts = (TypeSettings)this.types.get(type);
         if (ts != null && (Boolean)ts.enabled.get()) {
            long now = System.currentTimeMillis();
            int cooldown = (Integer)ts.cooldown.get();
            if (cooldown <= 0 || now - ts.lastPlayed >= (long)cooldown) {
               ts.lastPlayed = now;
               SoundEngine.INSTANCE.play(type, (SoundEngine.Mode)ts.mode.get(), (String)ts.file.get(), this.volumeFor(ts), this.pitchFor(ts));
            }
         }
      }
   }

   public void onModuleToggled(Module module) {
      if (this.mc.world != null || this.mc.currentScreen != null) {
         boolean on = module.isActive();
         this.play(on ? SoundType.MODULE_ON : SoundType.MODULE_OFF, module == this && !on);
      }
   }

   private void preview(SoundType type, String fileName) {
      TypeSettings ts = (TypeSettings)this.types.get(type);
      if (ts != null) {
         SoundEngine.INSTANCE.playNamed(type, fileName, this.volumeFor(ts), this.pitchFor(ts));
      }
   }

   private float volumeFor(TypeSettings ts) {
      float volume = (float)(Integer)ts.volume.get() / 100.0F * ((float)(Integer)this.masterVolume.get() / 100.0F);
      if ((Boolean)this.followGameVolume.get() && this.mc.options != null) {
         volume *= this.mc.options.getSoundVolume(SoundCategory.MASTER);
      }

      return volume;
   }

   private float pitchFor(TypeSettings ts) {
      double pitch = (Double)ts.pitch.get();
      double variation = (Double)ts.pitchVariation.get();
      if (variation > (double)0.0F) {
         pitch *= (double)1.0F + (ThreadLocalRandom.current().nextDouble() * (double)2.0F - (double)1.0F) * variation;
      }

      return (float)pitch;
   }

   @EventHandler
   public void onKey(KeyEvent event) {
      if (event.action == KeyAction.Press) {
         if (this.mc.currentScreen instanceof ChatScreen) {
            this.play(SoundType.TYPING);
         }
      }
   }

   @EventHandler
   public void onMessage(ReceiveMessageEvent event) {
      if (this.keywords != null && !((List)this.keywords.get()).isEmpty()) {
         String message = event.getMessage().getString().toLowerCase(Locale.ROOT);

         for(String keyword : (List<String>)this.keywords.get()) {
            if (keyword != null && !keyword.isBlank() && message.contains(keyword.toLowerCase(Locale.ROOT))) {
               this.play(SoundType.CHAT_KEYWORD);
               return;
            }
         }

      }
   }

   @EventHandler
   public void onEntityAdded(EntityAddedEvent event) {
      Entity var3 = event.entity;
      if (var3 instanceof PlayerEntity player) {
         if (player != this.mc.player) {
            if (Enemies.get().isEnemy(player.getName().getString())) {
               this.play(SoundType.ENEMY_SPOTTED);
            }
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent.Post event) {
      if (this.mc.player != null) {
         boolean dead = this.mc.player.getHealth() <= 0.0F || this.mc.player.isDead();
         if (dead && !this.wasDead) {
            this.play(SoundType.DEATH);
         }

         this.wasDead = dead;
      }
   }

   @EventHandler
   public void onGameLeft(GameLeftEvent event) {
      this.wasDead = false;
   }

   public void onActivate() {
      this.wasDead = false;
      SoundEngine.INSTANCE.rescan();
   }

   private static void hint(GuiTheme theme, WVerticalList parent, String text) {
      WLabel label = parent.add(theme.label(text, 420.0)).expandX().widget();
      label.color(theme.textSecondaryColor());
   }

   public WWidget getWidget(GuiTheme theme) {
      SoundEngine engine = SoundEngine.INSTANCE;
      engine.rescan();
      WVerticalList list = theme.verticalList();

      WHorizontalList bar = list.add(theme.horizontalList()).expandX().widget();
      WButton openRoot = bar.add(theme.button("Open sounds folder")).expandX().widget();
      openRoot.action = () -> Util.getOperatingSystem().open(engine.getRoot());
      WButton reload = bar.add(theme.button("Reload files")).expandX().widget();
      reload.action = () -> {
         engine.reload();
         this.mc.setScreen(theme.moduleScreen(this));
      };
      hint(theme, list, "Supports .ogg and .wav. New files are picked up automatically, press Reload files after editing an existing one.");

      list.add(theme.horizontalSeparator("Event sounds")).expandX();
      hint(theme, list, "Sounds this addon plays for its own events. Nothing plays for a type until its folder has a file.");

      for(SoundType type : SoundType.values()) {
         List<String> names = engine.getFileNames(type);
         TypeSettings ts = this.types.get(type);
         String title = type.display + " (" + names.size() + (names.size() == 1 ? " file)" : " files)");
         WSection section = list.add(theme.section(title, false)).expandX().widget();
         hint(theme, section, type.description);

         String selected = ts.mode.get() == SoundEngine.Mode.Specific ? ts.file.get() : "";
         WHorizontalList info = section.add(theme.horizontalList()).expandX().widget();
         info.add(theme.label("Mode: " + ts.mode.get() + (selected.isBlank() ? "" : " (" + selected + ")")));
         WButton openFolder = info.add(theme.button("Open folder")).widget();
         openFolder.action = () -> Util.getOperatingSystem().open(engine.getFolder(type));

         if (names.isEmpty()) {
            hint(theme, section, "No files yet. Drop .ogg / .wav into sounds/" + type.folder + "/");
            continue;
         }

         WTable files = section.add(theme.table()).expandX().widget();
         for(String name : names) {
            boolean active = name.equalsIgnoreCase(selected);
            files.add(theme.label(active ? name + "  (selected)" : name)).expandX();
            WButton play = files.add(theme.button("Play")).widget();
            play.action = () -> this.preview(type, name);
            WButton use = files.add(theme.button("Select")).widget();
            use.action = () -> {
               ts.mode.set(SoundEngine.Mode.Specific);
               ts.file.set(name);
               this.mc.setScreen(theme.moduleScreen(this));
            };
            files.row();
         }
      }

      return list;
   }

   private static final class TypeSettings {
      Setting<Boolean> enabled;
      Setting<Integer> volume;
      Setting<Double> pitch;
      Setting<Double> pitchVariation;
      Setting<Integer> cooldown;
      Setting<SoundEngine.Mode> mode;
      Setting<String> file;
      long lastPlayed;
   }
}
