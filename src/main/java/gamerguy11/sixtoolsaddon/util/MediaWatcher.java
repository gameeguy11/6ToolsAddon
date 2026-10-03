package gamerguy11.sixtoolsaddon.util;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class MediaWatcher {
   public static final MediaWatcher INSTANCE = new MediaWatcher();

   private static final long IDLE_TIMEOUT_MS = 15000L;
   private static final String OS = System.getProperty("os.name", "").toLowerCase();
   private static final boolean WINDOWS = OS.contains("win");
   private static final boolean LINUX = OS.contains("linux");

   private static final String LINUX_SCRIPT =
      "command -v playerctl >/dev/null 2>&1 || { echo MISSING; exit 0; }; "
      + "FMT='{{status}}\t{{artist}}\t{{title}}'; "
      + "while true; do "
      + "pick=''; "
      + "for p in $(playerctl -l 2>/dev/null); do "
      + "if [ \"$(playerctl -p \"$p\" status 2>/dev/null)\" = \"Playing\" ]; then pick=\"$p\"; break; fi; "
      + "done; "
      + "if [ -n \"$pick\" ]; then out=$(playerctl -p \"$pick\" metadata --format \"$FMT\" 2>/dev/null | head -n 1 | tr -d '\\r'); "
      + "else out=$(playerctl metadata --format \"$FMT\" 2>/dev/null | head -n 1 | tr -d '\\r'); fi; "
      + "if [ -n \"$out\" ]; then echo \"$out\"; else printf 'None\\t\\t\\n'; fi; "
      + "sleep 1; "
      + "done";

   private static final String SCRIPT = """
      [Console]::OutputEncoding = [Text.Encoding]::UTF8
      Add-Type -AssemblyName System.Runtime.WindowsRuntime
      $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
      function Await($t, $r) { $m = $asTask.MakeGenericMethod($r); $n = $m.Invoke($null, @($t)); $n.Wait(-1) | Out-Null; $n.Result }
      [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
      [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
      $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
      while ($true) {
        try {
          $s = $null
          foreach ($c in $mgr.GetSessions()) { if ($c.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $s = $c; break } }
          if (-not $s) { $s = $mgr.GetCurrentSession() }
          if ($s) {
            $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $st = $s.GetPlaybackInfo().PlaybackStatus
            $line = "$st`t$($p.Artist)`t$($p.Title)" -replace "[\\r\\n]", " "
          } else { $line = "None`t`t" }
        } catch { $line = "None`t`t" }
        [Console]::Out.WriteLine($line)
        [Console]::Out.Flush()
        Start-Sleep -Milliseconds 1000
      }
      """;

   private volatile String title = "";
   private volatile String artist = "";
   private volatile boolean playing = false;
   private volatile boolean running = false;
   private volatile boolean missingTool = false;
   private volatile long lastRequested = 0L;
   private volatile long lastStarted = 0L;
   private Process process;

   private MediaWatcher() {
   }

   public void request() {
      this.lastRequested = System.currentTimeMillis();
      if ((WINDOWS || LINUX) && !this.running) {
         if (!this.missingTool || this.lastRequested - this.lastStarted > 30000L) {
            this.start();
         }
      }
   }

   public boolean isSupported() {
      return WINDOWS || LINUX;
   }

   public boolean isToolMissing() {
      return this.missingTool;
   }

   public static String platformHint() {
      return LINUX ? "Install playerctl" : "Windows or Linux only";
   }

   public String getTitle() {
      return this.title;
   }

   public String getArtist() {
      return this.artist;
   }

   public boolean isPlaying() {
      return this.playing;
   }

   public boolean hasTrack() {
      return !this.title.isEmpty();
   }

   private synchronized void start() {
      if (this.running) {
         return;
      }
      this.running = true;
      this.lastStarted = System.currentTimeMillis();
      Thread thread = new Thread(this::run, "6Tools-MediaWatcher");
      thread.setDaemon(true);
      thread.start();
   }

   private void run() {
      try {
         ProcessBuilder builder;
         if (WINDOWS) {
            String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            builder = new ProcessBuilder(
               "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
               "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
         } else {
            builder = new ProcessBuilder("sh", "-c", LINUX_SCRIPT);
         }
         builder.redirectError(ProcessBuilder.Redirect.DISCARD);
         this.process = builder.start();

         try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
               if (System.currentTimeMillis() - this.lastRequested > IDLE_TIMEOUT_MS) {
                  break;
               }
               this.parse(line);
            }
         }
      } catch (Exception e) {
         SixToolsAddon.LOG.warn("Media watcher stopped: {}", e.getMessage());
      } finally {
         if (this.process != null) {
            this.process.destroy();
            this.process = null;
         }
         this.running = false;
      }
   }

   private void parse(String line) {
      if (line.trim().equals("MISSING")) {
         this.missingTool = true;
         return;
      }
      this.missingTool = false;
      String[] parts = line.split("\t", -1);
      if (parts.length < 3) {
         return;
      }
      this.playing = parts[0].equalsIgnoreCase("Playing");
      this.artist = parts[1].trim();
      this.title = parts[2].trim();
   }
}
