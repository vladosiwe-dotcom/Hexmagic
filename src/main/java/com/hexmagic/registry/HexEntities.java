package com.hexmagic.registry;

import com.hexmagic.HexMagic;
import com.hexmagic.entity.HexConstructEntity;
import com.hexmagic.entity.HexDrillEntity;
import com.hexmagic.entity.HexPrisonEntity;
import com.hexmagic.entity.HexSwarmEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HexEntities {
    private HexEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HexMagic.MOD_ID);

    /** One entity per construct; its hundreds of cells exist only in the renderer. */
    public static final DeferredHolder<EntityType<?>, EntityType<HexConstructEntity>> CONSTRUCT =
            register("hex_construct", EntityType.Builder.<HexConstructEntity>of(HexConstructEntity::new, MobCategory.MISC).sized(1f, 1f));
    public static final DeferredHolder<EntityType<?>, EntityType<HexDrillEntity>> DRILL =
            register("hex_drill", EntityType.Builder.<HexDrillEntity>of(HexDrillEntity::new, MobCategory.MISC).sized(0.6f, 0.6f));
    public static final DeferredHolder<EntityType<?>, EntityType<HexSwarmEntity>> SWARM =
            register("hex_swarm", EntityType.Builder.<HexSwarmEntity>of(HexSwarmEntity::new, MobCategory.MISC).sized(0.5f, 0.5f));
    public static final DeferredHolder<EntityType<?>, EntityType<HexPrisonEntity>> PRISON =
            register("hex_prison", EntityType.Builder.<HexPrisonEntity>of(HexPrisonEntity::new, MobCategory.MISC).sized(0.5f, 0.5f));

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        return ENTITIES.register(name, () -> builder
                .noSave()
                .noSummon()
                .fireImmune()
                .clientTrackingRange(10)
                .updateInterval(1)
                .build(name));
    }
}
