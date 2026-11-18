package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.item.RunicListItem;
import com.automagy.item.TallyLensItem;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The tally box points at a container, like a hopper, and holds a runic list. It gives a full redstone signal out of
 * its back (the side away from the container) when the container's contents meet the list. A lens changes what it
 * sees: unbound, the loose items in that place; yield, what the block there would drop.
 */
public class TallyBoxBlock extends BaseEntityBlock implements IInteractWithCaster {
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final EnumProperty<Lens> LENS = EnumProperty.create("lens", Lens.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public enum Lens implements StringRepresentable {
        NONE, UNBOUND, YIELD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public TallyBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LENS, Lens.NONE).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(TallyBoxBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TallyBoxBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.TALLY_BOX_BE.get(), TallyBoxBlockEntity::serverTick);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ICaster) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof TallyBoxBlockEntity box) || player.isSecondaryUseActive()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stack.getItem() instanceof TallyLensItem lens && lens.lens() != Lens.NONE && lens.lens() != state.getValue(LENS)) {
            if (!level.isClientSide()) {
                ItemStack old = TallyLensItem.stackFor(state.getValue(LENS));
                level.setBlock(pos, state.setValue(LENS, lens.lens()), Block.UPDATE_ALL);
                stack.consume(1, player);
                if (!old.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(old);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof RunicListItem) {
            if (!level.isClientSide()) {
                box.putList(player, stack);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Sneak-right-click with an empty hand takes out the list, then the lens. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof TallyBoxBlockEntity box)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (!box.list().isEmpty()) {
                box.takeList(player);
            } else if (state.getValue(LENS) != Lens.NONE) {
                player.getInventory().placeItemBackInInventory(TallyLensItem.stackFor(state.getValue(LENS)));
                level.setBlock(pos, state.setValue(LENS, Lens.NONE), Block.UPDATE_ALL);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TallyBoxBlockEntity box) {
            box.wandClick(player, player.isSecondaryUseActive());
        }
        return true;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Only out of the back: the asking neighbour sits behind the box when it asks in the box's own facing. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && direction == state.getValue(FACING) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LENS, POWERED);
    }
}
