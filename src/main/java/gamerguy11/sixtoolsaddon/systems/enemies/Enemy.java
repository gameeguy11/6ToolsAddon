package gamerguy11.sixtoolsaddon.systems.enemies;

import java.util.Objects;

public class Enemy implements Comparable<Enemy> {
   public volatile String name;

   public Enemy(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Enemy enemy = (Enemy)o;
         return this.name.equalsIgnoreCase(enemy.name);
      } else {
         return false;
      }
   }

   public int hashCode() {
      return Objects.hash(new Object[]{this.name.toLowerCase()});
   }

   public int compareTo(Enemy enemy) {
      return this.name.compareToIgnoreCase(enemy.name);
   }
}
