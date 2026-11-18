package com.automagy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bookshelf of bound enchanting paper: four times a bookshelf's power, so four give an enchanting table level 30.
 * The ensorcelled bookshelf is eight times. Both are archive shelves too (their slots are in the archive_capacity map).
 */
public class EnchantingBookshelfBlock extends Block {
    public static final float POWER = 4.0f;
    public static final float ENSORCELLED_POWER = 8.0f;
    private final float power;

    public EnchantingBookshelfBlock(Properties properties) {
        this(properties, POWER);
    }

    public EnchantingBookshelfBlock(Properties properties, float power) {
        super(properties);
        this.power = power;
    }

    @Override
    public float getEnchantPowerBonus(BlockState state, BlockGetter level, BlockPos pos) {
        return power;
    }
}
