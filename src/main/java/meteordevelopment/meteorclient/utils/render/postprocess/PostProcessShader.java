package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.FullScreenRenderer;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.entity.Entity;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

public abstract class PostProcessShader {
    public OutlineVertexConsumerProvider vertexConsumerProvider;
    public Framebuffer framebuffer;
    protected RenderPipeline pipeline;

    // Kept with init(RenderPipeline) for shaders from addons built against upstream's 1.21.5 build
    public PostProcessShader() {}

    protected PostProcessShader(RenderPipeline pipeline) {
        this.pipeline = pipeline;
        this.framebuffer = new SimpleFramebuffer(MeteorClient.NAME + " PostProcessShader " + this.getClass().getSimpleName(), mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), true);
    }

    // Kept for addons built against upstream's 1.21.5 build
    public void init(RenderPipeline pipeline) {
        vertexConsumerProvider = new OutlineVertexConsumerProvider(mc.getBufferBuilders().getEntityVertexConsumers());
        framebuffer = new SimpleFramebuffer(MeteorClient.NAME + " PostProcessShader", mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), true);
        this.pipeline = pipeline;
    }

    protected abstract boolean shouldDraw();
    public abstract boolean shouldDraw(Entity entity);

    protected void preDraw() {}
    protected void postDraw() {}

    protected abstract void setupPass(RenderPass pass);

    public void clearTexture() {
        if (this.shouldDraw()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(framebuffer.getColorAttachment(), 0);
        }
    }

    // Kept for addons built against upstream's 1.21.5 build
    public boolean beginRender() {
        return shouldDraw();
    }

    // Kept for addons built against upstream's 1.21.5 build; the composite now normally draws in render()
    public void endRender(Runnable draw) {
        submitVertices(draw);
        render();
    }

    public void submitVertices(Runnable draw) {
        if (!shouldDraw()) return;

        preDraw();
        draw.run();
        postDraw();
    }

    public void render() {
        if (!shouldDraw()) return;

        MeshRenderer.begin()
            .attachments(mc.getFramebuffer())
            .pipeline(pipeline)
            .mesh(FullScreenRenderer.mesh)
            .setupCallback(pass -> {
                pass.bindSampler("u_Texture", framebuffer.getColorAttachment());
                pass.setUniform("u_Size", (float) mc.getWindow().getFramebufferWidth(), (float) mc.getWindow().getFramebufferHeight());
                pass.setUniform("u_Time", (float) glfwGetTime());

                setupPass(pass);
            })
            .end();
    }

    public void onResized(int width, int height) {
        if (framebuffer == null) return;
        framebuffer.resize(width, height);
    }
}
