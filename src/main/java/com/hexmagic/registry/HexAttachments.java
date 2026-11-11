package com.hexmagic.registry;

import com.hexmagic.HexMagic;
import com.hexmagic.ability.HexPlayerData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class HexAttachments {
    private HexAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HexMagic.MOD_ID);

    public static final Supplier<AttachmentType<HexPlayerData>> PLAYER_DATA = ATTACHMENTS.register("player_data",
            () -> AttachmentType.builder(HexPlayerData::new).serialize(HexPlayerData.CODEC).build());
}
