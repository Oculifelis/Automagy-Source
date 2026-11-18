package com.automagy;

import net.minecraft.resources.ResourceLocation;

import com.automagy.nether.NethermindBlock;
import com.automagy.nether.NethermindBlockEntity;
import com.automagy.nether.NetherRune;
import com.automagy.nether.SirenLureBlock;
import com.automagy.nether.SirenLureBlockEntity;
import com.automagy.xp.AmnesiacStoneBlock;
import com.automagy.xp.AmnesiacStoneBlockEntity;
import com.automagy.xp.ConcentrateJarBlock;
import com.automagy.xp.ConcentrateJarBlockEntity;
import com.automagy.xp.ConcentratePhialItem;
import com.automagy.xp.SoulStoneBlock;
import com.automagy.xp.SoulStoneBlockEntity;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import com.automagy.archive.ArchiveMenu;
import com.automagy.archive.DispersingMirrorBlock;
import com.automagy.archive.DispersingMirrorBlockEntity;
import com.automagy.archive.GolemTaskmasterBlock;
import com.automagy.archive.GolemTaskmasterBlockEntity;
import com.automagy.archive.InventariumCoreBlock;
import com.automagy.archive.InventariumCoreBlockEntity;
import com.automagy.archive.RequisitionTomeBlock;
import com.automagy.archive.RequisitionTomeBlockEntity;
import com.automagy.archive.ScrivenerOculusBlock;
import com.automagy.archive.ScrivenerOculusBlockEntity;
import com.automagy.archive.UnseenScribeBlock;
import com.automagy.archive.UnseenScribeBlockEntity;
import com.automagy.focus.CraftingGridEffect;
import com.automagy.focus.FocusCraftingMenu;
import com.automagy.infusion.InfusionEnchantingRecipe;
import com.automagy.ring.EnderDisjunctionRingItem;
import com.leclowndu93150.thaumaturge.api.spell.behavior.SpellBehaviorType;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPartKind;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeType;
import com.automagy.block.AmplifiedRedcrystalBlock;
import com.automagy.block.DeviceChests;
import com.automagy.block.EagerChestBlockEntity;
import com.automagy.block.EnchantingBookshelfBlock;
import com.automagy.block.FinicalMawBlock;
import com.automagy.block.FinicalMawBlockEntity;
import com.automagy.block.GreedyChestBlockEntity;
import com.automagy.block.HourglassBlock;
import com.automagy.block.MeldingMirrorBlock;
import com.automagy.block.MeldingMirrorBlockEntity;
import com.automagy.block.TallyBoxBlock;
import com.automagy.block.TallyBoxBlockEntity;
import com.automagy.block.TenaciousChestBlockEntity;
import com.automagy.entity.AvaricePearlEntity;
import com.automagy.filter.ListCopyRecipe;
import com.automagy.filter.RunicList;
import com.automagy.item.AvaricePearlItem;
import com.automagy.item.HungryHandMirrorItem;
import com.automagy.item.RunicListItem;
import com.automagy.item.TallyLensItem;
import com.automagy.menu.RunicListMenu;
import com.automagy.boiler.BoilerBlock;
import com.automagy.boiler.BoilerBlockEntity;
import com.automagy.golem.EntityEyeItem;
import com.automagy.golem.EntitySignalBlock;
import com.automagy.golem.EntitySignalBlockEntity;
import com.automagy.golem.EntityWatch;
import com.automagy.golem.GolemInhibitorBlock;
import com.automagy.golem.GolemWorkbenchBlock;
import com.automagy.golem.GolemWorkbenchBlockEntity;
import com.automagy.golem.GolemWorkbenchMenu;
import com.automagy.golem.SpireControllerBlock;
import com.automagy.golem.SpireControllerBlockEntity;
import com.automagy.golem.GolemInhibitorBlockEntity;
import com.automagy.golem.GolemLinkerItem;
import com.automagy.locus.EssentiaLocusBlock;
import com.automagy.locus.EssentiaLocusBlockEntity;
import com.automagy.tank.ThirstyTankBlock;
import com.automagy.tank.VishroomSoupFluid;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.automagy.tank.ThirstyTankBlockEntity;
import com.automagy.block.HourglassBlockEntity;
import com.automagy.block.HungryMawBlock;
import com.automagy.block.HungryMawBlockEntity;
import com.automagy.block.InversionTorchBlock;
import com.automagy.block.LimitRedcrystalBlock;
import com.automagy.block.MercurialRedcrystalBlock;
import com.automagy.block.MercurialRedcrystalBlockEntity;
import com.automagy.block.RedcrystalBlock;
import com.automagy.block.RemoteComparatorBlock;
import com.automagy.block.RemoteComparatorBlockEntity;
import com.automagy.block.ResonatingRedcrystalBlock;
import com.automagy.block.VisReaderBlock;
import com.automagy.block.VisReaderBlockEntity;
import com.automagy.item.CrystallineEyeItem;
import com.automagy.item.MercurialRedcrystalItem;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.attachment.AttachmentType;

