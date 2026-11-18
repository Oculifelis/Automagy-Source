package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.filter.Filter;
import com.automagy.filter.RunicList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The eager chest trades with whoever opens it, if it knows them: first the one who placed it, then anyone who
 * introduces themselves with a wand. Sneaking opens it without a trade. It holds two runic lists, set with a wand which
 * slot a list goes into.
 * <ul>
 * <li>Give, whitelist: fills the visitor's inventory up to each item's quantity (a stack when none is given).</li>
 * <li>Give, blacklist: gives everything it does not name, and leaves a named item's quantity inside.</li>
 * <li>Take, whitelist: takes the named items until the chest holds each quantity (all of them when none is given).</li>
 * <li>Take, blacklist: takes everything it does not name from the visitor's backpack (never the hotbar or armour),
 * and leaves a named item's quantity with the visitor.</li>
 * </ul>
 * With no list in a slot, that half of the trade does nothing.
 */
public class EagerChestBlockEntity extends DeviceChestBlockEntity {
    private static final int GIVE = 0;
    private static final int TAKE = 1;
    private static final int HOTBAR = 9;

    private @Nullable UUID owner;
    private final List<UUID> known = new ArrayList<>();
    private boolean trade = true;
    private int listSlot = GIVE;

    public EagerChestBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.EAGER_CHEST_BE.get(), pos, state, 2);
    }

    private boolean knows(Player player) {
        return owner == null || owner.equals(player.getUUID()) || known.contains(player.getUUID());
    }

    @Override
    void placedBy(Player player) {
        owner = player.getUUID();
        setChanged();
    }

    @Override
    void wandClick(Player player, boolean sneaking) {
        if (!knows(player)) {
            known.add(player.getUUID());
            setChanged();
            player.sendOverlayMessage(Component.translatable("automagia.msg.eager_introduced"));
            return;
        }
        super.wandClick(player, sneaking);
    }

    @Override
    protected int listSlotFor() {
        return listSlot;
    }

    @Override
    void beforeOpen(Player player) {
        if (!trade || !knows(player)) {
            return;
        }
        Filter give = filter(GIVE);
        Filter take = filter(TAKE);
        if (give != null) {
            give(player.getInventory(), give);
        }
        if (take != null) {
            take(player.getInventory(), take);
        }
        setChanged();
    }

    private void give(Inventory inv, Filter f) {
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack s = getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemResource res = ItemResource.of(s);
            RunicList.Entry e = f.entry(res);
            int n;
            if (f.white()) {
                if (e == null) {
                    continue;
                }
                n = (e.hasQty() ? e.qty() : s.getMaxStackSize()) - countIn(inv, res, 0);
            } else {
                if (e != null && !e.hasQty()) {
                    continue;
                }
                n = e == null ? s.getCount() : count(res) - e.qty();
            }
            n = Math.min(n, s.getCount());
            if (n > 0) {
                ItemStack part = s.copyWithCount(n);
                inv.add(part);
                s.shrink(n - part.getCount());
            }
        }
    }

    private void take(Inventory inv, Filter f) {
        var items = inv.getNonEquipmentItems();
        for (int j = f.white() ? 0 : HOTBAR; j < items.size(); j++) {
            ItemStack s = items.get(j);
            if (s.isEmpty()) {
                continue;
            }
            ItemResource res = ItemResource.of(s);
            RunicList.Entry e = f.entry(res);
            int n;
            if (f.white()) {
                if (e == null) {
                    continue;
                }
                n = e.hasQty() ? e.qty() - count(res) : s.getCount();
            } else {
                if (e != null && !e.hasQty()) {
                    continue;
                }
                n = e == null ? s.getCount() : countIn(inv, res, HOTBAR) - e.qty();
            }
            n = Math.min(n, s.getCount());
            if (n > 0) {
                ItemStack left = HopperBlockEntity.addItem(null, this, s.copyWithCount(n), null);
                s.shrink(n - left.getCount());
            }
        }
        inv.setChanged();
    }

    private static int countIn(Inventory inv, ItemResource res, int from) {
        int n = 0;
        var items = inv.getNonEquipmentItems();
        for (int j = from; j < items.size(); j++) {
            if (res.matches(items.get(j))) {
                n += items.get(j).getCount();
            }
        }
        return n;
    }

    // ---- wand settings: trade on open, which slot a list goes into

    @Override
    protected int settingCount() {
        return 2;
    }

    @Override
    protected String settingKey(int i) {
        return i == 0 ? "trade" : "list_slot";
    }

    @Override
    protected Component valueName(int i) {
        if (i == 0) {
            return Component.translatable(trade ? "automagia.value.on" : "automagia.value.off");
        }
        return Component.translatable(listSlot == GIVE ? "automagia.value.give" : "automagia.value.take");
    }

    @Override
    protected void step(int i) {
        if (i == 0) {
            trade = !trade;
        } else {
            listSlot = 1 - listSlot;
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        known.clear();
        known.addAll(in.read("known", UUIDUtil.CODEC.listOf()).orElse(List.of()));
        trade = in.getBooleanOr("trade", true);
        listSlot = in.getIntOr("list_slot", GIVE) == TAKE ? TAKE : GIVE;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.storeNullable("owner", UUIDUtil.CODEC, owner);
        out.store("known", UUIDUtil.CODEC.listOf(), known);
        out.putBoolean("trade", trade);
        out.putInt("list_slot", listSlot);
    }
}
