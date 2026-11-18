package com.automagy.golem;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.leclowndu93150.thaumaturge.content.equipment.hover.ThaumostaticHarnessItem;
import com.leclowndu93150.thaumaturge.content.essentia.item.ComponentEssentia;
import com.leclowndu93150.thaumaturge.content.essentia.jar.JarItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The thaumostatic controller, the working part of a thaumostatic spire: a column with a block of the
 * {@code automagia:spire_base} tag (chiseled quartz) at the bottom, pylons and this controller in any order above it,
 * and a block of the {@code automagia:spire_top} tag (thaumium) on top under open sky. A wand forms it. Tubes feed it
 * potentia, which it sends to the jar of every worn thaumostatic harness within its range, drawing vis from the aura
 * as it does; aer piped in widens the range and volatus speeds the flow, both more with each pylon up to six. Each
 * side takes one aspect, set with a wand.
 */
public class SpireControllerBlockEntity extends BlockEntity implements IEssentiaTransport {
    public static final TagKey<Block> BASE = TagKey.create(Registries.BLOCK, Automagy.id("spire_base"));
    public static final TagKey<Block> TOP = TagKey.create(Registries.BLOCK, Automagy.id("spire_top"));
    public static final List<ResourceKey<IAspect>> INPUTS = List.of(TTAspects.POTENTIA, TTAspects.AER, TTAspects.VOLATUS);
    private static final int MIN_PYLONS = 2;
    private static final int MAX_PYLONS = 6;
    private static final int POTENTIA_CAP = 64;
    private static final int BOOST_CAP = 16;
    /** How long one aer or volatus keeps its boost going, in ticks. */
    private static final int BOOST_TICKS = 200;
    private static final float VIS_PER_POTENTIA = 0.25f;

    private final Map<Direction, Integer> sides = new EnumMap<>(Direction.class);
    private int potentia, aer, volatus;
    private int aerTime, volatusTime;
    /** Pylons in the formed spire, 0 when not formed. */
    private int pylons;

