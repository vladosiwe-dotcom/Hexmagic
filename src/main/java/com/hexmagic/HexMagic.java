package com.hexmagic;

import com.hexmagic.registry.HexAttachments;
import com.hexmagic.registry.HexEntities;
import com.hexmagic.registry.HexItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hex Magic: the Hex Staff, a weapon that bends honeycomb barriers into drills, spikes, platforms
 * and traps.
 */
@Mod(HexMagic.MOD_ID)
public class HexMagic {
    public static final String MOD_ID = "hexmagic";
    public static final Logger LOGGER = LoggerFactory.getLogger("HexMagic");

    public HexMagic(IEventBus modBus, ModContainer container) {
        HexItems.ITEMS.register(modBus);
        HexItems.TABS.register(modBus);
        HexEntities.ENTITIES.register(modBus);
        HexAttachments.ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, HexConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
