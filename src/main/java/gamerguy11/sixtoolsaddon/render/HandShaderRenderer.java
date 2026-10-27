package gamerguy11.sixtoolsaddon.render;

import meteordevelopment.meteorclient.mixininterface.IWorldRenderer;
import meteordevelopment.meteorclient.utils.OutlineRenderCommandQueue;
import meteordevelopment.meteorclient.utils.render.NoopImmediateVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.NoopOutlineVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.WrapperImmediateVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;

public final class HandShaderRenderer {
   private static final OutlineRenderCommandQueue QUEUE = new OutlineRenderCommandQueue();
   private static VertexConsumerProvider provider;
   private static RenderDispatcher dispatcher;
   private static boolean pending;

   private HandShaderRenderer() {
   }

   public static void submit(LivingEntity entity, ItemStack stack, ItemDisplayContext context, MatrixStack matrices, int light, Color color) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         return;
      }

      ItemRenderState state = new ItemRenderState();
      mc.getItemModelManager().update(state, stack, context, mc.world, entity, entity.getId() + context.ordinal());

      QUEUE.setColor(color);
      matrices.push();
      state.render(matrices, QUEUE, light, OverlayTexture.DEFAULT_UV, 0);
      matrices.pop();
      pending = true;
   }

   public static void flush(BetterShader shader) {
      if (!pending) {
         return;
      }

      pending = false;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (dispatcher == null) {
         dispatcher = new RenderDispatcher(
            QUEUE,
            mc.getBlockRenderManager(),
            new WrapperImmediateVertexConsumerProvider(() -> provider),
            mc.getAtlasManager(),
            NoopOutlineVertexConsumerProvider.INSTANCE,
            NoopImmediateVertexConsumerProvider.INSTANCE,
            mc.textRenderer
         );
      }

      IWorldRenderer worldRenderer = (IWorldRenderer)mc.worldRenderer;
      worldRenderer.meteor$pushEntityOutlineFramebuffer(shader.framebuffer);
      provider = shader.vertexConsumerProvider;

      dispatcher.render();
      QUEUE.onNextFrame();

      provider = null;
      worldRenderer.meteor$popEntityOutlineFramebuffer();
      shader.submitVertices();
   }
}
