package com.automagy.item;

import com.automagy.AMRegistry;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A crystalline eye lets a device watch a block somewhere else. Right-click the block to watch to attune the eye
 * (it glints once attuned), then put it into a remote comparator.
 */
public class CrystallineEyeItem extends Item {
    public CrystallineEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            ItemStack stack = context.getItemInHand();
            stack.set(AMRegistry.LINK.get(), GlobalPos.of(context.getLevel().dimension(), context.getClickedPos()));
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (context.getPlayer() != null) {
                var p = context.getClickedPos();
                context.getPlayer().sendOverlayMessage(Component.translatable("automagia.msg.attuned", p.getX(), p.getY(), p.getZ()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, out, flag);
        LinkTooltip.add(stack.get(AMRegistry.LINK.get()), out);
    }
}
