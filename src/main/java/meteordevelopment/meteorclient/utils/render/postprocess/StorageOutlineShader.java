package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.systems.RenderPass;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.StorageESP;

public class StorageOutlineShader extends PostProcessShader {
    private static StorageESP storageESP;

    public StorageOutlineShader() {
        super(MeteorRenderPipelines.POST_OUTLINE);
    }

    @Override
    protected boolean shouldDraw() {
        if (storageESP == null) storageESP = Modules.get().get(StorageESP.class);
        return storageESP.isShader();
    }

    @Override
    protected void setupPass(RenderPass pass) {
        pass.setUniform("u_Width", storageESP.outlineWidth.get());
        pass.setUniform("u_FillOpacity", storageESP.fillOpacity.get() / 255.0f);
        pass.setUniform("u_ShapeMode", storageESP.shapeMode.get().ordinal());
        pass.setUniform("u_GlowMultiplier", storageESP.glowMultiplier.get().floatValue());
    }
}
