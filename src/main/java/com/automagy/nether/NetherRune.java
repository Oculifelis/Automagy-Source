package com.automagy.nether;

import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.Nullable;

/**
 * What a block does in a Nethermind structure, from the block data map {@code automagia:nether_rune}. Only blocks in
 * this map count as runed obsidian. A basic or block rune conjures one of its {@code conjures} (by weight) for its
 * {@code cost}; a creature rune summons through a Dimensional Lure, which pays {@code lure_cost} every
 * {@code lure_every} conjurations; celerity runes only speed the Nethermind up.
 *
 * <pre>{"values": {"automagia:runed_obsidian_sea": {"kind": "block", "conjures": [{"block": "minecraft:lava"}],
 *   "cost": [{"aspect": "thaumaturge:ignis", "amount": 3}]}}}</pre>
 */
public record NetherRune(Kind kind, List<Conjure> conjures, AspectList cost, AspectList lureCost, int lureEvery,
                         int instability, int lureDistance) {
    public enum Kind implements StringRepresentable {
        BASIC, BLOCK, CREATURE, CELERITY;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One thing a rune can bring across: a block, or a creature (a Thaumaturge wisp can be given its aspect). */
    public record Conjure(Optional<Block> block, Optional<EntityType<?>> entity, Optional<Identifier> aspect, int weight) {
        public static final Codec<Conjure> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("block").forGetter(Conjure::block),
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("entity").forGetter(Conjure::entity),
                Identifier.CODEC.optionalFieldOf("aspect").forGetter(Conjure::aspect),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Conjure::weight)).apply(i, Conjure::new));
    }

    public static final Codec<NetherRune> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(NetherRune::kind),
            Conjure.CODEC.listOf().optionalFieldOf("conjures", List.of()).forGetter(NetherRune::conjures),
            AspectList.CODEC.optionalFieldOf("cost", AspectList.EMPTY).forGetter(NetherRune::cost),
            AspectList.CODEC.optionalFieldOf("lure_cost", AspectList.EMPTY).forGetter(NetherRune::lureCost),
            Codec.INT.optionalFieldOf("lure_every", 4).forGetter(NetherRune::lureEvery),
            Codec.INT.optionalFieldOf("instability", 0).forGetter(NetherRune::instability),
            Codec.INT.optionalFieldOf("lure_distance", 6).forGetter(NetherRune::lureDistance)).apply(i, NetherRune::new));

    public static final DataMapType<Block, NetherRune> DATA = DataMapType.builder(Automagy.id("nether_rune"),
            Registries.BLOCK, CODEC).synced(CODEC, false).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(DATA);
    }

    public static @Nullable NetherRune of(BlockState state) {
        return state.typeHolder().getData(DATA);
    }

    public @Nullable Conjure pick(RandomSource random) {
        int total = conjures.stream().mapToInt(Conjure::weight).sum();
        if (total <= 0) {
            return null;
        }
        int r = random.nextInt(total);
        for (Conjure c : conjures) {
            r -= c.weight();
            if (r < 0) {
                return c;
            }
        }
        return null;
    }
}
