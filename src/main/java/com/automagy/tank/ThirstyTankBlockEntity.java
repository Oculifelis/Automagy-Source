package com.automagy.tank;

import com.automagy.AMRegistry;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.block.BucketPickup;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * The thirsty tank: drinks the fluid source blocks that touch it, or that a chain of the same fluid leads to, and
 * holds them for buckets, pipes and golems. Next to a water source it never runs dry of water. Up to six glyphs, one
 * per side, change what it does (see {@link Glyph}).
 */
public class ThirstyTankBlockEntity extends BlockEntity implements com.automagy.port.PreRemove {
    private static final int BUCKET = 1000;
    public static final int BASE_CAPACITY = 8 * BUCKET;
    /** Ticks between drinks with no guzzler glyph. */
    private static final int DRINK_INTERVAL = 20;
    /** How many blocks of a fluid it follows to find a source. */
    private static final int REACH = 32;
    /** mB of rain caught a second when the sky is open above it. */
    private static final int RAIN = 25;

    private final Map<Direction, ItemStack> glyphs = new EnumMap<>(Direction.class);
    private final Map<UUID, Long> milked = new HashMap<>();
    private final Tank tank = new Tank();
    private int cooldown;

    public ThirstyTankBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.THIRSTY_TANK_BE.get(), pos, state);
    }

    public IFluidHandler tank() {
        return tank;
    }

    public FluidStack fluid() {
        return tank.getFluid();
    }

    // ---- glyphs -----------------------------------------------------------------------------------------------------

    public ItemStack glyph(Direction side) {
        return glyphs.getOrDefault(side, ItemStack.EMPTY);
    }

    void setGlyph(Direction side, ItemStack stack) {
        if (stack.isEmpty()) {
            glyphs.remove(side);
        } else {
            glyphs.put(side, stack);
        }
        setChanged();
    }

    /** The sum of the glyphs' amounts for one effect, or -1 when no glyph has it. */
    private int total(Glyph.Effect effect) {
        int sum = -1;
        for (ItemStack stack : glyphs.values()) {
            Glyph g = Glyph.of(stack);
            if (g != null && g.effect() == effect) {
                sum = Math.max(sum, 0) + g.amount();
            }
        }
        return sum;
    }

    private boolean has(Glyph.Effect effect) {
        return total(effect) >= 0;
    }

    public int capacity() {
        return BASE_CAPACITY + Math.max(0, total(Glyph.Effect.RESERVOIR));
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, ThirstyTankBlockEntity be) {
        long time = level.getGameTime();
        if (time % 20 == 0) {
            be.everySecond(level, pos);
        }
        if (--be.cooldown > 0) {
            return;
        }
        int guzzle = Math.max(0, be.total(Glyph.Effect.GUZZLER));
        be.cooldown = Math.max(1, DRINK_INTERVAL * 100 / (100 + guzzle));
        if (be.has(Glyph.Effect.TEMPERANCE) && !level.hasNeighborSignal(pos)) {
            return;
        }
        if (be.has(Glyph.Effect.BOVINE)) {
            be.milk(level, pos, time);
        } else {
            be.drink(level, pos);
        }
    }

    private void everySecond(Level level, BlockPos pos) {
        int consume = total(Glyph.Effect.CONSUMPTION);
        if (consume > 0 && !fluid().isEmpty()) {
            tank.consume(consume);
        }
        if (RAIN > 0 && level.isRainingAt(pos.above())) {
            fill(Fluids.WATER, RAIN);
        }
        for (Map.Entry<Direction, ItemStack> e : glyphs.entrySet()) {
            Glyph g = Glyph.of(e.getValue());
            if (g == null || (g.effect() != Glyph.Effect.SIPHONING && g.effect() != Glyph.Effect.ENVY)) {
                continue;
            }
            Direction side = e.getKey();
            IFluidHandler other = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.relative(side), side.getOpposite());
            if (other == null) {
                continue;
            }
            if (g.effect() == Glyph.Effect.SIPHONING) {
                FluidUtil.tryFluidTransfer(other, tank, g.amount(), true);
            } else {
                FluidUtil.tryFluidTransfer(tank, other, g.amount(), true);
            }
        }
    }

    /** Drinks one source block: the first one touching it, else one a chain of the same fluid leads to. */
    private void drink(Level level, BlockPos pos) {
        Fluid held = fluid().isEmpty() ? null : fluid().getFluid();
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            FluidState first = level.getFluidState(next);
            if (first.isEmpty() || (held != null && !first.getType().isSame(held))) {
                continue;
            }
            Fluid kind = first.getType();
            if (first.isSource() && first.getType().isSame(Fluids.WATER)) {
                // beside water it never runs dry: it drinks without taking the source
                if (fill(Fluids.WATER, BUCKET) > 0) {
                    return;
                }
                continue;
            }
            BlockPos source = findSource(level, next, kind);
            if (source != null && room(kind) > 0) {
                BlockState block = level.getBlockState(source);
                FluidStack want = new FluidStack(level.getFluidState(source).getType(), BUCKET);
                if (block.getBlock() instanceof BucketPickup pickup && tank.fill(want, IFluidHandler.FluidAction.SIMULATE) == BUCKET) {
                    ItemStack bucket = pickup.pickupBlock(null, level, source, block);
                    if (!bucket.isEmpty()) {
                        tank.fill(want, IFluidHandler.FluidAction.EXECUTE);
                        return;
                    }
                }
            }
        }
    }

    private @Nullable BlockPos findSource(Level level, BlockPos start, Fluid kind) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(start);
        seen.add(start);
        while (!open.isEmpty() && seen.size() <= REACH) {
            BlockPos at = open.poll();
            FluidState state = level.getFluidState(at);
            if (state.isSource()) {
                return at;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = at.relative(dir);
                if (!next.equals(worldPosition) && level.isLoaded(next) && seen.add(next) && level.getFluidState(next).getType().isSame(kind)) {
                    open.add(next);
                }
            }
        }
        return null;
    }

    private void milk(Level level, BlockPos pos, long time) {
        Fluid milk = NeoForgeMod.MILK.get();
        milked.values().removeIf(until -> until <= time);
        for (Map.Entry<Direction, ItemStack> e : glyphs.entrySet()) {
            Glyph g = Glyph.of(e.getValue());
            if (g == null || g.effect() != Glyph.Effect.BOVINE) {
                continue;
            }
            AABB area = new AABB(pos.relative(e.getKey())).expandTowards(e.getKey().getStepX() * 4, e.getKey().getStepY() * 4, e.getKey().getStepZ() * 4).inflate(1);
            for (Cow cow : level.getEntitiesOfClass(Cow.class, area, c -> c.isAlive() && !c.isBaby() && !milked.containsKey(c.getUUID()))) {
                if (room(milk) < BUCKET) {
                    return;
                }
                fill(milk, BUCKET);
                milked.put(cow.getUUID(), time + Math.max(20, g.amount()));
                return;
            }
        }
    }

    private int room(Fluid fluid) {
        if (has(Glyph.Effect.VOID) && (fluid().isEmpty() || fluid().getFluid().isSame(fluid))) {
            return Integer.MAX_VALUE;
        }
        return fluid().isEmpty() ? capacity() : fluid().getFluid().isSame(fluid) ? capacity() - fluid().getAmount() : 0;
    }

    private int fill(Fluid fluid, int amount) {
        return tank.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    public int signal() {
        return fluid().isEmpty() ? 0 : 1 + Math.min(14, fluid().getAmount() * 14 / capacity());
    }

    // ---- the tank ---------------------------------------------------------------------------------------------------

    private final class Tank extends FluidTank {
        Tank() {
            super(BASE_CAPACITY);
        }

        @Override
        public int getCapacity() {
            return capacity();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            this.capacity = capacity();
            boolean same = FluidStack.isSameFluidSameComponents(resource, fluid);
            int in = super.fill(resource, action);
            // a void glyph swallows the rest of whatever it already holds
            return has(Glyph.Effect.VOID) && (in > 0 || same) ? resource.getAmount() : in;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, fluid)) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            if (has(Glyph.Effect.PRESERVATION)) {
                amount = Math.min(amount, Math.max(0, fluid.getAmount() - 1));
            }
            return super.drain(amount, action);
        }

        void consume(int amount) {
            super.drain(Math.min(amount, fluid.getAmount()), FluidAction.EXECUTE);
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null) {
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            glyphs.values().forEach(g -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, g));
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        tank.readFromNBT(reg$, tag$);
        glyphs.clear();
        for (Direction dir : Direction.values()) {
            in.read("glyph_" + dir.getSerializedName(), ItemStack.CODEC).ifPresent(g -> glyphs.put(dir, g));
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        tank.writeToNBT(reg$, tag$);
        glyphs.forEach((dir, g) -> out.store("glyph_" + dir.getSerializedName(), ItemStack.CODEC, g));
    }
}
