package com.hexmagic.client.render;

import com.hexmagic.entity.HexPrisonEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** The prison conductor has no body of its own: its four walls are ordinary constructs. */
public class HexPrisonRenderer extends EntityRenderer<HexPrisonEntity> {
    public HexPrisonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(HexPrisonEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(HexPrisonEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
