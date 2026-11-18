package com.automagy.xp;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The jar of enchanter's concentrate. An empty phial draws a Phial of Enchanter's Concentrate (100 points), which
 * pours back in; a glass bottle draws a bottle o' enchanting. Emptied, it is a warded jar again.
 */
public class ConcentrateJarBlock extends BaseEntityBlock {
    public static final int PHIAL = 100;
    public static final int BOTTLE = 10;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public ConcentrateJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ConcentrateJarBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConcentrateJarBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ConcentrateJarBlockEntity jar)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack result;
        if (stack.is(AMRegistry.CONCENTRATE_PHIAL.get())) {
            if (jar.xp() + PHIAL > ConcentrateJarBlockEntity.CAPACITY) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                jar.add(PHIAL);
            }
            result = new ItemStack(TTItems.PHIAL.get());
        } else if (stack.is(TTItems.PHIAL.get()) && !stack.has(TTDataComponents.ESSENTIA_CONTENTS.get())) {
            if (jar.xp() < PHIAL) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                jar.take(PHIAL);
            }
            result = new ItemStack(AMRegistry.CONCENTRATE_PHIAL.get());
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            if (jar.xp() < BOTTLE) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                jar.take(BOTTLE);
            }
            result = new ItemStack(Items.EXPERIENCE_BOTTLE);
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.6f, 1.2f);
            if (jar.xp() <= 0) {
                level.setBlockAndUpdate(pos, TTBlocks.JAR_NORMAL.get().defaultBlockState());
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ConcentrateJarBlockEntity jar
                ? (int) Math.ceil(15.0 * jar.xp() / ConcentrateJarBlockEntity.CAPACITY) : 0;
    }
}
