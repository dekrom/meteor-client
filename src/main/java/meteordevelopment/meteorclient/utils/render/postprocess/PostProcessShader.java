package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.FullScreenRenderer;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

public abstract class PostProcessShader {
    protected final RenderPipeline pipeline;
    public final Framebuffer framebuffer;

    protected PostProcessShader(RenderPipeline pipeline) {
        this.pipeline = pipeline;
        this.framebuffer = new SimpleFramebuffer(MeteorClient.NAME + " PostProcessShader " + this.getClass().getSimpleName(), mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), true);
    }

    protected abstract boolean shouldDraw();

    protected void preDraw() {}
    protected void postDraw() {}

    protected abstract void setupPass(RenderPass pass);

    public void clearTexture() {
        if (this.shouldDraw()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(framebuffer.getColorAttachment(), 0);
        }
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
