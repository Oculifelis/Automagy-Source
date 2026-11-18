package com.automagy.archive;

import com.automagy.Automagy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * How many inventory slots a shelf lets an Inventarium core keep track of, from the block data map
 * {@code automagia:archive_capacity}: {@code {"values": {"minecraft:bookshelf": {"slots": 81}}}}. Only blocks in this
 * map count as archive shelves, for the ring around the core and for the shelves within three blocks of it.
 */
public record ArchiveCapacity(int slots) {
    public static final Codec<ArchiveCapacity> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("slots").forGetter(ArchiveCapacity::slots)).apply(i, ArchiveCapacity::new));

    public static final DataMapType<Block, ArchiveCapacity> DATA = DataMapType.builder(Automagy.id("archive_capacity"),
            Registries.BLOCK, CODEC).synced(CODEC, false).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(DATA);
    }

    public static int of(BlockState state) {
        ArchiveCapacity c = state.getBlockHolder().getData(DATA);
        return c == null ? 0 : c.slots();
    }
}
