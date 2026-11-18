package com.automagy.infusion;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionInput;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionJobRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * An infusion that raises one vanilla enchantment on the catalyst by a level, keeping everything else on it (the
 * fishing enchantments). Thaumaturge's own infusion enchanting only knows its own enchantments, so this is a job of its
 * own, found by the infusion matrix through {@code InfusionMatcherMixin}. The essentia is per level: the next level
 * costs {@code aspects} times that level.
 * <pre>{"type": "automagia:infusion_enchanting", "enchantment": "minecraft:lure", "catalyst": "minecraft:fishing_rod",
 *  "components": [...], "aspects": [...], "instability": 2, "research": {"entry": "automagia:enchant_fishing"}}</pre>
 */
public record InfusionEnchantingRecipe(Holder<Enchantment> enchantment, Ingredient catalyst, List<Ingredient> components,
                                       AspectList aspects, int instability, Optional<ResearchGate> research)
        implements InfusionJobRecipe {
    public static final MapCodec<InfusionEnchantingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Enchantment.CODEC.fieldOf("enchantment").forGetter(InfusionEnchantingRecipe::enchantment),
            Ingredient.CODEC.fieldOf("catalyst").forGetter(InfusionEnchantingRecipe::catalyst),
            Ingredient.CODEC.listOf().fieldOf("components").forGetter(InfusionEnchantingRecipe::components),
            AspectList.CODEC.fieldOf("aspects").forGetter(InfusionEnchantingRecipe::aspects),
            Codec.INT.optionalFieldOf("instability", 2).forGetter(InfusionEnchantingRecipe::instability),
            ResearchGate.CODEC.optionalFieldOf("research").forGetter(InfusionEnchantingRecipe::research))
            .apply(i, InfusionEnchantingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionEnchantingRecipe> STREAM_CODEC = StreamCodec.composite(
            Enchantment.STREAM_CODEC, InfusionEnchantingRecipe::enchantment,
            Ingredient.CONTENTS_STREAM_CODEC, InfusionEnchantingRecipe::catalyst,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), InfusionEnchantingRecipe::components,
            AspectList.STREAM_CODEC, InfusionEnchantingRecipe::aspects,
            ByteBufCodecs.VAR_INT, InfusionEnchantingRecipe::instability,
            ByteBufCodecs.optional(ResearchGate.STREAM_CODEC), InfusionEnchantingRecipe::research,
            InfusionEnchantingRecipe::new);
    public static final RecipeSerializer<InfusionEnchantingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private int nextLevel(ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) + 1;
    }

    @Override
    public boolean matches(InfusionInput input, Level level) {
        ItemStack stack = input.catalyst();
        if (!catalyst.test(stack) || nextLevel(stack) > enchantment.value().getMaxLevel()) {
            return false;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0
                || EnchantmentHelper.isEnchantmentCompatible(EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet(), enchantment);
    }

    @Override
    public AspectList jobEssentia(InfusionInput input) {
        int level = nextLevel(input.catalyst());
        return AspectList.ofEntries(aspects.entries().stream()
                .map(e -> new AspectInstance(e.aspect(), e.amount() * level)).toList());
    }

    @Override
    public ItemStack jobResult(InfusionInput input, RandomSource random) {
        return assemble(input);
    }

    @Override
    public ItemStack assemble(InfusionInput input) {
        ItemStack out = input.catalyst().copyWithCount(1);
        int level = nextLevel(out);
        EnchantmentHelper.updateEnchantments(out, m -> m.set(enchantment, level));
        return out;
    }

    @Override
    public ItemStack resultItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public Optional<ResearchGate> researchGate() {
        return research;
    }

    @Override
    public RecipeSerializer<InfusionEnchantingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<InfusionEnchantingRecipe> getType() {
        return AMRegistry.INFUSION_ENCHANTING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }
}
