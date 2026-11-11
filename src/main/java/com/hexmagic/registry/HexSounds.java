package com.hexmagic.registry;

import com.hexmagic.HexMagic;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events of the Hex Staff. The sounds themselves are synthesized, see assets/hexmagic/sounds. */
public final class HexSounds {
    private HexSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HexMagic.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HEX_FORM = register("hex_form");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEX_HIT = register("hex_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEX_SHATTER = register("hex_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL_CHARGE = register("drill_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL_FIRE = register("drill_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL_GRIND = register("drill_grind");
    public static final DeferredHolder<SoundEvent, SoundEvent> REFLECT = register("reflect");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH = register("launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRISON_CLOSE = register("prison_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRISON_CRUSH = register("prison_crush");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWARM_FIRE = register("swarm_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = register("summon");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HexMagic.id(name)));
    }
}
