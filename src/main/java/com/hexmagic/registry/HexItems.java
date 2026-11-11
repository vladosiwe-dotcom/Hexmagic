package com.hexmagic.registry;

import com.hexmagic.HexMagic;
import com.hexmagic.compat.IronsCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HexItems {
    private HexItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HexMagic.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HexMagic.MOD_ID);

    /**
     * With Iron's Spells 'n Spellbooks installed this is a subclass of its StaffItem; without it,
     * a plain item with the same behaviour.
     */
    public static final DeferredItem<Item> HEX_STAFF = ITEMS.register("hex_staff",
            () -> IronsCompat.createStaff(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hexmagic"))
            .icon(() -> new ItemStack(HEX_STAFF.get()))
            .displayItems((params, output) -> output.accept(HEX_STAFF.get()))
            .build());

    public static boolean isStaff(ItemStack stack) {
        return stack.is(HEX_STAFF.get());
    }

    public static boolean isHoldingStaff(LivingEntity entity) {
        return isStaff(entity.getMainHandItem()) || isStaff(entity.getOffhandItem());
    }
}
