package com.automagy.block;

import com.automagy.AMRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The remote comparator gives out the comparator signal of the block its crystalline eye watches, from up to
 * {@link RemoteComparatorBlockEntity#RANGE} blocks away, on every side. Right-click with an attuned eye to put it in,
 * with an empty hand to take it out.
 */
public class RemoteComparatorBlock extends AttachedBlock implements EntityBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public RemoteComparatorBlock(Properties properties) {
        super(16, 2, properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(RemoteComparatorBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteComparatorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() || type != AMRegistry.REMOTE_COMPARATOR_BE.get() ? null
                : (BlockEntityTicker<E>) (BlockEntityTicker<RemoteComparatorBlockEntity>) RemoteComparatorBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(AMRegistry.CRYSTALLINE_EYE.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RemoteComparatorBlockEntity be) {
            if (stack.get(AMRegistry.LINK.get()) == null) {
                player.sendOverlayMessage(Component.translatable("automagia.msg.eye_unattuned"));
            } else if (be.eye().isEmpty()) {
                be.setEye(stack.split(1));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof RemoteComparatorBlockEntity be && !be.eye().isEmpty()) {
            if (!level.isClientSide()) {
                player.getInventory().placeItemBackInInventory(be.eye());
                be.setEye(ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }
}
