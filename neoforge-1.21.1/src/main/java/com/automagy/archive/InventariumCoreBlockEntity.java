package com.automagy.archive;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.ObjIntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import com.automagy.port.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * The Inventarium core. It stands on an order crystal cluster over amber, ringed at its own height by eight archive
 * shelves; a wand forms it. It holds no items: unseen scribes tell it what their containers hold, and it adds those
 * up for tally boxes, requisition tomes and taskmasters.
 * <ul>
 * <li>Slots it can keep track of: every archive shelf ({@link ArchiveCapacity}) within three blocks.</li>
 * <li>Scribes it takes: one, and one more for each further order cluster in the 7x7x7 around it.</li>
 * <li>Link range: {@value #BASE_RANGE} blocks, {@value #RANGE_PER_CLUSTER} more for each further cluster.</li>
 * <li>Memory: each memory block in the 7x2x7 that starts two below it, touching amber, holds one taskmaster, tome or
 * dispersing mirror.</li>
 * </ul>
 */
public class InventariumCoreBlockEntity extends BlockEntity implements TallySource {
    public static final TagKey<Block> CLUSTER = TagKey.create(Registries.BLOCK, Automagy.id("inventarium_cluster"));
    public static final TagKey<Block> BASE = TagKey.create(Registries.BLOCK, Automagy.id("inventarium_base"));
    public static final int BASE_RANGE = 10;
    public static final int RANGE_PER_CLUSTER = 8;
    private static final int REACH = 3;
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();

    private final Map<Kind, LinkedHashSet<BlockPos>> nodes = new EnumMap<>(Kind.class);
    private final Map<BlockPos, List<ItemStackCount>> earmarks = new HashMap<>();
    private boolean formed;
    private boolean preferred;
    private int capacity;
    private int clusters;
    private int memory;

    public InventariumCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.INVENTARIUM_CORE_BE.get(), pos, state);
        for (Kind k : Kind.values()) {
            nodes.put(k, new LinkedHashSet<>());
        }
    }

    /** What links to a core. Every kind but the scribe takes a memory block. */
    public enum Kind implements StringRepresentable {
        SCRIBE, TASKMASTER, TOME, DISPERSER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** An item kind and an amount; earmarked deliveries are kept as these. */
    public record ItemStackCount(ItemResource item, int count) {
    }

    public boolean formed() {
        return formed;
    }

    public boolean preferred() {
        return preferred;
    }

    public int capacity() {
        return capacity;
    }

    public int scribeLimit() {
        return Math.max(1, clusters);
    }

    public int memory() {
        return memory;
    }

    public int linkRange() {
        return BASE_RANGE + RANGE_PER_CLUSTER * Math.max(0, clusters - 1);
    }

    public Set<BlockPos> nodes(Kind kind) {
        return Collections.unmodifiableSet(nodes.get(kind));
    }

    private int memoryUsed() {
        return nodes.get(Kind.TASKMASTER).size() + nodes.get(Kind.TOME).size() + nodes.get(Kind.DISPERSER).size();
    }

    /** Takes a node if there is room for it; an already linked node is taken again. */
    boolean link(Kind kind, BlockPos pos) {
        Set<BlockPos> set = nodes.get(kind);
        if (set.contains(pos)) {
            return true;
        }
        boolean room = kind == Kind.SCRIBE ? set.size() < scribeLimit() : memoryUsed() < memory;
        if (room) {
            set.add(pos.immutable());
            setChanged();
        }
        return room;
    }

    void unlink(Kind kind, BlockPos pos) {
        if (nodes.get(kind).remove(pos)) {
            earmarks.remove(pos);
            setChanged();
        }
    }

    public boolean has(Kind kind, BlockPos pos) {
        return nodes.get(kind).contains(pos);
    }

    // ---- structure

    /** Checks the structure and counts its shelves, clusters and memory; true if it stands. */
    public boolean form() {
        if (!check()) {
            setFormation(false, 0, 0, 0);
        }
        setChanged();
        return formed;
    }

    /**
     * The one place the formation changes. Capacity, clusters and memory only mean something while formed: an
     * unformed core always has them at zero, and they are saved only with a formed core.
     */
    private void setFormation(boolean formed, int capacity, int clusters, int memory) {
        this.formed = formed;
        this.capacity = formed ? capacity : 0;
        this.clusters = formed ? clusters : 0;
        this.memory = formed ? memory : 0;
    }

    private boolean check() {
        if (level == null || !level.getBlockState(worldPosition.below()).is(CLUSTER)
                || !level.getBlockState(worldPosition.below(2)).is(BASE)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && ArchiveCapacity.of(level.getBlockState(worldPosition.offset(dx, 0, dz))) <= 0) {
                    return false;
                }
            }
        }
        int slots = 0;
        int found = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-REACH, -REACH, -REACH), worldPosition.offset(REACH, REACH, REACH))) {
            BlockState s = level.getBlockState(p);
            slots += ArchiveCapacity.of(s);
            if (s.is(CLUSTER)) {
                found++;
            }
        }
        int mem = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-REACH, -2, -REACH), worldPosition.offset(REACH, -1, REACH))) {
            if (level.getBlockState(p).is(AMRegistry.MEMORY_BLOCK.get()) && touchesAmber(p)) {
                mem++;
            }
        }
        setFormation(true, slots, found, mem);
        return true;
    }

    private boolean touchesAmber(BlockPos p) {
        for (Direction d : Direction.values()) {
            if (level.getBlockState(p.relative(d)).is(BASE)) {
                return true;
            }
        }
        return false;
    }

    /** The core whose ring holds the shelf at this position, if any. */
    public static @Nullable InventariumCoreBlockEntity atShelf(Level level, BlockPos shelf) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && level.getBlockEntity(shelf.offset(dx, 0, dz)) instanceof InventariumCoreBlockEntity core
                        && core.formed) {
                    return core;
                }
            }
        }
        return null;
    }

    /** The loaded, formed cores within link range of a position: the preferred ones first, then the nearest. */
    static List<InventariumCoreBlockEntity> inRange(Level level, BlockPos pos) {
        List<InventariumCoreBlockEntity> out = new ArrayList<>();
        for (BlockPos p : LOADED.getOrDefault(level, Set.of())) {
            if (level.getBlockEntity(p) instanceof InventariumCoreBlockEntity core && core.formed
                    && p.closerThan(pos, core.linkRange())) {
                out.add(core);
            }
        }
        out.sort((a, b) -> a.preferred != b.preferred ? (a.preferred ? -1 : 1)
                : Double.compare(a.worldPosition.distSqr(pos), b.worldPosition.distSqr(pos)));
        return out;
    }

    /** A wand on the core: forms it if it is not formed, else makes it the preferred core and reports its numbers. */
    public Component wand() {
        if (!formed && !form()) {
            return Component.translatable("automagia.inventarium.structure");
        }
        form();
        for (BlockPos p : LOADED.getOrDefault(level, Set.of())) {
            if (!p.equals(worldPosition) && level.getBlockEntity(p) instanceof InventariumCoreBlockEntity other && other.preferred) {
                other.preferred = false;
                other.setChanged();
            }
        }
        preferred = true;
        return Component.translatable("automagia.inventarium.status", usedSlots(null), capacity, nodes.get(Kind.SCRIBE).size(),
                scribeLimit(), memoryUsed(), memory);
    }

    // ---- what the scribes read

    /** Slots taken by every linked scribe but the one given. */
    int usedSlots(@Nullable UnseenScribeBlockEntity except) {
        int used = 0;
        for (UnseenScribeBlockEntity s : scribes()) {
            if (s != except) {
                used += s.usedSlots();
            }
        }
        return used;
    }

    List<UnseenScribeBlockEntity> scribes() {
        List<UnseenScribeBlockEntity> out = new ArrayList<>();
        for (BlockPos p : nodes.get(Kind.SCRIBE)) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof UnseenScribeBlockEntity s) {
                out.add(s);
            }
        }
        return out;
    }

    /** Everything the Inventarium holds, by item. */
    public Map<ItemResource, Integer> totals() {
        Map<ItemResource, Integer> out = new HashMap<>();
        for (UnseenScribeBlockEntity s : scribes()) {
            s.read().forEach((k, n) -> out.merge(k, n, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b)));
        }
        return out;
    }

    @Override
    public void tally(ObjIntConsumer<ItemResource> out) {
        if (formed) {
            totals().forEach(out::accept);
        }
    }

    // ---- deliveries through a dispersing mirror

    public @Nullable BlockPos disperser() {
        for (BlockPos p : nodes.get(Kind.DISPERSER)) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof DispersingMirrorBlockEntity) {
                return p;
            }
        }
        return null;
    }

    /** Remembers that this many of an item, on its way to a dispersing mirror, belong to a tome. */
    void earmark(BlockPos tome, ItemResource item, int count) {
        earmarks.computeIfAbsent(tome.immutable(), k -> new ArrayList<>()).add(new ItemStackCount(item, count));
    }

    /** Which tome an item arriving at a dispersing mirror was ordered by, taking it off that tome's earmarks. */
    @Nullable BlockPos claim(ItemResource item, int count) {
        for (var e : earmarks.entrySet()) {
            List<ItemStackCount> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                ItemStackCount m = list.get(i);
                if (m.item().equals(item)) {
                    if (m.count() <= count) {
                        list.remove(i);
                    } else {
                        list.set(i, new ItemStackCount(item, m.count() - count));
                    }
                    return e.getKey();
                }
            }
        }
        return null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InventariumCoreBlockEntity be) {
        if (level.getGameTime() % 40 == pos.asLong() % 40 && be.formed) {
            be.form();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            Set<BlockPos> set = LOADED.get(level);
            if (set != null) {
                set.remove(worldPosition);
            }
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        setFormation(in.getBooleanOr("formed", false), in.getIntOr("capacity", 0), in.getIntOr("clusters", 0),
                in.getIntOr("memory", 0));
        preferred = in.getBooleanOr("preferred", false);
        for (Kind k : Kind.values()) {
            LinkedHashSet<BlockPos> set = nodes.get(k);
            set.clear();
            in.read(k.getSerializedName(), BlockPos.CODEC.listOf()).ifPresent(set::addAll);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.putBoolean("formed", formed);
        out.putBoolean("preferred", preferred);
        if (formed) {
            out.putInt("capacity", capacity);
            out.putInt("clusters", clusters);
            out.putInt("memory", memory);
        }
        for (Kind k : Kind.values()) {
            out.store(k.getSerializedName(), BlockPos.CODEC.listOf(), List.copyOf(nodes.get(k)));
        }
    }
}
