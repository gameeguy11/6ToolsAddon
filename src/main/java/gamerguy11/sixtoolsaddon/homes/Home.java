package gamerguy11.sixtoolsaddon.homes;

import meteordevelopment.meteorclient.utils.world.Dimension;

public class Home {
   public String name;
   public int x;
   public int y;
   public int z;
   public int radius = 50;
   public Dimension dimension;
   public boolean protect;
   public boolean denyInstead;
   public boolean allowFriends;
   public boolean overrideCooldown;
   public int cooldown;

   public Home() {
      this.dimension = Dimension.Overworld;
      this.protect = true;
      this.denyInstead = false;
      this.allowFriends = false;
      this.overrideCooldown = false;
      this.cooldown = 60;
   }

   public Home(String name, int x, int y, int z, int radius, Dimension dimension) {
      this.dimension = Dimension.Overworld;
      this.protect = true;
      this.denyInstead = false;
      this.allowFriends = false;
      this.overrideCooldown = false;
      this.cooldown = 60;
      this.name = name;
      this.x = x;
      this.y = y;
      this.z = z;
      this.radius = radius;
      this.dimension = dimension;
   }
}
