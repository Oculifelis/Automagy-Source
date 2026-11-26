package com.automagy.block;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.leclowndu93150.thaumaturge.api.items.ILabel;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The vis reader gives out a redstone signal from the aspects in the block it is stuck to (a warded jar, a crucible,
 * an essentia reservoir, anything that holds or reports aspects), or in the block watched by a remote comparator
 * beside it. A wand sets its mode, capacity, inversion and output side.
 */
public class VisReaderBlock extends AttachedBlock implements EntityBlock, IInteractWithCaster {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public VisReaderBlock(Properties properties) {
        super(10, 6, properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(VisReaderBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VisReaderBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() || type != AMRegistry.VIS_READER_BE.get() ? null
                : (BlockEntityTicker<E>) (BlockEntityTicker<VisReaderBlockEntity>) VisReaderBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ILabel label) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VisReaderBlockEntity be) {
                be.applyLabel(player, stack, label);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VisReaderBlockEntity be) {
            be.wandClick(player, player.isSecondaryUseActive());
        }
        return true;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof VisReaderBlockEntity be)) {
            return 0;
        }
        Direction out = be.outputSide();
        return out == null || out == direction.getOpposite() ? state.getValue(POWER) : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }
}
