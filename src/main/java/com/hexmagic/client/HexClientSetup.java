package com.hexmagic.client;

import com.hexmagic.HexMagic;
import com.hexmagic.client.render.HexConstructRenderer;
import com.hexmagic.client.render.HexDrillRenderer;
import com.hexmagic.client.render.HexPrisonRenderer;
import com.hexmagic.client.render.HexStaffRenderer;
import com.hexmagic.client.render.HexSwarmRenderer;
import com.hexmagic.item.HexStaffLogic;
import com.hexmagic.registry.HexEntities;
import com.hexmagic.registry.HexItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import com.hexmagic.client.render.HexRenderTypes;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = HexMagic.MOD_ID, value = Dist.CLIENT)
public final class HexClientSetup {
    private HexClientSetup() {}

    private static HexStaffRenderer staffRenderer;

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        HexStaffLogic.clientSelection = () -> HexClientState.selected;
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : HexKeys.all()) event.register(k);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HexEntities.CONSTRUCT.get(), HexConstructRenderer::new);
        event.registerEntityRenderer(HexEntities.DRILL.get(), HexDrillRenderer::new);
        event.registerEntityRenderer(HexEntities.SWARM.get(), HexSwarmRenderer::new);
        event.registerEntityRenderer(HexEntities.PRISON.get(), HexPrisonRenderer::new);
    }

    @SubscribeEvent
    public static void onModels(ModelEvent.RegisterAdditional event) {
        event.register(HexStaffRenderer.MODEL);
    }

    @SubscribeEvent
    public static void onClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (staffRenderer == null) staffRenderer = new HexStaffRenderer();
                return staffRenderer;
            }
        }, HexItems.HEX_STAFF.get());
    }

    /** Own buffers for the honeycomb render types, so several can be filled at the same time. */
    @SubscribeEvent
    public static void onRenderBuffers(RegisterRenderBuffersEvent event) {
        event.registerRenderBuffer(HexRenderTypes.GLOW);
        event.registerRenderBuffer(HexRenderTypes.FILL);
        event.registerRenderBuffer(HexRenderTypes.GLOW_NO_DEPTH);
    }

    @SubscribeEvent
    public static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(HexMagic.id("hud"), new HexHud());
    }
}
