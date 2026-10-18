package gamerguy11.sixtoolsaddon.mixin.shaders;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import gamerguy11.sixtoolsaddon.render.BetterShader;
import meteordevelopment.meteorclient.mixininterface.IEntityRenderState;
import meteordevelopment.meteorclient.mixininterface.IWorldRenderer;
import meteordevelopment.meteorclient.utils.OutlineRenderCommandQueue;
import meteordevelopment.meteorclient.utils.render.NoopImmediateVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.NoopOutlineVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.WrapperImmediateVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererBetterShaderMixin {
   @Shadow
   @Final
   private EntityRenderManager entityRenderManager;

   @Unique
   private final OutlineRenderCommandQueue sixtools$queue = new OutlineRenderCommandQueue();

   @Unique
   private VertexConsumerProvider sixtools$provider;

   @Unique
   private RenderDispatcher sixtools$dispatcher;

   @Inject(method = "render", at = @At("HEAD"))
   private void sixtools$onRenderHead(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f matrix4f2, GpuBufferSlice fog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
      BetterShader shader = BetterShader.getIfCreated();
      if (shader != null) {
         shader.clearTexture();
      }
   }

   @Inject(method = "pushEntityRenders", at = @At("TAIL"))
   private void sixtools$onPushEntityRenders(MatrixStack matrices, WorldRenderState worldState, OrderedRenderCommandQueue queue, CallbackInfo info) {
      BetterShader shader = BetterShader.getIfCreated();
      if (shader == null || !shader.shouldDrawAny()) {
         return;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.sixtools$dispatcher == null) {
         this.sixtools$dispatcher = new RenderDispatcher(
            this.sixtools$queue,
            mc.getBlockRenderManager(),
            new WrapperImmediateVertexConsumerProvider(() -> this.sixtools$provider),
            mc.getAtlasManager(),
            NoopOutlineVertexConsumerProvider.INSTANCE,
            NoopImmediateVertexConsumerProvider.INSTANCE,
            mc.textRenderer
         );
      }

      var camera = worldState.cameraRenderState.pos;
      boolean empty = true;

      for (var state : worldState.entityRenderStates) {
         Entity entity = ((IEntityRenderState)state).meteor$getEntity();
         if (entity == null || !shader.shouldDraw(entity)) {
            continue;
         }

         Color color = shader.colorOf(entity);
         if (color == null) {
            continue;
         }

         this.sixtools$queue.setColor(color);

         var renderer = this.entityRenderManager.getRenderer(state);
         var offset = renderer.getPositionOffset(state);

         matrices.push();
         matrices.translate(state.x - camera.x + offset.x, state.y - camera.y + offset.y, state.z - camera.z + offset.z);
         renderer.render(state, matrices, this.sixtools$queue, worldState.cameraRenderState);
         matrices.pop();

         empty = false;
      }

      if (empty) {
         return;
      }

      IWorldRenderer worldRenderer = (IWorldRenderer)(Object)this;
      worldRenderer.meteor$pushEntityOutlineFramebuffer(shader.framebuffer);
      this.sixtools$provider = shader.vertexConsumerProvider;

      this.sixtools$dispatcher.render();
      this.sixtools$queue.onNextFrame();

      this.sixtools$provider = null;
      worldRenderer.meteor$popEntityOutlineFramebuffer();
   }

   @Inject(method = "method_62214", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/OutlineVertexConsumerProvider;draw()V"))
   private void sixtools$onFlush(CallbackInfo ci) {
      BetterShader shader = BetterShader.getIfCreated();
      if (shader != null) {
         shader.submitVertices();
      }
   }

   @Inject(method = "onResized", at = @At("HEAD"))
   private void sixtools$onResized(int width, int height, CallbackInfo info) {
      BetterShader shader = BetterShader.getIfCreated();
      if (shader != null) {
         shader.onResized(width, height);
      }
   }
}
