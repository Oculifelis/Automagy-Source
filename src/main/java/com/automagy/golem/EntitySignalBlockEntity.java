package com.automagy.golem;

import com.automagy.AMRegistry;
import com.automagy.SavedEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The Signum Entia. It counts the entities its Oculo Anima watches for and gives that count as redstone. The area is
 * at most 11 blocks across; a solid block on one face cuts the area off on that side and pushes it out the other way,
 * blocks on both faces of an axis close that axis to the device's own row. Glass and replaceable blocks don't count.
 */
public class EntitySignalBlockEntity extends BlockEntity implements com.automagy.port.PreRemove {
    private static final int REACH = 10;

    /** Which ages count among creatures that have one. */
    public enum Ages { ALL, ADULTS, CHILDREN }

    private ItemStack eye = ItemStack.EMPTY;
    private Ages ages = Ages.ALL;
    private int count;

    public EntitySignalBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.ENTITY_SIGNAL_BE.get(), pos, state);
    }

    public ItemStack eye() {
        return eye;
    }

    void setEye(ItemStack stack) {
        eye = stack;
        setChanged();
    }

    public Ages ages() {
        return ages;
    }

    void cycleAges() {
        ages = SavedEnum.next(ages);
        setChanged();
    }

    public int signal() {
        return Math.min(15, count);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EntitySignalBlockEntity be) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        EntityWatch watch = be.eye.get(AMRegistry.ENTITY_WATCH.get());
        int n = watch == null ? 0 : level.getEntities((Entity) null, be.area(level, pos), e -> e.isAlive() && watch.matches(e) && be.ageOk(e)).size();
        if (n != be.count) {
            be.count = n;
            level.updateNeighborsAt(pos, state.getBlock());
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private boolean ageOk(Entity e) {
        if (ages == Ages.ALL || !(e instanceof AgeableMob mob)) {
            return true;
        }
        return mob.isBaby() == (ages == Ages.CHILDREN);
    }

    private AABB area(Level level, BlockPos pos) {
        int[] lo = new int[3];
        int[] hi = new int[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            boolean neg = blocks(level, pos.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)));
            boolean posBlocked = blocks(level, pos.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)));
            int i = axis.ordinal();
            if (neg && posBlocked) {
                lo[i] = 0;
                hi[i] = 0;
            } else if (neg) {
                lo[i] = 0;
                hi[i] = REACH;
            } else if (posBlocked) {
                lo[i] = -REACH;
                hi[i] = 0;
            } else {
                lo[i] = -REACH / 2;
                hi[i] = REACH / 2;
            }
        }
        return new AABB(pos.getX() + lo[0], pos.getY() + lo[1], pos.getZ() + lo[2],
                pos.getX() + hi[0] + 1, pos.getY() + hi[1] + 1, pos.getZ() + hi[2] + 1);
    }

    private static boolean blocks(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && !s.canBeReplaced() && !s.is(BlockTags.IMPERMEABLE) && s.getFluidState().isEmpty();
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !eye.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, eye);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        eye = in.read("eye", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        ages = SavedEnum.read(in, "ages", Ages.ALL);
        count = in.getIntOr("count", 0);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.store("eye", ItemStack.OPTIONAL_CODEC, eye);
        SavedEnum.write(out, "ages", ages);
        out.putInt("count", count);
    }
}
