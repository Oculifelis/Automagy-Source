package com.automagy.golem;

import com.automagy.Automagy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What an item does when put on a side of the golem's workbench. Read from the item data map
 * {@code automagia:workbench_upgrade}, so a datapack sets each upgrade's effect and its number (and can make any item
 * an upgrade): {@code {"values": {"automagia:workbench_upgrade_air": {"effect": "speed", "amount": 500}}}}.
 *
 * @param amount what the effect counts in: crafting progress per tick in hundredths per point of the golem's movement
 *               speed (speed) or attack damage (strength), fatigue taken off each craft (endurance); unused by the rest
 */
public record WorkbenchUpgrade(Effect effect, int amount) {
    public static final Codec<WorkbenchUpgrade> CODEC = RecordCodecBuilder.create(i -> i.group(
            Effect.CODEC.fieldOf("effect").forGetter(WorkbenchUpgrade::effect),
            Codec.INT.optionalFieldOf("amount", 0).forGetter(WorkbenchUpgrade::amount)).apply(i, WorkbenchUpgrade::new));

    public static final DataMapType<Item, WorkbenchUpgrade> DATA = DataMapType.builder(Automagy.id("workbench_upgrade"),
            Registries.ITEM, CODEC).synced(CODEC, false).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(DATA);
    }

    public static @Nullable WorkbenchUpgrade of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getItemHolder().getData(DATA);
    }

    public enum Effect implements StringRepresentable {
        /** The golem's speed hurries the work; it tires faster. */
        SPEED(4),
        /** One more pattern; at most two on a bench. */
        PATTERN(2),
        /** The golem tires more slowly. */
        ENDURANCE(4),
        /** The golem's strength hurries the work, less than speed does; it tires more slowly. */
        STRENGTH(4),
        /** Leftovers (an empty bucket after a cake) go into the bench instead of being thrown out. */
        TIDY(1),
        /** The first storage slot's item is a placeholder in the patterns; the golem tries every other item there. */
        PLACEHOLDER(1);

        public static final Codec<Effect> CODEC = StringRepresentable.fromEnum(Effect::values);
        /** How many of this effect one bench takes. */
        public final int limit;

        Effect(int limit) {
            this.limit = limit;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
