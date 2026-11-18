package com.automagy.block;

import com.automagy.AMRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.ChestBlock;

/** The three device chests' blocks; each differs only in its block entity. */
public final class DeviceChests {
    private DeviceChests() {
    }

    public static class Greedy extends DeviceChestBlock {
        public static final MapCodec<Greedy> CODEC = simpleCodec(Greedy::new);

        public Greedy(Properties p) {
            super(AMRegistry.GREEDY_CHEST_BE::get, p);
        }

        @Override
        public MapCodec<? extends ChestBlock> codec() {
            return CODEC;
        }
    }

    public static class Eager extends DeviceChestBlock {
        public static final MapCodec<Eager> CODEC = simpleCodec(Eager::new);

        public Eager(Properties p) {
            super(AMRegistry.EAGER_CHEST_BE::get, p);
        }

        @Override
        public MapCodec<? extends ChestBlock> codec() {
            return CODEC;
        }
    }

    public static class Tenacious extends DeviceChestBlock {
        public static final MapCodec<Tenacious> CODEC = simpleCodec(Tenacious::new);

        public Tenacious(Properties p) {
            super(AMRegistry.TENACIOUS_CHEST_BE::get, p);
        }

        @Override
        public MapCodec<? extends ChestBlock> codec() {
            return CODEC;
        }
    }
}
