package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.item.RunicListItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** A hungry maw that takes a runic list: right-click it with one to put it in, sneak-right-click to take it out. */
public class FinicalMawBlock extends HungryMawBlock {
    public FinicalMawBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(FinicalMawBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FinicalMawBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.FINICAL_MAW_BE.get(), HungryMawBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof RunicListItem && !player.isSecondaryUseActive()
                && level.getBlockEntity(pos) instanceof FinicalMawBlockEntity maw) {
            if (!level.isClientSide()) {
                ItemStack old = maw.list();
                maw.setList(stack.split(1));
                if (!old.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(old);
                }
                player.displayClientMessage(Component.translatable("automagia.msg.list_in", maw.list().getHoverName()), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof FinicalMawBlockEntity maw && !maw.list().isEmpty()) {
            if (!level.isClientSide()) {
                player.getInventory().placeItemBackInInventory(maw.list());
                maw.setList(ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
