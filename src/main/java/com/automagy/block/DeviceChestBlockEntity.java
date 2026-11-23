package com.automagy.block;

import com.automagy.filter.Filter;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import com.automagy.port.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * Shared by the greedy, eager and tenacious chests: wand settings (right-click steps the selected setting, sneak +
 * right-click selects the next one, the action bar shows the result) and slots for runic lists.
 */
public abstract class DeviceChestBlockEntity extends ChestBlockEntity implements com.automagy.port.PreRemove {
    private final ItemStack[] lists;
    private int selected;

    protected DeviceChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int listSlots) {
        super(type, pos, state);
        lists = new ItemStack[listSlots];
        java.util.Arrays.fill(lists, ItemStack.EMPTY);
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    protected abstract int settingCount();

    protected abstract String settingKey(int i);

    protected abstract Component valueName(int i);

    protected abstract void step(int i);

    void serverTick() {
    }

    void placedBy(Player player) {
    }

    void beforeOpen(Player player) {
    }

    void wandClick(Player player, boolean sneaking) {
        if (settingCount() == 0) {
            return;
        }
        if (sneaking) {
            selected = (selected + 1) % settingCount();
        } else {
            step(selected);
        }
        changed();
        player.displayClientMessage(Component.translatable("automagia.msg.setting",
                Component.translatable("automagia.setting." + settingKey(selected)), valueName(selected)), true);
    }

    protected void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- runic lists

    int listSlots() {
        return lists.length;
    }

    boolean hasLists() {
        for (ItemStack s : lists) {
            if (!s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Which list slot a list put in now goes to; the eager chest picks by a setting. */
    protected int listSlotFor() {
        return 0;
    }

    void putList(Player player, InteractionHand hand) {
        int slot = listSlotFor();
        ItemStack old = lists[slot];
        lists[slot] = player.getItemInHand(hand).split(1);
        if (!old.isEmpty()) {
            player.getInventory().placeItemBackInInventory(old);
        }
        changed();
        player.displayClientMessage(Component.translatable("automagia.msg.list_in", lists[slot].getHoverName()), true);
    }

    void takeLists(Player player) {
        for (int i = 0; i < lists.length; i++) {
            if (!lists[i].isEmpty()) {
                player.getInventory().placeItemBackInInventory(lists[i]);
                lists[i] = ItemStack.EMPTY;
            }
        }
        changed();
    }

    protected @Nullable Filter filter(int slot) {
        return Filter.of(lists[slot]);
    }

    /** Total count of an item in the chest. */
    protected int count(ItemResource res) {
        return count(res, i -> true);
    }

    protected int count(ItemResource res, IntPredicate slots) {
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) {
            if (slots.test(i) && res.matches(getItem(i))) {
                n += getItem(i).getCount();
            }
        }
        return n;
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (ItemStack s : lists) {
                if (!s.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
                }
            }
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        selected = in.getIntOr("selected", 0);
        for (int i = 0; i < lists.length; i++) {
            lists[i] = in.read("list" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.putInt("selected", selected);
        for (int i = 0; i < lists.length; i++) {
            out.store("list" + i, ItemStack.OPTIONAL_CODEC, lists[i]);
        }
    }
}
