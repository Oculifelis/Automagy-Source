package com.automagy.port;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** An item with its components and no count: a key for counting and matching stacks. */
public final class ItemResource {
    public static final ItemResource EMPTY = new ItemResource(Items.AIR.builtInRegistryHolder(), DataComponentPatch.EMPTY);
    public static final Codec<ItemResource> CODEC = RecordCodecBuilder.create(i -> i.group(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(ItemResource::holder),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemResource::components)
    ).apply(i, ItemResource::new));

    private final Holder<Item> item;
    private final DataComponentPatch components;
    private final int hash;

    private ItemResource(Holder<Item> item, DataComponentPatch components) {
        this.item = item;
        this.components = components;
        this.hash = Objects.hash(item.value(), components);
    }

    public static ItemResource of(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : new ItemResource(stack.getItemHolder(), stack.getComponentsPatch());
    }

    public Holder<Item> holder() {
        return item;
    }

    public Item getItem() {
        return item.value();
    }

    public DataComponentPatch components() {
        return components;
    }

    public boolean isEmpty() {
        return item.value() == Items.AIR;
    }

    public boolean is(Item i) {
        return item.value() == i;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == item.value() && stack.getComponentsPatch().equals(components);
    }

    public ItemStack toStack(int count) {
        return isEmpty() ? ItemStack.EMPTY : new ItemStack(item, count, components);
    }

    public int getMaxStackSize() {
        return toStack(1).getMaxStackSize();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemResource r && r.item.value() == item.value() && r.components.equals(components);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
