package com.hexmagic.item;

import com.hexmagic.ability.HexAbility;
import com.hexmagic.ability.HexCasting;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * Behaviour of the Hex Staff item. It lives outside the item class because the staff is one of two
 * classes depending on whether Iron's Spells 'n Spellbooks is installed, and both share this code.
 */
public final class HexStaffLogic {
    private HexStaffLogic() {}

    public static final float ATTACK_DAMAGE = 4f;
    public static final float ATTACK_SPEED = -3f;
    /** Set by the client: the ability currently bound to the "use" button. */
    public static IntSupplier clientSelection = () -> 0;

    public static ItemAttributeModifiers vanillaAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public static int selection(Level level, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) return HexCasting.data(sp).selected;
        return clientSelection.getAsInt();
    }

    /** Right click: cast (or start charging) the selected ability. */
    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int selected = selection(level, player);
        HexAbility ability = HexAbility.byIndex(selected);
        if (ability == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            HexCasting.press(sp, ability);
        }
        if (ability == HexAbility.DRILL) player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    public static void releaseUsing(Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            HexCasting.release(sp, HexAbility.DRILL);
        } else if (level.isClientSide && entity instanceof Player) {
            // Belt and braces: the server releases on its own, this covers a missed use packet.
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.hexmagic.net.AbilityInputPayload(HexAbility.DRILL.ordinal(), false));
        }
    }

    public static UseAnim useAnimation() {
        return clientSelection.getAsInt() == HexAbility.DRILL.ordinal() ? UseAnim.SPEAR : UseAnim.NONE;
    }

    public static void appendTooltip(List<Component> lines) {
        lines.add(Component.translatable("item.hexmagic.hex_staff.tooltip").withStyle(ChatFormatting.AQUA));
        for (HexAbility a : HexAbility.VALUES) {
            ChatFormatting color = switch (a.kind) {
                case ACTIVE -> ChatFormatting.WHITE;
                case PASSIVE -> ChatFormatting.GRAY;
                case ULTIMATE -> ChatFormatting.GOLD;
            };
            lines.add(Component.literal(" " + (a.ordinal() + 1) + ". ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(a.displayName().copy().withStyle(color))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("ability.hexmagic." + a.id + ".desc").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }
}
