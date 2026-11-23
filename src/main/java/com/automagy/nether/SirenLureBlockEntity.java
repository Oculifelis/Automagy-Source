package com.automagy.nether;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;

/**
 * The Siren's Lure: set into a vertical line of obsidian totems (at least two besides itself, blocks of the
 * {@code automagia:lure_totems} tag), it draws creatures within 8 blocks across (16 with a jar of desiderium within 4)
 * and within the totem line's height plus up to 4 above and below. The tallest lure in reach wins. A wand switches it
 * on and off. {@link SirenLures} moves the creatures.
 */
public class SirenLureBlockEntity extends BlockEntity {
    public static final TagKey<Block> TOTEMS = TagKey.create(Registries.BLOCK, Automagy.id("lure_totems"));
    private static final Map<Level, Set<SirenLureBlockEntity>> LOADED = new WeakHashMap<>();
    private static final int MIN_TOTEMS = 2;
    private static final int MAX_COLUMN = 16;

    private boolean active = true;
    /** Lowest and highest block of the totem line, and whether it stands; refreshed every two seconds. */
    private int bottom, top, totems;
    private boolean boosted;

    public SirenLureBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.SIREN_LURE_BE.get(), pos, state);
    }

    static Set<SirenLureBlockEntity> loaded(Level level) {
        Set<SirenLureBlockEntity> set = LOADED.get(level);
        return set == null ? Set.of() : set;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
            survey();
        }
    }

    @Override
    public void setRemoved() {
        loaded(level).remove(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        loaded(level).remove(this);
        super.onChunkUnloaded();
    }

    public boolean working() {
        return active && totems >= MIN_TOTEMS && !isRemoved();
    }

    int totems() {
        return totems;
    }

    int radius() {
        return boosted ? 16 : 8;
    }

    int minY() {
        return bottom - Math.min(4, top - bottom);
    }

    int maxY() {
        return top + Math.min(4, top - bottom);
    }

    boolean toggle() {
        active = !active;
        setChanged();
        return active;
    }

    private void survey() {
        int down = 0, up = 0;
        while (down < MAX_COLUMN && level.getBlockState(worldPosition.below(down + 1)).is(TOTEMS)) {
            down++;
        }
        while (up < MAX_COLUMN && level.getBlockState(worldPosition.above(up + 1)).is(TOTEMS)) {
            up++;
        }
        totems = down + up;
        bottom = worldPosition.getY() - down;
        top = worldPosition.getY() + up;
        boosted = Essentia.holds(Essentia.around(level, worldPosition, 4), TTAspects.DESIDERIUM);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SirenLureBlockEntity be) {
        if (level.getGameTime() % 40 == Math.floorMod(pos.asLong(), 40)) {
            be.survey();
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        active = in.getBooleanOr("active", true);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.putBoolean("active", active);
    }
}