public final class AMRegistry {
    private static final String MODID = Automagy.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static final List<Supplier<? extends Item>> TAB_ITEMS = new ArrayList<>();

    /** Primal slivers: a quarter of a Thaumaturge vis crystal each (aer, ignis, aqua, terra, ordo, perditio). */
    public static final List<DeferredItem<Item>> SLIVERS = List.of(sliver("aer"), sliver("ignis"), sliver("aqua"),
            sliver("terra"), sliver("ordo"), sliver("perditio"));

    /** Glowing amber is a little translucent (its texture has partial alpha), so it behaves like glass: no occlusion,
     *  faces between two amber blocks are skipped, and it does not suffocate or block the view. */
    public static final DeferredBlock<TransparentBlock> GLOWING_AMBER = block("glowing_amber", TransparentBlock::new,
            AMRegistry::amber);
    public static final DeferredBlock<TransparentBlock> GLOWING_AMBER_BRICKS = block("glowing_amber_bricks",
            TransparentBlock::new, AMRegistry::amber);

    public static final DeferredBlock<HungryMawBlock> HUNGRY_MAW = block("hungry_maw", HungryMawBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5f).sound(SoundType.STONE).noCollission().noOcclusion()
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HungryMawBlockEntity>> HUNGRY_MAW_BE = BLOCK_ENTITIES.register("hungry_maw",
            () -> BlockEntityType.Builder.of(HungryMawBlockEntity::new, HUNGRY_MAW.get()).build(null));

