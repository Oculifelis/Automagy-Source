package com.automagy.boiler;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntityAlembic;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The alchemical boiler: the alchemical furnace for liquids. It burns furnace fuel (alumentum makes it faster), boils
 * a bucket of its liquid at a time down into essentia, and sends that essentia up into the alembics stacked on it.
 * A liquid's essentia comes from the fluid data map {@code automagia:boiler_aspects}, or else from its bucket's
 * aspects less an empty bucket's.
 */
public class BoilerBlockEntity extends BlockEntity implements com.automagy.port.PreRemove {
    public static final DataMapType<Fluid, AspectList> ASPECTS = DataMapType.builder(Automagy.id("boiler_aspects"),
            Registries.FLUID, AspectList.CODEC).build();
    private static final int BUCKET = 1000;
    private static final int CAPACITY = 4 * BUCKET;
    /** Ticks to boil one bucket, halved while alumentum burns. */
    private static final int BOIL_TIME = 400;
    /** How many alembics up it sends to. */
    private static final int ALEMBICS = 5;

    private final FluidTank tank = new FluidTank(CAPACITY, f -> !aspectsOf(f.getFluid()).isEmpty()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getBurnTime(RecipeType.SMELTING) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private AspectList boiling = AspectList.EMPTY;
    private int burn;
    private int boil;
    private boolean fast;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.BOILER_BE.get(), pos, state);
    }

    public static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(ASPECTS);
    }

    public static AspectList aspectsOf(Fluid fluid) {
        AspectList set = fluid.builtInRegistryHolder().getData(ASPECTS);
        if (set != null) {
            return set;
        }
        AspectList full = AspectIndexAccess.of(fluid.getBucket());
        AspectList empty = AspectIndexAccess.of(Items.BUCKET);
        for (AspectInstance a : empty.entries()) {
            full = full.reduce(a.aspect(), a.amount());
        }
        return fluid.getBucket() == Items.AIR ? AspectList.EMPTY : full;
    }

    public IFluidHandler tank() {
        return tank;
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    public FluidStack fluid() {
        return tank.getFluid();
    }

    public int essentia() {
        return boiling.totalAmount();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        boolean lit = be.burn > 0;
        if (be.burn > 0) {
            be.burn--;
        }
        // essentia rises into the alembics one at a time
        if (!be.boiling.isEmpty() && level.getGameTime() % 5 == 0) {
            be.vent(level, pos);
        }
        if (be.boiling.isEmpty() && be.fluid().getAmount() >= BUCKET) {
            if (be.burn == 0) {
                be.refuel(level);
            }
            if (be.burn > 0 && ++be.boil >= (be.fast ? BOIL_TIME / 2 : BOIL_TIME)) {
                be.boil = 0;
                be.boiling = aspectsOf(be.fluid().getFluid());
                be.tank.drain(BUCKET, IFluidHandler.FluidAction.EXECUTE);
            }
        }
        if (lit != be.burn > 0) {
            level.setBlock(pos, state.setValue(BoilerBlock.LIT, be.burn > 0), Block.UPDATE_ALL);
        }
        be.setChanged();
    }

    private void refuel(Level level) {
        ItemStack stack = fuel.getStackInSlot(0).copy();
        int time = stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
        if (time <= 0) {
            return;
        }
        fast = stack.is(TTItems.ALUMENTUM.get());
        burn = time;
        fuel.extractItem(0, 1, false);
        ItemStack rest = stack.getCraftingRemainingItem();
        if (!rest.isEmpty() && fuel.getStackInSlot(0).isEmpty()) {
            fuel.setStackInSlot(0, rest.copy());
        }
    }

    private void vent(Level level, BlockPos pos) {
        AspectInstance next = boiling.entries().getFirst();
        for (int i = 1; i <= ALEMBICS; i++) {
            if (!(level.getBlockEntity(pos.above(i)) instanceof BlockEntityAlembic alembic)) {
                return;
            }
            if (alembic.fill(next.aspect(), 1) == 0) {
                boiling = boiling.reduce(next.aspect(), 1);
                return;
            }
        }
    }

    public int signal() {
        return fluid().isEmpty() ? 0 : 1 + Math.min(14, fluid().getAmount() * 14 / CAPACITY);
    }

    /** Called from the block's onRemove. */
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // the liquid is lost with the boiler; its fuel drops
        if (level != null && !fuel.getStackInSlot(0).isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    fuel.getStackInSlot(0).copy());
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        tank.readFromNBT(reg$, tag$.getCompound("tank"));
        fuel.deserializeNBT(reg$, tag$.getCompound("fuel"));
        boiling = in.read("boiling", AspectList.CODEC).orElse(AspectList.EMPTY);
        burn = in.getIntOr("burn", 0);
        boil = in.getIntOr("boil", 0);
        fast = in.getBooleanOr("fast", false);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        tag$.put("tank", tank.writeToNBT(reg$, new net.minecraft.nbt.CompoundTag()));
        tag$.put("fuel", fuel.serializeNBT(reg$));
        out.store("boiling", AspectList.CODEC, boiling);
        out.putInt("burn", burn);
        out.putInt("boil", boil);
        out.putBoolean("fast", fast);
    }
}
