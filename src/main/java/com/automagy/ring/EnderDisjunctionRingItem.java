package com.automagy.ring;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A ring of ender disjunction, worn in a curio ring slot; what it does is the item's {@link EnderDisjunction} data. */
public class EnderDisjunctionRingItem extends Item {
    public EnderDisjunctionRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> out, TooltipFlag flag) {
        super.appendHoverText(stack, context, out, flag);
        out.add(Component.translatable("automagia.tip.ender_disjunction").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
