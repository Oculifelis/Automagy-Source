package com.automagy.tank;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * The thirsty tank block. A glyph item pressed on a side goes onto that side (shown as a mark on the face); sneak-right-
 * click a side with a wand to take its glyph back. Buckets and water bottles fill and empty it as usual.
 */
public class ThirstyTankBlock extends BaseEntityBlock implements IInteractWithCaster {
    /** Whether a glyph is on each side, so the model can draw it. */
    public static final Map<Direction, BooleanProperty> GLYPH = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final int BOTTLE = 250;

    public ThirstyTankBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty p : GLYPH.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ThirstyTankBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThirstyTankBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.THIRSTY_TANK_BE.get(), ThirstyTankBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ICaster || !(level.getBlockEntity(pos) instanceof ThirstyTankBlockEntity tank)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        Direction side = hit.getDirection();
        if (Glyph.of(stack) != null) {
            if (!tank.glyph(side).isEmpty()) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                tank.setGlyph(side, stack.consumeAndReturn(1, player));
                level.setBlock(pos, state.setValue(GLYPH.get(side), true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(Items.GLASS_BOTTLE) && tank.fluid().getFluid().isSame(Fluids.WATER) && tank.fluid().getAmount() >= BOTTLE) {
            if (!level.isClientSide()) {
                tank.tank().drain(new FluidStack(Fluids.WATER, BOTTLE), IFluidHandler.FluidAction.EXECUTE);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, PotionContents.createItemStack(Items.POTION, Potions.WATER)));
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.SUCCESS;
        }
        PotionContents potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (stack.is(Items.POTION) && potion != null && potion.is(Potions.WATER)) {
            if (!level.isClientSide()) {
                {
                    if (tank.tank().fill(new FluidStack(Fluids.WATER, BOTTLE), IFluidHandler.FluidAction.SIMULATE) == BOTTLE) {
                        tank.tank().fill(new FluidStack(Fluids.WATER, BOTTLE), IFluidHandler.FluidAction.EXECUTE);
                        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1f, 1f);
                    }
                }
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(AMRegistry.VISHROOM_STEW.get())) {
            // the stew settles in the tank into vishroom soup, four bowls to the bucket
            if (!level.isClientSide()) {
                {
                    if (tank.tank().fill(new FluidStack(AMRegistry.VISHROOM_SOUP.get(), BOTTLE), IFluidHandler.FluidAction.SIMULATE) == BOTTLE) {
                        tank.tank().fill(new FluidStack(AMRegistry.VISHROOM_SOUP.get(), BOTTLE), IFluidHandler.FluidAction.EXECUTE);
                        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BOWL)));
                        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 0.8f);
                    }
                }
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, tank.tank())) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof ThirstyTankBlockEntity tank) || tank.glyph(side).isEmpty()) {
            return false;
        }
        if (!level.isClientSide()) {
            player.getInventory().placeItemBackInInventory(tank.glyph(side));
            tank.setGlyph(side, ItemStack.EMPTY);
            level.setBlock(pos, level.getBlockState(pos).setValue(GLYPH.get(side), false), Block.UPDATE_ALL);
        }
        return true;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ThirstyTankBlockEntity tank ? tank.signal() : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        GLYPH.values().forEach(builder::add);
    }
}
