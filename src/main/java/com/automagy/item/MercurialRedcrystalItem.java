package com.automagy.item;

import com.automagy.AMRegistry;
import com.automagy.block.MercurialRedcrystalBlock;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirrorBase;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Right-click a magic or essentia mirror to bind the crystals to it; then place them within eight blocks of it. */
public class MercurialRedcrystalItem extends BlockItem {
    public MercurialRedcrystalItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof BlockEntityMirrorBase) {
            if (!context.getLevel().isClientSide()) {
                GlobalPos mirror = GlobalPos.of(context.getLevel().dimension(), context.getClickedPos());
                context.getItemInHand().set(AMRegistry.LINK.get(), mirror);
                Player player = context.getPlayer();
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable("automagia.msg.bound_mirror"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        GlobalPos mirror = context.getItemInHand().get(AMRegistry.LINK.get());
        if (mirror == null || !MercurialRedcrystalBlock.inReach(mirror, context.getLevel(), context.getClickedPos())) {
            Player player = context.getPlayer();
            if (player != null && !context.getLevel().isClientSide()) {
                player.sendOverlayMessage(Component.translatable(mirror == null
                        ? "automagia.msg.unbound" : "automagia.msg.too_far", MercurialRedcrystalBlock.REACH));
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, out, flag);
        LinkTooltip.add(stack.get(AMRegistry.LINK.get()), out);
    }
}
