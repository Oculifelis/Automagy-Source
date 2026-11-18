package com.automagy.locus;

import com.automagy.AMRegistry;
import com.automagy.item.CrystallineEyeItem;
import com.mojang.serialization.MapCodec;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
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

/**
 * The essentia locus block. Right-click with an attuned crystalline eye to put it in; sneak-right-click with an empty
 * hand to take the last one out; right-click with an empty hand to see what it counts. A comparator or vis reader
 * reads its total.
 */
public class EssentiaLocusBlock extends BaseEntityBlock {
    public EssentiaLocusBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(EssentiaLocusBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssentiaLocusBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.ESSENTIA_LOCUS_BE.get(), EssentiaLocusBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof CrystallineEyeItem) || !(level.getBlockEntity(pos) instanceof EssentiaLocusBlockEntity locus)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        locus.rescan();
        GlobalPos at = stack.get(AMRegistry.LINK.get());
        if (!locus.formed()) {
            player.displayClientMessage(Component.translatable("automagia.locus.unformed"), true);
        } else if (at != null && at.pos().equals(pos)) {
            player.displayClientMessage(Component.translatable("automagia.locus.self"), true);
        } else if (at == null || at.dimension() != level.dimension() || !at.pos().closerThan(pos, locus.range())) {
            player.displayClientMessage(Component.translatable("automagia.locus.unlinked", locus.range()), true);
        } else if (locus.eyes().size() >= locus.maxEyes()) {
            player.displayClientMessage(Component.translatable("automagia.locus.full", locus.maxEyes()), true);
        } else {
            locus.addEye(stack.split(1));
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof EssentiaLocusBlockEntity locus)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            ItemStack eye = locus.takeEye();
            if (!eye.isEmpty()) {
                player.getInventory().placeItemBackInInventory(eye);
            }
        } else if (!locus.formed()) {
            player.displayClientMessage(Component.translatable("automagia.locus.unformed"), true);
        } else {
            String counts = locus.getAspects().sortedByAmount().stream()
                    .map(a -> a.amount() + " " + a.aspect().getRegisteredName().replaceFirst(".*:", ""))
                    .collect(Collectors.joining(", "));
            player.displayClientMessage(Component.translatable("automagia.locus.status", locus.eyes().size(), locus.maxEyes(),
                    counts.isEmpty() ? "-" : counts), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof EssentiaLocusBlockEntity locus)) {
            return 0;
        }
        int total = locus.getAspects().totalAmount();
        return total == 0 ? 0 : Math.min(15, 1 + total / 64);
    }
}
