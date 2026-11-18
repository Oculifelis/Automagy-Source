package com.automagy.compat;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.automagy.infusion.InfusionEnchantingRecipe;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI: information pages for the blocks that work in the world (redcrystal circuits, hourglasses, the hungry maw, the
 * remote comparator, the vis reader, the crystalline eye), reusing their research text. Every item has an arcane or
 * crafting recipe, which Thaumaturge and JEI already show. Automagy's own infusion enchanting (raising a vanilla
 * enchantment such as Lure by one level) gets a category of its own. Loaded only with JEI.
 */
@JeiPlugin
public final class AMJeiPlugin implements IModPlugin {
    /** Item -> the research entry whose second page explains it. */
    private static final Map<String, String> INFO = Map.ofEntries(
            Map.entry("glowing_amber", "glowing_amber"), Map.entry("hungry_maw", "hungry_maw"),
            Map.entry("inversion_torch", "redstone_theory"), Map.entry("hourglass", "redstone_theory"),
            Map.entry("magic_hourglass", "magic_hourglass"), Map.entry("redcrystal", "redcrystal"),
            Map.entry("redcrystal_amp", "redcrystal_amp"), Map.entry("redcrystal_dim", "redcrystal_dim"),
            Map.entry("redcrystal_dense", "redcrystal_dense"), Map.entry("redcrystal_res", "redcrystal_res"),
            Map.entry("redcrystal_merc", "redcrystal_merc"), Map.entry("remote_comparator", "remote_comparator"),
            Map.entry("crystalline_eye", "remote_comparator"), Map.entry("vis_reader", "vis_reader"),
            Map.entry("sliver_aer", "slivers"), Map.entry("sliver_ignis", "slivers"), Map.entry("sliver_aqua", "slivers"),
            Map.entry("sliver_terra", "slivers"), Map.entry("sliver_ordo", "slivers"), Map.entry("sliver_perditio", "slivers"),
            Map.entry("runic_whitelist", "enchanted_paper"), Map.entry("runic_blacklist", "enchanted_paper"),
            Map.entry("enchanting_bookshelf", "enchanted_paper"), Map.entry("greedy_chest", "greedy_chest"),
            Map.entry("avarice_pearl", "greedy_chest"),
            Map.entry("shattered_avarice_pearl", "greedy_chest"), Map.entry("misers_pearl", "avaricious"),
            Map.entry("eager_chest", "eager_chest"), Map.entry("tenacious_chest", "tenacious_chest"),
            Map.entry("finical_maw", "finical_maw"), Map.entry("hungry_hand_mirror", "auto_hand_mirror"),
            Map.entry("tally_box", "tally_box"), Map.entry("tally_lens_unbound", "tally_box_lens"),
            Map.entry("tally_lens_yield", "tally_box_lens"), Map.entry("melding_mirror", "melding_mirror"),
            Map.entry("conflux_disc", "melding_mirror"), Map.entry("thirsty_tank", "thirsty_tank"),
            Map.entry("aqueous_admixture", "thirsty_tank_glyph"), Map.entry("glyph_void", "thirsty_tank_glyph"),
            Map.entry("glyph_consumption", "thirsty_tank_glyph"), Map.entry("glyph_siphoning", "thirsty_tank_glyph"),
            Map.entry("glyph_envy", "thirsty_tank_glyph"), Map.entry("glyph_temperance", "thirsty_tank_glyph"),
            Map.entry("glyph_preservation", "thirsty_tank_glyph"), Map.entry("glyph_guzzler", "thirsty_tank_glyph"),
            Map.entry("glyph_reservoir", "thirsty_tank_glyph"), Map.entry("glyph_bovine", "thirsty_tank_glyph_bovine"),
            Map.entry("vishroom_stew", "vishroom_soup"), Map.entry("vishroom_soup_bucket", "vishroom_soup"),
            Map.entry("alchemical_boiler", "alchemy_boiler"), Map.entry("essentia_locus", "essentia_locus"),
            Map.entry("essentia_aggregator", "essentia_aggregator"), Map.entry("crystal_brain_aer", "crystal_brain"),
            Map.entry("crystal_brain_ignis", "crystal_brain"), Map.entry("crystal_brain_aqua", "crystal_brain"),
            Map.entry("crystal_brain_terra", "crystal_brain"), Map.entry("crystal_brain_ordo", "crystal_brain"),
            Map.entry("crystal_brain_perditio", "crystal_brain"), Map.entry("golem_linker", "golem_linker"),
            Map.entry("golem_inhibitor", "golem_inhibitor"), Map.entry("entity_eye", "entity_signal"),
            Map.entry("entity_signal", "entity_signal"), Map.entry("golem_workbench", "golem_workbench"),
            Map.entry("workbench_upgrade_aer", "workbench_upgrades"),
            Map.entry("workbench_upgrade_ignis", "workbench_upgrades"),
            Map.entry("workbench_upgrade_aqua", "workbench_upgrades"),
            Map.entry("workbench_upgrade_terra", "workbench_upgrades"),
            Map.entry("workbench_upgrade_ordo", "workbench_upgrades"),
            Map.entry("workbench_upgrade_perditio", "workbench_upgrades"),
            Map.entry("thaumostatic_pylon", "thaumostatic_spire"),
            Map.entry("thaumostatic_controller", "thaumostatic_spire"),
            Map.entry("inventarium_core", "inventarium"), Map.entry("unseen_scribe", "inventarium"),
            Map.entry("ensorcelled_bookshelf", "inventarium_extra"), Map.entry("scrivener_oculus", "inventarium_pointer"),
            Map.entry("memory_block", "inventarium_delivery"), Map.entry("golem_taskmaster", "inventarium_delivery"),
            Map.entry("requisition_tome", "inventarium_delivery"), Map.entry("dispersing_mirror", "mirror_multidest"),
            Map.entry("ender_disjunction_ring", "ender_disjunction"),
            Map.entry("lesser_ender_disjunction_ring", "ender_disjunction"),
            Map.entry("runed_obsidian", "nethermind"), Map.entry("runed_obsidian_rock", "nethermind"),
            Map.entry("runed_obsidian_sea", "nethermind"), Map.entry("runed_obsidian_light", "nethermind"),
            Map.entry("runed_obsidian_pebble", "nethermind"), Map.entry("runed_obsidian_crystal", "netherrune_crystal"),
            Map.entry("runed_obsidian_soul", "netherrune_soul"), Map.entry("runed_obsidian_celerity", "netherrune_celerity"),
            Map.entry("runed_obsidian_zombie", "netherrune_zombie"), Map.entry("runed_obsidian_blaze", "netherrune_zombie"),
            Map.entry("runed_obsidian_ghast", "netherrune_ghast"), Map.entry("runed_obsidian_skull", "netherrune_skeleton"),
            Map.entry("runed_obsidian_slime", "netherrune_slime"), Map.entry("runed_obsidian_bat", "netherrune_bat"),
            Map.entry("runed_obsidian_wisp", "netherrune_wisp"), Map.entry("nethermind", "nethermind"),
            Map.entry("ember_heart", "nethermind"), Map.entry("dimensional_lure", "dimensionlure"),
            Map.entry("siren_lure", "moblure"), Map.entry("amnesiac_stone", "amnesiastone"),
            Map.entry("soul_stone", "xpstone"), Map.entry("concentrate_jar", "xpstone"),
            Map.entry("concentrate_phial", "xpstone"));

