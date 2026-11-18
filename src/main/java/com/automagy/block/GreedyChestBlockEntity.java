package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.SavedEnum;
import com.automagy.filter.Filter;
import com.automagy.filter.RunicList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The greedy chest steals from its neighbours' inventories, twice a second. Modes:
 * <ul>
 * <li>Split New Contents: takes its percentage of whatever is newly put into a neighbour (50% at first).</li>
 * <li>Maintain Percentage: keeps its percentage of each item's combined total (its own plus the neighbour's).</li>
 * <li>Take Any Over: takes everything over the amount from each neighbour.</li>
 * <li>Maintain Stock of: takes until it holds the amount.</li>
 * </ul>
 * A runic whitelist makes it covet only what it names; a quantity on it overrides the amount (and in the first two
 * modes caps what the chest will hold). A runic blacklist makes it ignore what it names; a quantity there is left
 * behind in the neighbour. It never takes from a hopper below it, nor from a greedy chest that takes from it.
 */
public class GreedyChestBlockEntity extends DeviceChestBlockEntity {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN, Direction.UP};
    private static final int[] AMOUNTS = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048};

    enum Mode { SPLIT, PERCENTAGE, OVER, STOCK }

    private Mode mode = Mode.SPLIT;
    private int percent = 50;
    private int amount = 6;
    private int sides = 0b111111;
    /** Split mode: what each neighbour held at the last look, so new contents can be told apart. */
    private final Map<Direction, Map<ItemResource, Integer>> seen = new EnumMap<>(Direction.class);

    public GreedyChestBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.GREEDY_CHEST_BE.get(), pos, state, 1);
    }

    public boolean takesFrom(Direction side) {
        return (sides & (1 << side(side))) != 0;
    }

    private static int side(Direction d) {
        for (int i = 0; i < SIDES.length; i++) {
            if (SIDES[i] == d) {
                return i;
            }
        }
        return 0;
    }

    @Override
    void serverTick() {
        if (level == null || level.getGameTime() % 10 != Math.floorMod(worldPosition.hashCode(), 10)) {
            return;
        }
        ResourceHandler<ItemResource> self = VanillaContainerWrapper.of(this);
        Filter filter = filter(0);
        for (Direction d : SIDES) {
            if (!takesFrom(d)) {
                seen.remove(d);
                continue;
            }
            BlockPos at = worldPosition.relative(d);
            var other = level.getBlockEntity(at);
            if (d == Direction.DOWN && other instanceof HopperBlockEntity
                    || other instanceof GreedyChestBlockEntity g && g.takesFrom(d.getOpposite())) {
                continue;
            }
            ResourceHandler<ItemResource> them = level.getCapability(Capabilities.Item.BLOCK, at, d.getOpposite());
            if (them == null) {
                seen.remove(d);
                continue;
            }
            Map<ItemResource, Integer> now = totals(them);
            Map<ItemResource, Integer> before = seen.get(d);
            for (Map.Entry<ItemResource, Integer> e : now.entrySet()) {
                ItemResource res = e.getKey();
                int theirs = e.getValue();
                int want = want(res, theirs, before == null ? null : before.getOrDefault(res, 0), filter);
                if (want > 0) {
                    int moved = move(them, self, res, Math.min(want, theirs));
                    e.setValue(theirs - moved);
                }
            }
            seen.put(d, now);
        }
    }

    /** How many of an item to take from a neighbour holding {@code theirs} (and {@code before} at the last look). */
    private int want(ItemResource res, int theirs, Integer before, Filter filter) {
        int amount = AMOUNTS[this.amount];
        int cap = Integer.MAX_VALUE;
        int leave = 0;
        if (filter != null) {
            RunicList.Entry entry = filter.entry(res);
            if (filter.white()) {
                if (entry == null) {
                    return 0;
                }
                if (entry.hasQty()) {
                    amount = entry.qty();
                    cap = entry.qty();
                }
            } else if (entry != null) {
                if (!entry.hasQty()) {
                    return 0;
                }
                leave = entry.qty();
            }
        }
        int mine = count(res);
        int take = switch (mode) {
            case SPLIT -> before == null || theirs <= before ? 0 : Math.max(1, (theirs - before) * percent / 100);
            case PERCENTAGE -> (int) Math.ceil((theirs + mine) * percent / 100.0) - mine;
            case OVER -> theirs - amount;
            case STOCK -> amount - mine;
        };
        if (mode == Mode.SPLIT || mode == Mode.PERCENTAGE) {
            take = Math.min(take, cap - mine);
        }
        return Math.min(take, theirs - leave);
    }

    private static Map<ItemResource, Integer> totals(ResourceHandler<ItemResource> h) {
        Map<ItemResource, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < h.size(); i++) {
            ItemResource res = h.getResource(i);
            if (!res.isEmpty()) {
                out.merge(res, h.getAmountAsInt(i), Integer::sum);
            }
        }
        return out;
    }

    /** Moves up to {@code max} of an item, as much as both sides allow; returns how many moved. */
    static int move(ResourceHandler<ItemResource> from, ResourceHandler<ItemResource> to, ItemResource res, int max) {
        int fits;
        try (Transaction probe = Transaction.openRoot()) {
            fits = to.insert(res, max, probe);
        }
        if (fits <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int out = from.extract(res, fits, tx);
            int in = to.insert(res, out, tx);
            if (out > 0 && in == out) {
                tx.commit();
                return out;
            }
        }
        return 0;
    }

    // ---- wand settings: mode, percentage, amount, then one on/off per side

    @Override
    protected int settingCount() {
        return 3 + SIDES.length;
    }

    @Override
    protected String settingKey(int i) {
        return switch (i) {
            case 0 -> "pilfer_mode";
            case 1 -> "percentage";
            case 2 -> "amount";
            default -> "take_" + SIDES[i - 3].getSerializedName();
        };
    }

    @Override
    protected Component valueName(int i) {
        return switch (i) {
            case 0 -> Component.translatable("automagia.greedy.mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
            case 1 -> Component.literal(percent + "%");
            case 2 -> Component.literal(Integer.toString(AMOUNTS[amount]));
            default -> Component.translatable(takesFrom(SIDES[i - 3]) ? "automagia.value.on" : "automagia.value.off");
        };
    }

    @Override
    protected void step(int i) {
        switch (i) {
            case 0 -> {
                mode = SavedEnum.next(mode);
                seen.clear();
            }
            case 1 -> percent = percent >= 100 ? 10 : percent + 10;
            case 2 -> amount = (amount + 1) % AMOUNTS.length;
            default -> sides ^= 1 << (i - 3);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        mode = SavedEnum.read(in, "mode", Mode.SPLIT);
        percent = Math.clamp(in.getIntOr("percent", 50), 10, 100);
        amount = Math.clamp(in.getIntOr("amount", 6), 0, AMOUNTS.length - 1);
        sides = in.getIntOr("sides", 0b111111);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        SavedEnum.write(out, "mode", mode);
        out.putInt("percent", percent);
        out.putInt("amount", amount);
        out.putInt("sides", sides);
    }
}
