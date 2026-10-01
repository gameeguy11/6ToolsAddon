package gamerguy11.sixtoolsaddon.sound;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.LineEvent.Type;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.sound.OggAudioStream;

public final class SoundEngine {
   public static final SoundEngine INSTANCE = new SoundEngine();
   private static final Set<String> EXTENSIONS = Set.of("ogg", "wav", "aif", "aiff", "au");
   private static final int MAX_VOICES = 16;
   private static final long MAX_FILE_BYTES = 16777216L;
   private static final long MAX_DECODED_BYTES = 67108864L;
   private final Path root = FabricLoader.getInstance().getConfigDir().resolve("sixtoolsaddon").resolve("sounds");
   private final ExecutorService exec = Executors.newSingleThreadExecutor((r) -> {
      Thread t = new Thread(r, "6ToolsAddon-Sound");
      t.setDaemon(true);
      return t;
   });
   private final Map<SoundType, List<Path>> files = new ConcurrentHashMap();
   private final Map<SoundType, FileTime> stamps = new ConcurrentHashMap();
   private final Map<SoundType, Integer> sequence = new ConcurrentHashMap();
   private final Map<SoundType, Integer> lastIndex = new ConcurrentHashMap();
   private final Map<Path, PcmSound> cache = new ConcurrentHashMap();
   private final Set<Path> failed = ConcurrentHashMap.newKeySet();
   private final AtomicInteger voices = new AtomicInteger();

   private SoundEngine() {
   }

   public Path getRoot() {
      return this.root;
   }

   public Path getFolder(SoundType type) {
      return this.root.resolve(type.folder);
   }

   public void ensureFolders() {
      try {
         Files.createDirectories(this.root);

         for(SoundType type : SoundType.values()) {
            Files.createDirectories(this.getFolder(type));
         }

         Path readme = this.root.resolve("README.txt");
         if (!Files.exists(readme, new LinkOption[0])) {
            Files.writeString(readme, String.join(System.lineSeparator(), "6ToolsAddon custom sounds", "", "Put your own .ogg or .wav files in the folder that matches the event:", "", describeFolders(), "You can put several files in one folder - use the 'Sound Editor' module in the click GUI to choose", "Random / Sequential / Specific playback per event, and to preview or pick files.", "Nothing plays for an event until its folder has at least one file in it.", ""));
         }
      } catch (IOException e) {
         SixToolsAddon.LOG.warn("Could not create sound folders in {}", this.root, e);
      }

   }

   private static String describeFolders() {
      StringBuilder sb = new StringBuilder();

      for(SoundType type : SoundType.values()) {
         sb.append("  ").append(type.folder).append("/  -  ").append(type.description).append(System.lineSeparator());
      }

      return sb.toString();
   }

   public void rescan() {
      this.ensureFolders();

      for(SoundType type : SoundType.values()) {
         this.scan(type);
      }

   }

   public void reload() {
      this.cache.clear();
      this.failed.clear();
      this.sequence.clear();
      this.lastIndex.clear();
      this.stamps.clear();
      this.rescan();
   }

