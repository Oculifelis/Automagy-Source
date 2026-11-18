package com.automagy.archive;

import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/** A block whose block entity a wand links to an Inventarium core. {@link Facing} ones face the block they work on. */
public abstract class ArchiveNodeBlock<T extends ArchiveNodeBlockEntity> extends BaseEntityBlock implements IInteractWithCaster {
    private final Supplier<BlockEntityType<T>> type;
    private final @Nullable BlockEntityTicker<T> ticker;

    protected ArchiveNodeBlock(Properties properties, Supplier<BlockEntityType<T>> type, @Nullable BlockEntityTicker<T> ticker) {
        super(properties);
        this.type = type;
        this.ticker = ticker;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> t) {
        return level.isClientSide() || ticker == null ? null : createTickerHelper(t, type.get(), ticker);
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof ArchiveNodeBlockEntity node)) {
            return false;
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(node.link(), true);
        }
        return true;
    }

    /** A node that faces something: placed against a block, it faces away from it (its back on that block). */
    public abstract static class Facing<T extends ArchiveNodeBlockEntity> extends ArchiveNodeBlock<T> {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

        protected Facing(Properties properties, Supplier<BlockEntityType<T>> type, @Nullable BlockEntityTicker<T> ticker) {
            super(properties, type, ticker);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getClickedFace());
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }
    }
}
