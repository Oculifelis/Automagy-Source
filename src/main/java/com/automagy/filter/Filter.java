package com.automagy.filter;

import com.automagy.AMRegistry;
import com.automagy.item.RunicListItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** A runic list read as a whitelist (only what it names) or a blacklist (everything it does not name). */
public record Filter(RunicList list, boolean white) {
    /** The filter written on a runic list item, or null when the stack is not one. */
    public static @Nullable Filter of(ItemStack stack) {
        if (stack.getItem() instanceof RunicListItem item) {
            return new Filter(stack.getOrDefault(AMRegistry.RUNIC_LIST.get(), RunicList.EMPTY), item.white());
        }
        return null;
    }

    public RunicList.@Nullable Entry entry(ItemResource res) {
        return list.find(res);
    }

    /** Whether an item passes, ignoring quantities: a whitelist lets through what it names, a blacklist the rest. */
    public boolean allows(ItemResource res) {
        return (list.find(res) != null) == white;
    }
}
