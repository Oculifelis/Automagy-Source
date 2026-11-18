package com.automagy.locus;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectContainer;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The essentia locus. Built on a water crystal cluster over a core block (lapis), the core ringed by water sources and
 * the water walled in by rim blocks (silverwood planks); the three are the block tags {@code automagia:locus_core},
 * {@code automagia:locus_rim} and {@code automagia:locus_cluster}. Crystalline eyes put into it each watch a group of
 * essentia containers (the watched block and every container joined to it), and the locus adds up all they hold.
 * More clusters touching the rim raise its limits. With an essentia aggregator on top it is a source that draws from
 * those containers.
 */
public class EssentiaLocusBlockEntity extends BlockEntity implements IAspectContainer {
    public static final TagKey<Block> CORE = TagKey.create(Registries.BLOCK, Automagy.id("locus_core"));
    public static final TagKey<Block> RIM = TagKey.create(Registries.BLOCK, Automagy.id("locus_rim"));
    public static final TagKey<Block> CLUSTER = TagKey.create(Registries.BLOCK, Automagy.id("locus_cluster"));
    private static final int JARS = 12;
    private static final int JARS_PER_CLUSTER = 8;
    private static final int RANGE = 16;
    private static final int RANGE_PER_CLUSTER = 8;
    private static final int MAX_CLUSTERS = 8;

    private final List<ItemStack> eyes = new ArrayList<>();
    private List<BlockPos> sources = List.of();
    /** -1 while the structure is not built, else the number of extra clusters on the rim. */
    private int clusters = -1;
    private AspectList total = AspectList.EMPTY;

    public EssentiaLocusBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.ESSENTIA_LOCUS_BE.get(), pos, state);
    }

    public boolean formed() {
        return clusters >= 0;
    }

    public int maxEyes() {
        return 1 + Math.max(0, clusters);
    }

    public int range() {
        return RANGE + RANGE_PER_CLUSTER * Math.max(0, clusters);
    }

    private int maxJars() {
        return JARS + JARS_PER_CLUSTER * Math.max(0, clusters);
    }

    List<ItemStack> eyes() {
        return eyes;
    }

    void addEye(ItemStack eye) {
        eyes.add(eye);
        rescan();
    }

    ItemStack takeEye() {
        ItemStack eye = eyes.isEmpty() ? ItemStack.EMPTY : eyes.removeLast();
        rescan();
        return eye;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EssentiaLocusBlockEntity be) {
        if (level.getGameTime() % 40 == 0) {
            be.rescan();
        } else if (level.getGameTime() % 10 == 0) {
            be.recount();
        }
    }

    void rescan() {
        if (level == null || level.isClientSide()) {
            return;
        }
        clusters = structure(level, worldPosition);
        List<BlockPos> found = new ArrayList<>();
        if (formed()) {
            for (ItemStack eye : eyes) {
                GlobalPos at = eye.get(AMRegistry.LINK.get());
                if (at != null && at.dimension() == level.dimension()) {
                    group(at.pos(), found);
                }
            }
        }
        sources = found;
        recount();
        setChanged();
    }

    /** The watched container and every container joined to it face to face, up to the jar limit. */
    private void group(BlockPos start, List<BlockPos> into) {
        Set<BlockPos> seen = new HashSet<>(into);
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start);
        while (!open.isEmpty() && into.size() < maxJars()) {
            BlockPos p = open.poll();
            if (!seen.add(p) || !p.closerThan(worldPosition, range()) || !(container(p) instanceof IAspectSource)) {
                continue;
            }
            into.add(p);
            for (Direction d : Direction.values()) {
                open.add(p.relative(d));
            }
        }
    }

    private @Nullable IAspectContainer container(BlockPos p) {
        if (level == null || p.equals(worldPosition) || level.getBlockState(p).is(AMRegistry.ESSENTIA_AGGREGATOR.get())) {
            return null;
        }
        return level.getCapability(AspectCapabilities.CONTAINER, p, null);
    }

    private void recount() {
        AspectList sum = AspectList.EMPTY;
        for (BlockPos p : sources) {
            IAspectContainer c = container(p);
            if (c != null) {
                sum = sum.add(c.getAspects());
            }
        }
        total = sum;
    }

    /**
     * The extra clusters on the rim of a locus at {@code pos}, or -1 if it is not built: a cluster below it, the core
     * below that, the core's eight neighbours water sources, and the sixteen blocks around those the rim.
     */
    public static int structure(Level level, BlockPos pos) {
        BlockPos core = pos.below(2);
        if (!level.getBlockState(pos.below()).is(CLUSTER) || !level.getBlockState(core).is(CORE)) {
            return -1;
        }
        Set<BlockPos> extra = new HashSet<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos p = core.offset(dx, 0, dz);
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (rim ? !level.getBlockState(p).is(RIM)
                        : !(level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource())) {
                    return -1;
                }
                if (rim) {
                    for (Direction d : Direction.values()) {
                        if (level.getBlockState(p.relative(d)).is(CLUSTER)) {
                            extra.add(p.relative(d));
                        }
                    }
                }
            }
        }
        return Math.min(MAX_CLUSTERS, extra.size());
    }

    // ---- what the locus holds: every watched container's essentia added up ----------------------------------------

    @Override
    public AspectList getAspects() {
        return total;
    }

    @Override
    public void setAspects(AspectList aspects) {
    }

    @Override
    public boolean accepts(Holder<IAspect> aspect) {
        return false;
    }

    @Override
    public int fill(Holder<IAspect> aspect, int amount) {
        return amount;
    }

    /** Draws from the watched containers, one after another, until the amount is met. */
    @Override
    public boolean drain(Holder<IAspect> aspect, int amount) {
        if (level == null || !formed() || total.amountOf(aspect) < amount) {
            return false;
        }
        int left = amount;
        for (BlockPos p : sources) {
            IAspectContainer c = container(p);
            int take = c == null ? 0 : Math.min(left, c.amountOf(aspect));
            if (take > 0 && c.drain(aspect, take)) {
                left -= take;
                BlockEntity be = level.getBlockEntity(p);
                if (be != null) {
                    be.setChanged();
                }
            }
            if (left == 0) {
                break;
            }
        }
        recount();
        return left == 0;
    }

    /** The locus as an essentia source, offered through an aggregator on top of it. */
    public IAspectSource asSource() {
        EssentiaLocusBlockEntity locus = this;
        return new IAspectSource() {
            @Override
            public boolean isBlocked() {
                return !locus.formed();
            }

            @Override
            public AspectList getAspects() {
                return locus.getAspects();
            }

            @Override
            public void setAspects(AspectList aspects) {
            }

            @Override
            public boolean accepts(Holder<IAspect> aspect) {
                return false;
            }

            @Override
            public int fill(Holder<IAspect> aspect, int amount) {
                return amount;
            }

            @Override
            public boolean drain(Holder<IAspect> aspect, int amount) {
                return locus.drain(aspect, amount);
            }
        };
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (ItemStack eye : eyes) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, eye);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        eyes.clear();
        in.read("eyes", ItemStack.CODEC.listOf()).ifPresent(eyes::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("eyes", ItemStack.CODEC.listOf(), eyes);
    }
}
