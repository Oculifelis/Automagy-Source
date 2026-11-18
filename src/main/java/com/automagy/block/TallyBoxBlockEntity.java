package com.automagy.block;

import com.automagy.archive.InventariumCoreBlockEntity;
import com.automagy.archive.TallySource;
import com.automagy.AMRegistry;
import com.automagy.filter.Filter;
import com.automagy.filter.RunicList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Twice a second, counts what its list names at its target and sets its signal. A whitelist gives a signal when the
 * target holds all (or any, by the wand's Match setting) of the named items, each at least its quantity; a blacklist
 * gives a signal unless it does. With a remote comparator on top, the box reads the comparator's watched block instead;
 * pointed at another tally box, it reads that box's target, and so on down the chain.
 */
public class TallyBoxBlockEntity extends ConfigurableBlockEntity {
    private static final int MAX_CHAIN = 16;
    private ItemStack list = ItemStack.EMPTY;
    private boolean matchAll = true;

    public TallyBoxBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.TALLY_BOX_BE.get(), pos, state);
    }

    ItemStack list() {
        return list;
    }

    void putList(Player player, ItemStack stack) {
        ItemStack old = list;
        list = stack.split(1);
        if (!old.isEmpty()) {
            player.getInventory().placeItemBackInInventory(old);
        }
        setChanged();
        player.sendOverlayMessage(Component.translatable("automagia.msg.list_in", list.getHoverName()));
    }

    void takeList(Player player) {
        player.getInventory().placeItemBackInInventory(list);
        list = ItemStack.EMPTY;
        setChanged();
    }

    /** Where a box reads from: a place, the face it reads through, and the lens it reads with. */
    record Source(BlockPos pos, @Nullable Direction face, TallyBoxBlock.Lens lens) {
    }

    @Nullable Source source(int depth) {
        if (level == null || depth > MAX_CHAIN) {
            return null;
        }
        BlockState state = getBlockState();
        Direction facing = state.getValue(TallyBoxBlock.FACING);
        BlockPos pos = worldPosition.relative(facing);
        Direction face = facing.getOpposite();
        if (level.getBlockEntity(worldPosition.above()) instanceof RemoteComparatorBlockEntity eye && eye.target() != null) {
            pos = eye.target();
            face = null;
        }
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof TallyBoxBlockEntity next) {
            return next.source(depth + 1);
        }
        return new Source(pos, face, state.getValue(TallyBoxBlock.LENS));
    }

    /** Each named entry's count at the source. */
    private Map<RunicList.Entry, Integer> counts(Filter f, Source src) {
        Map<RunicList.Entry, Integer> out = new HashMap<>();
        switch (src.lens()) {
            case NONE -> {
                // the Inventarium is counted by asking it: its core, one of its ring shelves, or a tome (what is on order)
                TallySource asked = level.getBlockEntity(src.pos()) instanceof TallySource t ? t
                        : InventariumCoreBlockEntity.atShelf(level, src.pos());
                ResourceHandler<ItemResource> h = asked != null ? null : level.getCapability(Capabilities.Item.BLOCK, src.pos(), src.face());
                if (asked != null) {
                    asked.tally((res, n) -> add(out, f, res, n));
                } else if (h != null) {
                    for (int i = 0; i < h.size(); i++) {
                        add(out, f, h.getResource(i), h.getAmountAsInt(i));
                    }
                }
            }
            case UNBOUND -> {
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(src.pos()), Entity::isAlive)) {
                    add(out, f, ItemResource.of(item.getItem()), item.getItem().getCount());
                }
            }
            case YIELD -> {
                // what the block could drop: by an ordinary pickaxe and by a silk touch one, the larger of each
                ServerLevel server = (ServerLevel) level;
                BlockState target = level.getBlockState(src.pos());
                ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
                ItemStack silk = pick.copy();
                silk.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
                Map<RunicList.Entry, Integer> plain = new HashMap<>();
                for (ItemStack d : drops(server, target, src.pos(), pick)) {
                    add(plain, f, ItemResource.of(d), d.getCount());
                }
                for (ItemStack d : drops(server, target, src.pos(), silk)) {
                    add(out, f, ItemResource.of(d), d.getCount());
                }
                plain.forEach((e, n) -> out.merge(e, n, Math::max));
            }
        }
        return out;
    }

    private List<ItemStack> drops(ServerLevel server, BlockState state, BlockPos pos, ItemStack tool) {
        return Block.getDrops(state, server, pos, server.getBlockEntity(pos), null, tool);
    }

    private static void add(Map<RunicList.Entry, Integer> out, Filter f, ItemResource res, int n) {
        RunicList.Entry e = f.entry(res);
        if (e != null) {
            out.merge(e, n, Integer::sum);
        }
    }

    boolean evaluate() {
        Filter f = Filter.of(list);
        if (f == null || f.list().entries().isEmpty()) {
            return false;
        }
        Source src = source(0);
        if (src == null) {
            return !f.white();
        }
        Map<RunicList.Entry, Integer> counts = counts(f, src);
        boolean all = true;
        boolean any = false;
        for (RunicList.Entry e : f.list().entries()) {
            boolean has = counts.getOrDefault(e, 0) >= Math.max(1, e.qty());
            all &= has;
            any |= has;
        }
        boolean met = matchAll ? all : any;
        return f.white() == met;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TallyBoxBlockEntity be) {
        if (level.getGameTime() % 10 != Math.floorMod(pos.hashCode(), 10)) {
            return;
        }
        boolean on = be.evaluate();
        if (on != state.getValue(TallyBoxBlock.POWERED)) {
            level.setBlock(pos, state.setValue(TallyBoxBlock.POWERED, on), Block.UPDATE_ALL);
            BlockPos back = pos.relative(state.getValue(TallyBoxBlock.FACING).getOpposite());
            level.updateNeighborsAt(back, state.getBlock());
        }
    }

    @Override
    protected int settingCount() {
        return 1;
    }

    @Override
    protected String settingKey(int i) {
        return "match";
    }

    @Override
    protected Component valueName(int i) {
        return Component.translatable(matchAll ? "automagia.value.all" : "automagia.value.any");
    }

    @Override
    protected void step(int i) {
        matchAll = !matchAll;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !list.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, list);
        }
        if (level != null && state.getValue(TallyBoxBlock.LENS) != TallyBoxBlock.Lens.NONE) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    com.automagy.item.TallyLensItem.stackFor(state.getValue(TallyBoxBlock.LENS)));
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        list = in.read("list", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        matchAll = in.getBooleanOr("match_all", true);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("list", ItemStack.OPTIONAL_CODEC, list);
        out.putBoolean("match_all", matchAll);
    }
}
