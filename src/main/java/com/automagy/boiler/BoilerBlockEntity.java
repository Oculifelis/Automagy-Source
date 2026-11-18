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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The alchemical boiler: the alchemical furnace for liquids. It burns furnace fuel (alumentum makes it faster), boils
 * a bucket of its liquid at a time down into essentia, and sends that essentia up into the alembics stacked on it.
 * A liquid's essentia comes from the fluid data map {@code automagia:boiler_aspects}, or else from its bucket's
 * aspects less an empty bucket's.
 */
public class BoilerBlockEntity extends BlockEntity {
    public static final DataMapType<Fluid, AspectList> ASPECTS = DataMapType.builder(Automagy.id("boiler_aspects"),
            Registries.FLUID, AspectList.CODEC).build();
    private static final int BUCKET = 1000;
    private static final int CAPACITY = 4 * BUCKET;
    /** Ticks to boil one bucket, halved while alumentum burns. */
    private static final int BOIL_TIME = 400;
    /** How many alembics up it sends to. */
    private static final int ALEMBICS = 5;

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return !aspectsOf(resource.getFluid()).isEmpty();
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            setChanged();
        }
    };
    private final ItemStacksResourceHandler fuel = new ItemStacksResourceHandler(1) {
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return level != null && resource.toStack().getBurnTime(RecipeType.SMELTING, level.fuelValues()) > 0;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previous) {
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

    public ResourceHandler<FluidResource> tank() {
        return tank;
    }

    public ResourceHandler<ItemResource> fuel() {
        return fuel;
    }

    public FluidStack fluid() {
        return FluidUtil.getStack(tank, 0);
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
                try (Transaction tx = Transaction.openRoot()) {
                    be.tank.extract(0, FluidResource.of(be.fluid()), BUCKET, tx);
                    tx.commit();
                }
            }
        }
        if (lit != be.burn > 0) {
            level.setBlock(pos, state.setValue(BoilerBlock.LIT, be.burn > 0), Block.UPDATE_ALL);
        }
        be.setChanged();
    }

    private void refuel(Level level) {
        ItemStack stack = fuel.getResource(0).toStack(fuel.getAmountAsInt(0));
        int time = stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING, level.fuelValues());
        if (time <= 0) {
            return;
        }
        fast = stack.is(TTItems.ALUMENTUM.get());
        burn = time;
        try (Transaction tx = Transaction.openRoot()) {
            fuel.extract(0, fuel.getResource(0), 1, tx);
            tx.commit();
        }
        ItemStack rest = stack.getItem().getCraftingRemainder(stack) == null ? ItemStack.EMPTY : stack.getItem().getCraftingRemainder(stack).create();
        if (!rest.isEmpty() && fuel.getAmountAsInt(0) == 0) {
            fuel.set(0, ItemResource.of(rest), rest.getCount());
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

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // the liquid is lost with the boiler; its fuel drops
        if (level != null && fuel.getAmountAsInt(0) > 0) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    fuel.getResource(0).toStack(fuel.getAmountAsInt(0)));
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
        fuel.deserialize(in.childOrEmpty("fuel"));
        boiling = in.read("boiling", AspectList.CODEC).orElse(AspectList.EMPTY);
        burn = in.getIntOr("burn", 0);
        boil = in.getIntOr("boil", 0);
        fast = in.getBooleanOr("fast", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out.child("tank"));
        fuel.serialize(out.child("fuel"));
        out.store("boiling", AspectList.CODEC, boiling);
        out.putInt("burn", burn);
        out.putInt("boil", boil);
        out.putBoolean("fast", fast);
    }
}