    /** A bound place: the mirror a mercurial redcrystal is bound to, or the block a crystalline eye watches. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINK = COMPONENTS.registerComponentType("link",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    // ---- phase 2: redstone devices
    public static final DeferredBlock<InversionTorchBlock> INVERSION_TORCH = block("inversion_torch", InversionTorchBlock::new,
            p -> p.noCollission().instabreak().sound(SoundType.WOOD).lightLevel(s -> s.getValue(InversionTorchBlock.POWER) > 0 ? 5 : 0)
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<HourglassBlock> HOURGLASS = block("hourglass", HourglassBlock::new, AMRegistry::hourglass);
    public static final DeferredBlock<HourglassBlock.Magic> MAGIC_HOURGLASS = block("magic_hourglass", HourglassBlock.Magic::new,
            AMRegistry::hourglass);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HourglassBlockEntity>> HOURGLASS_BE = BLOCK_ENTITIES.register("hourglass",
            () -> BlockEntityType.Builder.of(HourglassBlockEntity::new, HOURGLASS.get(), MAGIC_HOURGLASS.get()).build(null));

    public static final DeferredBlock<RedcrystalBlock> REDCRYSTAL = block("redcrystal", RedcrystalBlock::new, AMRegistry::redcrystal);
    public static final DeferredBlock<AmplifiedRedcrystalBlock> REDCRYSTAL_AMP = block("redcrystal_amp", AmplifiedRedcrystalBlock::new,
            AMRegistry::redcrystal);
    public static final DeferredBlock<LimitRedcrystalBlock.Dim> REDCRYSTAL_DIM = block("redcrystal_dim", LimitRedcrystalBlock.Dim::new,
            AMRegistry::redcrystal);
    public static final DeferredBlock<LimitRedcrystalBlock.Dense> REDCRYSTAL_DENSE = block("redcrystal_dense", LimitRedcrystalBlock.Dense::new,
            AMRegistry::redcrystal);
    public static final DeferredBlock<ResonatingRedcrystalBlock> REDCRYSTAL_RES = block("redcrystal_res", ResonatingRedcrystalBlock::new,
            AMRegistry::redcrystal);
    public static final DeferredBlock<MercurialRedcrystalBlock> REDCRYSTAL_MERC = blockP("redcrystal_merc",
            MercurialRedcrystalBlock::new, AMRegistry::redcrystal);
    public static final DeferredItem<MercurialRedcrystalItem> REDCRYSTAL_MERC_ITEM = tabbed(ITEMS.registerItem("redcrystal_merc",
            p -> new MercurialRedcrystalItem(REDCRYSTAL_MERC.get(), p)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MercurialRedcrystalBlockEntity>> MERCURIAL_REDCRYSTAL_BE =
            BLOCK_ENTITIES.register("redcrystal_merc", () -> BlockEntityType.Builder.of(MercurialRedcrystalBlockEntity::new, REDCRYSTAL_MERC.get()).build(null));

    public static final DeferredItem<CrystallineEyeItem> CRYSTALLINE_EYE = tabbed(ITEMS.registerItem("crystalline_eye",
            p -> new CrystallineEyeItem(p.stacksTo(16))));
    public static final DeferredBlock<RemoteComparatorBlock> REMOTE_COMPARATOR = block("remote_comparator", RemoteComparatorBlock::new,
            p -> p.mapColor(MapColor.STONE).instabreak().sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RemoteComparatorBlockEntity>> REMOTE_COMPARATOR_BE =
            BLOCK_ENTITIES.register("remote_comparator", () -> BlockEntityType.Builder.of(RemoteComparatorBlockEntity::new, REMOTE_COMPARATOR.get()).build(null));
    public static final DeferredBlock<VisReaderBlock> VIS_READER = block("vis_reader", VisReaderBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(0.5f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VisReaderBlockEntity>> VIS_READER_BE =
            BLOCK_ENTITIES.register("vis_reader", () -> BlockEntityType.Builder.of(VisReaderBlockEntity::new, VIS_READER.get()).build(null));

    // ---- phase 3: lists, chests, maws, tally box, mirrors
    /** What a runic whitelist or blacklist names. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RunicList>> RUNIC_LIST = COMPONENTS.registerComponentType("runic_list",
            b -> b.persistent(RunicList.CODEC).networkSynchronized(RunicList.STREAM_CODEC));
    public static final DeferredHolder<MenuType<?>, MenuType<RunicListMenu>> RUNIC_LIST_MENU = MENUS.register("runic_list",
            () -> IMenuTypeExtension.create(RunicListMenu::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ListCopyRecipe>> LIST_COPY = RECIPE_SERIALIZERS.register("list_copy",
            () -> ListCopyRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<com.automagy.filter.ListTransmuteRecipe>> LIST_TRANSMUTE = RECIPE_SERIALIZERS.register("list_transmute",
            () -> com.automagy.filter.ListTransmuteRecipe.SERIALIZER);

    public static final DeferredItem<Item> ENCHANTED_PAPER = simple("enchanted_paper");
    public static final DeferredItem<RunicListItem> RUNIC_WHITELIST = tabbed(ITEMS.registerItem("runic_whitelist",
            p -> new RunicListItem(true, p.stacksTo(16))));
    public static final DeferredItem<RunicListItem> RUNIC_BLACKLIST = tabbed(ITEMS.registerItem("runic_blacklist",
            p -> new RunicListItem(false, p.stacksTo(16))));
    public static final DeferredItem<Item> BOUND_ENCHANTING_PAPER = simple("bound_enchanting_paper");
    public static final DeferredBlock<EnchantingBookshelfBlock> ENCHANTING_BOOKSHELF = block("enchanting_bookshelf", EnchantingBookshelfBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).ignitedByLava());

    public static final DeferredBlock<DeviceChests.Greedy> GREEDY_CHEST = block("greedy_chest", DeviceChests.Greedy::new, AMRegistry::chest);
    public static final DeferredBlock<DeviceChests.Eager> EAGER_CHEST = block("eager_chest", DeviceChests.Eager::new, AMRegistry::chest);
    public static final DeferredBlock<DeviceChests.Tenacious> TENACIOUS_CHEST = block("tenacious_chest", DeviceChests.Tenacious::new, AMRegistry::chest);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GreedyChestBlockEntity>> GREEDY_CHEST_BE = BLOCK_ENTITIES.register("greedy_chest",
            () -> BlockEntityType.Builder.of(GreedyChestBlockEntity::new, GREEDY_CHEST.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EagerChestBlockEntity>> EAGER_CHEST_BE = BLOCK_ENTITIES.register("eager_chest",
            () -> BlockEntityType.Builder.of(EagerChestBlockEntity::new, EAGER_CHEST.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TenaciousChestBlockEntity>> TENACIOUS_CHEST_BE = BLOCK_ENTITIES.register("tenacious_chest",
            () -> BlockEntityType.Builder.of(TenaciousChestBlockEntity::new, TENACIOUS_CHEST.get()).build(null));

    public static final DeferredItem<AvaricePearlItem> AVARICE_PEARL = tabbed(ITEMS.registerItem("avarice_pearl",
            p -> new AvaricePearlItem(false, p.stacksTo(16))));
    public static final DeferredItem<AvaricePearlItem> MISERS_PEARL = tabbed(ITEMS.registerItem("misers_pearl",
            p -> new AvaricePearlItem(true, p.stacksTo(16).rarity(Rarity.UNCOMMON))));
    public static final DeferredItem<Item> SHATTERED_AVARICE_PEARL = simple("shattered_avarice_pearl");
    public static final DeferredHolder<EntityType<?>, EntityType<AvaricePearlEntity>> AVARICE_PEARL_ENTITY = ENTITIES.register("avarice_pearl",
            () -> EntityType.Builder.<AvaricePearlEntity>of(AvaricePearlEntity::new, MobCategory.MISC).sized(0.25f, 0.25f)
                    .clientTrackingRange(4).updateInterval(10).build("avarice_pearl"));

    public static final DeferredBlock<FinicalMawBlock> FINICAL_MAW = block("finical_maw", FinicalMawBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5f).sound(SoundType.STONE).noCollission().noOcclusion()
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FinicalMawBlockEntity>> FINICAL_MAW_BE = BLOCK_ENTITIES.register("finical_maw",
            () -> BlockEntityType.Builder.of(FinicalMawBlockEntity::new, FINICAL_MAW.get()).build(null));

    public static final DeferredBlock<TallyBoxBlock> TALLY_BOX = block("tally_box", TallyBoxBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).isRedstoneConductor((s, l, pos) -> false));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TallyBoxBlockEntity>> TALLY_BOX_BE = BLOCK_ENTITIES.register("tally_box",
            () -> BlockEntityType.Builder.of(TallyBoxBlockEntity::new, TALLY_BOX.get()).build(null));
    public static final DeferredItem<TallyLensItem> TALLY_LENS_CLEAR = lens("tally_lens_clear", TallyBoxBlock.Lens.NONE);
    public static final DeferredItem<TallyLensItem> TALLY_LENS_UNBOUND = lens("tally_lens_unbound", TallyBoxBlock.Lens.UNBOUND);
    public static final DeferredItem<TallyLensItem> TALLY_LENS_YIELD = lens("tally_lens_yield", TallyBoxBlock.Lens.YIELD);

    public static final DeferredItem<HungryHandMirrorItem> HUNGRY_HAND_MIRROR = tabbed(ITEMS.registerItem("hungry_hand_mirror",
            p -> new HungryHandMirrorItem(p.stacksTo(1).rarity(Rarity.UNCOMMON))));
    public static final DeferredItem<Item> CONFLUX_DISC = simple("conflux_disc");
    public static final DeferredBlock<MeldingMirrorBlock> MELDING_MIRROR = block("melding_mirror", MeldingMirrorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeldingMirrorBlockEntity>> MELDING_MIRROR_BE = BLOCK_ENTITIES.register("melding_mirror",
            () -> BlockEntityType.Builder.of(MeldingMirrorBlockEntity::new, MELDING_MIRROR.get()).build(null));

    // ---- phase 4: liquids and essentia
    public static final DeferredBlock<ThirstyTankBlock> THIRSTY_TANK = block("thirsty_tank", ThirstyTankBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThirstyTankBlockEntity>> THIRSTY_TANK_BE = BLOCK_ENTITIES.register("thirsty_tank",
            () -> BlockEntityType.Builder.of(ThirstyTankBlockEntity::new, THIRSTY_TANK.get()).build(null));
    /** The aqua-imbued ink glyphs are pressed from; each glyph's effect is in the {@code automagia:glyph} data map. */
    public static final DeferredItem<Item> AQUEOUS_ADMIXTURE = simple("aqueous_admixture");
    public static final List<DeferredItem<Item>> GLYPHS = List.of(glyph("void"), glyph("consumption"), glyph("siphoning"),
            glyph("envy"), glyph("temperance"), glyph("preservation"), glyph("guzzler"), glyph("reservoir"), glyph("bovine"));

