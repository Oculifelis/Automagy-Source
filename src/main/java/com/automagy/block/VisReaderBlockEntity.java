package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.SavedEnum;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectContainer;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IAspectQuery;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaJar;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.items.ILabel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Reads aspects and turns them into a signal. Like a comparator, the strength scales with how full the target is
 * against its capacity (Auto takes a jar's own capacity); Invert gives a full signal when empty instead.
 * Modes: Total (every aspect together), Any (High) and Any (Low) (the fullest or emptiest single aspect),
 * Aspect Count (how many aspects are there, one strength each, never inverted). Right-clicking it with a jar label
 * makes it read only that label's aspect; a blank label clears the filter.
 */
public class VisReaderBlockEntity extends ConfigurableBlockEntity {
    private static final int[] CAPACITIES = {0, 8, 16, 32, 64, 128, 250, 500, 1000};
    private static final int FALLBACK_CAPACITY = 64;

    enum Mode { DISABLED, TOTAL, ANY_HIGH, ANY_LOW, COUNT }

    private Mode mode = Mode.TOTAL;
    private int capacity; // index into CAPACITIES; 0 = Auto
    private boolean invert;
    private int output; // 0 = all sides, else Direction ordinal + 1
    private @Nullable ResourceKey<IAspect> filter; // from a jar label: read only this aspect

    public VisReaderBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.VIS_READER_BE.get(), pos, state);
    }

    public @Nullable Direction outputSide() {
        return output == 0 ? null : Direction.values()[output - 1];
    }

    /** What the reader reads: the block a remote comparator beside it watches, else the block it is stuck to. */
    private BlockPos target(BlockState state) {
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof RemoteComparatorBlockEntity remote && remote.target() != null) {
                return remote.target();
            }
        }
        return AttachedBlock.attachedTo(worldPosition, state);
    }

    private static @Nullable AspectList read(Level level, BlockPos at, Direction side) {
        IAspectContainer container = level.getCapability(AspectCapabilities.CONTAINER, at, side);
        if (container != null) {
            return container.getAspects();
        }
        IEssentiaStorage storage = level.getCapability(EssentiaCapabilities.STORAGE, at, side);
        if (storage != null) {
            return storage.contents();
        }
        IAspectQuery query = level.getCapability(EssentiaCapabilities.ASPECT_QUERY, at, side);
        return query == null ? null : query.queryAspects();
    }

    private int capacityOf(Level level, BlockPos at) {
        if (CAPACITIES[capacity] > 0) {
            return CAPACITIES[capacity];
        }
        if (level.getBlockEntity(at) instanceof IEssentiaJar jar) {
            return jar.jarCapacity();
        }
        if (level.getBlockState(at).getBlock() instanceof IEssentiaJar jar) {
            return jar.jarCapacity();
        }
        return FALLBACK_CAPACITY;
    }

    private int signal(Level level, BlockState state) {
        if (mode == Mode.DISABLED) {
            return 0;
        }
        BlockPos at = target(state);
        if (!level.isLoaded(at)) {
            return 0;
        }
        AspectList aspects = read(level, at, state.getValue(AttachedBlock.FACING));
        if (aspects == null) {
            return 0;
        }
        if (filter != null) {
            ResourceKey<IAspect> only = filter;
            aspects = AspectList.ofEntries(aspects.entries().stream().filter(a -> a.aspect().is(only)).toList());
        }
        if (mode == Mode.COUNT) {
            return Math.min(15, aspects.size());
        }
        int amount = switch (mode) {
            case TOTAL -> aspects.totalAmount();
            case ANY_HIGH -> aspects.entries().stream().mapToInt(AspectInstance::amount).max().orElse(0);
            default -> aspects.entries().stream().mapToInt(AspectInstance::amount).min().orElse(0);
        };
        int cap = Math.max(1, capacityOf(level, at));
        int strength = amount <= 0 ? 0 : Math.min(15, 1 + amount * 14 / cap);
        return invert ? 15 - strength : strength;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VisReaderBlockEntity be) {
        if (level.getGameTime() % 5 != 0) {
            return;
        }
        int power = be.signal(level, state);
        if (power != state.getValue(VisReaderBlock.POWER)) {
            level.setBlock(pos, state.setValue(VisReaderBlock.POWER, power), Block.UPDATE_ALL);
        }
    }

    /** A jar label sets the aspect filter; a blank one clears it. */
    public void applyLabel(Player player, ItemStack stack, ILabel label) {
        filter = label.getFilteredAspect(stack);
        setChanged();
        if (filter == null) {
            player.displayClientMessage(Component.translatable("automagia.msg.vis_reader_filter_off"), true);
        } else {
            ResourceLocation id = filter.location();
            player.displayClientMessage(Component.translatable("automagia.msg.vis_reader_filter",
                    Component.translatable("aspect." + id.getNamespace() + "." + id.getPath())), true);
        }
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected int settingCount() {
        return 4;
    }

    @Override
    protected String settingKey(int i) {
        return switch (i) {
            case 0 -> "mode";
            case 1 -> "capacity";
            case 2 -> "invert";
            default -> "output";
        };
    }

    @Override
    protected Component valueName(int i) {
        return switch (i) {
            case 0 -> Component.translatable("automagia.vis_reader.mode." + mode.name().toLowerCase());
            case 1 -> capacity == 0 ? Component.translatable("automagia.value.auto") : Component.literal(String.valueOf(CAPACITIES[capacity]));
            case 2 -> Component.translatable(invert ? "automagia.value.on" : "automagia.value.off");
            default -> output == 0 ? Component.translatable("automagia.value.all_sides")
                    : Component.translatable("automagia.direction." + outputSide().getSerializedName());
        };
    }

    @Override
    protected void step(int i) {
        switch (i) {
            case 0 -> mode = SavedEnum.next(mode);
            case 1 -> capacity = (capacity + 1) % CAPACITIES.length;
            case 2 -> invert = !invert;
            default -> output = (output + 1) % 7;
        }
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        mode = SavedEnum.read(in, "mode", Mode.TOTAL);
        capacity = Math.floorMod(in.getIntOr("capacity", 0), CAPACITIES.length);
        invert = in.getBooleanOr("invert", false);
        output = Math.floorMod(in.getIntOr("output", 0), 7);
        ResourceLocation id = ResourceLocation.tryParse(in.getStringOr("filter", ""));
        filter = id == null || in.getStringOr("filter", "").isEmpty() ? null : ResourceKey.create(IAspect.REGISTRY_KEY, id);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        SavedEnum.write(out, "mode", mode);
        out.putInt("capacity", capacity);
        out.putBoolean("invert", invert);
        out.putInt("output", output);
        if (filter != null) {
            out.putString("filter", filter.location().toString());
        }
    }
}
