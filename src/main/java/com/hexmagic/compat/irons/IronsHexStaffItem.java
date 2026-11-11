package com.hexmagic.compat.irons;

import com.hexmagic.item.HexStaffLogic;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The Hex Staff as a real Iron's Spells 'n Spellbooks staff: it carries the casting-implement
 * component, so with the "Spellbook" slot selected the right mouse button casts the player's
 * Iron's spell exactly like any other staff of that mod.
 */
public class IronsHexStaffItem extends StaffItem {
    public IronsHexStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return HexStaffLogic.use(level, player, hand);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        HexStaffLogic.releaseUsing(level, entity);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return HexStaffLogic.useAnimation();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        HexStaffLogic.appendTooltip(lines);
    }
}
