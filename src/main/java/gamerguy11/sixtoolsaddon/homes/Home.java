package gamerguy11.sixtoolsaddon.homes;

import meteordevelopment.meteorclient.utils.world.Dimension;

public class Home {
    public String name;
    public int x, y, z;
    public int radius = 50;
    public Dimension dimension = Dimension.Overworld;

    public boolean protect = true;
    public boolean denyInstead = false;
    public boolean allowFriends = false;

    public boolean overrideCooldown = false;
    public int cooldown = 60;

    public Home() {}

    public Home(String name, int x, int y, int z, int radius, Dimension dimension) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.dimension = dimension;
    }
}
