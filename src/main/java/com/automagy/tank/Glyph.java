package com.automagy.tank;

import com.automagy.Automagy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.Nullable;

/**
 * What an item does when pressed onto a thirsty tank's side. Read from the item data map {@code automagia:glyph}, so a
 * datapack sets each glyph's effect and its number (and can make any item a glyph):
 * {@code {"values": {"automagia:glyph_guzzler": {"effect": "guzzler", "amount": 100}}}}.
 *
 * @param amount what the effect counts in: mB per second for consumption, siphoning and envy, extra speed in percent
 *               for the guzzler, extra mB of capacity for the reservoir, ticks before a cow can be milked again for
 *               the bovine glyph; unused by the rest
 */
public record Glyph(Effect effect, int amount) {
    public static final Codec<Glyph> CODEC = RecordCodecBuilder.create(i -> i.group(
            Effect.CODEC.fieldOf("effect").forGetter(Glyph::effect),
            Codec.INT.optionalFieldOf("amount", 0).forGetter(Glyph::amount)).apply(i, Glyph::new));

    public static final DataMapType<Item, Glyph> DATA = DataMapType.builder(Automagy.id("glyph"), Registries.ITEM, CODEC)
            .synced(CODEC, false).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(DATA);
    }

    public static @Nullable Glyph of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(DATA);
    }

    public enum Effect implements StringRepresentable {
        /** Accepts more than it holds; the rest is destroyed. */
        VOID,
        /** Destroys what it holds, {@code amount} mB a second. */
        CONSUMPTION,
        /** Pushes {@code amount} mB a second into the container on its side. */
        SIPHONING,
        /** Pulls {@code amount} mB a second from the container on its side. */
        ENVY,
        /** Drinks only while powered. */
        TEMPERANCE,
        /** Never lets itself be emptied completely, so it keeps its fluid. */
        PRESERVATION,
        /** Drinks {@code amount} percent faster. */
        GUZZLER,
        /** Holds {@code amount} mB more. */
        RESERVOIR,
        /** Milks cows on its side instead of drinking; a cow waits {@code amount} ticks between milkings. */
        BOVINE;

        public static final Codec<Effect> CODEC = StringRepresentable.fromEnum(Effect::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
