package gamerguy11.sixtoolsaddon.systems.enemies;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;

public class Enemies implements Iterable<Enemy> {
   private static final Enemies INSTANCE = new Enemies();
   private static final File FILE;
   private final List<Enemy> enemies = new ArrayList();
   private boolean loaded;

   private Enemies() {
   }

   public static Enemies get() {
      INSTANCE.loadIfNeeded();
      return INSTANCE;
   }

   private void loadIfNeeded() {
      if (!this.loaded) {
         this.loaded = true;
         if (FILE.exists()) {
            try {
               BufferedReader reader = new BufferedReader(new FileReader(FILE));

               try {
                  String line;
                  while((line = reader.readLine()) != null) {
                     String name = line.trim();
                     if (!name.isEmpty() && this.get(name) == null) {
                        this.enemies.add(new Enemy(name));
                     }
                  }

                  Collections.sort(this.enemies);
               } catch (Throwable var5) {
                  try {
                     reader.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }

                  throw var5;
               }

               reader.close();
            } catch (IOException e) {
               MeteorClient.LOG.error("6ToolsAddon: failed to load enemies list", e);
            }

         }
      }
   }

   private void save() {
      try {
         FileWriter writer = new FileWriter(FILE);

         try {
            for(Enemy enemy : this.enemies) {
               writer.write(enemy.getName());
               writer.write(System.lineSeparator());
            }
         } catch (Throwable var5) {
            try {
               writer.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }

            throw var5;
         }

         writer.close();
      } catch (IOException e) {
         MeteorClient.LOG.error("6ToolsAddon: failed to save enemies list", e);
      }

   }

   public boolean add(Enemy enemy) {
      if (!enemy.name.isEmpty() && !enemy.name.contains(" ")) {
         if (this.get(enemy.name) != null) {
            return false;
         } else {
            this.enemies.add(enemy);
            Collections.sort(this.enemies);
            this.save();
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean remove(Enemy enemy) {
      if (this.enemies.remove(enemy)) {
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public Enemy get(String name) {
      for(Enemy enemy : this.enemies) {
         if (enemy.name.equalsIgnoreCase(name)) {
            return enemy;
         }
      }

      return null;
   }

   public boolean isEnemy(String name) {
      return this.get(name) != null;
   }

   public int count() {
      return this.enemies.size();
   }

   public boolean isEmpty() {
      return this.enemies.isEmpty();
   }

   public Iterator<Enemy> iterator() {
      return this.enemies.iterator();
   }

   static {
      FILE = new File(MeteorClient.FOLDER, "sixtoolsaddon-enemies.txt");
   }
}
