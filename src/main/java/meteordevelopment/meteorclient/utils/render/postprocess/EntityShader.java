package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import meteordevelopment.meteorclient.mixininterface.IWorldRenderer;
import net.minecraft.client.render.OutlineVertexConsumerProvider;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public abstract class EntityShader extends PostProcessShader {
    // Kept with init(RenderPipeline) for shaders from addons built against upstream's 1.21.5 build
    public EntityShader() {}

    protected EntityShader(RenderPipeline pipeline) {
        super(pipeline);
        this.vertexConsumerProvider = new OutlineVertexConsumerProvider(mc.getBufferBuilders().getEntityVertexConsumers());
    }

    @Override
    protected void preDraw() {
        ((IWorldRenderer) mc.worldRenderer).meteor$pushEntityOutlineFramebuffer(framebuffer);
    }

    @Override
    protected void postDraw() {
        ((IWorldRenderer) mc.worldRenderer).meteor$popEntityOutlineFramebuffer();
    }

    public void submitVertices() {
        submitVertices(vertexConsumerProvider::draw);
    }

    // Kept for addons built against upstream's 1.21.5 build
    @Override
    public boolean beginRender() {
        clearTexture();
        return shouldDraw();
    }

    // Kept for addons built against upstream's 1.21.5 build
    public void endRender() {
        endRender(vertexConsumerProvider::draw);
    }
}
