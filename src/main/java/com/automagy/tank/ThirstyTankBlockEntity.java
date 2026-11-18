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
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The thirsty tank: drinks the fluid source blocks that touch it, or that a chain of the same fluid leads to, and
 * holds them for buckets, pipes and golems. Next to a water source it never runs dry of water. Up to six glyphs, one
 * per side, change what it does (see {@link Glyph}).
 */
public class ThirstyTankBlockEntity extends BlockEntity {
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

    public ResourceHandler<FluidResource> tank() {
        return tank;
    }

    public FluidStack fluid() {
        return FluidUtil.getStack(tank, 0);
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
            try (Transaction tx = Transaction.openRoot()) {
                tank.drain(consume, tx);
                tx.commit();
            }
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
            ResourceHandler<FluidResource> other = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());
            if (other == null) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                if (g.effect() == Glyph.Effect.SIPHONING) {
                    ResourceHandlerUtil.move(tank, other, r -> true, g.amount(), tx);
                } else {
                    ResourceHandlerUtil.move(other, tank, r -> true, g.amount(), tx);
                }
                tx.commit();
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
                try (Transaction tx = Transaction.openRoot()) {
                    if (!FluidUtil.tryPickupFluid(tank, null, level, source, dir.getOpposite(), tx).isEmpty()) {
                        tx.commit();
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
        try (Transaction tx = Transaction.openRoot()) {
            int in = tank.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return in;
        }
    }

    public int signal() {
        return fluid().isEmpty() ? 0 : 1 + Math.min(14, fluid().getAmount() * 14 / capacity());
    }

    // ---- the tank ---------------------------------------------------------------------------------------------------

    private final class Tank extends FluidStacksResourceHandler {
        Tank() {
            super(1, BASE_CAPACITY);
        }

        @Override
        protected int getCapacity(int index, FluidResource resource) {
            return capacity();
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            int in = super.insert(index, resource, amount, tx);
            // a void glyph swallows the rest of whatever it already holds
            return has(Glyph.Effect.VOID) && (in > 0 || resource.matches(stacks.get(0))) ? amount : in;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (has(Glyph.Effect.PRESERVATION)) {
                amount = Math.min(amount, Math.max(0, stacks.get(0).getAmount() - 1));
            }
            return super.extract(index, resource, amount, tx);
        }

        void drain(int amount, TransactionContext tx) {
            FluidStack now = stacks.get(0);
            super.extract(0, FluidResource.of(now), Math.min(amount, now.getAmount()), tx);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            setChanged();
            if (level != null) {
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            glyphs.values().forEach(g -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, g));
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in);
        glyphs.clear();
        for (Direction dir : Direction.values()) {
            in.read("glyph_" + dir.getSerializedName(), ItemStack.CODEC).ifPresent(g -> glyphs.put(dir, g));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out);
        glyphs.forEach((dir, g) -> out.store("glyph_" + dir.getSerializedName(), ItemStack.CODEC, g));
    }
}