    public SpireControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.SPIRE_CONTROLLER_BE.get(), pos, state);
    }

    public boolean formed() {
        return pylons > 0;
    }

    /** Tries to form the spire; true if it stands. */
    boolean form() {
        pylons = count(level, worldPosition);
        setChanged();
        return formed();
    }

    /** Pylons in a well-built spire through {@code pos}, or 0. */
    static int count(Level level, BlockPos pos) {
        BlockPos p = pos.below();
        int n = 0;
        while (isPylon(level, p)) {
            n++;
            p = p.below();
        }
        if (!level.getBlockState(p).is(BASE)) {
            return 0;
        }
        p = pos.above();
        while (isPylon(level, p)) {
            n++;
            p = p.above();
        }
        if (!level.getBlockState(p).is(TOP) || !level.canSeeSky(p.above()) || n < MIN_PYLONS) {
            return 0;
        }
        return n;
    }

    private static boolean isPylon(Level level, BlockPos p) {
        return level.getBlockState(p).is(AMRegistry.SPIRE_PYLON.get());
    }

    ResourceKey<IAspect> input(Direction side) {
        return INPUTS.get(sides.getOrDefault(side, 0));
    }

    ResourceKey<IAspect> cycle(Direction side) {
        sides.put(side, (sides.getOrDefault(side, 0) + 1) % INPUTS.size());
        setChanged();
        return input(side);
    }

    private Holder<IAspect> holder(ResourceKey<IAspect> key) {
        return level.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(key);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpireControllerBlockEntity be) {
        if (!(level instanceof ServerLevel server) || !be.formed()) {
            return;
        }
        if (level.getGameTime() % 40 == 0 && count(level, pos) != be.pylons) {
            be.pylons = count(level, pos);
            be.setChanged();
            if (!be.formed()) {
                return;
            }
        }
        if (be.aerTime > 0) {
            be.aerTime--;
        } else if (be.aer > 0) {
            be.aer--;
            be.aerTime = BOOST_TICKS;
        }
        if (be.volatusTime > 0) {
            be.volatusTime--;
        } else if (be.volatus > 0) {
            be.volatus--;
            be.volatusTime = BOOST_TICKS;
        }
        int strength = Math.min(be.pylons, MAX_PYLONS);
        int period = be.volatusTime > 0 ? Math.max(2, 20 - 3 * strength) : 20;
        if (level.getGameTime() % period != 0 || be.potentia <= 0) {
            return;
        }
        double range = 4 * strength * (be.aerTime > 0 ? 2 : 1);
        for (ServerPlayer player : server.players()) {
            if (be.potentia <= 0) {
                break;
            }
            double dx = player.getX() - (pos.getX() + 0.5), dz = player.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz > range * range) {
                continue;
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack harness = player.getItemBySlot(slot);
                if (harness.getItem() instanceof ThaumostaticHarnessItem gear && be.refuel(server, harness, gear)) {
                    break;
                }
            }
        }
    }

    /** Puts one potentia into the harness's jar if it has room and the aura gives the vis for it. */
    private boolean refuel(ServerLevel level, ItemStack harness, ThaumostaticHarnessItem gear) {
        ItemStack jar = ThaumostaticHarnessItem.getJar(harness);
        if (!(jar.getItem() instanceof JarItem) || gear.getHoverFuel(harness) >= gear.getMaxHoverFuel(harness)) {
            return false;
        }
        Holder<IAspect> pot = holder(TTAspects.POTENTIA);
        ComponentEssentia<?> held = ComponentEssentia.jar(jar);
        AspectList list = held.getAspects();
        if (list.size() > 1 || !list.isEmpty() && list.amountOf(pot) == 0) {
            return false;
        }
        if (AuraHelper.drainVis(level, worldPosition, VIS_PER_POTENTIA, true) < VIS_PER_POTENTIA) {
            return false;
        }
        AuraHelper.drainVis(level, worldPosition, VIS_PER_POTENTIA, false);
        held.setAspects(list.add(pot, 1));
        ThaumostaticHarnessItem.setJar(harness, jar);
        potentia--;
        setChanged();
        return true;
    }

    // ---- essentia tubes: every side takes in its one aspect, nothing comes out

    @Override
    public boolean isConnectable(Direction side) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return true;
    }

    @Override
    public boolean canOutputTo(Direction side) {
        return false;
    }

    @Override
    public void setSuction(Holder<IAspect> aspect, int amount) {
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction side) {
        return level == null ? null : holder(input(side));
    }

    @Override
    public int getSuctionAmount(Direction side) {
        return room(input(side)) > 0 ? 64 : 0;
    }

    private int room(ResourceKey<IAspect> key) {
        if (key == TTAspects.POTENTIA) {
            return POTENTIA_CAP - potentia;
        }
        return BOOST_CAP - (key == TTAspects.AER ? aer : volatus);
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        ResourceKey<IAspect> key = input(side);
        if (!aspect.is(key)) {
            return 0;
        }
        int n = Math.min(amount, room(key));
        if (n <= 0) {
            return 0;
        }
        if (key == TTAspects.POTENTIA) {
            potentia += n;
        } else if (key == TTAspects.AER) {
            aer += n;
        } else {
            volatus += n;
        }
        setChanged();
        return n;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        return null;
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        return 0;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        sides.clear();
        for (Direction d : Direction.values()) {
            int i = in.getIntOr("side_" + d.getSerializedName(), 0);
            if (i > 0 && i < INPUTS.size()) {
                sides.put(d, i);
            }
        }
        potentia = in.getIntOr("potentia", 0);
        aer = in.getIntOr("aer", 0);
        volatus = in.getIntOr("volatus", 0);
        aerTime = in.getIntOr("aer_time", 0);
        volatusTime = in.getIntOr("volatus_time", 0);
        pylons = in.getIntOr("pylons", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        sides.forEach((d, i) -> out.putInt("side_" + d.getSerializedName(), i));
        out.putInt("potentia", potentia);
        out.putInt("aer", aer);
        out.putInt("volatus", volatus);
        out.putInt("aer_time", aerTime);
        out.putInt("volatus_time", volatusTime);
        out.putInt("pylons", pylons);
    }
}
