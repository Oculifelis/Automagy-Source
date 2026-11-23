package com.automagy.golem;

import com.automagy.AMRegistry;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Oculo Anima: a crystalline eye imprinted with a creature. Right-click a creature (or another player) to imprint
 * it; sneak-right-click the air to watch for yourself, and again to watch for any player.
 */
public class EntityEyeItem extends Item {
    public EntityEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!player.level().isClientSide()) {
            imprint(player.getItemInHand(hand), EntityWatch.of(target), player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = useItem(level, player, hand);
        return new net.minecraft.world.InteractionResultHolder<>(result, player.getItemInHand(hand));
    }

    private InteractionResult useItem(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            EntityWatch self = EntityWatch.of(player);
            imprint(stack, self.equals(stack.get(AMRegistry.ENTITY_WATCH.get())) ? EntityWatch.anyPlayer() : self, player);
        }
        return InteractionResult.SUCCESS;
    }

    private static void imprint(ItemStack stack, EntityWatch watch, Player player) {
        stack.set(AMRegistry.ENTITY_WATCH.get(), watch);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        player.displayClientMessage(Component.translatable("automagia.entity_eye.imprinted", watch.describe()), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> out, TooltipFlag flag) {
        super.appendHoverText(stack, context, out, flag);
        EntityWatch watch = stack.get(AMRegistry.ENTITY_WATCH.get());
        if (watch != null) {
            out.add(Component.translatable("automagia.entity_eye.watching", watch.describe()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
