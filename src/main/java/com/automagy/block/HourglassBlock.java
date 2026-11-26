package com.automagy.block;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The redstone hourglass. Right-click or a rising redstone signal turns the glass; when the sand has settled it gives
 * a short pulse. A comparator reads how much sand is still falling. Sneak-right-click with an empty hand sets the
 * time, in five-second steps up to a minute (so does right-clicking it with a wand or a stick). It sends its signal
 * out of its left and right sides ({@link #AXIS}, set from the placer's view) and takes activation from every other side. The magic hourglass ({@link #magic}) adds modes, repeating, redstone
 * gating and fine timing, all set with a wand.
 *
 * <p>The sand is drawn as it runs: {@link #SAND} is eighths left in the top bulb, {@link #RUNNING} adds a thread through
 * the neck. Turning the mundane glass makes it tumble end over end for {@link #TURN_TICKS} ({@code TURNING}: the model
 * stands aside and HourglassRenderer draws it); the magic glass's enchanted sand needs no turning.
 */
public class HourglassBlock extends BaseEntityBlock implements IInteractWithCaster {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Eighths of sand left in the top bulb: 7 just turned, 0 run out (or idle). */
    public static final IntegerProperty SAND = IntegerProperty.create("sand", 0, 7);
    /** Sand is falling: a thin stream runs through the neck. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    /** The mundane glass is turning over; the renderer draws it tumbling. */
    public static final BooleanProperty TURNING = BooleanProperty.create("turning");
    public static final int TURN_TICKS = 12;
    /** The axis the glass sends its signal along: its left and right as placed. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(3, 2, 3, 13, 14, 13),
            Block.box(2, 14, 2, 14, 16, 14));

    public HourglassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(SAND, 0).setValue(RUNNING, false)
                .setValue(TURNING, false).setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(AXIS,
                    state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            default -> state;
        };
    }

    /** A signal reaching the glass from any side but its two output sides. */
    public static boolean inputPowered(Level level, BlockPos pos, BlockState state) {
        Direction.Axis out = state.hasProperty(AXIS) ? state.getValue(AXIS) : Direction.Axis.X;
        for (Direction side : Direction.values()) {
            if (side.getAxis() != out && level.getSignal(pos.relative(side), side) > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean magic() {
        return false;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(HourglassBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HourglassBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.HOURGLASS_BE.get(), HourglassBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(TURNING) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(TURNING)) {
            level.setBlock(pos, state.setValue(TURNING, false), Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        // a wand goes on to the wand's own use, which calls onCasterRightClick (sneaking included)
        if (!magic() && stack.is(Items.STICK)) { // a stick poked into the hole sets the mundane timer
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HourglassBlockEntity glass) {
                glass.stepMundaneTime(player);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return stack.getItem() instanceof ICaster ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HourglassBlockEntity glass) {
            if (player.isSecondaryUseActive() && !magic()) {
                glass.stepMundaneTime(player);
            } else {
                glass.activate();
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HourglassBlockEntity glass) {
            if (magic()) {
                glass.wandClick(player, player.isSecondaryUseActive());
            } else {
                glass.stepMundaneTime(player);
            }
        }
        return true;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moved) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HourglassBlockEntity glass) {
            glass.onRedstone(inputPowered(level, pos, state));
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && direction.getAxis() == state.getValue(AXIS) ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HourglassBlockEntity glass ? glass.sandLevel() : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, SAND, RUNNING, TURNING, AXIS);
    }

    public static class Magic extends HourglassBlock {
        public Magic(Properties properties) {
            super(properties);
        }

        @Override
        public boolean magic() {
            return true;
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(Magic::new);
        }
    }
}
