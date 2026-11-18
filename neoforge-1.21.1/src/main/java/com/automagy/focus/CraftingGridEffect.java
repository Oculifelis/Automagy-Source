package com.automagy.focus;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.spell.behavior.SpellBehavior;
import com.leclowndu93150.thaumaturge.api.spell.behavior.SpellBehaviorType;
import com.leclowndu93150.thaumaturge.api.spell.cast.CastContext;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * The crafting focus's effect: casting it opens an arcane crafting grid paid for by the caster in hand. Its numbers
 * sit in the spell part file ({@code data/automagia/thaumaturge/spell_part/crafting.json}):
 * {@code {"type": "automagia:crafting_grid", "open_vis": 2, "craft_vis": 1}}.
 *
 * @param openVis  vis the caster pays each time the grid opens
 * @param craftVis vis the caster pays on top of the recipe for each arcane craft (mundane crafts pay nothing)
 */
public record CraftingGridEffect(float openVis, float craftVis) implements SpellBehavior {
    public static final MapCodec<CraftingGridEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("open_vis", 2f).forGetter(CraftingGridEffect::openVis),
            Codec.FLOAT.optionalFieldOf("craft_vis", 1f).forGetter(CraftingGridEffect::craftVis)).apply(i, CraftingGridEffect::new));

    @Override
    public SpellBehaviorType<?> type() {
        return AMRegistry.CRAFTING_GRID.get();
    }

    @Override
    public void execute(CastContext ctx, List<SpellTarget> targets) {
        if (ctx.caster() instanceof ServerPlayer player) {
            FocusCrafting.openFromCast(player, this, FocusCrafting.Upgrades.of(player, ctx::setting));
        }
    }

    @Override
    public int maxChildren() {
        return 0;
    }
}
