package meteordevelopment.meteorclient.utils.render.postprocess;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.VertexConsumerProvider;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PostProcessShaders {
    public static EntityShader CHAMS;
    public static EntityShader ENTITY_OUTLINE;
    public static PostProcessShader STORAGE_OUTLINE;

    public static boolean rendering;

    private PostProcessShaders() {}

    @PreInit
    public static void init() {
        CHAMS = new ChamsShader();
        ENTITY_OUTLINE = new EntityOutlineShader();
        STORAGE_OUTLINE = new StorageOutlineShader();

        MeteorClient.EVENT_BUS.subscribe(PostProcessShaders.class);
    }

    public static void beginRender() {
        CHAMS.clearTexture();
        ENTITY_OUTLINE.clearTexture();
        STORAGE_OUTLINE.clearTexture();
    }

    public static void submitEntityVertices() {
        CHAMS.submitVertices();
        ENTITY_OUTLINE.submitVertices();
    }

    // Kept for addons built against upstream's 1.21.8 build
    public static void endRender() {
        CHAMS.endRender();
        ENTITY_OUTLINE.endRender();
    }

    @EventHandler
    private static void onRender(Render2DEvent event) {
        CHAMS.render();
        ENTITY_OUTLINE.render();
    }

    public static void onResized(int width, int height) {
        if (mc == null) return;

        CHAMS.onResized(width, height);
        ENTITY_OUTLINE.onResized(width, height);
        STORAGE_OUTLINE.onResized(width, height);
    }

    public static boolean isCustom(VertexConsumerProvider vcp) {
        return vcp == CHAMS.vertexConsumerProvider || vcp == ENTITY_OUTLINE.vertexConsumerProvider;
    }
}
