package com.automagy.golem;

import com.automagy.AMRegistry;
import com.automagy.item.LinkTooltip;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The golem linking bell. Right-click a device to attune the bell to it, then right-click golems to link them to it.
 * Sneak-right-click the device to clear all its links; sneak-right-click any other block to clear the bell.
 */
public class GolemLinkerItem extends Item {
    public GolemLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof GolemLinks.Device device) {
            if (player != null && player.isSecondaryUseActive()) {
                device.golems().clear();
                device.linksChanged();
                player.sendOverlayMessage(Component.translatable("automagia.golem_linker.cleared"));
            } else {
                stack.set(AMRegistry.LINK.get(), GlobalPos.of(level.dimension(), pos));
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable("automagia.golem_linker.attuned",
                            level.getBlockState(pos).getBlock().getName()));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (player != null && player.isSecondaryUseActive() && stack.has(AMRegistry.LINK.get())) {
            stack.remove(AMRegistry.LINK.get());
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EntityThaumaturgeGolem golem)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        GlobalPos at = stack.get(AMRegistry.LINK.get());
        if (at == null || at.dimension() != player.level().dimension()
                || !(player.level().getBlockEntity(at.pos()) instanceof GolemLinks.Device device)) {
            player.sendOverlayMessage(Component.translatable("automagia.golem_linker.unattuned"));
        } else if (GolemLinks.link(golem, player.level(), device, at.pos())) {
            player.sendOverlayMessage(Component.translatable("automagia.golem_linker.linked", device.golems().size(), GolemLinks.MAX));
        } else {
            player.sendOverlayMessage(Component.translatable("automagia.golem_linker.full", GolemLinks.MAX));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, out, flag);
        LinkTooltip.add(stack.get(AMRegistry.LINK.get()), out);
    }
}
