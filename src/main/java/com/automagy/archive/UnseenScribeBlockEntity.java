package com.automagy.archive;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The unseen scribe. The container behind it sets the one kind of container it reads (a scrivener's oculus on top adds
 * more kinds and widens the area); it reads every container of those kinds in the 7x7x7 around it and tells its
 * Inventarium core what they hold. Each reading costs {@value #VIS_PER_READ} vis from the aura. A container with more
 * slots than the core has free is left out whole, with black smoke over the scribe.
 */
public class UnseenScribeBlockEntity extends ArchiveNodeBlockEntity {
    public static final float VIS_PER_READ = 0.05f;
    private static final int PERIOD = 40;
    private static final int RADIUS = 3;

    private Map<ItemResource, Integer> read = Map.of();
    private int used;

    public UnseenScribeBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.UNSEEN_SCRIBE_BE.get(), pos, state);
    }

    @Override
    public InventariumCoreBlockEntity.Kind kind() {
        return InventariumCoreBlockEntity.Kind.SCRIBE;
    }

    /** What it read last, by item; nothing while unlinked. */
    public Map<ItemResource, Integer> read() {
        return core() == null ? Map.of() : read;
    }

    int usedSlots() {
        return used;
    }

    private Set<Block> kinds() {
        Set<Block> out = new HashSet<>();
        Direction back = getBlockState().getValue(UnseenScribeBlock.FACING).getOpposite();
        BlockState behind = level.getBlockState(worldPosition.relative(back));
        if (!behind.isAir()) {
            out.add(behind.getBlock());
        }
        if (level.getBlockEntity(worldPosition.above()) instanceof ScrivenerOculusBlockEntity oculus) {
            for (BlockPos p : oculus.targets()) {
                if (!p.equals(worldPosition) && level.isLoaded(p)) {
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir()) {
                        out.add(s.getBlock());
                    }
                }
            }
        }
        return out;
    }

    private int radius() {
        int r = RADIUS;
        if (level.getBlockEntity(worldPosition.above()) instanceof ScrivenerOculusBlockEntity oculus) {
            for (BlockPos p : oculus.targets()) {
                if (p.equals(worldPosition)) {
                    r++;
                }
            }
        }
        return r;
    }

    private void scan(InventariumCoreBlockEntity core) {
        Set<Block> kinds = kinds();
        int free = core.capacity() - core.usedSlots(this);
        Map<ItemResource, Integer> out = new HashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        int taken = 0;
        boolean leftOut = false;
        int r = radius();
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-r, -r, -r), worldPosition.offset(r, r, r))) {
            BlockState s = level.getBlockState(p);
            if (!kinds.contains(s.getBlock()) || !seen.add(p.immutable())) {
                continue;
            }
            // a double chest is one container: read it from one half only
            if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                seen.add(p.relative(ChestBlock.getConnectedDirection(s)).immutable());
            }
            ResourceHandler<ItemResource> h = level.getCapability(Capabilities.Item.BLOCK, p, null);
            if (h == null) {
                continue;
            }
            if (taken + h.size() > free) {
                leftOut = true;
                continue;
            }
            taken += h.size();
            for (int i = 0; i < h.size(); i++) {
                ItemResource res = h.getResource(i);
                int n = h.getAmountAsInt(i);
                if (!res.isEmpty() && n > 0) {
                    out.merge(res, n, Integer::sum);
                }
            }
        }
        read = out;
        used = taken;
        if (leftOut && level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.0);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, UnseenScribeBlockEntity be) {
        if (level.getGameTime() % PERIOD != pos.asLong() % PERIOD) {
            return;
        }
        InventariumCoreBlockEntity core = be.core();
        if (core == null) {
            be.read = Map.of();
            be.used = 0;
            return;
        }
        if (AuraHelper.drainVis(level, pos, VIS_PER_READ, true) < VIS_PER_READ) {
            return;
        }
        AuraHelper.drainVis(level, pos, VIS_PER_READ, false);
        be.scan(core);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        used = in.getIntOr("used", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("used", used);
    }
}
