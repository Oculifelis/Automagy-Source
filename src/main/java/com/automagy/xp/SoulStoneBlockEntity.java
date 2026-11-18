package com.automagy.xp;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectContainer;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJarBrain;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Soul Stone keeps the brain jars beside it level: it takes experience from one that is more than half full and
 * gives it back to one that runs below a quarter. It holds 10000 points; broken, half of them spill. When it holds
 * enough it drips concentrate into the jar one or two blocks below it, turning an empty warded jar into a jar of
 * enchanter's concentrate.
 */
public class SoulStoneBlockEntity extends BlockEntity {
    public static final int CAPACITY = 10000;
    private static final int FLOW = 100;
    private static final int DRIP = 50;

    private int xp;

    public SoulStoneBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.SOUL_STONE_BE.get(), pos, state);
    }

    public int xp() {
        return xp;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SoulStoneBlockEntity be) {
        if (!(level instanceof ServerLevel server) || level.getGameTime() % 20 != Math.floorMod(pos.asLong(), 20)) {
            return;
        }
        int before = be.xp;
        for (Direction d : Direction.values()) {
            if (server.getBlockEntity(pos.relative(d)) instanceof BlockEntityJarBrain brain) {
                int half = BlockEntityJarBrain.XP_MAX / 2, low = BlockEntityJarBrain.XP_MAX / 4;
                if (brain.xp() > half) {
                    int take = Math.min(FLOW, Math.min(brain.xp() - half, CAPACITY - be.xp));
                    brain.setXp(brain.xp() - take);
                    be.xp += take;
                } else if (brain.xp() < low) {
                    int give = Math.min(FLOW, Math.min(low - brain.xp(), be.xp));
                    brain.setXp(brain.xp() + give);
                    be.xp -= give;
                }
            }
        }
        if (be.xp >= DRIP) {
            be.drip(server);
        }
        if (be.xp != before) {
            be.setChanged();
            server.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private void drip(ServerLevel level) {
        for (int d = 1; d <= 2; d++) {
            BlockPos p = worldPosition.below(d);
            BlockState below = level.getBlockState(p);
            if (below.is(TTBlocks.JAR_NORMAL.get())) {
                IAspectContainer jar = level.getCapability(AspectCapabilities.CONTAINER, p, null);
                if (jar != null && !jar.getAspects().isEmpty()) {
                    return;
                }
                level.setBlockAndUpdate(p, AMRegistry.CONCENTRATE_JAR.get().defaultBlockState());
            }
            if (level.getBlockEntity(p) instanceof ConcentrateJarBlockEntity jar) {
                int moved = jar.add(DRIP);
                if (moved > 0) {
                    xp -= moved;
                    level.sendParticles(ParticleTypes.FALLING_HONEY, worldPosition.getX() + 0.5, worldPosition.getY() - 0.05,
                            worldPosition.getZ() + 0.5, 1, 0.1, 0, 0.1, 0);
                }
                return;
            }
            if (!below.isAir()) {
                return;
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            Experience.orbs(server, Vec3.atCenterOf(pos), xp / 2);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        xp = in.getIntOr("xp", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("xp", xp);
    }
}
