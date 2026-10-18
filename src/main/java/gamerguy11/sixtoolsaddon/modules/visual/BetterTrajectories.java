package gamerguy11.sixtoolsaddon.modules.visual;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class BetterTrajectories extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgEntities = this.settings.createGroup("Entities");
   private final SettingGroup sgBlocks = this.settings.createGroup("Blocks");
   private final Setting<SettingColor> lineColor = this.sgGeneral.add(new ColorSetting.Builder().name("line-color").description("Color of the trajectory line.").defaultValue(new SettingColor(255, 255, 255, 255)).build());
   private final Setting<ShapeMode> entitiesMode = this.sgEntities.add(new EnumSetting.Builder<ShapeMode>().name("entities-mode").description("Rendering applied to the entity the trajectory hits.").defaultValue(ShapeMode.Both).build());
   private final Setting<SettingColor> entitiesFill = this.sgEntities.add(new ColorSetting.Builder().name("entities-fill-color").description("Fill color.").defaultValue(new SettingColor(255, 255, 255, 40)).build());
   private final Setting<SettingColor> entitiesOutline = this.sgEntities.add(new ColorSetting.Builder().name("entities-outline-color").description("Outline color.").defaultValue(new SettingColor(255, 255, 255, 255)).build());
   private final Setting<ShapeMode> blocksMode = this.sgBlocks.add(new EnumSetting.Builder<ShapeMode>().name("blocks-mode").description("Rendering applied to the landing point.").defaultValue(ShapeMode.Both).build());
   private final Setting<SettingColor> blocksFill = this.sgBlocks.add(new ColorSetting.Builder().name("blocks-fill-color").description("Fill color.").defaultValue(new SettingColor(255, 255, 255, 40)).build());
   private final Setting<SettingColor> blocksOutline = this.sgBlocks.add(new ColorSetting.Builder().name("blocks-outline-color").description("Outline color.").defaultValue(new SettingColor(255, 255, 255, 255)).build());
   private final List<Entity> hitEntities = new ArrayList<>();

   public BetterTrajectories() {
      super(SixToolsAddon.CATEGORY, "better-trajectories", "Draws the predicted path of throwables, bows and crossbows.");
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player == null || this.mc.world == null || this.mc.options.hudHidden || !this.mc.options.getPerspective().isFirstPerson()) {
         return;
      }

      Hand hand;
      if (this.isLaunchable(this.mc.player.getMainHandStack().getItem())) {
         hand = Hand.MAIN_HAND;
      } else if (this.isLaunchable(this.mc.player.getOffHandStack().getItem())) {
         hand = Hand.OFF_HAND;
      } else {
         return;
      }

      ItemStack stack = hand == Hand.MAIN_HAND ? this.mc.player.getMainHandStack() : this.mc.player.getOffHandStack();
      this.hitEntities.clear();
      float yaw = this.mc.player.getYaw();
      if (stack.isOf(Items.CROSSBOW) && this.multishot(stack)) {
         this.project(event, stack.getItem(), hand == Hand.MAIN_HAND, yaw - 10.0f);
         this.project(event, stack.getItem(), hand == Hand.MAIN_HAND, yaw);
         this.project(event, stack.getItem(), hand == Hand.MAIN_HAND, yaw + 10.0f);
      } else {
         this.project(event, stack.getItem(), hand == Hand.MAIN_HAND, yaw);
      }
   }

   private boolean multishot(ItemStack stack) {
      return EnchantmentHelper.getLevel(this.mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.MULTISHOT), stack) != 0;
   }

   private boolean isLaunchable(Item item) {
      return item == Items.BOW || item == Items.CROSSBOW || item == Items.SNOWBALL || item == Items.EGG || item == Items.ENDER_PEARL || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.EXPERIENCE_BOTTLE || item == Items.TRIDENT;
   }

   private void project(Render3DEvent event, Item item, boolean mainHand, float yaw) {
      double td = event.tickDelta;
      double x = MathHelper.lerp(td, this.mc.player.lastRenderX, this.mc.player.getX());
      double y = MathHelper.lerp(td, this.mc.player.lastRenderY, this.mc.player.getY());
      double z = MathHelper.lerp(td, this.mc.player.lastRenderZ, this.mc.player.getZ());
      y = y + this.mc.player.getEyeHeight(this.mc.player.getPose()) - 0.1000000014901161;
      float side = mainHand ? -0.16f : 0.16f;
      x += MathHelper.cos(yaw / 180.0f * 3.1415927f) * side;
      z += MathHelper.sin(yaw / 180.0f * 3.1415927f) * side;
      float pitch = this.mc.player.getPitch();
      float base = item == Items.BOW ? 1.0f : 0.4f;
      double mx = -MathHelper.sin(yaw / 180.0f * 3.1415927f) * MathHelper.cos(pitch / 180.0f * 3.1415927f) * base;
      double my = -MathHelper.sin((pitch - this.throwPitch(item)) / 180.0f * 3.141593f) * base;
      double mz = MathHelper.cos(yaw / 180.0f * 3.1415927f) * MathHelper.cos(pitch / 180.0f * 3.1415927f) * base;
      float power = this.mc.player.getItemUseTime() / 20.0f;
      power = (power * power + power * 2.0f) / 3.0f;
      if (power > 1.0f || power == 0.0f) {
         power = 1.0f;
      }

      float length = MathHelper.sqrt((float) (mx * mx + my * my + mz * mz));
      mx /= length;
      my /= length;
      mz /= length;
      float pow = (item == Items.BOW ? power * 2.0f : item == Items.CROSSBOW ? 2.2f : 1.0f) * this.throwVelocity(item);
      mx *= pow;
      my *= pow;
      mz *= pow;
      if (!this.mc.player.isOnGround()) {
         my += this.mc.player.getVelocity().getY();
      }

      boolean arrowLike = item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
      boolean landed = false;
      HitResult result = null;
      Entity entity = null;
      int steps = 0;
      while (!landed && y > -65.0 && steps++ < 600) {
         Vec3d last = new Vec3d(x, y, z);
         x += mx;
         y += my;
         z += mz;
         if (this.mc.world.getBlockState(BlockPos.ofFloored(x, y, z)).isOf(Blocks.WATER)) {
            mx *= 0.8;
            my *= 0.8;
            mz *= 0.8;
         } else {
            mx *= 0.99;
            my *= 0.99;
            mz *= 0.99;
         }

         my -= arrowLike ? 0.05000000074505806 : 0.03f;
         Vec3d position = new Vec3d(x, y, z);
         Entity hitEntity = this.hitEntity(new Box(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0));
         HitResult possible = this.mc.world.raycast(new RaycastContext(last, position, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, this.mc.player));
         if (hitEntity != null) {
            entity = hitEntity;
            landed = true;
         } else if (possible != null && possible.getType() != HitResult.Type.MISS) {
            result = possible;
            landed = true;
         }

         event.renderer.line(last.x, last.y, last.z, position.x, position.y, position.z, this.lineColor.get());
      }

      if (result != null && result.getType() == HitResult.Type.BLOCK) {
         Vec3d p = result.getPos();
         Box box = new Box(p.x - 0.15, p.y - 0.15, p.z - 0.15, p.x + 0.15, p.y + 0.15, p.z + 0.15);
         event.renderer.box(box, this.blocksFill.get(), this.blocksOutline.get(), this.blocksMode.get(), 0);
      }

      if (entity != null && !this.hitEntities.contains(entity)) {
         event.renderer.box(entity.getBoundingBox(), this.entitiesFill.get(), this.entitiesOutline.get(), this.entitiesMode.get(), 0);
         this.hitEntities.add(entity);
      }
   }

   private Entity hitEntity(Box box) {
      for (Entity e : this.mc.world.getEntities()) {
         if (e == this.mc.player || e instanceof ArrowEntity) {
            continue;
         }

         if (e.getBoundingBox().intersects(box)) {
            return e;
         }
      }

      return null;
   }

   private float throwVelocity(Item item) {
      if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
         return 0.5f;
      }

      if (item == Items.EXPERIENCE_BOTTLE) {
         return 0.59f;
      }

      if (item == Items.TRIDENT) {
         return 2.0f;
      }

      return 1.5f;
   }

   private int throwPitch(Item item) {
      return item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.EXPERIENCE_BOTTLE ? 20 : 0;
   }
}