    /** Vishroom stew: a poor meal that leaves you queasy and seeing more than you should. */
    public static final DeferredItem<Item> VISHROOM_STEW = tabbed(ITEMS.registerItem("vishroom_stew", p -> new Item(p.stacksTo(1)
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.2f).usingConvertsTo(Items.BOWL)
                    .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 600), 1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 300), 1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 1200), 1.0f)
                    .build()))));
    /** Vishroom soup: the stew settled in a thirsty tank, a liquid that grows fungi and brings on the whispers. */
    public static final DeferredHolder<FluidType, FluidType> VISHROOM_SOUP_TYPE = FLUID_TYPES.register("vishroom_soup",
            () -> new FluidType(FluidType.Properties.create().descriptionId("block.automagia.vishroom_soup").density(1200)
                    .viscosity(1800).canDrown(true).canSwim(true).canExtinguish(true)));
    public static final DeferredHolder<Fluid, VishroomSoupFluid.Source> VISHROOM_SOUP = FLUIDS.register("vishroom_soup",
            () -> new VishroomSoupFluid.Source(soup()));
    public static final DeferredHolder<Fluid, VishroomSoupFluid.Flowing> VISHROOM_SOUP_FLOWING = FLUIDS.register("flowing_vishroom_soup",
            () -> new VishroomSoupFluid.Flowing(soup()));
    public static final DeferredBlock<LiquidBlock> VISHROOM_SOUP_BLOCK = blockP("vishroom_soup",
            p -> new VishroomSoupFluid.SoupBlock(VISHROOM_SOUP.get(), p), p -> p.mapColor(MapColor.COLOR_PURPLE).replaceable().noCollission()
                    .strength(100f).pushReaction(PushReaction.DESTROY).noLootTable().liquid().sound(SoundType.EMPTY));
    public static final DeferredItem<BucketItem> VISHROOM_SOUP_BUCKET = tabbed(ITEMS.registerItem("vishroom_soup_bucket",
            p -> new BucketItem(VISHROOM_SOUP.get(), p.craftRemainder(Items.BUCKET).stacksTo(1))));

    public static final DeferredBlock<BoilerBlock> BOILER = block("alchemical_boiler", BoilerBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3f).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(BoilerBlock.LIT) ? 9 : 0));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> BOILER_BE = BLOCK_ENTITIES.register("alchemical_boiler",
            () -> BlockEntityType.Builder.of(BoilerBlockEntity::new, BOILER.get()).build(null));

    public static final DeferredBlock<EssentiaLocusBlock> ESSENTIA_LOCUS = block("essentia_locus", EssentiaLocusBlock::new,
            p -> p.mapColor(MapColor.LAPIS).strength(2f).sound(SoundType.AMETHYST).lightLevel(s -> 6));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssentiaLocusBlockEntity>> ESSENTIA_LOCUS_BE = BLOCK_ENTITIES.register("essentia_locus",
            () -> BlockEntityType.Builder.of(EssentiaLocusBlockEntity::new, ESSENTIA_LOCUS.get()).build(null));
    public static final DeferredBlock<Block> ESSENTIA_AGGREGATOR = block("essentia_aggregator", Block::new,
            p -> p.mapColor(MapColor.LAPIS).strength(2f).sound(SoundType.AMETHYST).lightLevel(s -> 4));

    // ---- phase 5: crystal brains and golems
    public static final List<DeferredItem<Item>> CRYSTAL_BRAINS = Stream.of("aer", "ignis", "aqua", "terra", "ordo", "perditio")
            .map(a -> simple("crystal_brain_" + a)).toList();
    /** The device a golem is linked to with the golem linking bell. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GlobalPos>> GOLEM_LINK = ATTACHMENT_TYPES.register("golem_link",
            () -> AttachmentType.<GlobalPos>builder(() -> null).serialize(GlobalPos.CODEC).build());
    public static final DeferredItem<GolemLinkerItem> GOLEM_LINKER = tabbed(ITEMS.registerItem("golem_linker",
            p -> new GolemLinkerItem(p.stacksTo(1))));
    public static final DeferredBlock<GolemInhibitorBlock> GOLEM_INHIBITOR = block("golem_inhibitor", GolemInhibitorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2f).sound(SoundType.METAL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GolemInhibitorBlockEntity>> GOLEM_INHIBITOR_BE = BLOCK_ENTITIES.register("golem_inhibitor",
            () -> BlockEntityType.Builder.of(GolemInhibitorBlockEntity::new, GOLEM_INHIBITOR.get()).build(null));

    /** What an Oculo Anima watches for. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EntityWatch>> ENTITY_WATCH = COMPONENTS.registerComponentType("entity_watch",
            b -> b.persistent(EntityWatch.CODEC).networkSynchronized(EntityWatch.STREAM_CODEC));
    public static final DeferredItem<EntityEyeItem> ENTITY_EYE = tabbed(ITEMS.registerItem("entity_eye",
            p -> new EntityEyeItem(p.stacksTo(16))));
    public static final DeferredBlock<EntitySignalBlock> ENTITY_SIGNAL = block("entity_signal", EntitySignalBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.STONE));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EntitySignalBlockEntity>> ENTITY_SIGNAL_BE = BLOCK_ENTITIES.register("entity_signal",
            () -> BlockEntityType.Builder.of(EntitySignalBlockEntity::new, ENTITY_SIGNAL.get()).build(null));

    public static final DeferredBlock<GolemWorkbenchBlock> GOLEM_WORKBENCH = block("golem_workbench", GolemWorkbenchBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GolemWorkbenchBlockEntity>> GOLEM_WORKBENCH_BE = BLOCK_ENTITIES.register("golem_workbench",
            () -> BlockEntityType.Builder.of(GolemWorkbenchBlockEntity::new, GOLEM_WORKBENCH.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<GolemWorkbenchMenu>> GOLEM_WORKBENCH_MENU = MENUS.register("golem_workbench",
            () -> IMenuTypeExtension.create(GolemWorkbenchMenu::new));
    /** Workbench upgrades, one per primal; what each does is the item data map automagia:workbench_upgrade. */
    public static final List<DeferredItem<Item>> WORKBENCH_UPGRADES = Stream.of("aer", "ignis", "aqua", "terra", "ordo", "perditio")
            .map(a -> simple("workbench_upgrade_" + a)).toList();

    public static final DeferredBlock<Block> SPIRE_PYLON = block("thaumostatic_pylon", Block::new,
            p -> p.mapColor(MapColor.QUARTZ).strength(2f).sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredBlock<SpireControllerBlock> SPIRE_CONTROLLER = block("thaumostatic_controller", SpireControllerBlock::new,
            p -> p.mapColor(MapColor.QUARTZ).strength(2f).sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpireControllerBlockEntity>> SPIRE_CONTROLLER_BE = BLOCK_ENTITIES.register("thaumostatic_controller",
            () -> BlockEntityType.Builder.of(SpireControllerBlockEntity::new, SPIRE_CONTROLLER.get()).build(null));

    // ---- phase 6: the Inventarium, the crafting focus, ender disjunction, fishing enchantments
    public static final DeferredRegister<SpellBehaviorType<?>> SPELL_BEHAVIORS = DeferredRegister.create(SpellBehaviorType.REGISTRY_KEY, MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, MODID);

    public static final DeferredBlock<EnchantingBookshelfBlock> ENSORCELLED_BOOKSHELF = block("ensorcelled_bookshelf",
            p -> new EnchantingBookshelfBlock(p, EnchantingBookshelfBlock.ENSORCELLED_POWER),
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(1.5f).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredBlock<InventariumCoreBlock> INVENTARIUM_CORE = block("inventarium_core", InventariumCoreBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2f).sound(SoundType.AMETHYST).lightLevel(s -> 7));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InventariumCoreBlockEntity>> INVENTARIUM_CORE_BE = BLOCK_ENTITIES.register("inventarium_core",
            () -> BlockEntityType.Builder.of(InventariumCoreBlockEntity::new, INVENTARIUM_CORE.get()).build(null));
    public static final DeferredBlock<UnseenScribeBlock> UNSEEN_SCRIBE = block("unseen_scribe", UnseenScribeBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UnseenScribeBlockEntity>> UNSEEN_SCRIBE_BE = BLOCK_ENTITIES.register("unseen_scribe",
            () -> BlockEntityType.Builder.of(UnseenScribeBlockEntity::new, UNSEEN_SCRIBE.get()).build(null));
    public static final DeferredBlock<ScrivenerOculusBlock> SCRIVENER_OCULUS = block("scrivener_oculus", ScrivenerOculusBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScrivenerOculusBlockEntity>> SCRIVENER_OCULUS_BE = BLOCK_ENTITIES.register("scrivener_oculus",
            () -> BlockEntityType.Builder.of(ScrivenerOculusBlockEntity::new, SCRIVENER_OCULUS.get()).build(null));
    public static final DeferredBlock<Block> MEMORY_BLOCK = block("memory_block", Block::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2f).sound(SoundType.AMETHYST).lightLevel(s -> 3));
    public static final DeferredBlock<GolemTaskmasterBlock> GOLEM_TASKMASTER = block("golem_taskmaster", GolemTaskmasterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2f).sound(SoundType.METAL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GolemTaskmasterBlockEntity>> GOLEM_TASKMASTER_BE = BLOCK_ENTITIES.register("golem_taskmaster",
            () -> BlockEntityType.Builder.of(GolemTaskmasterBlockEntity::new, GOLEM_TASKMASTER.get()).build(null));
    public static final DeferredBlock<RequisitionTomeBlock> REQUISITION_TOME = block("requisition_tome", RequisitionTomeBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RequisitionTomeBlockEntity>> REQUISITION_TOME_BE = BLOCK_ENTITIES.register("requisition_tome",
            () -> BlockEntityType.Builder.of(RequisitionTomeBlockEntity::new, REQUISITION_TOME.get()).build(null));
    public static final DeferredBlock<DispersingMirrorBlock> DISPERSING_MIRROR = block("dispersing_mirror", DispersingMirrorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DispersingMirrorBlockEntity>> DISPERSING_MIRROR_BE = BLOCK_ENTITIES.register("dispersing_mirror",
            () -> BlockEntityType.Builder.of(DispersingMirrorBlockEntity::new, DISPERSING_MIRROR.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ArchiveMenu>> ARCHIVE_MENU = MENUS.register("archive",
            () -> IMenuTypeExtension.create(ArchiveMenu::new));

    /** What a storage-upgraded crafting focus keeps from its grid. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> STORED_GRID = COMPONENTS.registerComponentType("stored_grid",
            b -> b.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));
    public static final DeferredHolder<SpellBehaviorType<?>, SpellBehaviorType<CraftingGridEffect>> CRAFTING_GRID = SPELL_BEHAVIORS.register("crafting_grid",
            () -> new SpellBehaviorType<>(SpellPartKind.EFFECT, CraftingGridEffect.CODEC));
    public static final DeferredHolder<MenuType<?>, MenuType<FocusCraftingMenu>> FOCUS_CRAFTING_MENU = MENUS.register("focus_crafting",
            () -> IMenuTypeExtension.create(FocusCraftingMenu::new));

    public static final DeferredItem<EnderDisjunctionRingItem> ENDER_DISJUNCTION_RING = tabbed(ITEMS.registerItem("ender_disjunction_ring",
            p -> new EnderDisjunctionRingItem(p.stacksTo(1).rarity(Rarity.RARE))));
    public static final DeferredItem<EnderDisjunctionRingItem> LESSER_ENDER_DISJUNCTION_RING = tabbed(ITEMS.registerItem("lesser_ender_disjunction_ring",
            p -> new EnderDisjunctionRingItem(p.stacksTo(1).rarity(Rarity.UNCOMMON))));

    public static final DeferredHolder<RecipeType<?>, RecipeType<InfusionEnchantingRecipe>> INFUSION_ENCHANTING = RECIPE_TYPES.register("infusion_enchanting",
            () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return "automagia:infusion_enchanting";
                }
            });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<InfusionEnchantingRecipe>> INFUSION_ENCHANTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("infusion_enchanting", () -> InfusionEnchantingRecipe.SERIALIZER);

    /** Phase 7, the Nethermind: an ember heart, the runed obsidian (see {@link NetherRune}), the brain and the lures. */
    public static final DeferredItem<Item> EMBER_HEART = tabbed(ITEMS.registerSimpleItem("ember_heart", new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final List<String> RUNE_NAMES = List.of("", "rock", "sea", "crystal", "light", "soul", "pebble", "celerity",
            "zombie", "blaze", "ghast", "skull", "slime", "bat", "wisp");
    public static final Map<String, DeferredBlock<Block>> RUNED_OBSIDIAN = runes();
    public static final DeferredBlock<NethermindBlock> NETHERMIND = block("nethermind", NethermindBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(3f, 1200f).sound(SoundType.GLASS).lightLevel(s -> 9).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NethermindBlockEntity>> NETHERMIND_BE = BLOCK_ENTITIES.register("nethermind",
            () -> BlockEntityType.Builder.of(NethermindBlockEntity::new, NETHERMIND.get()).build(null));
    public static final DeferredBlock<SirenLureBlock> SIREN_LURE = block("siren_lure", SirenLureBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(20f, 1200f).sound(SoundType.STONE).lightLevel(s -> 5).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SirenLureBlockEntity>> SIREN_LURE_BE = BLOCK_ENTITIES.register("siren_lure",
            () -> BlockEntityType.Builder.of(SirenLureBlockEntity::new, SIREN_LURE.get()).build(null));
    public static final DeferredBlock<Block> DIMENSIONAL_LURE = block("dimensional_lure", Block::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(20f, 1200f).sound(SoundType.STONE).lightLevel(s -> 7).requiresCorrectToolForDrops());

    /** Phase 7, experience: the Amnesiac's Stone, the Soul Stone and enchanter's concentrate. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_XP = COMPONENTS.registerComponentType("stored_xp",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredBlock<AmnesiacStoneBlock> AMNESIAC_STONE = block("amnesiac_stone", AmnesiacStoneBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmnesiacStoneBlockEntity>> AMNESIAC_STONE_BE = BLOCK_ENTITIES.register("amnesiac_stone",
            () -> BlockEntityType.Builder.of(AmnesiacStoneBlockEntity::new, AMNESIAC_STONE.get()).build(null));
    public static final DeferredBlock<SoulStoneBlock> SOUL_STONE = block("soul_stone", SoulStoneBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(2f, 6f).sound(SoundType.STONE).lightLevel(s -> 4).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulStoneBlockEntity>> SOUL_STONE_BE = BLOCK_ENTITIES.register("soul_stone",
            () -> BlockEntityType.Builder.of(SoulStoneBlockEntity::new, SOUL_STONE.get()).build(null));
    public static final DeferredBlock<ConcentrateJarBlock> CONCENTRATE_JAR = blockP("concentrate_jar", ConcentrateJarBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.3f).sound(SoundType.GLASS).lightLevel(s -> 6).noOcclusion());
    public static final DeferredItem<BlockItem> CONCENTRATE_JAR_ITEM = tabbed(ITEMS.registerItem("concentrate_jar",
            p -> new BlockItem(CONCENTRATE_JAR.get(), p.stacksTo(1).component(STORED_XP.get(), ConcentrateJarBlockEntity.CAPACITY))));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConcentrateJarBlockEntity>> CONCENTRATE_JAR_BE = BLOCK_ENTITIES.register("concentrate_jar",
            () -> BlockEntityType.Builder.of(ConcentrateJarBlockEntity::new, CONCENTRATE_JAR.get()).build(null));
    public static final DeferredItem<ConcentratePhialItem> CONCENTRATE_PHIAL = tabbed(ITEMS.registerItem("concentrate_phial",
            p -> new ConcentratePhialItem(p.stacksTo(16).rarity(Rarity.UNCOMMON))));

    private static Map<String, DeferredBlock<Block>> runes() {
        Map<String, DeferredBlock<Block>> out = new LinkedHashMap<>();
        for (String r : RUNE_NAMES) {
            out.put(r, block(r.isEmpty() ? "runed_obsidian" : "runed_obsidian_" + r, Block::new,
                    p -> p.mapColor(MapColor.COLOR_BLACK).strength(25f, 1200f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static BaseFlowingFluid.Properties soup() {
        return new BaseFlowingFluid.Properties(VISHROOM_SOUP_TYPE, VISHROOM_SOUP, VISHROOM_SOUP_FLOWING).block(VISHROOM_SOUP_BLOCK)
                .bucket(VISHROOM_SOUP_BUCKET).slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(20);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.automagia"))
            .icon(() -> new ItemStack(HUNGRY_MAW.get()))
            .displayItems((params, out) -> TAB_ITEMS.forEach(i -> out.accept(i.get())))
            .build());

    private AMRegistry() {
    }

    private static Block.Properties amber(Block.Properties p) {
        return p.mapColor(MapColor.COLOR_ORANGE).strength(0.8f).sound(SoundType.GLASS).lightLevel(s -> 15).noOcclusion()
                .isRedstoneConductor((s, l, pos) -> false).isSuffocating((s, l, pos) -> false)
                .isViewBlocking((s, l, pos) -> false);
    }

    private static Block.Properties hourglass(Block.Properties p) {
        return p.mapColor(MapColor.WOOD).strength(0.6f).sound(SoundType.GLASS).noOcclusion();
    }

    private static Block.Properties redcrystal(Block.Properties p) {
        return p.mapColor(MapColor.COLOR_RED).strength(0.3f).sound(SoundType.AMETHYST_CLUSTER).noCollission().noOcclusion()
                .lightLevel(s -> s.getValue(RedcrystalBlock.POWER) > 0 ? 3 : 0).pushReaction(PushReaction.DESTROY);
    }

    private static Block.Properties chest(Block.Properties p) {
        return p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).ignitedByLava();
    }

    private static DeferredItem<Item> glyph(String name) {
        return tabbed(ITEMS.registerSimpleItem("glyph_" + name, new Item.Properties().stacksTo(16)));
    }

    private static DeferredItem<Item> simple(String name) {
        return tabbed(ITEMS.registerSimpleItem(name));
    }

    private static DeferredItem<TallyLensItem> lens(String name, TallyBoxBlock.Lens lens) {
        return tabbed(ITEMS.registerItem(name, p -> new TallyLensItem(lens, p)));
    }

    private static <I extends Item> DeferredItem<I> tabbed(DeferredItem<I> item) {
        TAB_ITEMS.add(item);
        return item;
    }

    private static DeferredItem<Item> sliver(String aspect) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem("sliver_" + aspect);
        TAB_ITEMS.add(item);
        return item;
    }

    private static <B extends Block> DeferredBlock<B> block(String name, Function<Block.Properties, B> factory,
                                                           UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> block = blockP(name, factory, props);
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    private static <B extends Block> DeferredBlock<B> blockP(String name, Function<Block.Properties, ? extends B> factory,
                                                            UnaryOperator<Block.Properties> props) {
        return BLOCKS.registerBlock(name, factory, props.apply(Block.Properties.of()));
    }

    static void register(IEventBus bus) {
        // worlds saved before the id became "automagia" still find every entry
        oldIds(BLOCKS);
        oldIds(ITEMS);
        oldIds(BLOCK_ENTITIES);
        oldIds(COMPONENTS);
        oldIds(MENUS);
        oldIds(ENTITIES);
        oldIds(RECIPE_SERIALIZERS);
        oldIds(FLUID_TYPES);
        oldIds(FLUIDS);
        oldIds(ATTACHMENT_TYPES);
        oldIds(SPELL_BEHAVIORS);
        oldIds(RECIPE_TYPES);
        oldIds(TABS);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        COMPONENTS.register(bus);
        MENUS.register(bus);
        ENTITIES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        ATTACHMENT_TYPES.register(bus);
        SPELL_BEHAVIORS.register(bus);
        RECIPE_TYPES.register(bus);
        TABS.register(bus);
    }

    private static void oldIds(DeferredRegister<?> reg) {
        for (var holder : reg.getEntries()) {
            reg.addAlias(ResourceLocation.fromNamespaceAndPath(LegacyIds.OLD, holder.getId().getPath()), holder.getId());
        }
    }
}
