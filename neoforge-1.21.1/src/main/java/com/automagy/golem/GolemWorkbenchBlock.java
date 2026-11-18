package com.automagy.golem;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The golem's workbench block. Right-click opens it; right-click a side with a workbench upgrade puts it there (one
 * per side, four sides); sneak-right-click a side with a wand takes that side's upgrade off. Each side's state shows
 * the plate of the upgrade on it.
 */
public class GolemWorkbenchBlock extends BaseEntityBlock implements IInteractWithCaster {
    public static final Map<Direction, EnumProperty<Plate>> PLATES = Map.of(
            Direction.NORTH, EnumProperty.create("north", Plate.class),
            Direction.EAST, EnumProperty.create("east", Plate.class),
            Direction.SOUTH, EnumProperty.create("south", Plate.class),
            Direction.WEST, EnumProperty.create("west", Plate.class));

    public GolemWorkbenchBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any();
        for (EnumProperty<Plate> p : PLATES.values()) {
            s = s.setValue(p, Plate.NONE);
        }
        registerDefaultState(s);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(GolemWorkbenchBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PLATES.values().forEach(builder::add);
    }

    static BlockState withPlates(BlockState state, GolemWorkbenchBlockEntity be) {
        for (Map.Entry<Direction, EnumProperty<Plate>> e : PLATES.entrySet()) {
            WorkbenchUpgrade u = WorkbenchUpgrade.of(be.upgrade(e.getKey()));
            state = state.setValue(e.getValue(), u == null ? Plate.NONE : Plate.values()[u.effect().ordinal() + 1]);
        }
        return state;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GolemWorkbenchBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.GOLEM_WORKBENCH_BE.get(), GolemWorkbenchBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (WorkbenchUpgrade.of(stack) == null || !(level.getBlockEntity(pos) instanceof GolemWorkbenchBlockEntity bench)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide() && !bench.addUpgrade(hit.getDirection(), stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GolemWorkbenchBlockEntity bench) {
            player.openMenu(bench, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof GolemWorkbenchBlockEntity bench)
                || bench.upgrade(side).isEmpty()) {
            return false;
        }
        if (!level.isClientSide()) {
            player.getInventory().placeItemBackInInventory(bench.removeUpgrade(side));
        }
        return true;
    }

    /** What a side shows: nothing, or the plate of one upgrade effect (same order as {@link WorkbenchUpgrade.Effect}). */
    public enum Plate implements StringRepresentable {
        NONE, SPEED, PATTERN, ENDURANCE, STRENGTH, TIDY, PLACEHOLDER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
