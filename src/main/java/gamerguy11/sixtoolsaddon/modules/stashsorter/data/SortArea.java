package gamerguy11.sixtoolsaddon.modules.stashsorter.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class SortArea {
   public String dimension;
   public int x1;
   public int z1;
   public int x2;
   public int z2;

   public SortArea() {
   }

   public SortArea(String dimension, BlockPos first, BlockPos second) {
      this.dimension = dimension;
      this.x1 = first.getX();
      this.z1 = first.getZ();
      this.x2 = second.getX();
      this.z2 = second.getZ();
   }

   public int minX() {
      return Math.min(this.x1, this.x2);
   }

   public int maxX() {
      return Math.max(this.x1, this.x2);
   }

   public int minZ() {
      return Math.min(this.z1, this.z2);
   }

   public int maxZ() {
      return Math.max(this.z1, this.z2);
   }

   public boolean contains(BlockPos pos) {
      return pos.getX() >= this.minX() && pos.getX() <= this.maxX() && pos.getZ() >= this.minZ() && pos.getZ() <= this.maxZ();
   }

   public Box box(int minY, int maxY) {
      return new Box((double)this.minX(), (double)minY, (double)this.minZ(), (double)this.maxX() + (double)1.0F, (double)maxY + (double)1.0F, (double)this.maxZ() + (double)1.0F);
   }

   public String corners() {
      int var10000 = this.minX();
      return var10000 + ", " + this.minZ() + " -> " + this.maxX() + ", " + this.maxZ();
   }

   public String footprint() {
      int var10000 = this.maxX() - this.minX() + 1;
      return var10000 + "x" + (this.maxZ() - this.minZ() + 1);
   }
}
