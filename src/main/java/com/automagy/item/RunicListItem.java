package com.automagy.item;

import com.automagy.AMRegistry;
import com.automagy.filter.RunicList;
import com.automagy.menu.RunicListMenu;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A runic whitelist or blacklist: enchanted paper with up to nine items (and quantities) written on it. Use it in the
 * air to write on it; put it into a greedy or eager chest, tally box, finical maw or hungry hand mirror to tell that
 * device what to see.
 */
public class RunicListItem extends Item {
    private final boolean white;

    public RunicListItem(boolean white, Properties properties) {
        super(properties);
        this.white = white;
    }

    public boolean white() {
        return white;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RunicListMenu(id, inv, hand),
                    player.getItemInHand(hand).getHoverName()), buf -> buf.writeEnum(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, out, flag);
        RunicList list = stack.getOrDefault(AMRegistry.RUNIC_LIST.get(), RunicList.EMPTY);
        if (list.entries().isEmpty()) {
            out.accept(Component.translatable("automagia.tip.list_blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (RunicList.Entry e : list.entries()) {
            Component name = e.item().create().getHoverName();
            out.accept((e.hasQty() ? Component.translatable("automagia.tip.list_qty", e.qty(), name)
                    : Component.translatable("automagia.tip.list_item", name)).withStyle(ChatFormatting.GRAY));
        }
        if (list.partial()) {
            out.accept(Component.translatable("automagia.tip.list_partial").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
