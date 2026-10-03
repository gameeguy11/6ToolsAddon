package gamerguy11.sixtoolsaddon.utils;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public final class WatermarkImage {
   private static final String BUNDLED = "bundled";
   private static final String BUNDLED_PATH = "/assets/sixtoolsaddon/icon.png";
   private static final int MAX_SIDE = 256;
   private static final long CHECK_INTERVAL_MS = 1000L;
   private int counter;
   private Identifier id;
   private int width;
   private int height;
   private String loadedKey = "";
   private long loadedStamp = Long.MIN_VALUE;
   private long lastCheck;
   private boolean usable;

   public static Path folder() {
      return MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("sixtoolsaddon").resolve("watermark");
   }

   public boolean update(String setting) {
      long now = System.currentTimeMillis();
      if (now - this.lastCheck < CHECK_INTERVAL_MS && this.loadedStamp != Long.MIN_VALUE && this.loadedKey.equals(this.keyOf(setting))) {
         return this.usable;
      }

      this.lastCheck = now;
      String key = this.keyOf(setting);
      Path path = this.resolve(setting);
      long stamp = this.stampOf(path);
      if (key.equals(this.loadedKey) && stamp == this.loadedStamp) {
         return this.usable;
      }

      this.release();
      this.loadedKey = key;
      this.loadedStamp = stamp;
      this.usable = this.load(path);
      return this.usable;
   }

   public Identifier id() {
      return this.id;
   }

   public int width() {
      return this.width;
   }

   public int height() {
      return this.height;
   }

   public void release() {
      if (this.id != null) {
         MinecraftClient.getInstance().getTextureManager().destroyTexture(this.id);
         this.id = null;
      }

      this.usable = false;
   }

   private String keyOf(String setting) {
      return setting == null || setting.isBlank() ? BUNDLED : setting.trim();
   }

   private Path resolve(String setting) {
      if (setting == null || setting.isBlank()) {
         return null;
      }

      Path given = Paths.get(setting.trim());
      return given.isAbsolute() ? given : folder().resolve(given);
   }

   private long stampOf(Path path) {
      if (path == null) {
         return 0L;
      }

      try {
         return Files.getLastModifiedTime(path).toMillis();
      } catch (IOException e) {
         return -1L;
      }
   }

   private boolean load(Path path) {
      try {
         Files.createDirectories(folder());
      } catch (IOException ignored) {
      }

      byte[] source;
      try {
         source = path == null ? this.readBundled() : Files.readAllBytes(path);
      } catch (IOException e) {
         if (path != null) {
            SixToolsAddon.LOG.warn("Watermark image not found: {}", path);
         }

         return false;
      }

      try {
         BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(source));
         if (decoded == null) {
            SixToolsAddon.LOG.warn("Watermark image format is not supported: {}", path);
            return false;
         }

         byte[] png = this.toPng(this.fit(decoded));
         NativeImage image = NativeImage.read(png);
         this.width = image.getWidth();
         this.height = image.getHeight();
         Identifier identifier = Identifier.of("sixtoolsaddon", "watermark/" + this.counter++);
         MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, new NativeImageBackedTexture(() -> "6tools-watermark", image));
         this.id = identifier;
         return true;
      } catch (Exception e) {
         SixToolsAddon.LOG.warn("Could not load watermark image", e);
         return false;
      }
   }

   private byte[] readBundled() throws IOException {
      try (InputStream stream = WatermarkImage.class.getResourceAsStream(BUNDLED_PATH)) {
         if (stream == null) {
            throw new IOException("bundled image missing");
         }

         return stream.readAllBytes();
      }
   }

   private BufferedImage fit(BufferedImage source) {
      int w = source.getWidth();
      int h = source.getHeight();
      int largest = Math.max(w, h);
      if (largest <= MAX_SIDE && source.getType() == BufferedImage.TYPE_INT_ARGB) {
         return source;
      }

      double factor = largest > MAX_SIDE ? (double) MAX_SIDE / (double) largest : 1.0;
      int targetW = Math.max(1, (int) Math.round(w * factor));
      int targetH = Math.max(1, (int) Math.round(h * factor));
      BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = result.createGraphics();
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      Image scaled = source.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
      graphics.drawImage(scaled, 0, 0, null);
      graphics.dispose();
      return result;
   }

   private byte[] toPng(BufferedImage image) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
   }
}
