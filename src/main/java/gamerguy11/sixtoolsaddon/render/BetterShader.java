package gamerguy11.sixtoolsaddon.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import gamerguy11.sixtoolsaddon.modules.visual.BetterShaders;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.ExtendedRenderPipelineBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorVertexFormats;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.postprocess.EntityShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.UniformType;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BetterShader extends EntityShader {
   private static final RenderPipeline PIPELINE = new ExtendedRenderPipelineBuilder()
      .withLocation(Identifier.of("sixtoolsaddon", "pipeline/post/better_outline"))
      .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
      .withVertexShader(MeteorClient.identifier("shaders/post-process/base.vert"))
      .withFragmentShader(Identifier.of("sixtoolsaddon", "shaders/post-process/better_outline.frag"))
      .withSampler("u_Texture")
      .withUniform("PostData", UniformType.UNIFORM_BUFFER)
      .withUniform("BetterOutlineData", UniformType.UNIFORM_BUFFER)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .build();

   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putInt().putFloat().putInt().putFloat().putVec4().putInt().putFloat().putFloat().putFloat().putVec4().get();
   private static final DynamicUniformStorage<UniformData> UNIFORM_STORAGE = new DynamicUniformStorage<>("SixTools - Better Outline UBO", UNIFORM_SIZE, 16);

   private static BetterShader instance;
   private static BetterShaders module;

   private BetterShader() {
      super(PIPELINE);
   }

   public static BetterShader get() {
      if (instance == null) {
         precompile();
         instance = new BetterShader();
      }

      return instance;
   }

   public static BetterShader getIfCreated() {
      return instance;
   }

   public static void precompile() {
      var resources = MinecraftClient.getInstance().getResourceManager();
      RenderSystem.getDevice().precompilePipeline(PIPELINE, (identifier, shaderType) -> {
         var resource = resources.getResource(identifier).get();

         try (var in = resource.getInputStream()) {
            return IOUtils.toString(in, StandardCharsets.UTF_8);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });
   }

   public static void flipFrame() {
      UNIFORM_STORAGE.clear();
   }

   private BetterShaders module() {
      if (module == null) {
         module = Modules.get().get(BetterShaders.class);
      }

      return module;
   }

   public Color colorOf(Entity entity) {
      return this.module().getColor(entity);
   }

   @Override
   protected boolean shouldDraw() {
      BetterShaders m = this.module();
      return m != null && m.isActive();
   }

   public boolean shouldDrawAny() {
      return this.shouldDraw();
   }

   @Override
   public boolean shouldDraw(Entity entity) {
      return this.shouldDraw() && this.module().isValid(entity);
   }

   @Override
   protected void setupPass(MeshRenderer renderer) {
      BetterShaders m = this.module();
      var glint = m.animatedColor.get();
      var second = m.secondColor.get();
      GpuBufferSlice slice = UNIFORM_STORAGE.write(new UniformData(
         m.mode.get().ordinal(),
         m.opacity.get() / 255.0F,
         m.animated.get() ? 1 : 0,
         (float) ((System.currentTimeMillis() % 100000L) / 1000.0 * m.animationSpeed.get()),
         glint.r / 255.0F, glint.g / 255.0F, glint.b / 255.0F,
         m.style.get().ordinal(),
         (float) m.lineWidth.get().intValue(),
         m.lineStrength.get().floatValue(),
         m.fillStrength.get().floatValue(),
         second.r / 255.0F, second.g / 255.0F, second.b / 255.0F
      ));
      renderer.uniform("BetterOutlineData", slice);
   }

   private record UniformData(int renderMode, float fillOpacity, int animated, float time, float r, float g, float b, int style, float lineWidth, float lineStrength, float fillStrength, float r2, float g2, float b2) implements DynamicUniformStorage.Uploadable {
      @Override
      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer)
            .putInt(this.renderMode).putFloat(this.fillOpacity).putInt(this.animated).putFloat(this.time)
            .putVec4(this.r, this.g, this.b, 1.0F)
            .putInt(this.style).putFloat(this.lineWidth).putFloat(this.lineStrength).putFloat(this.fillStrength)
            .putVec4(this.r2, this.g2, this.b2, 1.0F);
      }
   }
}