   private void scan(SoundType type) {
      Path dir = this.getFolder(type);
      List<Path> found = new ArrayList();
      if (Files.isDirectory(dir, new LinkOption[0])) {
         try {
            Stream<Path> stream = Files.list(dir);

            try {
               Stream<Path> var10000 = stream.filter((x$0) -> Files.isRegularFile(x$0, new LinkOption[0])).filter((p) -> EXTENSIONS.contains(extension(p))).sorted(Comparator.comparing((p) -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
               Objects.requireNonNull(found);
               var10000.forEach(found::add);
            } catch (Throwable var9) {
               if (stream != null) {
                  try {
                     stream.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }
               }

               throw var9;
            }

            if (stream != null) {
               stream.close();
            }
         } catch (IOException e) {
            SixToolsAddon.LOG.warn("Could not list {}", dir, e);
         }

         try {
            this.stamps.put(type, Files.getLastModifiedTime(dir));
         } catch (IOException var7) {
         }
      }

      this.files.put(type, found);
   }

   private void refreshIfChanged(SoundType type) {
      try {
         Path dir = this.getFolder(type);
         if (!Files.isDirectory(dir, new LinkOption[0])) {
            if (!((List)this.files.getOrDefault(type, List.of())).isEmpty()) {
               this.files.put(type, List.of());
            }

            return;
         }

         FileTime now = Files.getLastModifiedTime(dir);
         if (!now.equals(this.stamps.get(type)) || !this.files.containsKey(type)) {
            this.scan(type);
         }
      } catch (IOException var4) {
      }

   }

   private static String extension(Path p) {
      String name = p.getFileName().toString();
      int dot = name.lastIndexOf(46);
      return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
   }

   public List<String> getFileNames(SoundType type) {
      List<String> names = new ArrayList();

      for(Path p : (List<Path>)this.files.getOrDefault(type, List.of())) {
         names.add(p.getFileName().toString());
      }

      return names;
   }

   public void play(SoundType type, Mode mode, String specific, float volume, float pitch) {
      this.exec.execute(() -> {
         try {
            this.refreshIfChanged(type);
            Path file = this.pick(type, mode, specific);
            if (file != null) {
               this.playFile(file, volume, pitch);
            }
         } catch (Throwable t) {
            SixToolsAddon.LOG.warn("Failed to play {} sound", type.folder, t);
         }

      });
   }

   public void playNamed(SoundType type, String fileName, float volume, float pitch) {
      this.exec.execute(() -> {
         try {
            for(Path p : (List<Path>)this.files.getOrDefault(type, List.of())) {
               if (p.getFileName().toString().equalsIgnoreCase(fileName)) {
                  this.playFile(p, volume, pitch);
                  return;
               }
            }
         } catch (Throwable t) {
            SixToolsAddon.LOG.warn("Failed to preview {}", fileName, t);
         }

      });
   }

   private Path pick(SoundType type, Mode mode, String specific) {
      List<Path> list = (List)this.files.getOrDefault(type, List.of());
      if (list.isEmpty()) {
         return null;
      } else {
         if (mode == SoundEngine.Mode.Specific && specific != null && !specific.isBlank()) {
            String wanted = specific.trim();

            for(Path p : list) {
               String name = p.getFileName().toString();
               String noExt = name.substring(0, Math.max(0, name.lastIndexOf(46)));
               if (name.equalsIgnoreCase(wanted) || noExt.equalsIgnoreCase(wanted)) {
                  return p;
               }
            }
         }

         if (mode == SoundEngine.Mode.Sequential) {
            int idx = (Integer)this.sequence.merge(type, 1, Integer::sum) - 1;
            return (Path)list.get(Math.floorMod(idx, list.size()));
         } else {
            int idx = ThreadLocalRandom.current().nextInt(list.size());
            if (list.size() > 1 && idx == (Integer)this.lastIndex.getOrDefault(type, -1)) {
               idx = (idx + 1) % list.size();
            }

            this.lastIndex.put(type, idx);
            return (Path)list.get(idx);
         }
      }
   }

   private void playFile(Path file, float volume, float pitch) {
      if (!(volume <= 1.0E-4F)) {
         PcmSound sound = this.load(file);
         if (sound != null) {
            if (this.voices.get() < 16) {
               float p = Math.max(0.25F, Math.min(4.0F, pitch));
               byte[] data = Math.abs(p - 1.0F) < 0.005F ? sound.data() : resample(sound, p);

               try {
                  Clip clip = AudioSystem.getClip();
                  this.voices.incrementAndGet();
                  clip.addLineListener((event) -> {
                     if (event.getType() == Type.STOP) {
                        clip.close();
                     } else if (event.getType() == Type.CLOSE) {
                        this.voices.decrementAndGet();
                     }

                  });

                  try {
                     clip.open(sound.format(), data, 0, data.length);
                  } catch (Throwable t) {
                     clip.close();
                     throw t;
                  }

                  if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                     FloatControl gain = (FloatControl)clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                     float db = (float)((double)20.0F * Math.log10((double)Math.max(volume, 1.0E-4F)));
                     gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
                  }

                  clip.start();
               } catch (Throwable t) {
                  SixToolsAddon.LOG.warn("Could not play {}", file.getFileName(), t);
               }

            }
         }
      }
   }

   private PcmSound load(Path file) {
      PcmSound cached = (PcmSound)this.cache.get(file);
      if (cached != null) {
         return cached;
      } else if (this.failed.contains(file)) {
         return null;
      } else {
         try {
            if (Files.size(file) > 16777216L) {
               throw new IOException("file is larger than 16 MB - use short clips");
            } else {
               PcmSound decoded = extension(file).equals("ogg") ? decodeOgg(file) : decodeJavaSound(file);
               if ((long)decoded.data().length > 67108864L) {
                  throw new IOException("sound is too long");
               } else {
                  this.cache.put(file, decoded);
                  return decoded;
               }
            }
         } catch (Throwable t) {
            this.failed.add(file);
            SixToolsAddon.LOG.warn("Could not load sound {} - it will be skipped until you press Reload ({})", file.getFileName(), t.toString());
            return null;
         }
      }
   }

   private static PcmSound decodeOgg(Path file) throws IOException {
      InputStream in = new BufferedInputStream(Files.newInputStream(file));

      PcmSound var15;
      try {
         OggAudioStream ogg = new OggAudioStream(in);

         try {
            AudioFormat format = ogg.getFormat();
            int channels = Math.max(1, format.getChannels());
            FloatBuffer samples = new FloatBuffer();
            int guard = 0;

            while(true) {
               Objects.requireNonNull(samples);
               if (ogg.read(samples::add)) {
                  if ((long)samples.size * 2L <= 67108864L) {
                     ++guard;
                     if (guard <= 1000000) {
                        continue;
                     }
                  }

                  throw new IOException("sound is too long");
               }

               int usable = samples.size - samples.size % channels;
               if (usable <= 0) {
                  throw new IOException("no audio data");
               }

               ByteBuffer out = ByteBuffer.allocate(usable * 2).order(ByteOrder.LITTLE_ENDIAN);

               for(int i = 0; i < usable; ++i) {
                  float v = Math.max(-1.0F, Math.min(1.0F, samples.data[i]));
                  out.putShort((short)Math.round(v * 32767.0F));
               }

               var15 = new PcmSound(out.array(), format.getSampleRate(), channels);
               break;
            }
         } catch (Throwable var13) {
            try {
               ogg.close();
            } catch (Throwable var12) {
               var13.addSuppressed(var12);
            }

            throw var13;
         }

         ogg.close();
      } catch (Throwable var14) {
         try {
            in.close();
         } catch (Throwable var11) {
            var14.addSuppressed(var11);
         }

         throw var14;
      }

      in.close();
      return var15;
   }

   private static PcmSound decodeJavaSound(Path file) throws Exception {
      AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile());

      PcmSound var6;
      try {
         AudioFormat src = source.getFormat();
         int channels = Math.max(1, src.getChannels());
         AudioFormat target = new AudioFormat(Encoding.PCM_SIGNED, src.getSampleRate(), 16, channels, channels * 2, src.getSampleRate(), false);
         AudioInputStream pcm = AudioSystem.getAudioInputStream(target, source);

         try {
            var6 = new PcmSound(pcm.readAllBytes(), src.getSampleRate(), channels);
         } catch (Throwable var10) {
            if (pcm != null) {
               try {
                  pcm.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (pcm != null) {
            pcm.close();
         }
      } catch (Throwable var11) {
         if (source != null) {
            try {
               source.close();
            } catch (Throwable var8) {
               var11.addSuppressed(var8);
            }
         }

         throw var11;
      }

      if (source != null) {
         source.close();
      }

      return var6;
   }

   private static byte[] resample(PcmSound sound, float pitch) {
      int ch = sound.channels();
      ShortBuffer in = ByteBuffer.wrap(sound.data()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
      int frames = in.capacity() / ch;
      int outFrames = Math.max(1, (int)((float)frames / pitch));
      ByteBuffer outBytes = ByteBuffer.allocate(outFrames * ch * 2).order(ByteOrder.LITTLE_ENDIAN);
      ShortBuffer out = outBytes.asShortBuffer();

      for(int i = 0; i < outFrames; ++i) {
         double pos = (double)i * (double)pitch;
         int i0 = Math.min((int)pos, frames - 1);
         int i1 = Math.min(i0 + 1, frames - 1);
         double frac = pos - (double)i0;

         for(int c = 0; c < ch; ++c) {
            short a = in.get(i0 * ch + c);
            short b = in.get(i1 * ch + c);
            out.put(i * ch + c, (short)((int)Math.round((double)a + (double)(b - a) * frac)));
         }
      }

      return outBytes.array();
   }

   public static enum Mode {
      Random,
      Sequential,
      Specific;

      private static Mode[] $values() {
         return new Mode[]{Random, Sequential, Specific};
      }
   }

   private static record PcmSound(byte[] data, float sampleRate, int channels) {
      AudioFormat format() {
         return new AudioFormat(Encoding.PCM_SIGNED, this.sampleRate, 16, this.channels, this.channels * 2, this.sampleRate, false);
      }
   }

   private static final class FloatBuffer {
      float[] data = new float[65536];
      int size;

      void add(float v) {
         if (this.size == this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
         }

         this.data[this.size++] = v;
      }
   }
}
