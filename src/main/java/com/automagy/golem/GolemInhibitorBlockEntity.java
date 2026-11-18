package com.automagy.golem;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.content.golem.BlockGolemFetter;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The golemic inhibitor. It sits on a golem fetter and works while that fetter is powered; every further powered
 * fetter chained under it, up to four in all, widens the radius by four blocks. Golems in range count as fettered:
 * all of them, all but the golems linked to it, or only those (a wand switches between the last two; with no links
 * the first is the same as "all but linked").
 */
public class GolemInhibitorBlockEntity extends BlockEntity implements GolemLinks.Device {
    private static final int RADIUS_PER_FETTER = 4;
    private static final int MAX_FETTERS = 4;
    /** Loaded inhibitors per level, so a golem's fetter check need not search blocks. */
    private static final Map<Level, Set<GolemInhibitorBlockEntity>> LOADED = new WeakHashMap<>();

    private final Set<UUID> golems = new HashSet<>();
    private boolean exclusive;
    private int radius;

    public GolemInhibitorBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.GOLEM_INHIBITOR_BE.get(), pos, state);
    }

    /** Whether an inhibitor holds this golem still (called from the golem's fetter check). */
    public static boolean inhibits(Entity golem) {
        Set<GolemInhibitorBlockEntity> here = LOADED.get(golem.level());
        if (here == null) {
            return false;
        }
        for (GolemInhibitorBlockEntity be : here) {
            if (be.radius > 0 && golem.blockPosition().closerThan(be.worldPosition, be.radius)
                    && GolemLinks.linked(golem, be.level, be, be.worldPosition) == be.exclusive) {
                return true;
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GolemInhibitorBlockEntity be) {
        if (level.getGameTime() % 10 == 0) {
            int fetters = 0;
            for (BlockPos p = pos.below(); fetters < MAX_FETTERS; p = p.below()) {
                BlockState below = level.getBlockState(p);
                if (!below.is(TTBlocks.GOLEM_FETTER.get()) || !below.getValue(BlockGolemFetter.POWERED)) {
                    break;
                }
                fetters++;
            }
            be.radius = fetters * RADIUS_PER_FETTER;
        }
    }

    public boolean exclusive() {
        return exclusive;
    }

    void toggleMode() {
        exclusive = !exclusive;
        setChanged();
    }

    @Override
    public Set<UUID> golems() {
        return golems;
    }

    @Override
    public void linksChanged() {
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.computeIfAbsent(level, l -> new HashSet<>()).add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            Set<GolemInhibitorBlockEntity> here = LOADED.get(level);
            if (here != null) {
                here.remove(this);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        golems.clear();
        in.read("golems", UUIDUtil.CODEC.listOf()).ifPresent(golems::addAll);
        exclusive = in.getBooleanOr("exclusive", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("golems", UUIDUtil.CODEC.listOf(), java.util.List.copyOf(golems));
        out.putBoolean("exclusive", exclusive);
    }
}
