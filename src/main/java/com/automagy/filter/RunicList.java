package com.automagy.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * What a runic whitelist or blacklist has written on it: up to nine items, each with an optional quantity, and whether
 * matching is partial (the item alone) or exact (the item and its components). A quantity of 1 or less means "no
 * quantity given"; each device says what a given quantity means to it.
 */
public record RunicList(List<Entry> entries, boolean partial) {
    public static final int SIZE = 9;
    public static final int MAX_QTY = 9999;
    public static final RunicList EMPTY = new RunicList(List.of(), false);

    public record Entry(int slot, ItemStackTemplate item, int qty) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, SIZE - 1).fieldOf("slot").forGetter(Entry::slot),
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.intRange(1, MAX_QTY).optionalFieldOf("qty", 1).forGetter(Entry::qty)
        ).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::slot, ItemStackTemplate.STREAM_CODEC, Entry::item, ByteBufCodecs.VAR_INT, Entry::qty,
                Entry::new);

        public boolean hasQty() {
            return qty > 1;
        }
    }

    public static final Codec<RunicList> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(RunicList::entries),
            Codec.BOOL.optionalFieldOf("partial", false).forGetter(RunicList::partial)
    ).apply(i, RunicList::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RunicList> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(SIZE)), RunicList::entries, ByteBufCodecs.BOOL, RunicList::partial,
            RunicList::new);

    public @Nullable Entry at(int slot) {
        for (Entry e : entries) {
            if (e.slot == slot) {
                return e;
            }
        }
        return null;
    }

    /** The entry an item matches, or null when the list does not name it. */
    public @Nullable Entry find(ItemResource res) {
        if (res.isEmpty()) {
            return null;
        }
        for (Entry e : entries) {
            if (partial ? res.is(e.item.item().value()) : res.equals(ItemResource.of(e.item))) {
                return e;
            }
        }
        return null;
    }

    public @Nullable Entry find(ItemStack stack) {
        return find(ItemResource.of(stack));
    }

    public RunicList with(Entry entry) {
        List<Entry> out = new ArrayList<>(entries.stream().filter(e -> e.slot != entry.slot).toList());
        out.add(entry);
        out.sort(Comparator.comparingInt(Entry::slot));
        return new RunicList(List.copyOf(out), partial);
    }

    public RunicList without(int slot) {
        return new RunicList(entries.stream().filter(e -> e.slot != slot).toList(), partial);
    }

    public RunicList withPartial(boolean partial) {
        return new RunicList(entries, partial);
    }
}
