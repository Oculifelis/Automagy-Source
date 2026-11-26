package com.automagy;

import com.automagy.block.HourglassBlock;
import com.automagy.block.HourglassBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hourglass behaviour in a real level, run with the save/load checks ({@code -Dautomagia.selftest=true}): which sides
 * give the signal, which sides take one, and what a second activation does to the mundane and the magic glass. Builds
 * high above spawn and clears up after itself. Logs one {@code SELFTEST PASS} or {@code SELFTEST FAIL} line each.
 */
final class BehaviourTest {
    private BehaviourTest() {
    }

    static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            BlockPos p = new BlockPos(0, 250, 0);
            level.getChunk(p);
            BlockState lit = AMRegistry.HOURGLASS.get().defaultBlockState().setValue(HourglassBlock.AXIS, Direction.Axis.X)
                    .setValue(HourglassBlock.POWERED, true);
            level.setBlock(p, lit, Block.UPDATE_ALL);
            BlockState s = level.getBlockState(p);
            report("hourglass signal out of its two output sides", s.getSignal(level, p, Direction.EAST) == 15
                    && s.getSignal(level, p, Direction.WEST) == 15 && s.getSignal(level, p, Direction.NORTH) == 0
                    && s.getSignal(level, p, Direction.SOUTH) == 0 && s.getSignal(level, p, Direction.UP) == 0
                    && s.getSignal(level, p, Direction.DOWN) == 0);
            level.setBlock(p.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            boolean fromOutput = HourglassBlock.inputPowered(level, p, level.getBlockState(p));
            level.setBlock(p.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(p.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            boolean fromSide = HourglassBlock.inputPowered(level, p, level.getBlockState(p));
            level.setBlock(p.north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(p.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            boolean fromTop = HourglassBlock.inputPowered(level, p, level.getBlockState(p));
            level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            report("hourglass input from every side but its outputs", !fromOutput && fromSide && fromTop);

            level.setBlock(p, AMRegistry.HOURGLASS.get().defaultBlockState(), Block.UPDATE_ALL);
            report("hourglass mundane second turn flips the sand", reactivate(level, p) == 5);
            level.setBlock(p, AMRegistry.MAGIC_HOURGLASS.get().defaultBlockState(), Block.UPDATE_ALL);
            report("hourglass magic second activation puts the sand on top", reactivate(level, p) == 15);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } catch (Exception ex) {
            Automagy.LOG.error("SELFTEST FAIL behaviour crashed", ex);
        }
    }

    /** Activates a five-second glass, lets 30 ticks run, activates again and returns the sand level (out of 15). */
    private static int reactivate(ServerLevel level, BlockPos p) {
        if (!(level.getBlockEntity(p) instanceof HourglassBlockEntity glass)) {
            return -1;
        }
        glass.activate();
        for (int i = 0; i < 30; i++) {
            HourglassBlockEntity.serverTick(level, p, level.getBlockState(p), glass);
        }
        glass.activate();
        return glass.sandLevel();
    }

    private static void report(String name, boolean ok) {
        Automagy.LOG.info("SELFTEST {} {}", ok ? "PASS" : "FAIL", name);
    }
}