    @Override
    public ResourceLocation getPluginUid() {
        return Automagy.id("jei");
    }

    private static final RecipeType<JeiKit.Conversion> INFUSION_ENCHANTING = JeiKit.type(Automagy.id("infusion_enchanting"));

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new JeiKit.ConversionCategory(registration.getJeiHelpers().getGuiHelper(),
                INFUSION_ENCHANTING, Component.translatable("jei.automagia.infusion_enchanting"), Items.ENCHANTED_BOOK));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        JeiKit.stations(registration, INFUSION_ENCHANTING, TTItems.INFUSION_MATRIX.get());
    }

    private static List<ItemStack> stacks(Ingredient ingredient) {
        return List.of(ingredient.getItems());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(INFUSION_ENCHANTING, JeiKit.recipes(AMRegistry.INFUSION_ENCHANTING.get()).stream().<JeiKit.Conversion>map(holder -> {
            InfusionEnchantingRecipe r = holder.value();
            JeiKit.Builder b = JeiKit.Conversion.builder().in(stacks(r.catalyst()));
            r.components().forEach(c -> b.in(stacks(c)));
            List<ItemStack> out = stacks(r.catalyst()).stream().map(s -> {
                ItemStack e = s.copy();
                e.enchant(r.enchantment(), 1);
                return e;
            }).toList();
            return b.station(TTItems.INFUSION_MATRIX.get()).aspects(r.aspects()).out(out.toArray(ItemStack[]::new))
                    .note(Component.translatable("jei.automagia.infusion_enchanting.note")).build();
        }).toList());
        INFO.forEach((name, research) -> BuiltInRegistries.ITEM.getOptional(Automagy.id(name)).ifPresent(item ->
                registration.addIngredientInfo(item, Component.translatable("research.automagia." + research + ".stage_1"))));
    }
}
